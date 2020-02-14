package checkers.inference.test;

import org.checkerframework.framework.test.CheckerFrameworkPerFileTest;
import org.checkerframework.framework.test.TestUtilities;
import org.checkerframework.javacutil.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.AbstractProcessor;

import org.junit.Test;

public abstract class CFInferenceTest extends CheckerFrameworkPerFileTest {

    public static final boolean isAtMost7Jvm;

    static {
        isAtMost7Jvm = org.checkerframework.javacutil.PluginUtil.getJreVersion() <= 1.7d;
    }

    public CFInferenceTest(File testFile, Class<? extends AbstractProcessor> checker,
                           String testDir, String... checkerOptions) {
        super(testFile, checker, testDir, checkerOptions);
    }

    public boolean useHacks() {
        return TestUtilities.testBooleanProperty("use.hacks");
    }

    public abstract Pair<String, List<String>> getSolverNameAndOptions();

    public List<String> getAdditionalInferenceOptions() {
        return new ArrayList<String>();
    }

    public String getPathToAfuScripts() {
        return System.getProperty("path.afu.scripts");
    }

    public String getPathToInferenceScript() {
        return System.getProperty("path.inference.script");
    }

    @Override
    @Test
    public void run() {
        boolean shouldEmitDebugInfo = TestUtilities.testBooleanProperty("emit.test.debug");
        Pair<String, List<String>> solverArgs = getSolverNameAndOptions();

        final File testDataDir = new File("testdata");

        InferenceTestConfiguration config = InferenceTestConfigurationBuilder.buildDefaultConfiguration(testDir,
                testFile, testDataDir, checkerName, checkerOptions, getAdditionalInferenceOptions(), solverArgs.first,
                solverArgs.second, useHacks(), shouldEmitDebugInfo, getPathToAfuScripts(), getPathToInferenceScript());

        InferenceTestResult testResult = new InferenceTestExecutor().runTest(config);
        InferenceTestUtilities.assertResultsAreValid(testResult);
    }
}
