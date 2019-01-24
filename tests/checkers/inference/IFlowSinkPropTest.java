package checkers.inference;

import checkers.inference.test.CFInferenceTest;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.framework.test.TestUtilities;
import org.checkerframework.javacutil.Pair;
import org.junit.runners.Parameterized.Parameters;
import sparta.checkers.IFlowSinkChecker;
import sparta.checkers.propagation.IFlowSinkSolver;

public class IFlowSinkPropTest extends CFInferenceTest {

    public IFlowSinkPropTest(File testFile) {
        super(
                testFile,
                IFlowSinkChecker.class,
                "sparta" + File.separator + "checkers",
                "-Anomsgtext",
                "-Astubs=src/sparta/checkers/information_flow.astub",
                "-d",
                "tests/build/outputdir");
    }

    @Override
    public Pair<String, List<String>> getSolverNameAndOptions() {
        return Pair.<String, List<String>>of(
                IFlowSinkSolver.class.getCanonicalName(), new ArrayList<String>());
    }

    @Parameters
    public static List<File> getTestFiles() {
        List<File> testfiles = new ArrayList<>(); // InferenceTestUtilities.findAllSystemTests();
        if (isAtMost7Jvm) {
            testfiles.addAll(TestUtilities.findRelativeNestedJavaFiles("testdata", "iflowsink"));
        }
        return testfiles;
    }
}
