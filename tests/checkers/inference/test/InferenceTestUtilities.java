package checkers.inference.test;

import org.checkerframework.framework.test.TestUtilities;
import org.checkerframework.framework.test.TypecheckResult;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;

/**
 * Created by jburke on 7/7/15.
 */
public class InferenceTestUtilities {

    public static List<File> replaceParentDirs(File newParent, List<File> testSourceFiles) {
        List<File> outFiles = new ArrayList<>(testSourceFiles.size());
        for (File file : testSourceFiles) {
            outFiles.add(new File(newParent, file.getName()));
        }
        return outFiles;
    }

    public static List<File> replacePath(File testDataDir, File annotatedSourceDir, List<File> testSourceFiles) {
        final String testDataPath = testDataDir.getAbsolutePath();
        final String annotatedSrcPath = annotatedSourceDir.getAbsolutePath();

        List<File> annotatedTestDataPath = new ArrayList<>();
        for (File sourceFile : testSourceFiles) {
            String sourcePath = sourceFile.getAbsolutePath();
            String annotatedSourcePath = sourcePath.replace(testDataPath, annotatedSrcPath);
            annotatedTestDataPath.add(new File(annotatedSourcePath));
        }

        return annotatedTestDataPath;
    }

    public static File findCheckerFrameworkDir() {

        String checkerProp = System.getenv("CHECKERFRAMEWORK");
        if (checkerProp == null) {
            checkerProp = "../checkerframework";
        }

        final File checkerFrameworkDir = new File(checkerProp);
        if (!checkerFrameworkDir.exists() || !checkerFrameworkDir.isDirectory()) {
            throw new RuntimeException(
                    "Could not find Checker Framework framework dir.  Environment variable"
                            + "CHECKERFRAMEWORK should be set to the top level of the Checker Framework repo");
        }

        return checkerFrameworkDir;
    }

    public static File findInCheckerFrameworkDir(String pathInCheckerFrameworkDir) {

        String checkerProp = System.getenv("CHECKERFRAMEWORK");
        if (checkerProp == null) {
            checkerProp = "../checkerframework";
        }

        final File checkerFrameworkDir = new File(checkerProp);
        if (!checkerFrameworkDir.exists() || !checkerFrameworkDir.isDirectory()) {
            throw new RuntimeException(
                    "Could not find Checker Framework framework dir.  Environment variable"
                            + "CHECKERFRAMEWORK should be set to the top level of the Checker Framework repo");
        }

        return new File(checkerFrameworkDir, pathInCheckerFrameworkDir);
    }

    public static void assertExists(File file) {
        if (!file.exists()) {
            throw new RuntimeException("Could not find file: " + file.getAbsolutePath());
        }
    }

    public static void assertIsDir(File file) {
        assertExists(file);
        if (!file.isDirectory()) {
            throw new RuntimeException("File is not a directory: " + file.getAbsolutePath());
        }
    }

    public static void assertFail(InferenceTestPhase lastPhase, String summary) {
        String message =
            "Test failed on " + lastPhase + "!\n" + summary;
        Assert.fail(message);
    }

    public static void assertResultsAreValid(InferenceTestResult testResult) {
        final InferenceTestPhase lastPhaseRun = testResult.getLastPhaseRun();

        switch (lastPhaseRun) {
            case INITIAL_TYPECHECK:
                assertFail(InferenceTestPhase.INITIAL_TYPECHECK, testResult.getInitialTypecheckingResult().summarize());
                break;

            case INFER:
                assertFail(InferenceTestPhase.INFER, testResult.getInferenceResult().summarize());
                break;

            case INSERT:
                assertFail(InferenceTestPhase.INSERT, testResult.getInsertionResult().summarize());
                break;

            case FINAL_TYPECHECK:
                TypecheckResult finalTypecheckResult = testResult.getFinalTypecheckResult();
                if (finalTypecheckResult.didTestFail()) {
                    assertFail(InferenceTestPhase.INSERT, finalTypecheckResult.summarize());
                }
                break;
        }
    }

    public static List<File> findAllSystemTests() {
        File frameworkTestsDir = InferenceTestUtilities.findInCheckerFrameworkDir("framework/tests");
        InferenceTestUtilities.assertIsDir(frameworkTestsDir);
        return TestUtilities.findRelativeNestedJavaFiles(frameworkTestsDir, "all-systems");
    }

    /**
     * The Annotation File Utility has adds annotations followed by a new line for
     * fields/methods.  This will cause diagnostics that were on those fields and methods to
     * point to the wrong line.  This method detects any location where there is an annotation
     * on a line by itself followed by a non-empty line.  It moves that annotation onto the next line.
     *
     * @param javaFile
     */
    public static void inlineAnnotationsAfterDiagnostics(final File javaFile) {

        final List<String> originalLines = getLines(javaFile);
        final List<String> inlinedLines = inlineAnnotationsAfterDiagnostics(originalLines);
        writeLines(inlinedLines, javaFile);
    }

    public static List<String> inlineAnnotationsAfterDiagnostics(List<String> lines) {
        final String annotationLineRegex = "^\\s*@[a-zA-Z_0-9.]*\\s*$";
        final Pattern annotationLinePattern = Pattern.compile(annotationLineRegex);
        final Pattern nonWhitespacePattern = Pattern.compile("\\S");

        List<String> outputLines = new ArrayList<>();

        int i = 0;
        while (i < lines.size() - 1) {

            String currentLine = lines.get(i);
            if (annotationLinePattern.matcher(currentLine).matches()) {
                String nextLine = lines.get(i + 1);
                Matcher nonWhitespaceMatcher = nonWhitespacePattern.matcher(nextLine);

                if (!nonWhitespaceMatcher.find()) {
                    i += 2; //skip this line and the blank line
                    outputLines.add(currentLine);
                    outputLines.add(nextLine);
                    continue;
                } //else

                int firstNonWhitespaceIndex = nonWhitespaceMatcher.start();
                String withoutIndent = nextLine.substring(firstNonWhitespaceIndex);
                boolean isCommented = withoutIndent.startsWith("\\\\") || withoutIndent.startsWith("\\*");
                if (isCommented) {
                    i += 2; //skip this line and the comment line
                    outputLines.add(currentLine);
                    outputLines.add(nextLine);
                    continue;
                } //else

                String indent = nextLine.substring(0, firstNonWhitespaceIndex);
                String nextLineWithAnno = indent + currentLine.trim() + " " + withoutIndent;

                i += 2; //skip this line and the next line
                outputLines.add(nextLineWithAnno);

            } else {
                outputLines.add(currentLine);
                ++i;
            }
        }

        if (i == lines.size() - 1) {
            outputLines.add(lines.get(i));
        } //else this was consumed in the while loop above

        return outputLines;
    }

    public static List<String> getLines(File javaFile) {
        try {
            final List<String> lines = new ArrayList<>();
            final BufferedReader lineReader = new BufferedReader(new FileReader(javaFile));
            String line;
            while (true) {
                line = lineReader.readLine();
                if (line != null) {
                    lines.add(line);
                } else {
                    break;
                }

            }
            lineReader.close();
            return lines;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeLines(List<String> lines, File javaFile) {

        try {
            final BufferedWriter lineWriter = new BufferedWriter(new FileWriter(javaFile));
            for (String line : lines) {
                lineWriter.write(line);
                lineWriter.newLine();
            }
            lineWriter.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
