package checkers.inference.model.serialization;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import checkers.inference.model.LubVariableSlot;
import checkers.inference.model.ImplicationConstraint;
import org.sat4j.core.VecInt;

import checkers.inference.SlotManager;
import checkers.inference.model.ArithmeticConstraint;
import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.CombineConstraint;
import checkers.inference.model.ComparableConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.ExistentialConstraint;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.InequalityConstraint;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Serializer;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;

/**
 */
public abstract class CnfVecIntSerializer implements Serializer<VecInt[], VecInt[]> {
    private final SlotManager slotManager;

    /** var representing whether or not some potential var exists mapped to that potential var
     * <p>var exists -> var</p>**/
    private final Map<Integer, Integer> existentialToPotentialVar = new HashMap<>();

    public CnfVecIntSerializer(SlotManager slotManager) {
        this.slotManager = slotManager;
    }

    public Map<Integer, Integer> getExistentialToPotentialVar() {
        return existentialToPotentialVar;
    }

    @Override
    public VecInt[] serialize(SubtypeConstraint constraint) {
        return new VariableCombos<SubtypeConstraint>() {

            @Override
            protected VecInt[] constant_variable(ConstantSlot subtype, VariableSlot supertype, SubtypeConstraint constraint) {

                if (isTop(subtype)) {
                    return asVecArray(-supertype.getId());
                }

                return emptyClauses;
            }

            @Override
            protected VecInt[] variable_constant(VariableSlot subtype, ConstantSlot supertype, SubtypeConstraint constraint) {
                if (!isTop(supertype)) {
                    return asVecArray(subtype.getId());
                }

                return emptyClauses;
            }

            @Override
            protected VecInt[] variable_variable(VariableSlot subtype, VariableSlot supertype, SubtypeConstraint constraint) {

                // this is supertype => subtype which is the equivalent of (!supertype v subtype)
                return asVecArray(-supertype.getId(), subtype.getId());
            }

        }.accept(constraint.getSubtype(), constraint.getSupertype(), constraint);
    }

    @Override
    public VecInt[] serialize(EqualityConstraint constraint) {

        return new VariableCombos<EqualityConstraint>() {

            @Override
            protected VecInt[] constant_variable(ConstantSlot slot1, VariableSlot slot2, EqualityConstraint constraint) {

                if (isTop(slot1)) {
                    return asVecArray(-slot2.getId());
                } else {
                    return asVecArray(slot2.getId());
                }
            }

            @Override
            protected VecInt[] variable_constant(VariableSlot slot1, ConstantSlot slot2, EqualityConstraint constraint) {
                return constant_variable(slot2, slot1, constraint);
            }

            @Override
            protected VecInt[] variable_variable(VariableSlot slot1, VariableSlot slot2, EqualityConstraint constraint) {

                // a <=> b which is the same as (!a v b) & (!b v a)
                return new VecInt[]{
                    asVec(-slot1.getId(),  slot2.getId()),
                    asVec( slot1.getId(), -slot2.getId())
                };
            }

        }.accept(constraint.getFirst(), constraint.getSecond(), constraint);

    }

    @Override
    public VecInt[] serialize(InequalityConstraint constraint) {
        return new VariableCombos<InequalityConstraint>() {

            @Override
            protected VecInt[] constant_variable(ConstantSlot slot1, VariableSlot slot2, InequalityConstraint constraint) {

                if (isTop(slot1)) {
                    return asVecArray(slot2.getId());
                } else {
                    return asVecArray(-slot2.getId());
                }
            }

            @Override
            protected VecInt[] variable_constant(VariableSlot slot1, ConstantSlot slot2, InequalityConstraint constraint) {
                return constant_variable(slot2, slot1, constraint);
            }

            @Override
            protected VecInt[] variable_variable(VariableSlot slot1, VariableSlot slot2, InequalityConstraint constraint) {

                // a <=> !b which is the same as (!a v !b) & (b v a)
                return new VecInt[]{
                        asVec(-slot1.getId(), -slot2.getId()),
                        asVec( slot1.getId(),  slot2.getId())
                };
            }

        }.accept(constraint.getFirst(), constraint.getSecond(), constraint);
    }


    @Override
    public VecInt[] serialize(ExistentialConstraint constraint) {
        // holds a list of Integers that should be prepended to the current set of constraints
        // being generated.  This will create "fake" variables that indicate whether or not
        // another variable exists

        // TODO: THIS ONLY WORKS IF THE CONSTRAINTS ARE NORMALIZED
        // TODO: WE SHOULD INSTEAD PIPE THROUGH THE ExistentialVariable ID
        Integer existentialId = existentialToPotentialVar.get(constraint.getPotentialVariable().getId());
        if (existentialId == null) {
            // existentialId should not overlap with the Id of real slots in slot manager
            // thus by computing sum of total slots number in slot manager
            // and the size of existentialToPotentialVar and plus 1 to get next id of existential Id here
            existentialId = slotManager.getNumberOfSlots() + existentialToPotentialVar.size() + 1;
            this.existentialToPotentialVar.put(Integer.valueOf(existentialId), Integer.valueOf(constraint.getPotentialVariable().getId()));
        }

        /**
         * if we have an existential constraint of the form:
         * if (a exists) {
         *   a <: b
         * } else {
         *   c <: b
         * }
         *
         * Let E be a new variable that implies that a exists
         * The above existential constraint becomes:
         * (E => a <: b) && (!E => c <: b)
         *
         * Recall:   x <: y  <=> !x | y
         * Then the existential constraint becomes:
         * (E => a | !b) && (!E => c | !b)
         *
         * We then convert => using material implication we get:
         * (!E | a | !b) && (E | c | !b)
         *
         * So, we do this for every constraint in the if block (i.e. the potentialConstraints)
         * and for every constraint in the else block (i.e. the alternativeConstraints)
         */
        List<VecInt> potentialClauses   = convertAll(constraint.potentialConstraints());
        List<VecInt> alternativeClauses = convertAll(constraint.getAlternateConstraints());

        for (VecInt clause : potentialClauses) {
            clause.insertFirst(-existentialId);
        }

        for (VecInt clause : alternativeClauses) {
            clause.insertFirst(existentialId);
        }

        VecInt[] clauses = new VecInt[potentialClauses.size() + alternativeClauses.size()];
        potentialClauses.toArray(clauses);

        int index = 0;
        for (VecInt clause : alternativeClauses) {
            clauses[potentialClauses.size() + index] = clause;
            index += 1;
        }

        return clauses;
    }

