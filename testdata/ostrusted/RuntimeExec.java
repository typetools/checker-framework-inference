import ostrusted.quals.*;

class RuntimeExec {

    void basic(String utStr) {

        //:: fixable-error: (assignment.type.incompatible)
        @OsTrusted String trusted = utStr;


    }
}
