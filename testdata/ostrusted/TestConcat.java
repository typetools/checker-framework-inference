import ostrusted.quals.*;
import ostrusted.quals.OsUntrusted;


class TestConcat {

    @OsTrusted String trustedField;

    int field = -1;
    void test() {
        int size = 1;
        size += field;
    }

    @OsTrusted String sfield = "";
    void test2() {
        String slocal = "";
        slocal += field;

        //:: fixable-error: (assignment.type.incompatible)
        trustedField = slocal;

        String locTrusted = "";
        locTrusted += trustedField;
        locTrusted += "other trusted";
        trustedField = locTrusted;
    }

    @OsTrusted String osTrusted;
    String unknownField = "";
    void test3() {
        //:: fixable-error: (assignment.type.incompatible)
        osTrusted = unknownField;

        unknownField += "whatever" + "trusted";

        //:: fixable-error: (assignment.type.incompatible)
        osTrusted = unknownField;
    }
}
