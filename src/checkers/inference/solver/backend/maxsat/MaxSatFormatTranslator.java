package checkers.inference.solver.backend.maxsat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.javacutil.AnnotationUtils;
import org.sat4j.core.VecInt;

import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.CombineConstraint;
import checkers.inference.model.ComparableConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.ExistentialConstraint;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.InequalityConstraint;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.FormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.frontend.VariableCombos;

/**
 * MaxSatFormatTranslator converts constraint into array of VecInt as clauses.
 * 
 * @author jianchu
 *
 */

public class MaxSatFormatTranslator implements FormatTranslator<VecInt[], VecInt[], Integer> {

    protected final Lattice lattice;

    /**
     * typeToInt maps each type qualifier to an unique integer value starts from
     * 0 on continuous basis.
     */
    protected final Map<AnnotationMirror, Integer> typeToInt;

    /**
     * intToType maps an integer value to each type qualifier, which is a
     * reversed map of typeToInt.
     */
    protected final Map<Integer, AnnotationMirror> intToType;


    public MaxSatFormatTranslator(Lattice lattice) {
        this.lattice = lattice;

        // Initialize mappings between type and int.
        Map<AnnotationMirror, Integer>typeToIntRes = AnnotationUtils.createAnnotationMap();
        Map<Integer, AnnotationMirror> intToTypeRes = new HashMap<Integer, AnnotationMirror>();

        int curInt = 0;
        for (AnnotationMirror type : lattice.allTypes) {
            typeToIntRes.put(type, curInt);
            intToTypeRes.put(curInt, type);
            curInt ++;
        }

        typeToInt = Collections.unmodifiableMap(typeToIntRes);
        intToType = Collections.unmodifiableMap(intToTypeRes);
    }


    @Override
    public VecInt[] serialize(SubtypeConstraint constraint) {
        return new SubtypeVariableCombos(emptyClauses).accept(constraint.getSubtype(),
                constraint.getSupertype(), constraint);
    }

    protected class SubtypeVariableCombos extends VariableCombos<SubtypeConstraint, VecInt[]> {
        final Set<AnnotationMirror> mustNotBe = new HashSet<AnnotationMirror>();

        public SubtypeVariableCombos(VecInt[] emptyValue) {
            super(emptyValue);
        }

        @Override
        protected VecInt[] constant_variable(ConstantSlot subtype, VariableSlot supertype,
                SubtypeConstraint constraint) {
            if (AnnotationUtils.areSame(subtype.getValue(), lattice.top)) {
                return VectorUtils.asVecArray(
                        MathUtils.mapIdToMatrixEntry(supertype.getId(), typeToInt.get(lattice.top), lattice));
            }
            if (lattice.subType.get(subtype.getValue()) != null) {
                mustNotBe.addAll(lattice.subType.get(subtype.getValue()));
            }

            if (lattice.incomparableType.keySet().contains(subtype.getValue())) {
                mustNotBe.addAll(lattice.incomparableType.get(subtype.getValue()));
            }
            return getMustNotBe(mustNotBe, supertype, subtype);
        }

        @Override
        protected VecInt[] variable_constant(VariableSlot subtype, ConstantSlot supertype,
                SubtypeConstraint constraint) {

            if (AnnotationUtils.areSame(supertype.getValue(), lattice.bottom)) {
                return VectorUtils.asVecArray(
                        MathUtils.mapIdToMatrixEntry(subtype.getId(), typeToInt.get(lattice.bottom), lattice));
            }

            if (lattice.superType.get(supertype.getValue()) != null) {
                mustNotBe.addAll(lattice.superType.get(supertype.getValue()));
            }
            if (lattice.incomparableType.keySet().contains(supertype.getValue())) {
                mustNotBe.addAll(lattice.incomparableType.get(supertype.getValue()));
            }
            return getMustNotBe(mustNotBe, subtype, supertype);
        }

