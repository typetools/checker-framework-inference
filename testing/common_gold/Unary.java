
import checkers.inference.quals.VarAnnot;
import ostrusted.qual.*;

class Test {

    void compoundAssignment(@VarAnnot(3) int i, @VarAnnot(4) int j) {
        int x = i += j;
    }
// EqualityConstraint: [@ostrusted.qual.OsTrusted, VariableAnnoPos(1)]
// EqualityConstraint: [@ostrusted.qual.OsTrusted, VariableAnnoPos(2)]
// EqualityConstraint: [@ostrusted.qual.OsTrusted, VariableAnnoPos(3)]
// EqualityConstraint: [@ostrusted.qual.OsTrusted, VariableAnnoPos(4)]
// SubtypeConstraint: [VariableAnnoPos(1), VariableAnnoPos(4)]
// SubtypeConstraint: [VariableAnnoPos(2), VariableAnnoPos(1)]

    void concat(@VarAnnot(6) String a, @VarAnnot(7) String b) {
        String c = a + b;
    }
// SubtypeConstraint: [VariableAnnoPos(1), CombVariableAnnoPos(4)]
// SubtypeConstraint: [VariableAnnoPos(2), CombVariableAnnoPos(4)]
// SubtypeConstraint: [CombVariableAnnoPos(4), VariableAnnoPos(3)]

    void unary(@VarAnnot(9) int i) {
        int x = i++;
    }
//    ALl types are OsTrusted
//
//EqualityConstraint: [@ostrusted.qual.OsTrusted, VariableAnnoPos(1)]
//EqualityConstraint: [@ostrusted.qual.OsTrusted, VariableAnnoPos(2)]
//EqualityConstraint: [@ostrusted.qual.OsTrusted, VariableAnnoPos(3)]
//SubtypeConstraint: [VariableAnnoPos(1), VariableAnnoPos(3)]
}
