package checkers.inference;

import checkers.inference.test.CFInferenceTest;
import org.checkerframework.framework.test.TestUtilities;
import org.checkerframework.javacutil.Pair;
import org.junit.runners.Parameterized.Parameters;
import sparta.checkers.propagation.SpartaSourceSolver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SpartaSourceTest extends CFInferenceTest {

    public SpartaSourceTest(File testFile) {
        super(testFile,  sparta.checkers.SpartaSourceChecker.class, "sparta"+File.separator+"checkers",
                "-Anomsgtext",  "-Astubs=src/sparta/checkers/information_flow.astub", "-d", "tests/build/outputdir");
    }

    @Override
    public Pair<String, List<String>> getSolverNameAndOptions() {
        return Pair.<String, List<String>>of(SpartaSourceSolver.class.getCanonicalName(), new ArrayList<String>());
    }

    @Parameters
    public static List<File> getTestFiles(){
        List<File> testfiles = new ArrayList<>();//InferenceTestUtilities.findAllSystemTests();
        TestUtilities.filterOutJdk8Sources(testfiles);
        testfiles.addAll(TestUtilities.findRelativeNestedJavaFiles("testdata", "sparta-source"));
        return testfiles;
    }
}
