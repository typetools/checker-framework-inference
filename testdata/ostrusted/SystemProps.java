import ostrusted.qual.*;
import ostrusted.qual.OsUntrusted;

import java.io.File;
import java.io.IOException;
import java.lang.Runtime;

class SystemProps {

    @OsTrusted String trustedField = "";
    @OsUntrusted String untrustedField = "";
    String unknownField = "";

    public void loadContext(String unknownParam, String someParam1, String someParam2) {
        System.setProperty("prop1", trustedField);

        // :: error: (argument.type.incompatible)
        System.setProperty("prop2", untrustedField);

        // :: fixable-error: (argument.type.incompatible)
        System.setProperty("prop3", unknownField);

        System.setProperty("prop4", "some" + "Lib" + "name");

        // :: fixable-error: (argument.type.incompatible)
        System.setProperty("prop5", unknownParam + "name");

        // :: fixable-error: (argument.type.incompatible)
        System.setProperty("prop6", someParam1 + someParam2);

        String someLocal1 = "";
        String someLocal2 = "";
        System.setProperty("prop7", someLocal1 + someLocal2);
    }

}