package checkers.inference.solver.backend.logiql.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.SubtypeConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.NameUtils;

public class LogiQLSubtypeConstraintEncoder extends LogiQLAbstractConstraintEncoder implements SubtypeConstraintEncoder<String> {

    public LogiQLSubtypeConstraintEncoder(Lattice lattice) {
        super(lattice);
    }

    @Override
    public String encodeVariable_Variable(VariableSlot subtype, VariableSlot supertype) {
        String logiQLData = "+subtypeConstraint(v1, v2), +variable(v1), +hasvariableName[v1] = "
                + subtype.getId() + ", +variable(v2), +hasvariableName[v2] = " + supertype.getId()
                + ".\n";
        return logiQLData;
    }

    @Override
    public String encodeVariable_Constant(VariableSlot subtype, ConstantSlot supertype) {
        String supertypeName = NameUtils.getSimpleName(supertype.getValue());
        int subtypeId = subtype.getId();
        String logiQLData = "+subtypeConstraintRightConstant(v, c), +variable(v), +hasvariableName[v] = "
                + subtypeId + ", +constant(c), +hasconstantName[c] = \"" + supertypeName + "\" .\n";
        return logiQLData;
    }

    @Override
    public String encodeConstant_Variable(ConstantSlot subtype, VariableSlot supertype) {
        String subtypeName = NameUtils.getSimpleName(subtype.getValue());
        int supertypeId = supertype.getId();
        String logiQLData = "+subtypeConstraintLeftConstant(c, v), +constant(c), +hasconstantName[c] = \""
                + subtypeName + "\", +variable(v), +hasvariableName[v] = " + supertypeId + ".\n";
        return logiQLData;
    }
}