    public boolean emptyClause(VecInt ... clauses) {
        for (VecInt clause : clauses) {
            if (clause.size() == 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public VecInt[] serialize(VariableSlot slot) {
        // doesn't really mean anything
        return null;
    }

    @Override
    public VecInt[] serialize(RefinementVariableSlot slot) {
        // doesn't really mean anything
        return null;
    }

    @Override
    public VecInt[] serialize(ConstantSlot slot) {
        // doesn't really mean anything
        return null;
    }

    @Override
    public VecInt[] serialize(CombVariableSlot slot) {
        // doesn't really mean anything
        return null;
    }

    @Override
    public VecInt[] serialize(LubVariableSlot slot) {
        return null;
    }

    @Override
    public VecInt[] serialize(ExistentialVariableSlot slot) {
        // See checkers.inference.ConstraintNormalizer.normalize()
        throw new UnsupportedOperationException("Existential slots should be normalized away before serialization.");
    }

    @Override
    public VecInt[] serialize(ComparableConstraint comparableConstraint) {
        // not sure what this means
        return emptyClauses;
    }

    @Override
    public VecInt[] serialize(CombineConstraint combineConstraint) {
        // does this just say that the result is a subtype of the other 2?
        // not sure what this means
        return emptyClauses;
    }

    @Override
    public VecInt[] serialize(ArithmeticConstraint arithmeticConstraint) {
        throw new UnsupportedOperationException(
                "Serializing ArithmeticConstraint is unsupported in CnfVecIntSerializer");
    }

    @Override
    public VecInt[] serialize(PreferenceConstraint preferenceConstraint) {
        throw new UnsupportedOperationException("APPLY WEIGHTING FOR WEIGHTED MAX-SAT");
    }

    @Override
    public VecInt[] serialize(ImplicationConstraint implicationConstraint) {
        throw new UnsupportedOperationException("ImplicationConstraint is supported in more-advanced" +
                "MaxSAT backend. Use MaxSATSolver instead!");
    }

    public List<VecInt> convertAll(Iterable<Constraint> constraints) {
        return convertAll(constraints, new LinkedList<VecInt>());
    }

    public List<VecInt> convertAll(Iterable<Constraint> constraints, List<VecInt> results) {
        for (Constraint constraint : constraints) {
            for (VecInt res : constraint.serialize(this)) {
                if (res.size() != 0) {
                    results.add(res);
                }
            }
        }

        return results;
    }

    protected abstract boolean isTop(ConstantSlot constantSlot);

    VecInt asVec(int ... vars) {
        return new VecInt(vars);
    }

    /**
     * Creates a single clause using integers and then wraps that clause in an array
     * @param vars The positive/negative literals of the clause
     * @return A VecInt array containing just 1 element
     */
    VecInt[] asVecArray(int ... vars) {
        return new VecInt[]{new VecInt(vars)};
    }

    /**
     * Takes 2 slots and constraints, down casts them to the right VariableSlot or ConstantSlot
     * and passes them to the corresponding method.
     */
    class VariableCombos<T extends Constraint> {

        protected VecInt[] variable_variable(VariableSlot slot1, VariableSlot slot2, T constraint) {
            return defaultAction(slot1, slot2, constraint);
        }

        protected VecInt[] constant_variable(ConstantSlot slot1, VariableSlot slot2, T constraint) {
            return defaultAction(slot1, slot2, constraint);
        }

        protected VecInt[] variable_constant(VariableSlot slot1, ConstantSlot slot2, T constraint) {
            return defaultAction(slot1, slot2, constraint);
        }

        protected VecInt[] constant_constant(ConstantSlot slot1, ConstantSlot slot2, T constraint) {
            return defaultAction(slot1, slot2, constraint);
        }

        public VecInt[] defaultAction(Slot slot1, Slot slot2, T constraint) {
            return emptyClauses;
        }

        public VecInt[] accept(Slot slot1, Slot slot2, T constraint) {
            final VecInt[] result;
            if (slot1 instanceof ConstantSlot) {
                if (slot2 instanceof ConstantSlot) {
                    result = constant_constant((ConstantSlot) slot1, (ConstantSlot) slot2, constraint);
                } else {
                    result = constant_variable((ConstantSlot) slot1, (VariableSlot) slot2, constraint);
                }
            } else if (slot2 instanceof ConstantSlot) {
                result = variable_constant((VariableSlot) slot1, (ConstantSlot) slot2, constraint);
            } else {
                result = variable_variable((VariableSlot) slot1, (VariableSlot) slot2, constraint);
            }

            return result;
        }
    }

    public static final VecInt[] emptyClauses = new VecInt[0];
}
