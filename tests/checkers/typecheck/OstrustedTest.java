package checkers.typecheck;

import org.checkerframework.framework.test.CheckerFrameworkPerFileTest;
import org.checkerframework.framework.test.TestUtilities;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class OstrustedTest extends CheckerFrameworkPerFileTest {

    public OstrustedTest(File testFile) {
        super(testFile,  ostrusted.OsTrustedChecker.class, "ostrusted",
                "-Anomsgtext",  "-Astubs=src/ostrusted/jdk.astub", "-d", "tests/build/outputdir");
    }

    @Parameters
    public static List<File> getTestFiles(){
        List<File> testfiles = new ArrayList<>();
        testfiles.addAll(TestUtilities.findRelativeNestedJavaFiles("testdata", "ostrusted-not-inferrable-test"));
        return testfiles;
    }
}