        @Override
        protected VecInt[] variable_variable(VariableSlot subtype, VariableSlot supertype,
                SubtypeConstraint constraint) {

            // if subtype is top, then supertype is top.
            // if supertype is bottom, then subtype is bottom.
            VecInt supertypeOfTop = VectorUtils.asVec(
                    -MathUtils.mapIdToMatrixEntry(subtype.getId(), typeToInt.get(lattice.top), lattice),
                    MathUtils.mapIdToMatrixEntry(supertype.getId(), typeToInt.get(lattice.top), lattice));
            VecInt subtypeOfBottom = VectorUtils.asVec(
                    -MathUtils.mapIdToMatrixEntry(supertype.getId(), typeToInt.get(lattice.bottom), lattice),
                    MathUtils.mapIdToMatrixEntry(subtype.getId(), typeToInt.get(lattice.bottom), lattice));

            List<VecInt> resultList = new ArrayList<VecInt>();
            for (AnnotationMirror type : lattice.allTypes) {
                // if we know subtype
                if (!AnnotationUtils.areSame(type, lattice.top)) {
                    resultList.add(VectorUtils
                            .asVec(getMaybe(type, subtype, supertype, lattice.superType.get(type))));
                }

                // if we know supertype
                if (!AnnotationUtils.areSame(type, lattice.bottom)) {
                    resultList.add(VectorUtils
                            .asVec(getMaybe(type, supertype, subtype, lattice.subType.get(type))));
                }
            }
            resultList.add(supertypeOfTop);
            resultList.add(subtypeOfBottom);
            VecInt[] result = resultList.toArray(new VecInt[resultList.size()]);
            return result;
        }

        @Override
        protected VecInt[] constant_constant(ConstantSlot subtype, ConstantSlot supertype,
                SubtypeConstraint constraint) {
            // if (!ConstantUtils.checkConstant(subtype, supertype, constraint))
            // {
            // ErrorReporter.errorAbort("Confliction in subtype constraint: " +
            // subtype.getValue()
            // + " is not subtype of " + supertype.getValue());
            // }
            return defaultAction(subtype, supertype, constraint);
        }
    }

    /**
     * for subtype constraint, if supertype is constant slot, then the subtype
     * cannot be the super type of supertype, same for subtype
     * 
     * @param mustNotBe
     * @param vSlot
     * @param cSlot
     * @return
     */
    private VecInt[] getMustNotBe(Set<AnnotationMirror> mustNotBe, VariableSlot vSlot, ConstantSlot cSlot) {

        List<Integer> resultList = new ArrayList<Integer>();

        for (AnnotationMirror sub : mustNotBe) {
            if (!AnnotationUtils.areSame(sub, cSlot.getValue())) {
                resultList.add(-MathUtils.mapIdToMatrixEntry(vSlot.getId(), typeToInt.get(sub), lattice));
            }
        }

        VecInt[] result = new VecInt[resultList.size()];
        if (resultList.size() > 0) {
            Iterator<Integer> iterator = resultList.iterator();
            for (int i = 0; i < result.length; i++) {
                result[i] = VectorUtils.asVec(iterator.next().intValue());
            }
            return result;
        }
        return emptyClauses;
    }

    /**
     * 
     * @param type
     * @param knownType
     * @param unknownType
     * @param maybeSet
     * @return
     */
    private int[] getMaybe(AnnotationMirror type, VariableSlot knownType, VariableSlot unknownType,
            Collection<AnnotationMirror> maybeSet) {
        int[] maybeArray = new int[maybeSet.size() + 1];
        int i = 1;
        maybeArray[0] = -MathUtils.mapIdToMatrixEntry(knownType.getId(), typeToInt.get(type), lattice);
        for (AnnotationMirror sup : maybeSet) {
            maybeArray[i] = MathUtils.mapIdToMatrixEntry(unknownType.getId(), typeToInt.get(sup), lattice);
            i++;
        }
        return maybeArray;
    }

    @Override
    public VecInt[] serialize(EqualityConstraint constraint) {
        return new EqualityVariableCombos(emptyClauses).accept(constraint.getFirst(),
                constraint.getSecond(), constraint);
    }

    protected class EqualityVariableCombos extends VariableCombos<EqualityConstraint, VecInt[]> {
        public EqualityVariableCombos(VecInt[] emptyValue) {
            super(emptyValue);
        }

        @Override
        protected VecInt[] constant_variable(ConstantSlot slot1, VariableSlot slot2,
                EqualityConstraint constraint) {
            if (lattice.allTypes.contains(slot1.getValue())) {
                return VectorUtils.asVecArray(
                        MathUtils.mapIdToMatrixEntry(slot2.getId(), typeToInt.get(slot1.getValue()), lattice));
            } else {
                return emptyClauses;
            }
        }

