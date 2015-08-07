package checkers.inference;

import checkers.inference.solver.MaxSat2TypeSolver;
import checkers.inference.test.DefaultInferenceTest;
import org.checkerframework.framework.test2.TestUtilities;
import org.checkerframework.javacutil.Pair;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class InterningTest extends DefaultInferenceTest {

    public InterningTest(File testFile) {
        super(testFile,  ostrusted.OsTrustedChecker.class, "ostrusted",
              "-Anomsgtext",  "-Astubs=src/ostrusted/jdk.astub", "-d", "tests/build/outputdir");
    }

    @Override
    public Pair<String, List<String>> getSolverNameAndOptions() {
        return Pair.<String, List<String>>of(MaxSat2TypeSolver.class.getCanonicalName(), new ArrayList<String>());
    }

    @Parameters
    public static Collection<Object[]> getTestFiles() {
        List<Object []> testfiles = new ArrayList<Object[]>();//InferenceTestUtilities.findAllSystemTests();
        testfiles.addAll(TestUtilities.findRelativeNestedJavaFiles("testdata", "ostrusted"));
        return testfiles;
    }
}
