package checkers.inference.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import checkers.inference.util.JaifFileReader.JaifPackage;

/**
 * Splits a JAIF file by package into many JAIFs and writes them to the outputDir.
 * Creates a shell script that will insert all of them one-by-one.
 *
 * When running insert-annotation-to-source you must pass a list of Java files as arguments.  Sometimes this
 * list is longer than the permissiable number of arguments on the command line (this occurs for Hadoop).
 * In these cases you can use the JaifSplitter to split the jaif into many files that can be inserted individually.
 * In order to make it easy to insert all of them, JaifSplitter will also write a shell script that will
 * insert all of the jaifs one-by-one.
 *
 * Splitting jaifs also has the following effects:
 *   a) The AFU doesn't have to search as many Java files when searching for the correct file to insert into.
 *   b) If the AFU crashes on a malformed JAIF for one package, annotations are still inserted into the other
 *   packages
 *   c) The shell script can and will report when it has completed a package and how many total packages
 *   there are.  Therefore, you can get some sense of progress when inserting the annotations for a large project
 *
 *   To use JaifSplitter from source code, create a new JaifSplitter and call jaifSplitter.split()
 *   To use JaifSplitter from the command-line, run scripts/splitJaif or scripts/debugSplitJaif
 */
public class JaifSplitter {

    /**
     * The input JAIF file
     */
    private final File jaifFile;

    /**
     */
    private final File outputDir;
    private final File commandFile;
    private final List<String> annos;
    private final String srcPattern;

    /**
     *  @param jaifFile the input JAIF file
     *  @param outputDir the directory in which the new jaifs, created by splitting jaifFile, will be placed
     *  @param commandFile the shell file in which to place the shell script for inserting all jaifs in outputDir
     *  @param srcPattern in order to limit the directories searched for java file, srcPattern should be a path
     *                    from the projects root to an ancestor directory of all java files
     *                    e.g. for Maven project, src/main/java
     *  @param annos the annotations to place in every jaif files header, these annotations should be strings of
     *               the following format:
     *               fully.qualified.AnnotationClassName[type arg, type arg]
     *               Where "type arg" specifies a property of AnnotationClassName, e.g. "int value"
     *               VarAnnot would be represented as checkers.inference.qual.VarAnnot[int value]
     */
    public JaifSplitter(File jaifFile, File outputDir, File commandFile, String srcPattern, List<String> annos) {
        this.jaifFile = jaifFile;
        this.outputDir = outputDir;
        this.commandFile = commandFile;
        this.srcPattern = srcPattern;
        this.annos = annos;
    }

    public static void main(String [] args) {
        if (args.length < 5) {
            printError("Too few arguments");
        } //else

        List<String> annos = new ArrayList<>();
        for (int i = 4; i < args.length; i++) {
            annos.add(args[i]);
        }

        new JaifSplitter(new File(args[0]), new File(args[1]), new File(args[2]), args[3], annos).split();
    }

    public static void printUsage() {
        System.out.println("This class splits a large jaif into a number of smaller jaifs and generates a shell script "
              + "that will insert the jaif into source code starting at $PWD/**/srcPattern\n");
        System.out.println("Usage: JaifSplitter jaifFile outputDir commandFile srcPattern anno [anno ...]");
        System.out.println("    jaifFile -- the file to split");
        System.out.println("    outputDir -- the directory in which to put the new jaifs");
        System.out.println("    commandFile -- the sh file in which to place sh commands to insert the new jaifs");
        System.out.println("    srcPattern -- a path from the root of your project directory to the start of " +
                "the source files, e.g. for hadoop src/main/java");
        System.out.println("    anno -- annotations used in the jaif, there must be at least 1.  Use the format "
                + "my.path.to.Qual[String arg1, int arg2] or my.path.to.no.arg.Qual[] e.g., "
                + "checkers.inference.qual.VarAnnot[int value]");
    }

    public static void printError(String message) {
        System.out.println(message);
        printUsage();
        System.exit(1);
    }

    public void split() {
        makeDirectoryOrFail(outputDir);
        String header = makeHeader(annos);
        splitAndWrite(jaifFile, outputDir, commandFile, srcPattern, header);
    }

    public static Pattern ANNOTATION_PATTERN = Pattern.compile("^((?:\\w+\\.)*)(\\w+)\\[(.*)\\]$");

    /**
     * Split a large jaif into smaller jaifs by package name.  The smaller jaifs are placed
     * in output dir.  This method also generates a shell script full of insert-annotations-to-source
     * commands that will insert all of the jaifs.
     * @param jaifFile The file to be split
     * @param outputDir The directory to place the output jaifs
     * @param commandFile The shell script in which to place the insert commands
     * @param header A header placed at the top of every jaif
     */
    protected static void splitAndWrite(File jaifFile, File outputDir, File commandFile, String srcPattern, String header) {
        Set<String> insertionCommands = new LinkedHashSet<>();
        Set<File> visitedFiles = new LinkedHashSet<>();

        JaifFileReader reader = new JaifFileReader(jaifFile);
        for (JaifPackage jaifPackage : reader) {
            final File outputJaif = new File(outputDir, jaifPackage.name + ".jaif");
            if (!visitedFiles.contains(outputJaif)) {
                overwriteLines(outputJaif, Arrays.asList(header, "\n"));

                insertionCommands.add(makeInsertionCommand(outputJaif, jaifPackage.name, srcPattern));
                visitedFiles.add(outputJaif);
            }

            System.out.print("Writing to package file: " + outputJaif.getName() + " -- ");
            long time = System.currentTimeMillis();

            appendLines(outputJaif, jaifPackage.getLines());
            long timeWriting = System.currentTimeMillis() - time;
            System.out.println("Done! " + ((timeWriting) / 1000f) + " seconds");
            System.out.flush();
        }

        System.out.println("Writing " + insertionCommands.size() + " insertions commands to file:\n"
                + commandFile.getAbsolutePath());
        writeInsertShellScript(commandFile, insertionCommands, visitedFiles);
    }