        @Override
        protected VecInt[] variable_constant(VariableSlot slot1, ConstantSlot slot2,
                EqualityConstraint constraint) {
            return constant_variable(slot2, slot1, constraint);
        }

        @Override
        protected VecInt[] variable_variable(VariableSlot slot1, VariableSlot slot2,
                EqualityConstraint constraint) {
            // a <=> b which is the same as (!a v b) & (!b v a)
            VecInt[] result = new VecInt[lattice.numTypes * 2];
            int i = 0;
            for (AnnotationMirror type : lattice.allTypes) {
                if (lattice.allTypes.contains(type)) {
                    result[i] = VectorUtils.asVec(
                            -MathUtils.mapIdToMatrixEntry(slot1.getId(), typeToInt.get(type), lattice),
                            MathUtils.mapIdToMatrixEntry(slot2.getId(), typeToInt.get(type), lattice));
                    result[i + 1] = VectorUtils.asVec(
                            -MathUtils.mapIdToMatrixEntry(slot2.getId(), typeToInt.get(type), lattice),
                            MathUtils.mapIdToMatrixEntry(slot1.getId(), typeToInt.get(type), lattice));
                    i = i + 2;
                }
            }
            return result;
        }

        @Override
        protected VecInt[] constant_constant(ConstantSlot slot1, ConstantSlot slot2,
                EqualityConstraint constraint) {
            // if (!ConstantUtils.checkConstant(slot1, slot2, constraint)) {
            // ErrorReporter.errorAbort("Confliction in equality constraint: " +
            // slot1.getValue()
            // + " is not equal to " + slot2.getValue());
            // }

            return defaultAction(slot1, slot2, constraint);
        }
    }

    @Override
    public VecInt[] serialize(InequalityConstraint constraint) {
        return new InequalityVariableCombos(emptyClauses).accept(constraint.getFirst(),
                constraint.getSecond(), constraint);
    }

    protected class InequalityVariableCombos extends VariableCombos<InequalityConstraint, VecInt[]> {
        public InequalityVariableCombos(VecInt[] emptyValue) {
            super(emptyValue);
        }

        @Override
        protected VecInt[] constant_variable(ConstantSlot slot1, VariableSlot slot2,
                InequalityConstraint constraint) {
            if (lattice.allTypes.contains(slot1.getValue())) {
                return VectorUtils.asVecArray(
                        -MathUtils.mapIdToMatrixEntry(slot2.getId(), typeToInt.get(slot1.getValue()), lattice));
            } else {
                return emptyClauses;
            }
        }

        @Override
        protected VecInt[] variable_constant(VariableSlot slot1, ConstantSlot slot2,
                InequalityConstraint constraint) {
            return constant_variable(slot2, slot1, constraint);
        }

        @Override
        protected VecInt[] variable_variable(VariableSlot slot1, VariableSlot slot2,
                InequalityConstraint constraint) {
            // a <=> !b which is the same as (!a v !b) & (b v a)
            VecInt[] result = new VecInt[lattice.numTypes * 2];
            int i = 0;
            for (AnnotationMirror type : lattice.allTypes) {
                if (lattice.allTypes.contains(type)) {
                    result[i] = VectorUtils.asVec(
                            -MathUtils.mapIdToMatrixEntry(slot1.getId(), typeToInt.get(type), lattice),
                            -MathUtils.mapIdToMatrixEntry(slot2.getId(), typeToInt.get(type), lattice));
                    result[i + 1] = VectorUtils.asVec(
                            MathUtils.mapIdToMatrixEntry(slot2.getId(), typeToInt.get(type), lattice),
                            MathUtils.mapIdToMatrixEntry(slot1.getId(), typeToInt.get(type), lattice));
                    i = i + 2;
                }
            }
            return result;
        }

        @Override
        protected VecInt[] constant_constant(ConstantSlot slot1, ConstantSlot slot2,
                InequalityConstraint constraint) {
            // if (!ConstantUtils.checkConstant(slot1, slot2, constraint)) {
            // ErrorReporter.errorAbort("Confliction in inequality constraint: "
            // + slot1.getValue()
            // + " is equal to " + slot2.getValue());
            // }

            return defaultAction(slot1, slot2, constraint);
        }
    }

    @Override
    public VecInt[] serialize(ComparableConstraint constraint) {
        return new ComparableVariableCombos(emptyClauses).accept(constraint.getFirst(),
                constraint.getSecond(), constraint);
    }

