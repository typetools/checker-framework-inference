package checkers.inference.test;

import org.checkerframework.framework.test2.OneByOneCheckerTest;
import org.checkerframework.framework.test2.TestUtilities;
import org.checkerframework.javacutil.Pair;
import org.junit.Test;

import javax.annotation.processing.AbstractProcessor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static checkers.inference.test.InferenceTestConfigurationBuilder.buildDefaultConfiguration;

public abstract class InferenceOneByOneTest extends OneByOneCheckerTest {

    public InferenceOneByOneTest(File testFile, Class<? extends AbstractProcessor> checker,
                                 String checkerDir, String... checkerOptions) {
        super(testFile, checker, checkerDir, checkerOptions);
    }

    public boolean useHacks() {
        return TestUtilities.testBooleanProperty("use.hacks");
    }

    public abstract Pair<String, List<String>> getSolverNameAndOptions();

    public List<String> getAdditionalInferenceOptions() { return new ArrayList<String>(); }

    @Test
    public void run() {
        boolean shouldEmitDebugInfo = TestUtilities.testBooleanProperty("emit.test.debug");
        Pair<String, List<String>> solverArgs = getSolverNameAndOptions();

        final File testDataDir = new File("testdata");

        InferenceTestConfiguration config =
                buildDefaultConfiguration(checkerDir, testFile, testDataDir, checkerName, checkerOptions,
                        getAdditionalInferenceOptions(), solverArgs.first, solverArgs.second,
                        useHacks(), shouldEmitDebugInfo);

        InferenceTestResult testResult = new InferenceTestExecutor().runTest(config);
        InferenceTestUtilities.assertResultsAreValid(testResult);
    }
}
