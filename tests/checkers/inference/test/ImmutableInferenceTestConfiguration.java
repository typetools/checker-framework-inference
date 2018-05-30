package checkers.inference.test;

import org.checkerframework.framework.test.ImmutableTestConfiguration;
import org.checkerframework.framework.test.TestConfiguration;
import org.checkerframework.framework.test.TestUtilities;

import java.io.File;
import java.util.List;
import java.util.Map;

public class ImmutableInferenceTestConfiguration implements InferenceTestConfiguration{
    private final File outputJaif;
    private final File testDataDir;
    private final File annotatedSourceDir;
    private final Map<String, String> inferenceJavacArgs;
    private final String solver;
    private final Map<String, String> solverArgs;
    private final boolean shouldUseHacks;
    private final String pathToAfuScripts;
    private final String pathToInferenceScript;
    private final TestConfiguration initialConfig;

    public ImmutableInferenceTestConfiguration(File outputJaif, File testDataDir, File annotatedSourceDir,
                                               Map<String, String> inferenceJavacArgs, String solver,
                                               Map<String, String> solverArgs, boolean shouldUseHacks, String pathToAfuScripts,
                                               String pathToInferenceScript, TestConfiguration initialConfig) {
        this.outputJaif = outputJaif;
        this.testDataDir = testDataDir;
        this.annotatedSourceDir = annotatedSourceDir;
        this.inferenceJavacArgs = inferenceJavacArgs;
        this.solver = solver;
        this.solverArgs = solverArgs;
        this.shouldUseHacks = shouldUseHacks;
        this.pathToAfuScripts = pathToAfuScripts;
        this.initialConfig = initialConfig;
        this.pathToInferenceScript = pathToInferenceScript;
    }

    @Override
    public File getOutputJaif() {
        return outputJaif;
    }

    @Override
    public File getAnnotatedSourceDir() {
        return annotatedSourceDir;
    }

    @Override
    public File getTestDataDir() {
        return testDataDir;
    }

    @Override
    public Map<String, String> getInferenceJavacArgs() {
        return inferenceJavacArgs;
    }

    public String getSolver() {
        return solver;
    }

    @Override
    public Map<String, String> getSolverArgs() {
        return solverArgs;
    }

    public boolean shouldUseHacks() {
        return shouldUseHacks;
    }

    public String getPathToAfuScripts() {
        return pathToAfuScripts;
    }

    @Override
    public String getPathToInferenceScript() {
        return pathToInferenceScript;
    }

    public TestConfiguration getInitialTypecheckConfig() {
        return initialConfig;
    }

    @Override
    public List<String> getFlatInferenceJavacArgs() {
        return TestUtilities.optionMapToList(inferenceJavacArgs);
    }

    @Override
    public List<String> getFlatSolverArgs() {
        return TestUtilities.optionMapToList(solverArgs);
    }

    @Override
    public TestConfiguration getFinalTypecheckConfig() {
        List<File> translatedDiagnostics =
                InferenceTestUtilities.replaceParentDirs(annotatedSourceDir, initialConfig.getDiagnosticFiles());
        List<File> translatedFiles =
                InferenceTestUtilities.replaceParentDirs(annotatedSourceDir, initialConfig.getTestSourceFiles());
        return new ImmutableTestConfiguration(translatedDiagnostics, translatedFiles, initialConfig.getProcessors(),
                                              initialConfig.getOptions(), initialConfig.shouldEmitDebugInfo());
    }

}
