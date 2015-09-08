package checkers.inference;


import checkers.inference.InferenceOptions.InitStatus;
import org.checkerframework.framework.test.TestUtilities;
import org.checkerframework.framework.util.CheckerMain;
import org.checkerframework.framework.util.ExecUtil;
import org.checkerframework.framework.util.PluginUtil;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class InferenceLauncher {

    private final PrintStream outStream;
    private final PrintStream errStream;

    public InferenceLauncher(PrintStream outStream, PrintStream errStream) {
        this.outStream = outStream;
        this.errStream = errStream;
    }

    public void launch(String [] args) {
        InitStatus initStatus = InferenceOptions.init(args, true);
        initStatus.validateOrExit();

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

    public enum Mode {
        //just run typechecking do not infer anything
        TYPECHECK,

        //run inference but do not typecheck or insert the result into source code
        INFER,

        //run inference and insert the result back into source code
        ROUNDTRIP,

        //run inference, insert the result back into source code, and typecheck
        ROUNDTRIP_TYPECHECK
    }

    public static void main(String [] args) {
        new InferenceLauncher(System.out, System.err).launch(args);
    }

    public void typecheck(String [] javaFiles) {
        printStep("Typechecking", outStream);

        final int initialOptsLength = 2 + (InferenceOptions.debug != null ? 2 : 0);

        String [] options;
        options = new String[initialOptsLength + InferenceOptions.javacOptions.length + javaFiles.length];
        options[0] = "-processor";
        options[1] = InferenceOptions.checker;

        if (InferenceOptions.debug != null) {
            options[2] = "-J-Xdebug";
            options[3] = "-J-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=" + InferenceOptions.debug;
        }

        System.arraycopy(InferenceOptions.javacOptions, 0, options, initialOptsLength, InferenceOptions.javacOptions.length);
        System.arraycopy(javaFiles, 0, options, InferenceOptions.javacOptions.length + initialOptsLength, javaFiles.length);

        final CheckerMain checkerMain = new CheckerMain(InferenceOptions.checkerJar, options);
        checkerMain.addToRuntimeBootclasspath(getInferenceRuntimeBootJars());

        if (InferenceOptions.printCommands) {
            outStream.println("Running typecheck command:");
            outStream.println(PluginUtil.join(" ", checkerMain.getExecArguments()));
        }

        int result = checkerMain.invokeCompiler();

        reportStatus("Typechecking", result, outStream);
        outStream.flush();
        exitOnNonZeroStatus(result);
    }

    public void infer() {
        printStep("Inferring", outStream);
        final String java = PluginUtil.getJavaCommand(System.getProperty("java.home"), outStream);
        List<String> argList = new LinkedList<>();
        argList.add(java);
        argList.addAll(getMemoryArgs());

        if (InferenceOptions.debug != null) {
            argList.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + InferenceOptions.debug);
        }

        argList.add(getInferenceRuntimeBootclassPath());
        argList.addAll(
                Arrays.asList(
                        "-ea", "-ea:checkers.inference...",
                        "checkers.inference.InferenceMain",
                        "--checker", InferenceOptions.checker)
        );

        addIfNotNull("--jaifFile", InferenceOptions.jaifFile, argList);
        addIfNotNull("--logLevel", InferenceOptions.logLevel, argList);
        addIfNotNull("--solver", InferenceOptions.solver, argList);
        addIfNotNull("--solverArgs", InferenceOptions.solverArgs, argList);

        addIfTrue("--hacks", InferenceOptions.hacks, argList);

        argList.add("--");
        argList.add(getInferenceCompilationBootclassPath());
        int preJavacOptsSize = argList.size();
        argList.addAll(Arrays.asList(InferenceOptions.javacOptions));
        removeXmArgs(argList, preJavacOptsSize, argList.size());

        //TODO: NEED TO HANDLE JDK
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

    public List<String> insertJaif() {
        List<String> outputJavaFiles = new ArrayList<>(InferenceOptions.javaFiles.length);

        printStep("Inserting annotations", outStream);
        int result;
        if (!InferenceOptions.inPlace) {
            final File outputDir = new File(InferenceOptions.afuOutputDir);
            TestUtilities.ensureDirectoryExists(outputDir);

            String jaifFile = getJaifFilePath(outputDir);

            String [] options = new String [5 + InferenceOptions.javaFiles.length];
            options[0] = "insert-annotations-to-source";
            options[1] = "-v";
            options[2] = "-d";
            options[3] = outputDir.getAbsolutePath();
            options[4] = jaifFile;

            System.arraycopy(InferenceOptions.javaFiles, 0, options, 5, InferenceOptions.javaFiles.length);

            if (InferenceOptions.printCommands) {
                outStream.println("Running Insert Annotations Command:");
                outStream.println(PluginUtil.join(" ", options));
            }

            //this can get quite large for large projects and it is not advisable to run
            //roundtripping via the InferenceLauncher for these projects
            ByteArrayOutputStream insertOut = new ByteArrayOutputStream();
            result = ExecUtil.execute(options, insertOut, errStream);
            outStream.println(insertOut.toString());


            List<File> newJavaFiles = findWrittenFiles(insertOut.toString());
            for (File newJavaFile : newJavaFiles) {
                outputJavaFiles.add(newJavaFile.getAbsolutePath());
            }

        } else {
            String jaifFile = getJaifFilePath(new File("."));

            String [] options = new String [5 + InferenceOptions.javaFiles.length];
            options[0] = "insert-annotations-to-source";
            options[1] = "-v";
            options[2] = "-i";
            options[4] = jaifFile;

            System.arraycopy(InferenceOptions.javaFiles, 0, options, 5, InferenceOptions.javaFiles.length);

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

    private static List<File> findWrittenFiles(String output) {
        //This will be brittle; if the AFU Changes it's output string then no files will be found
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

    private static String getJaifFilePath(File outputDir) {

        String jaifFile = InferenceOptions.jaifFile;
        if (jaifFile == null) {
            jaifFile = new File(outputDir, "inference.jaif").getAbsolutePath();
        }

        return jaifFile;
    }

    private static List<String> getMemoryArgs() {
        //this should instead read them from InferenceOptions and fall back to this if they are not present
        //perhaps just find all -J
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

    public static List<String> getInferenceRuntimeBootJars() {
        final File distDir = InferenceOptions.pathToThisJar.getParentFile();
        String jdkJarName = PluginUtil.getJdkJarName();

        List<String> filePaths = new ArrayList<>();
        for (File child : distDir.listFiles()) {
            String name = child.getName();
            if (!name.endsWith(jdkJarName)) {
                filePaths.add(child.getAbsolutePath());
            }
        }

        return filePaths;
    }

    //what's used to run the compiler
    public static String getInferenceRuntimeBootclassPath() {
        List<String> filePaths = getInferenceRuntimeBootJars();
        return "-Xbootclasspath/p:" + PluginUtil.join(File.pathSeparator, filePaths);
    }

    //what the compiler compiles against
    public static String getInferenceCompilationBootclassPath() {
        String jdkJarName = PluginUtil.getJdkJarName();
        final File jdkFile = new File(InferenceOptions.pathToThisJar.getParentFile(), jdkJarName);

        return "-Xbootclasspath/p:" + jdkFile.getAbsolutePath();
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
