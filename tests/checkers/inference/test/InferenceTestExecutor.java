package checkers.inference.test;


import org.checkerframework.framework.test.CompilationResult;
import org.checkerframework.framework.test.TestConfiguration;
import org.checkerframework.framework.test.TypecheckExecutor;
import org.checkerframework.framework.test.TypecheckResult;
import org.checkerframework.framework.test.diagnostics.JavaDiagnosticReader;
import org.checkerframework.framework.test.diagnostics.TestDiagnostic;
import org.checkerframework.framework.util.ExecUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class InferenceTestExecutor {

    public InferenceTestExecutor() {
    }

    public InferenceTestResult runTest(InferenceTestConfiguration config) {

        InferenceTestPhase lastPhaseRun;
        boolean failed;

        //first run the checker on the source files and ensure that the typechecker returns the expected errors
        TypecheckResult initialTypecheckResult = initialTypecheck(config);
        failed = initialTypecheckResult.didTestFail();
        lastPhaseRun = InferenceTestPhase.INITIAL_TYPECHECK;

        //run inference over the java files
        InferenceResult inferenceResult = null;
        if (!failed) {
            inferenceResult = infer(config);
            failed = inferenceResult.didFail();
            lastPhaseRun = InferenceTestPhase.INFER;
        }

        InsertionResult insertionResult = null;
        if (!failed) {
            insertionResult = insertAnnotations(config);
            failed = insertionResult.didFail();
            lastPhaseRun = InferenceTestPhase.INSERT;
        }

        TypecheckResult finalTypecheckResult = null;
        if (!failed) {
            finalTypecheckResult = finalTypecheck(config);
            failed = finalTypecheckResult.didTestFail();
            lastPhaseRun = InferenceTestPhase.FINAL_TYPECHECK;
        }

        return new InferenceTestResult(config, initialTypecheckResult, inferenceResult,
                                       insertionResult, finalTypecheckResult, lastPhaseRun);
    }

    public static InferenceResult infer(InferenceTestConfiguration configuration) {
        TestConfiguration initialConfig = configuration.getInitialTypecheckConfig();

        ensureDirectoryExists(configuration.getAnnotatedSourceDir());
        ensureParentDirectoryExists(configuration.getOutputJaif());
        ensureDirectoryExists(new File(initialConfig.getOptions().get("-d")));

        if (initialConfig.getProcessors().size() != 1) {
            throw new RuntimeException("You must specify exactly one checker!");
        }

        final List<String> options = new ArrayList<String>();
        options.add(configuration.getPathToInferenceScript());
        options.add("--mode=INFER");
        options.add("--checker=" + initialConfig.getProcessors().get(0));
        if (configuration.getInitialTypecheckConfig().shouldEmitDebugInfo()) {
            options.add("-p");
        }
        if (configuration.shouldUseHacks()) {
            options.add("--hacks");
        }

        options.add("--jaifFile=" + configuration.getOutputJaif().getAbsolutePath());

        options.add("--solver=" + configuration.getSolver());

        List<String> solverArgs = configuration.getFlatSolverArgs();
        if (!solverArgs.isEmpty()) {
            options.add("--solverArgs=\"" + String.join(" ", configuration.getFlatSolverArgs()) + "\"");
        }
        if (configuration.getPathToAfuScripts() != null && !configuration.getPathToAfuScripts().equals("")) {
            options.add("--pathToAfuScripts=" + configuration.getPathToAfuScripts());
        }

        options.add("--");

        List<String> javacArgs = initialConfig.getFlatOptions();
        javacArgs.addAll(configuration.getFlatInferenceJavacArgs());
        if (!javacArgs.isEmpty()) {
            options.addAll(javacArgs);
        }

        for (File sourceFile : initialConfig.getTestSourceFiles()) {
            options.add(sourceFile.getAbsolutePath());
        }

        String [] args = options.toArray(new String[options.size()]);

        if (configuration.getInitialTypecheckConfig().shouldEmitDebugInfo()) {
            System.out.println("Running command: \n" + String.join(" ", args));
            System.out.flush();
        }

        ByteArrayOutputStream inferenceOut = new ByteArrayOutputStream();
        PrintStream inferenceOutPrint = new PrintStream(inferenceOut);
        int result = ExecUtil.execute(args, inferenceOutPrint, inferenceOutPrint);

        return new InferenceResult(configuration, inferenceOut.toString(), result != 0);
    }

    public static InsertionResult insertAnnotations(InferenceTestConfiguration configuration) {
        String pathToAfuScripts = configuration.getPathToAfuScripts().equals("") ? "":configuration.getPathToAfuScripts()+File.separator;
        String insertAnnotationsScript = pathToAfuScripts+"insert-annotations-to-source";

        List<File> sourceFiles = configuration.getInitialTypecheckConfig().getTestSourceFiles();
        String [] options = new String [5 + sourceFiles.size()];
        options[0] = insertAnnotationsScript;
        options[1] = "-v";
        options[2] = "-d";
        options[3] = configuration.getAnnotatedSourceDir().getAbsolutePath();
        options[4] = configuration.getOutputJaif().getAbsolutePath();

        for (int i = 0; i < sourceFiles.size(); i++) {
            options[i + 5] = sourceFiles.get(i).getAbsolutePath();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int returnCode = ExecUtil.execute(options, outputStream, outputStream);

        return new InsertionResult(options, returnCode != 0, outputStream.toString());
    }

    private static void ensureParentDirectoryExists(File path) {
        if (!path.getParentFile().exists()) {
            if (!path.mkdirs()) {
                throw new RuntimeException("Could not make directory: " + path.getAbsolutePath());
            }
        }
    }

    private static void ensureDirectoryExists(File path) {
        if (!path.exists()) {
            if (!path.mkdirs()) {
                throw new RuntimeException("Could not make directory: " + path.getAbsolutePath());
            }
        }
    }

    private static List<TestDiagnostic> filterOutFixables(List<TestDiagnostic> expectedDiagnostics) {
        List<TestDiagnostic> filteredDiagnostics = new ArrayList<>(expectedDiagnostics.size());
        for (TestDiagnostic diagnostic : expectedDiagnostics) {
            if (!diagnostic.isFixable()) {
                filteredDiagnostics.add(diagnostic);
            }
        }

        return filteredDiagnostics;
    }

    private static TypecheckResult initialTypecheck(InferenceTestConfiguration configuration) {
        TestConfiguration typecheckConfig = configuration.getInitialTypecheckConfig();

        List<TestDiagnostic> expectedDiagnostics = JavaDiagnosticReader.readJavaSourceFiles(typecheckConfig.getTestSourceFiles());
        TypecheckExecutor typecheckExecutor = new TypecheckExecutor();
        CompilationResult compilationResult = typecheckExecutor.compile(typecheckConfig);

        return TypecheckResult.fromCompilationResultsExpectedDiagnostics(typecheckConfig, compilationResult, expectedDiagnostics);
    }

    private static TypecheckResult finalTypecheck(InferenceTestConfiguration configuration) {
        TestConfiguration typecheckConfig = configuration.getFinalTypecheckConfig();

        for (final File javaFile : typecheckConfig.getTestSourceFiles()) {
            InferenceTestUtilities.inlineAnnotationsAfterDiagnostics(javaFile);
        }

        List<TestDiagnostic> expectedDiagnostics =
            filterOutFixables(JavaDiagnosticReader.readJavaSourceFiles(typecheckConfig.getTestSourceFiles()));

        TypecheckExecutor typecheckExecutor = new TypecheckExecutor();
        CompilationResult compilationResult = typecheckExecutor.compile(typecheckConfig);

        return TypecheckResult.fromCompilationResultsExpectedDiagnostics(typecheckConfig, compilationResult, expectedDiagnostics);
    }
}
