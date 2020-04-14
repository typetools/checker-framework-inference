package checkers.inference;


import org.checkerframework.framework.util.CheckerMain;
import org.checkerframework.framework.util.ExecUtil;
import org.checkerframework.javacutil.PluginUtil;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import checkers.inference.InferenceOptions.InitStatus;


/**
 * Main class used to execute inference and related tasks. It can be run from:
 * The InferenceLauncher can be run from checker-framework-inference/scripts
 *
 * InferenceLauncher parses a set of options (defined in InferenceOptions).
 * Based on the options, InferenceLauncher will run 1 or more tasks.
 * Use the --mode option to specify which tasks are run.  The values that can
 * be passed to this option are enumerated in InferenceLauncher.Mode
 *
 * See InferenceOptions.java for more information on arguments to InferenceLauncher
 */
public class InferenceLauncher {

    private final PrintStream outStream;
    private final PrintStream errStream;

    private static final String PROP_PREFIX = "InferenceLauncher";
    private static final String RUNTIME_BCP_PROP = PROP_PREFIX + ".runtime.bcp";

    public InferenceLauncher(PrintStream outStream, PrintStream errStream) {
        this.outStream = outStream;
        this.errStream = errStream;
    }

    protected void initInferenceOptions(String [] args) {
        InitStatus initStatus = InferenceOptions.init(args, true);

        initStatus.validateOrExit();
    }

    public void launch(String [] args) {
        initInferenceOptions(args);

        Mode mode = null;
        try {
            mode = Mode.valueOf(InferenceOptions.mode);

        } catch (IllegalArgumentException iexc) {
            outStream.println("Could not recognize mode: " + InferenceOptions.mode + "\n"
                    + "valid modes: " + PluginUtil.join(", ", Mode.values()));
            System.exit(1);
        }

        switch (mode) {
            case TYPECHECK:
                typecheck(InferenceOptions.javaFiles);
                break;

            case INFER:
                infer();
                break;

            case ROUNDTRIP:
                infer();
                insertJaif();
                break;

            case ROUNDTRIP_TYPECHECK:
                infer();
                List<String> updatedJavaFiles =  insertJaif();
                typecheck(updatedJavaFiles.toArray(new String[updatedJavaFiles.size()]));
                break;
        }
    }

    /**
     * Mode describes what actions should be performed by the launcher.
     */
    public enum Mode {
        /** just run typechecking do not infer anything*/
        TYPECHECK,

        /** run inference but do not typecheck or insert the result into source code*/
        INFER,

        /** run inference and insert the result back into source code*/
        ROUNDTRIP,

        /** run inference, insert the result back into source code, and typecheck*/
        ROUNDTRIP_TYPECHECK
    }

    public static void main(String [] args) {
        new InferenceLauncher(System.out, System.err).launch(args);
    }

    /**
     * Runs typechecking on the input set of files using the arguments passed
     * to javacOptions on the command line.
     * @param javaFiles Source files to typecheck, we use this argument instead of InferenceOptions.javaFiles
     *                  because when we roundtrip we may or may not have inserted annotations in place.
     */
    public void typecheck(String [] javaFiles) {
        printStep("Typechecking", outStream);

        List<String> options = new ArrayList<>(InferenceOptions.javacOptions.size() + javaFiles.length + 2);
        options.add("-processor");
        options.add(InferenceOptions.checker);

        if (InferenceOptions.debug != null) {
            options.add("-J-Xdebug");
            options.add("-J-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=" + InferenceOptions.debug);
        }

        options.addAll(InferenceOptions.javacOptions);
        if (InferenceOptions.cfArgs != null && !InferenceOptions.cfArgs.isEmpty()) {
            options.add(InferenceOptions.cfArgs);
        }
        options.addAll(Arrays.asList(javaFiles));

        final CheckerMain checkerMain = new CheckerMain(InferenceOptions.checkerJar, options);
        checkerMain.addToRuntimeClasspath(getInferenceRuntimeJars());
        checkerMain.addToClasspath(getInferenceRuntimeJars());

        if (InferenceOptions.printCommands) {
            outStream.println("Running typecheck command:");
            outStream.println(PluginUtil.join(" ", checkerMain.getExecArguments()));
        }

        int result = checkerMain.invokeCompiler();

        reportStatus("Typechecking", result, outStream);
        outStream.flush();
        exitOnNonZeroStatus(result);
    }

    /**
     * Infers annotations for the set of source files found in InferenceOptions.java
     * This method creates a process that runs InferenceMain on the same options
     * in InferenceOptions but excluding those that do not apply to the inference step
     */
    public void infer() {
        printStep("Inferring", outStream);
        final String java = PluginUtil.getJavaCommand(System.getProperty("java.home"), outStream);
        List<String> argList = new LinkedList<>();
        argList.add(java);
        argList.addAll(getMemoryArgs());

        String bcp = getInferenceRuntimeBootclassPath();
        if (bcp != null && !bcp.isEmpty()) {
            argList.add("-Xbootclasspath/p:" + bcp);
        }

        argList.add("-classpath");
        argList.add(getInferenceRuntimeClassPath());

        if (InferenceOptions.debug != null) {
            argList.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + InferenceOptions.debug);
        }

