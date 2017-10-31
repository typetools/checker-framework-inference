package checkers.inference.solver.backend.logiql;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

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
import checkers.inference.solver.util.NameUtils;

/**
 * LogiQLFormatTranslator converts constraint into string as logiQL data.
 * 
 * @author jianchu
 *
 */
public class LogiQLFormatTranslator implements FormatTranslator<String, String, String> {


    public LogiQLFormatTranslator(Lattice lattice) {

    }

    @Override
    public String serialize(SubtypeConstraint constraint) {
        return new SubtypeVariableCombos(emptyString).accept(constraint.getSubtype(),
                constraint.getSupertype(),
                constraint);
    }

    protected class SubtypeVariableCombos extends VariableCombos<SubtypeConstraint,String> {   
            
        public SubtypeVariableCombos(String emptyValue) {
            super(emptyValue);
        }

        @Override
        protected String constant_variable(ConstantSlot subtype, VariableSlot supertype,
                SubtypeConstraint constraint) {
            String subtypeName = NameUtils.getSimpleName(subtype.getValue());
            int supertypeId = supertype.getId();
            String logiQLData = "+subtypeConstraintLeftConstant(c, v), +constant(c), +hasconstantName[c] = \""
                    + subtypeName + "\", +variable(v), +hasvariableName[v] = " + supertypeId + ".\n";
            return logiQLData;
        }

        @Override
        protected String variable_constant(VariableSlot subtype, ConstantSlot supertype,
                SubtypeConstraint constraint) {
            String supertypeName = NameUtils.getSimpleName(supertype.getValue());
            int subtypeId = subtype.getId();
            String logiQLData = "+subtypeConstraintRightConstant(v, c), +variable(v), +hasvariableName[v] = "
                    + subtypeId + ", +constant(c), +hasconstantName[c] = \"" + supertypeName + "\" .\n";
            return logiQLData;
        }

        @Override
        protected String variable_variable(VariableSlot subtype, VariableSlot supertype,
                SubtypeConstraint constraint) {
            String logiQLData = "+subtypeConstraint(v1, v2), +variable(v1), +hasvariableName[v1] = "
                    + subtype.getId() + ", +variable(v2), +hasvariableName[v2] = " + supertype.getId()
                    + ".\n";
            return logiQLData;
        }

        @Override
        protected String constant_constant(ConstantSlot slot1, ConstantSlot slot2,
                SubtypeConstraint constraint) {
            return defaultAction(slot1, slot2, constraint);
        }
    }


    @Override
    public String serialize(EqualityConstraint constraint) {
        return new EqualityVariableCombos(emptyString).accept(constraint.getFirst(),
                constraint.getSecond(), constraint);
    }

    protected class EqualityVariableCombos extends VariableCombos<EqualityConstraint, String> {

        public EqualityVariableCombos(String emptyValue) {
            super(emptyValue);
        }

        @Override
        protected String constant_variable(ConstantSlot slot1, VariableSlot slot2,
                EqualityConstraint constraint) {
            String constantName = NameUtils.getSimpleName(slot1.getValue());
            int variableId = slot2.getId();
            String logiQLData = "+equalityConstraintContainsConstant(c, v), +constant(c), +hasconstantName[c] = \""
                    + constantName + "\", +variable(v), +hasvariableName[v] = " + variableId + ".\n";
            return logiQLData;
        }

        @Override
        protected String variable_constant(VariableSlot slot1, ConstantSlot slot2,
                EqualityConstraint constraint) {
            return constant_variable(slot2, slot1, constraint);
        }

        @Override
        protected String variable_variable(VariableSlot slot1, VariableSlot slot2,
                EqualityConstraint constraint) {
            String logiQLData = "+equalityConstraint(v1, v2), +variable(v1), +hasvariableName[v1] = "
                    + slot1.getId() + ", +variable(v2), +hasvariableName[v2] = " + slot2.getId() + ".\n";
            return logiQLData;
        }

        @Override
        protected String constant_constant(ConstantSlot slot1, ConstantSlot slot2,
                EqualityConstraint constraint) {
            return defaultAction(slot1, slot2, constraint);
        }
    }

    @Override
    public String serialize(ExistentialConstraint constraint) {
        return null;
    }

