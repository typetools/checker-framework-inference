import ostrusted.qual.*;
import ostrusted.qual.OsUntrusted;

import java.io.File;
import java.io.IOException;
import java.lang.Runtime;

class SystemLoad {

    @OsTrusted String trustedField = "";
    @OsUntrusted String untrustedField = "";
    String unknownField = "";

    public void loadContext(String unknownParam, String someParam1, String someParam2) {
        System.load(trustedField);

        // :: error: (argument.type.incompatible)
        System.load(untrustedField);

        // :: fixable-error: (argument.type.incompatible)
        System.load(unknownField);

        System.loadLibrary(trustedField);

        // :: error: (argument.type.incompatible)
        System.loadLibrary(untrustedField);

        // :: fixable-error: (argument.type.incompatible)
        System.loadLibrary(unknownField);

        System.loadLibrary("some" + "Lib" + "name" );

        // :: fixable-error: (argument.type.incompatible)
        System.loadLibrary(unknownParam + "name");

        // :: fixable-error: (argument.type.incompatible)
        System.loadLibrary(someParam1 + someParam2);

        String someLocal1 = "";
        String someLocal2 = "";
        System.loadLibrary(someLocal1 + someLocal2);
    }

}