import ostrusted.qual.*;
import ostrusted.qual.OsUntrusted;

import java.io.File;
import java.io.IOException;
import java.lang.Runtime;

class RuntimeLoad {

    @OsTrusted String trustedField = "";
    @OsUntrusted String untrustedField = "";
    String unknownField = "";

    public void loadContext(String unknownParam, String someParam1, String someParam2) {
        Runtime.getRuntime().load(trustedField);

        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().load(untrustedField);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().load(unknownField);

        Runtime.getRuntime().loadLibrary(trustedField);

        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().loadLibrary(untrustedField);

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().loadLibrary(unknownField);

        Runtime.getRuntime().loadLibrary("some" + "Lib" + "name" );

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().loadLibrary(unknownParam + "name");

        // :: fixable-error: (argument.type.incompatible)
        Runtime.getRuntime().loadLibrary(someParam1 + someParam2);

        String someLocal1 = "";
        String someLocal2 = "";
        Runtime.getRuntime().loadLibrary(someLocal1 + someLocal2);
    }

}