package checkers.inference.test;

import org.checkerframework.framework.test.SimpleOptionMap;
import org.checkerframework.framework.test.TestConfiguration;
import org.checkerframework.framework.test.TestConfigurationBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InferenceTestConfigurationBuilder {
    private TestConfiguration initialConfiguration = null;
    private File outputJaif = null;
    private File annotatedSourceDir = null;
    private File testDataDir = null;
    private String solver = null;
    private boolean shouldUseHacks;
    private String pathToAfuScripts="";
    private String pathToInferenceScript="";

    private SimpleOptionMap inferenceJavacArgs = new SimpleOptionMap();
    private SimpleOptionMap solverArgs = new SimpleOptionMap();

    public InferenceTestConfigurationBuilder() {
    }

    public InferenceTestConfigurationBuilder(TestConfiguration initialConfiguration) {
        this.initialConfiguration = initialConfiguration;
    }

    public InferenceTestConfigurationBuilder setOutputJaif(File outputJaif) {
        this.outputJaif = outputJaif;
        return this;
    }

    public InferenceTestConfigurationBuilder setInitialConfiguration(TestConfiguration initialConfiguration) {
        this.initialConfiguration = initialConfiguration;
        return this;
    }

    public InferenceTestConfigurationBuilder setAnnotatedSourceDir(File annotatedSourceDir) {
        this.annotatedSourceDir = annotatedSourceDir;
        return this;
    }

    public InferenceTestConfigurationBuilder setTestDataDir(File testDataDir) {
        this.testDataDir = testDataDir;
        return this;
    }

    public InferenceTestConfigurationBuilder setSolver(String solver) {
        this.solver = solver;
        return this;
    }

    public InferenceTestConfigurationBuilder setShouldUseHacks(boolean shouldUseHacks) {
        this.shouldUseHacks = shouldUseHacks;
        return this;
    }

    public InferenceTestConfigurationBuilder setPathToAfuScripts(String pathToAfuScripts) {
        this.pathToAfuScripts = pathToAfuScripts;
        return this;
    }

    public InferenceTestConfigurationBuilder setPathToInferenceScript(String pathToInferenceScript) {
        this.pathToInferenceScript = pathToInferenceScript;
        return this;
    }

    // ---------------------------------
    // Infernece Javac Options Delegation Methods

    public InferenceTestConfigurationBuilder addToInferenceJavacPathOption(String key, String toAppend) {
        inferenceJavacArgs.addOption(key, toAppend);
        return this;
    }

    public InferenceTestConfigurationBuilder addInferenceJavacOption(String option) {
        inferenceJavacArgs.addOption(option);
        return this;
    }

    public InferenceTestConfigurationBuilder addInferenceJavacOption(String option, String value) {
        inferenceJavacArgs.addOption(option, value);
        return this;
    }


    public InferenceTestConfigurationBuilder addInferenceJavacOptionIfValueNonEmpty(String option, String value) {
        inferenceJavacArgs.addOptionIfValueNonEmpty(option, value);
        return this;
    }

    public InferenceTestConfigurationBuilder addInferenceJavacOptions(Map<String, String> options) {
        inferenceJavacArgs.addOptions(options);
        return this;
    }

    public InferenceTestConfigurationBuilder addInferenceJavacOptions(Iterable<String> newOptions) {
        inferenceJavacArgs.addOptions(newOptions);
        return this;
    }

    // ---------------------------------
    // Solver Options Delegation Methods

    public InferenceTestConfigurationBuilder addToSolverPathOption(String key, String toAppend) {
        solverArgs.addOption(key, toAppend);
        return this;
    }

    public InferenceTestConfigurationBuilder addSolverOption(String option) {
        solverArgs.addOption(option);
        return this;
    }

    public InferenceTestConfigurationBuilder addSolverOption(String option, String value) {
        solverArgs.addOption(option, value);
        return this;
    }

    public InferenceTestConfigurationBuilder addSolverOptionIfValueNonEmpty(String option, String value) {
        solverArgs.addOptionIfValueNonEmpty(option, value);
        return this;
    }

    public InferenceTestConfigurationBuilder addSolverOptions(Map<String, String> options) {
        solverArgs.addOptions(options);
        return this;
    }

    public InferenceTestConfigurationBuilder addSolverOptions(Iterable<String> newOptions) {
        solverArgs.addOptions(newOptions);
        return this;
    }

    private void ensureNotNull(Object reference, final String name, List<String> errors) {
        if (reference == null) {
            errors.add(name + " must not be null!");
        }
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        //Note: The initial config should be validated before being passed to InferenceTestConfigurationBuilder
        ensureNotNull(initialConfiguration, "initialConfiguration", errors);
        ensureNotNull(outputJaif,  "outputJaif", errors);
        ensureNotNull(testDataDir, "testDataDir", errors);
        ensureNotNull(annotatedSourceDir, "annotatedSourceDir", errors);
        return errors;
    }

    public InferenceTestConfiguration build() {
        return new ImmutableInferenceTestConfiguration(outputJaif, testDataDir,
                annotatedSourceDir, new LinkedHashMap<>(inferenceJavacArgs.getOptions()),
                solver, new LinkedHashMap<>(solverArgs.getOptions()), shouldUseHacks,pathToAfuScripts,
                pathToInferenceScript, initialConfiguration);
    }

    public InferenceTestConfiguration validateThenBuild() {
        List<String> errors = validate() ;
        if (errors.isEmpty()) {
            return build();
        }

        throw new RuntimeException("Attempted to build invalid inference test configuration:\n"
                + "Errors:\n"
                + String.join("\n", errors) + "\n"
                + this.toString() + "\n");
    }

    public static InferenceTestConfiguration buildDefaultConfiguration(
            String testSourcePath, File testFile, File testDataRoot, String checkerName, List<String> typecheckOptions,
            List<String> inferenceOptions,  String solverName, List<String> solverOptions,
            boolean shouldUseHacks, boolean shouldEmitDebugInfo, String pathToAfuScripts, String pathToInferenceScript) {

        final File defaultInferenceOutDir = new File("testdata/tmp");
        final File defaultOutputJaif = new File(defaultInferenceOutDir, "default.jaif");
        final File defaultAnnotatedSourceDir = new File(defaultInferenceOutDir, "annotated-source");

        TestConfiguration initialConfig = TestConfigurationBuilder.buildDefaultConfiguration(
                testSourcePath, testFile, checkerName, typecheckOptions, shouldEmitDebugInfo);

        InferenceTestConfigurationBuilder configBuilder =
            new InferenceTestConfigurationBuilder()
                .setInitialConfiguration(initialConfig)
                .setOutputJaif(defaultOutputJaif)
                .setTestDataDir(testDataRoot)
                .setAnnotatedSourceDir(defaultAnnotatedSourceDir)
                .setSolver(solverName)
                .setShouldUseHacks(shouldUseHacks)
                .setPathToAfuScripts(pathToAfuScripts)
                .setPathToInferenceScript(pathToInferenceScript);

        if (inferenceOptions != null) {
            configBuilder.addInferenceJavacOptions(inferenceOptions);
        }

        if (solverOptions != null) {
            configBuilder.addSolverOptions(solverOptions);
        }

        return configBuilder.validateThenBuild();
    }
}
