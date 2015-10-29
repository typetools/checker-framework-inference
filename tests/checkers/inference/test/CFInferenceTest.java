package checkers.inference.test;

import org.checkerframework.framework.test.CheckerFrameworkTest;
import org.checkerframework.framework.test.TestUtilities;
import org.checkerframework.javacutil.Pair;
import org.junit.Test;

import javax.annotation.processing.AbstractProcessor;
import java.io.File;
import java.lang.System;
import java.util.ArrayList;
import java.util.List;

import static checkers.inference.test.InferenceTestConfigurationBuilder.buildDefaultConfiguration;

public abstract class CFInferenceTest extends CheckerFrameworkTest {

    public CFInferenceTest(File testFile, Class<? extends AbstractProcessor> checker,
                           String checkerDir, String... checkerOptions) {
        super(testFile, checker, checkerDir, checkerOptions);
    }

    public boolean useHacks() {
        return TestUtilities.testBooleanProperty("use.hacks");
    }

    public abstract Pair<String, List<String>> getSolverNameAndOptions();

    public List<String> getAdditionalInferenceOptions() { return new ArrayList<String>(); }
    public String getPathToAfuScripts(){return System.getProperty("path.afu.scripts");}


    @Test
    public void run() {
        boolean shouldEmitDebugInfo = TestUtilities.testBooleanProperty("emit.test.debug");
        Pair<String, List<String>> solverArgs = getSolverNameAndOptions();

        final File testDataDir = new File("testdata");

        InferenceTestConfiguration config =
                buildDefaultConfiguration(checkerDir, testFile, testDataDir, checkerName, checkerOptions,
                        getAdditionalInferenceOptions(), solverArgs.first, solverArgs.second,
                        useHacks(), shouldEmitDebugInfo,  getPathToAfuScripts());

        InferenceTestResult testResult = new InferenceTestExecutor().runTest(config);
        InferenceTestUtilities.assertResultsAreValid(testResult);
    }
}