    @Override
    public String serialize(InequalityConstraint constraint) {
        return new InequalityVariableCombos(emptyString).accept(constraint.getFirst(),
                constraint.getSecond(), constraint);
    }

    protected class InequalityVariableCombos extends VariableCombos<InequalityConstraint, String> {

        public InequalityVariableCombos(String emptyValue) {
            super(emptyValue);
        }
        
        @Override
        protected String constant_variable(ConstantSlot slot1, VariableSlot slot2,
                InequalityConstraint constraint) {
            String constantName = NameUtils.getSimpleName(slot1.getValue());
            int variableId = slot2.getId();
            String logiQLData = "+inequalityConstraintContainsConstant(c, v), +constant(c), +hasconstantName[c] = \""
                    + constantName + "\", +variable(v), +hasvariableName[v] = " + variableId + ".\n";
            return logiQLData;
        }

        @Override
        protected String variable_constant(VariableSlot slot1, ConstantSlot slot2,
                InequalityConstraint constraint) {
            return constant_variable(slot2, slot1, constraint);
        }

        @Override
        protected String variable_variable(VariableSlot slot1, VariableSlot slot2,
                InequalityConstraint constraint) {
            String logiQLData = "+inequalityConstraint(v1, v2), +variable(v1), +hasvariableName[v1] = "
                    + slot1.getId() + ", +variable(v2), +hasvariableName[v2] = " + slot2.getId() + ".\n";
            return logiQLData;
        }

        @Override
        protected String constant_constant(ConstantSlot slot1, ConstantSlot slot2,
                InequalityConstraint constraint) {
            return defaultAction(slot1, slot2, constraint);
        }
    }

    @Override
    public String serialize(VariableSlot slot) {
        return null;
    }

    @Override
    public String serialize(ConstantSlot slot) {
        return null;
    }

    @Override
    public String serialize(ExistentialVariableSlot slot) {
        return null;
    }

    @Override
    public String serialize(RefinementVariableSlot slot) {
        return null;
    }

    @Override
    public String serialize(CombVariableSlot slot) {
        return null;
    }

    @Override
    public String serialize(ComparableConstraint constraint) {
        return new ComparableVariableCombos(emptyString).accept(constraint.getFirst(),
                constraint.getSecond(), constraint);
    }

    protected class ComparableVariableCombos extends VariableCombos<ComparableConstraint, String> {
        public ComparableVariableCombos(String emptyValue) {
            super(emptyValue);
        }

        @Override
        protected String constant_variable(ConstantSlot slot1, VariableSlot slot2,
                ComparableConstraint constraint) {
            String constantName = NameUtils.getSimpleName(slot1.getValue());
            int variableId = slot2.getId();
            String logiQLData = "+equalityConstraintContainsConstant(c, v), +constant(c), +hasconstantName[c] = \""
                    + constantName + "\", +variable(v), +hasvariableName[v] = " + variableId + ".\n";
            return logiQLData;
        }

        @Override
        protected String variable_constant(VariableSlot slot1, ConstantSlot slot2,
                ComparableConstraint constraint) {
            return constant_variable(slot2, slot1, constraint);
        }

        @Override
        protected String variable_variable(VariableSlot slot1, VariableSlot slot2,
                ComparableConstraint constraint) {
            String logiQLData = "+comparableConstraint(v1, v2), +variable(v1), +hasvariableName[v1] = "
                    + slot1.getId() + ", +variable(v2), +hasvariableName[v2] = " + slot2.getId() + ".\n";
            return logiQLData;
        }

        @Override
        protected String constant_constant(ConstantSlot slot1, ConstantSlot slot2,
                ComparableConstraint constraint) {
            return defaultAction(slot1, slot2, constraint);
        }
    }

    @Override
    public String serialize(CombineConstraint combineConstraint) {
        return null;
    }

    @Override
    public String serialize(PreferenceConstraint preferenceConstraint) {
        return null;
    }

    public static final String emptyString = "";

    @Override
    public AnnotationMirror decodeSolution(String solution, ProcessingEnvironment processingEnvironment) {
        // TODO Refactor LogiQL backend to follow the design protocal.
        return null;
    }

}