    public static void writeInsertShellScript(File commandFile, Set<String> insertionCommands, Set<File> jaifs) {

        List<String> shFileCommands = new ArrayList<>(insertionCommands.size() + 6);
        shFileCommands.add("if [ -z \"$1\" ]");
        shFileCommands.add("then");
        shFileCommands.add("echo \"You must specify 1 argument, the root of the source directory that contains " +
                "all source files to which annotations should be added.\"");
        shFileCommands.add("exit 1");
        shFileCommands.add("fi");
        shFileCommands.add("");

        int current = 0;
        int total = insertionCommands.size();

        Iterator<String> cmdsIter = insertionCommands.iterator();
        Iterator<File> jaifIter = jaifs.iterator();

        while (cmdsIter.hasNext()) {
            String insertionCommand = cmdsIter.next();
            File jaif = jaifIter.next();

            shFileCommands.add("echo \"-----------------\"");
            shFileCommands.add("echo \"running insertion (" + current + " / " + total + ") on file " + jaif.getAbsolutePath() + "\"" );
            shFileCommands.add(insertionCommand);
            shFileCommands.add("echo \"-----------------\"");
            shFileCommands.add("echo \"\"");
            shFileCommands.add("echo \"\"");
            ++current;
        }


        overwriteLines(commandFile, shFileCommands);
    }

    public static void makeDirectoryOrFail(final File dir) {
        if (!dir.exists() && !dir.mkdir()) {
            throw new RuntimeException("Could not create directory: " + dir.getAbsolutePath());
        }
    }


    /**
     * Writes the input set of lines to the given file
     * @param file destination file for lines
     * @param append whether or not to append the lines to the file
     * @param lines the lines to write
     */
    private static void writeLines(File file, boolean append, Iterable<? extends Object> lines) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, append));
            for (Object line : lines) {
                writer.write(line.toString());
                writer.newLine();
            }

            writer.flush();
            writer.close();

        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private static void appendLines(File file, Iterable<? extends Object> lines) {
        writeLines(file, true, lines);
    }

    private static void overwriteLines(File file, Iterable<? extends Object> lines) {
        writeLines(file, false, lines);
    }


    private static class AnnotationDescription {
        public final String packageName;
        public final String className;
        public final List<String> argsList;

        AnnotationDescription(String packageName, String className, List<String> argsList) {
            this.packageName = packageName;
            this.className = className;
            this.argsList = argsList;
        }
    }
    /**
     * Takes a list of fully qualified annotations and turns them into Jaif headers that
     * allow them to be used within insertions
     * @param fullyQualifiedAnnotations
     * @return
     */
    public static String makeHeader(List<String> fullyQualifiedAnnotations) {
        StringBuffer header = new StringBuffer();
        for (String fullyQualifiedAnnotation : fullyQualifiedAnnotations) {
            AnnotationDescription description = parseAnnotation(fullyQualifiedAnnotation);
            header.append("package ");
            header.append(description.packageName);
            header.append(":\n");

            header.append("  annotation ");
            header.append(description.className);
            header.append(":\n");

            for (String args : description.argsList) {
                header.append("     " + args);
                header.append("\n");
            }
        }

        return header.toString();
    }


    /**
     * This method does not handle nested annotations at the moment
     *
     * @param fullyQualifiedAnnotation
     * @return (packageName, AnnotationName)
     */
    private static AnnotationDescription parseAnnotation(String fullyQualifiedAnnotation) {
        Matcher matcher = ANNOTATION_PATTERN.matcher(fullyQualifiedAnnotation);
        if (matcher.matches()) {
            String packageName = matcher.group(1);
            packageName = packageName.substring(0, packageName.length() - 1);  //drop trailing (.)

            String annotationName = "@" + matcher.group(2);
            List<String> argsList = arrayToArgsList(matcher.group(3).split(","));
            return new AnnotationDescription(packageName, annotationName, argsList);
        }

        throw new IllegalArgumentException(
                "Annotations must be in the form: package.path.ClassName[arg1,arg2,...,argN]\n" +
                "found: " + fullyQualifiedAnnotation);
    }

    private static List<String> arrayToArgsList(String [] args) {
        List<String> argList = new ArrayList<>(args.length);
        for (String arg : args) {
            arg = arg.trim();
            if (!arg.isEmpty()) {
                argList.add(arg);
            }
        }

        return argList;
    }

    /**
     * Given a jaif file and the corresponding package name for that jaif file, return an insert-annotations-to-source
     * command that will find the correct files and insert the jaif
     */
    private static String makeInsertionCommand(File jaifFile, String packageName, String srcPattern) {
        String packagePath = packageName.replace(".", File.separator);

        //right now this is tailored for hadoop
        String srcPath = "**/" + srcPattern + File.separator + packagePath + File.separator + "*.java";
        String findPackageSourcesCmd = " `find $1 -path " + srcPath + "`";

        return "insert-annotations-to-source -i \"" + jaifFile.getAbsolutePath() + "\" " + findPackageSourcesCmd;
    }

}

