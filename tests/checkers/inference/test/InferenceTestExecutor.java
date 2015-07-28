package checkers.inference.test;


import org.checkerframework.framework.test2.CompilationResult;
import org.checkerframework.framework.test2.TestConfiguration;
import org.checkerframework.framework.test2.TypecheckExecutor;
import org.checkerframework.framework.test2.TypecheckResult;
import org.checkerframework.framework.test2.diagnostics.DiagnosticCategory;
import org.checkerframework.framework.test2.diagnostics.ExpectedDiagnosticLine;
import org.checkerframework.framework.test2.diagnostics.ExpectedDiagnosticLine.ExpectedDiagnostic;
import org.checkerframework.framework.test2.diagnostics.MixedDiagnosticReader;
import org.checkerframework.framework.util.ExecUtil;
import org.checkerframework.framework.util.PluginUtil;

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
        options.add("./scripts/inference");
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
            options.add("--solverArgs=\"" + PluginUtil.join(" ", configuration.getFlatSolverArgs()) + "\"");
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
            System.out.println("Running command: \n" + PluginUtil.join(" ", args));
            System.out.flush();
        }

        ByteArrayOutputStream inferenceOut = new ByteArrayOutputStream();
        PrintStream inferenceOutPrint = new PrintStream(inferenceOut);
        int result = ExecUtil.execute(args, inferenceOutPrint, inferenceOutPrint);

        return new InferenceResult(configuration, inferenceOut.toString(), result != 0);
    }

    public static InsertionResult insertAnnotations(InferenceTestConfiguration configuration) {
        List<File> sourceFiles = configuration.getInitialTypecheckConfig().getTestSourceFiles();
        String [] options = new String [5 + sourceFiles.size()];
        options[0] = "insert-annotations-to-source";
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

    public static ExpectedDiagnostic convertFixableToError(ExpectedDiagnostic diagnostic) {
        if (diagnostic.category == DiagnosticCategory.FixableError) {
            return new ExpectedDiagnostic(diagnostic.lineNumber, DiagnosticCategory.Error, diagnostic.errorMessage, diagnostic.noParentheses);
        }
        return diagnostic;
    }


    public static List<ExpectedDiagnostic> convertFixableToError(List<ExpectedDiagnostic> diagnostics) {
        List<ExpectedDiagnostic> updatedDiagnostics = new ArrayList<>(diagnostics.size());
        for (ExpectedDiagnostic originalDiagnostic : diagnostics) {
            updatedDiagnostics.add(convertFixableToError(originalDiagnostic));
        }
        return updatedDiagnostics;
    }

    private static List<ExpectedDiagnostic> flattenDiagnosticLines(List<ExpectedDiagnosticLine> lines) {
        List<ExpectedDiagnostic> diagnostics = new ArrayList<>(lines.size());
        for (ExpectedDiagnosticLine line : lines) {
            diagnostics.addAll(line.getDiagnostics());
        }

        return diagnostics;
    }


    private static List<ExpectedDiagnostic> removeFixables(List<ExpectedDiagnostic> expectedDiagnostics) {
        List<ExpectedDiagnostic> filteredDiagnostics = new ArrayList<>(expectedDiagnostics.size());
        for (ExpectedDiagnostic diagnostic : expectedDiagnostics) {
            if (diagnostic.category != DiagnosticCategory.FixableError) {
                filteredDiagnostics.add(diagnostic);
            }
        }

        return filteredDiagnostics;
    }

    private static TypecheckResult initialTypecheck(InferenceTestConfiguration configuration) {
        TestConfiguration typecheckConfig = configuration.getInitialTypecheckConfig();

        List<ExpectedDiagnostic> expectedDiagnostics = flattenDiagnosticLines(
                MixedDiagnosticReader.readExpectedDiagnostics(typecheckConfig.getTestSourceFiles(), true));
        TypecheckExecutor typecheckExecutor = new TypecheckExecutor();
        CompilationResult compilationResult = typecheckExecutor.compile(typecheckConfig);

        List<ExpectedDiagnostic> filteredDiagnostics = convertFixableToError(expectedDiagnostics);
        return TypecheckResult.fromCompilationResultsExpectedDiagnostics(typecheckConfig, compilationResult, filteredDiagnostics);
    }

    private static TypecheckResult finalTypecheck(InferenceTestConfiguration configuration) {
        TestConfiguration typecheckConfig = configuration.getFinalTypecheckConfig();

        for (final File javaFile : typecheckConfig.getTestSourceFiles()) {
            InferenceTestUtilities.inlineAnnotationsAfterDiagnostics(javaFile);
        }

        List<ExpectedDiagnostic> expectedDiagnostics = flattenDiagnosticLines(
                MixedDiagnosticReader.readExpectedDiagnostics(typecheckConfig.getTestSourceFiles(), true));

        TypecheckExecutor typecheckExecutor = new TypecheckExecutor();
        CompilationResult compilationResult = typecheckExecutor.compile(typecheckConfig);
        List<ExpectedDiagnostic> diagnosticsWithoutFixables = removeFixables(expectedDiagnostics);

        return TypecheckResult.fromCompilationResultsExpectedDiagnostics(typecheckConfig, compilationResult, diagnosticsWithoutFixables);
    }
}
