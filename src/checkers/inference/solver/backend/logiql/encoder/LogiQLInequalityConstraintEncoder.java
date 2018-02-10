package checkers.inference.solver.backend.logiql.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.InequalityConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.NameUtils;

public class LogiQLInequalityConstraintEncoder extends LogiQLAbstractConstraintEncoder implements InequalityConstraintEncoder<String> {

    public LogiQLInequalityConstraintEncoder(Lattice lattice) {
        super(lattice);
    }

    @Override
    public String encodeVariable_Variable(VariableSlot fst, VariableSlot snd) {
        String logiQLData = "+inequalityConstraint(v1, v2), +variable(v1), +hasvariableName[v1] = "
                + fst.getId() + ", +variable(v2), +hasvariableName[v2] = " + snd.getId() + ".\n";
        return logiQLData;
    }

    @Override
    public String encodeVariable_Constant(VariableSlot fst, ConstantSlot snd) {
        return encodeConstant_Variable(snd, fst);
    }

    @Override
    public String encodeConstant_Variable(ConstantSlot fst, VariableSlot snd) {
        String constantName = NameUtils.getSimpleName(fst.getValue());
        int variableId = snd.getId();
        String logiQLData = "+inequalityConstraintContainsConstant(c, v), +constant(c), +hasconstantName[c] = \""
                + constantName + "\", +variable(v), +hasvariableName[v] = " + variableId + ".\n";
        return logiQLData;
    }
}