        argList.addAll(
                Arrays.asList(
                        "-ea", "-ea:checkers.inference...",
                        // TODO: enable assertions.
                        "-da:org.checkerframework.framework.flow...",
                        "checkers.inference.InferenceMain",
                        "--checker", InferenceOptions.checker)
        );

        addIfNotNull("--jaifFile", InferenceOptions.jaifFile, argList);
        addIfNotNull("--logLevel", InferenceOptions.logLevel, argList);
        addIfNotNull("--solver", InferenceOptions.solver, argList);
        addIfNotNull("--solverArgs", InferenceOptions.solverArgs, argList);
        addIfNotNull("--cfArgs", InferenceOptions.cfArgs, argList);

        addIfTrue("--hacks", InferenceOptions.hacks, argList);

        argList.add("--");

        String compilationBcp = getInferenceCompilationBootclassPath();
        if (compilationBcp != null && !compilationBcp.isEmpty()) {
            argList.add("-Xbootclasspath/p:" + compilationBcp);
        }

        int preJavacOptsSize = argList.size();
        argList.addAll(InferenceOptions.javacOptions);
        removeXmArgs(argList, preJavacOptsSize, argList.size());

        // TODO: NEED TO HANDLE JDK
        argList.addAll(Arrays.asList(InferenceOptions.javaFiles));

        if (InferenceOptions.printCommands) {
            outStream.println("Running infer command:");
            outStream.println(PluginUtil.join(" ", argList));
        }

        int result = ExecUtil.execute(argList.toArray(new String[argList.size()]), outStream, System.err);
        outStream.flush();
        errStream.flush();

