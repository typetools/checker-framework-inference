package checkers.typecheck;

import org.checkerframework.framework.test.CheckerFrameworkPerFileTest;
import org.checkerframework.framework.test.TestUtilities;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DataflowTest extends CheckerFrameworkPerFileTest {

    public DataflowTest(File testFile) {
        super(testFile,  dataflow.DataflowChecker.class, "dataflow",
                "-Anomsgtext", "-d", "tests/build/outputdir");
    }

    @Parameters
    public static List<File> getTestFiles(){
        List<File> testfiles = new ArrayList<>();
        testfiles.addAll(TestUtilities.findRelativeNestedJavaFiles("testing", "dataflow-not-inferrable-test"));
        return testfiles;
    }
}