    protected class ComparableVariableCombos extends VariableCombos<ComparableConstraint, VecInt[]> {
        public ComparableVariableCombos(VecInt[] emptyValue) {
            super(emptyValue);
        }

        @Override
        protected VecInt[] variable_variable(VariableSlot slot1, VariableSlot slot2,
                ComparableConstraint constraint) {
            // a <=> !b which is the same as (!a v !b) & (b v a)
            List<VecInt> list = new ArrayList<VecInt>();
            for (AnnotationMirror type : lattice.allTypes) {
                if (lattice.incomparableType.keySet().contains(type)) {
                    for (AnnotationMirror notComparable : lattice.incomparableType.get(type)) {
                        list.add(VectorUtils.asVec(
                                -MathUtils.mapIdToMatrixEntry(slot1.getId(), typeToInt.get(type), lattice),
                                -MathUtils.mapIdToMatrixEntry(slot2.getId(), typeToInt.get(notComparable), lattice),
                                MathUtils.mapIdToMatrixEntry(slot2.getId(), typeToInt.get(notComparable), lattice),
                                MathUtils.mapIdToMatrixEntry(slot1.getId(), typeToInt.get(type), lattice)));
                    }
                }
            }
            VecInt[] result = list.toArray(new VecInt[list.size()]);
            return result;
        }

        @Override
        protected VecInt[] constant_constant(ConstantSlot slot1, ConstantSlot slot2,
                ComparableConstraint constraint) {
            // if (!ConstantUtils.checkConstant(slot1, slot2, constraint)) {
            // ErrorReporter.errorAbort("Confliction in comparable constraint: "
            // + slot1.getValue()
            // + " is not comparable to " + slot2.getValue());
            // }

            return defaultAction(slot1, slot2, constraint);
        }
    }


    @Override
    public VecInt[] serialize(ExistentialConstraint constraint) {
        return emptyClauses;
    }

    @Override
    public VecInt[] serialize(VariableSlot slot) {
        return null;
    }

    @Override
    public VecInt[] serialize(ConstantSlot slot) {
        return null;
    }

    @Override
    public VecInt[] serialize(ExistentialVariableSlot slot) {
        return null;
    }

    @Override
    public VecInt[] serialize(RefinementVariableSlot slot) {
        return null;
    }

    @Override
    public VecInt[] serialize(CombVariableSlot slot) {
        return null;
    }

    @Override
    public VecInt[] serialize(CombineConstraint combineConstraint) {
        return emptyClauses;
    }

    // TODO: we should consider the situation that the type annotations with
    // different weights.
    @Override
    public VecInt[] serialize(PreferenceConstraint preferenceConstraint) {
        VariableSlot vs = preferenceConstraint.getVariable();
        ConstantSlot cs = preferenceConstraint.getGoal();
        if (lattice.allTypes.contains(cs.getValue())) {
            return VectorUtils.asVecArray(MathUtils.mapIdToMatrixEntry(vs.getId(), typeToInt.get(cs.getValue()),
                    lattice));
        } else {
            return emptyClauses;
        }

    }

    protected static final VecInt[] emptyClauses = new VecInt[0];

    /**
     * generate well form clauses such that there is one and only one beta value
     * can be true.
     *
     * @param clauses
     */
    protected void generateOneHotClauses(List<VecInt> clauses, Integer varSlotId) {
        int[] leastOneIsTrue = new int[lattice.numTypes];
        for (Integer i : intToType.keySet()) {
            leastOneIsTrue[i] = MathUtils.mapIdToMatrixEntry(varSlotId, i.intValue(), lattice);
        }
        clauses.add(VectorUtils.asVec(leastOneIsTrue));
        List<Integer> varList = new ArrayList<Integer>(intToType.keySet());
        for (int i = 0; i < varList.size(); i++) {
            for (int j = i + 1; j < varList.size(); j++) {
                VecInt vecInt = new VecInt(2);
                vecInt.push(-MathUtils.mapIdToMatrixEntry(varSlotId, varList.get(i), lattice));
                vecInt.push(-MathUtils.mapIdToMatrixEntry(varSlotId, varList.get(j), lattice));
                clauses.add(vecInt);
            }
        }
    }


    @Override
    public AnnotationMirror decodeSolution(Integer var, ProcessingEnvironment processingEnvironment) {
        return intToType.get(MathUtils.getIntRep(var, lattice));
    }

}