        reportStatus("Inference", result, outStream);
        outStream.flush();
        exitOnNonZeroStatus(result);
    }

    private void removeXmArgs(List<String> argList, int preJavacOptsSize, int postJavacOptsSize) {
        for (int i = preJavacOptsSize; i < argList.size() && i < postJavacOptsSize; /*incremented-below*/) {
            String current = argList.get(i);
            if (current.startsWith("-Xmx") || current.startsWith("-Xms")) {
                argList.remove(i);
            } else {
                ++i;
            }
        }
    }

    /**
     * Inserts the Jaif resulting from Inference into the source code.
     * TODO: Currently we have an InferenceOption.afuOptions field which should
     * TODO: be piped into the isnert-annotation-to-source command but is not
     * @return The list of source files that were passed as arguments to the AFU and were
     * potentially altered.   This list is needed for subsequent typechecking.
     */
    public List<String> insertJaif() {
        List<String> outputJavaFiles = new ArrayList<>(InferenceOptions.javaFiles.length);

        printStep("Inserting annotations", outStream);
        int result;
        String pathToAfuScripts = InferenceOptions.pathToAfuScripts == null ? "":InferenceOptions.pathToAfuScripts+File.separator;
        String insertAnnotationsScript = pathToAfuScripts+"insert-annotations-to-source";
        if (!InferenceOptions.inPlace) {
            final File outputDir = new File(InferenceOptions.afuOutputDir);
            ensureDirectoryExists(outputDir);

            String jaifFile = getJaifFilePath (outputDir);

            List<String> options = new ArrayList<>();
            options.add(insertAnnotationsScript);
            options.add("-v");
            options.add("--print-error-stack=true");
            options.add("--outdir=" + outputDir.getAbsolutePath());
            options.add(jaifFile);

            Collections.addAll(options, InferenceOptions.javaFiles);

            if (InferenceOptions.printCommands) {
                outStream.println("Running Insert Annotations Command:");
                outStream.println(PluginUtil.join(" ", options));
            }

            // this can get quite large for large projects and it is not advisable to run
            // roundtripping via the InferenceLauncher for these projects
            ByteArrayOutputStream insertOut = new ByteArrayOutputStream();
            result = ExecUtil.execute(options.toArray(new String[options.size()]), insertOut, errStream);
            outStream.println(insertOut.toString());


            List<File> newJavaFiles = findWrittenFiles(insertOut.toString());
            for (File newJavaFile : newJavaFiles) {
                outputJavaFiles.add(newJavaFile.getAbsolutePath());
            }

        } else {
            String jaifFile = getJaifFilePath(new File("."));

            String [] options = new String [4 + InferenceOptions.javaFiles.length];
            options[0] = insertAnnotationsScript;
            options[1] = "-v";
            options[2] = "-i";
            options[3] = jaifFile;

            System.arraycopy(InferenceOptions.javaFiles, 0, options, 4, InferenceOptions.javaFiles.length);

            if (InferenceOptions.printCommands) {
                outStream.println("Running Insert Annotations Command:");
                outStream.println(PluginUtil.join(" ", options));
            }

            result = ExecUtil.execute(options, outStream, errStream);

            for (String filePath : InferenceOptions.javaFiles) {
                outputJavaFiles.add(filePath);
            }
        }

        reportStatus("Insert annotations", result, outStream);
        outStream.flush();
        exitOnNonZeroStatus(result);
        return outputJavaFiles;
    }

    public static void ensureDirectoryExists(File path) {
        if (!path.exists()) {
            if (!path.mkdirs()) {
                throw new RuntimeException("Could not make directory: " + path.getAbsolutePath());
            }
        }
    }

    /**
     * This is a potentially brittle method to scan the output of the AFU
     * for Java file paths.
     * @param output The output of the Annotation File Utilities
     * @return The files that the AFU processed
     */
    private static List<File> findWrittenFiles(String output) {
        // This will be brittle; if the AFU Changes it's output string then no files will be found
        final Pattern afuWritePattern = Pattern.compile("^Writing (.*\\.java)$");

        List<File> writtenFiles = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new StringReader(output));
        String line;

        do {
            try {
                line = reader.readLine();
                if (line != null) {
                    Matcher afuWriteMatcher = afuWritePattern.matcher(line);
                    if (afuWriteMatcher.matches()) {
                        writtenFiles.add(new File(afuWriteMatcher.group(1)));
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        while (line != null);
        return writtenFiles;
    }

    /**
     * @return InferenceOptions.jaifFile if it is non null, otherwise a path to "inference.jaif" in the
     * output directory
     */
    private static String getJaifFilePath(File outputDir) {

        String jaifFile = InferenceOptions.jaifFile;
        if (jaifFile == null) {
            jaifFile = new File(outputDir, "inference.jaif").getAbsolutePath();
        }

        return jaifFile;
    }

    private static List<String> getMemoryArgs() {
        // this should instead read them from InferenceOptions and fall back to this if they are not present
        // perhaps just find all -J
        String xmx = "-Xmx2048m";
        String xms = "-Xms512m";
        for (String javacOpt : InferenceOptions.javacOptions) {
            if (javacOpt.startsWith("-Xms") || javacOpt.startsWith("-J-Xms")) {
                xms = javacOpt;
            } else if (javacOpt.startsWith("-Xmx") || javacOpt.startsWith("-J-Xmx")) {
                xmx = javacOpt;
            }
        }

        return Arrays.asList(xms, xmx);
    }

    /**
     * @return the paths to the set of jars that are needed to be placed on
     * the classpath of the process running inference
     */
    protected List<String> getInferenceRuntimeJars() {
        final File distDir = InferenceOptions.pathToThisJar.getParentFile();
        String jdkJarName = PluginUtil.getJdkJarName();

        List<String> filePaths = new ArrayList<>();
        for (File child : distDir.listFiles()) {
            String name = child.getName();
            if (!name.endsWith(jdkJarName)) {
                filePaths.add(child.getAbsolutePath());
            }
        }
        filePaths.add(InferenceOptions.targetclasspath);
        return filePaths;
    }

    // what used as bootclass to run the compiler
    protected String getInferenceRuntimeBootclassPath() {
        return System.getProperty( RUNTIME_BCP_PROP );
    }

    // what's used to run the compiler
    protected String getInferenceRuntimeClassPath() {
        List<String> filePaths = getInferenceRuntimeJars();
        filePaths.add(InferenceOptions.targetclasspath);

        String systemClasspath = System.getProperty("java.class.path");
        if (!systemClasspath.isEmpty()) {
            filePaths.add(systemClasspath);
        }

        return PluginUtil.join(File.pathSeparator, filePaths);
    }

    // what the compiler compiles against
    protected String getInferenceCompilationBootclassPath() {
        String jdkJarName = PluginUtil.getJdkJarName();
        final File jdkFile = new File(InferenceOptions.pathToThisJar.getParentFile(), jdkJarName);

        if (jdkFile.exists()) {
            return jdkFile.getAbsolutePath();
        }
        return "";
    }


    public static void printStep(String step, PrintStream out) {
        out.println("\n--- " + step + " ---" + "\n");
    }

    public static void reportStatus(String prefix, int returnCode, PrintStream out) {
        out.println("\n--- " + prefix + (returnCode == 0 ? " succeeded" : " failed") + " ---" + "\n");
    }

    public static void exitOnNonZeroStatus(int result) {
        if (result != 0) {
            System.exit(result);
        }
    }

    public static void addIfTrue(String name, boolean isPresent, List<String> args) {
        if (isPresent) {
            args.add(name);
        }
    }

    public static void addIfNotNull(String name, String option, List<String> args) {
        if (option != null && !option.isEmpty()) {
            args.add(name);
            args.add(option);
        }
    }
}
