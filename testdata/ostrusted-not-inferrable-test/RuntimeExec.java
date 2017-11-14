import ostrusted.qual.*;
import ostrusted.qual.OsUntrusted;

import java.io.File;
import java.io.IOException;

class RuntimeExec {

    @OsTrusted String trustedField = "";
    @OsUntrusted String untrustedField = "";

    String unannotatedField = "";

    @OsTrusted String [] trustedFieldArr = new String[]{""};
    @OsUntrusted String [] untrustedFieldArr = new String[]{""};

    String [] unannotatedFieldArr = new String[]{""};

    void explicits(String utStr, @OsUntrusted String untrusted) {
        // :: fixable-error: (assignment.type.incompatible)
        @OsTrusted String trusted = utStr;

        // :: error: (assignment.type.incompatible)
        trusted = untrusted;
    }

    void runtimeExec(String execString, String[] execArray) throws IOException {
        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(execString);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(execArray);

        String fromGetStr = getStr();

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(fromGetStr);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(getStr2());


        //---------------------------------------------
        //Fields
        Runtime.getRuntime().exec(trustedField);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(unannotatedField);

        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().exec(untrustedField);

        Runtime.getRuntime().exec(trustedFieldArr);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(unannotatedFieldArr);

        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().exec(untrustedFieldArr);
    }

    void runtimeExec2(String execString2, String[] execArray2, String [] envp) throws IOException {
        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(execString2, envp);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(execArray2, envp);

        String fromGetStr = getStr3();

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(fromGetStr, envp);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(getStr4(), envp);

        //---------------------------------------------
        //Fields
        Runtime.getRuntime().exec(trustedField, envp);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(unannotatedField, envp);

        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().exec(untrustedField, envp);

        Runtime.getRuntime().exec(trustedFieldArr, envp);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(unannotatedFieldArr, envp);

        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().exec(untrustedFieldArr, envp);
    }

    void runtimeExec3(String execString3, String[] execArray3, String [] envp, File workingDir) throws IOException {
        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(execString3, envp, workingDir);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(execArray3, envp, workingDir);

        String fromGetStr = getStr5();

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(fromGetStr, envp, workingDir);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(getStr6(), envp, workingDir);

        //---------------------------------------------
        //Fields
        Runtime.getRuntime().exec(trustedField, envp, workingDir);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(unannotatedField, envp, workingDir);

        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().exec(untrustedField, envp, workingDir);

        Runtime.getRuntime().exec(trustedFieldArr, envp, workingDir);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().exec(unannotatedFieldArr, envp, workingDir);

        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().exec(untrustedFieldArr, envp, workingDir);
    }

    void badRuntimeExec(@OsUntrusted String untrusted, @OsUntrusted String [] arr, String [] envp, File workingDir) throws IOException {
        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().exec(untrusted);

        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().exec(untrusted, envp);

        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().exec(untrusted, envp, workingDir);


        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().exec(arr);

        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().exec(arr, envp);

        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().exec(arr, envp, workingDir);
    }

    //these should be inferred to be @OsTrusted because it is used in a runtim.exec
    String getStr() {
        return "";
    }

    String getStr2() {
        return "";
    }

    String getStr3() {
        return "";
    }

    String getStr4() {
        return "";
    }

    String getStr5() {
        return "";
    }

    String getStr6() {
        return "";
    }
}
