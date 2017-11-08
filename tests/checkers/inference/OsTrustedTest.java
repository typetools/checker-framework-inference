package checkers.inference;

import checkers.inference.solver.MaxSat2TypeSolver;
import checkers.inference.test.CFInferenceTest;
import org.checkerframework.framework.test.TestUtilities;
import org.checkerframework.javacutil.Pair;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class OsTrustedTest extends CFInferenceTest {

    public OsTrustedTest(File testFile) {
        super(testFile,  ostrusted.OsTrustedChecker.class, "ostrusted",
              "-Anomsgtext",  "-Astubs=src/ostrusted/jdk.astub", "-d", "tests/build/outputdir");
    }

    @Override
    public Pair<String, List<String>> getSolverNameAndOptions() {
        return Pair.<String, List<String>>of(MaxSat2TypeSolver.class.getCanonicalName(), new ArrayList<String>());
    }

    @Parameters
    public static List<File> getTestFiles(){
        List<File> testfiles = new ArrayList<>();//InferenceTestUtilities.findAllSystemTests();
        testfiles.addAll(TestUtilities.findRelativeNestedJavaFiles("testdata", "ostrusted-inferrable-test"));
        return testfiles;
    }
}
