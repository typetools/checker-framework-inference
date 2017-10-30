import ostrusted.qual.*;
import ostrusted.qual.OsTrusted;
import ostrusted.qual.OsUntrusted;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder;
import java.lang.Runtime;
import java.util.List;
import java.util.Map;

class ProcessBuilding {
    @OsTrusted String trustedField;
    @OsUntrusted String untrustedField;
    String unknownField;

    List<@OsTrusted String> fieldOfTrusteds;
    List<@OsUntrusted String> fieldOfUntrusted;
    List<String> fieldOfUnknowns;

    public void strArrays(@OsTrusted String [] trustedArr, @OsUntrusted String untrustedArr, String [] unknownArr) {

        ProcessBuilder pb1 = new ProcessBuilder(trustedArr);

        // :: error: (argument.type.incompatible)
        ProcessBuilder pb2 = new ProcessBuilder(untrustedArr);

        // :: fixable-error: (argument.type.incompatible)
        ProcessBuilder pb3 = new ProcessBuilder(unknownArr);


        pb1.command(trustedArr);

        // :: error: (argument.type.incompatible)
        pb2.command(untrustedArr);

        // :: fixable-error: (argument.type.incompatible)
        pb3.command(unknownArr);
    }

    public void varArgs(@OsTrusted String trustedParam, String unknown1, String unknown2) {
        String local = "";

        ProcessBuilder pb4 = new ProcessBuilder("", "trusted", "literals");
        ProcessBuilder pb5 = new ProcessBuilder("", trustedParam, local);

        // :: error: (argument.type.incompatible)
        ProcessBuilder pb6 = new ProcessBuilder("", untrustedField, local);

        // :: fixable-error: (argument.type.incompatible)
        ProcessBuilder pb7 = new ProcessBuilder("", unknownField, local);

        // :: fixable-error: (argument.type.incompatible)
        ProcessBuilder pb8 = new ProcessBuilder(unknown1, unknown2);


        pb4.command("", "trusted", "literals");
        pb5.command("", trustedParam, local);

        // :: error: (argument.type.incompatible)
        pb6.command("", untrustedField, local);

        // :: fixable-error: (argument.type.incompatible)
        pb7.command("", unknownField, local);

        // :: fixable-error: (argument.type.incompatible)
        pb8.command(unknown1, unknown2);
    }

    public void stringLists(List<@OsTrusted String> listOfTrusteds, List<@OsUntrusted String> listOfUntrusted,
                            List<String> listOfUnknowns) {

        ProcessBuilder pb9 = new ProcessBuilder(listOfTrusteds);
        // :: error: (argument.type.incompatible)
        ProcessBuilder pb10 = new ProcessBuilder(listOfUntrusted);
        // :: fixable-error: (argument.type.incompatible)
        ProcessBuilder pb11 = new ProcessBuilder(listOfUnknowns);

        pb9.command(listOfTrusteds);
        // :: error: (argument.type.incompatible)
        pb10.command(listOfUntrusted);
        // :: fixable-error: (argument.type.incompatible)
        pb11.command(listOfUnknowns);
    }

    public void environment(ProcessBuilder pb, String unknownParam) {
        pb.environment().put("whatever", trustedField);
        // :: error: (argument.type.incompatible)
        pb.environment().put("otherWhatever", untrustedField);
        // :: fixable-error: (argument.type.incompatible)
        pb.environment().put("otherWhatever", unknownParam);

        Map<String, @OsTrusted String> trustMap = pb.environment();

        // :: error: (assignment.type.incompatible)
        Map<String, @OsUntrusted String> noTrustMap = pb.environment();

        // :: fixable-error: (assignment.type.incompatible)
        Map<String, String> unknownMap = pb.environment();
    }

    public void command(ProcessBuilder pbc, List<@OsTrusted String> cListOfTrusteds,
                        List<@OsUntrusted String> cListOfUntrusted, List<String> cListOfUnknowns) {
        cListOfTrusteds = pbc.command();
        // :: error: (assignment.type.incompatible)
        cListOfUntrusted = pbc.command();
        // :: fixable-error: (assignment.type.incompatible)
        cListOfUnknowns = pbc.command();

        ProcessBuilder pb12 = new ProcessBuilder(pbc.command());
    }

}