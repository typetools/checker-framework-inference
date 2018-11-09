package checkers.inference.solver.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.checkerframework.javacutil.BugInCF;

public class FileUtils {

    /**
     * Helper method which opens the given file and returns a PrintStream to the
     * file.
     *
     * @param file
     *            a file to be written to.
     * @param append
     *            if set to true the file will be appended, and if set to false
     *            the file will be written over.
     * @return a PrintStream to the file.
     */
    public static PrintStream getFilePrintStream(File file, boolean append) {
        try {
            return new PrintStream(new FileOutputStream(file, append));
        } catch (FileNotFoundException e) {
            throw new BugInCF("Cannot find file " + file);
        }
    }

    /**
     * Writes the given content to the given file. This method overwrites the
     * given file if it already exists.
     *
     * @param file
     *            a file to be written to.
     * @param content
     *            the content to be written to the file.
     */
    public static void writeFile(File file, String content) {
        writeToFile(file, content, false);
    }

    /**
     * Writes the given content to the given file. This method appends to the
     * given file if it already exists.
     *
     * @param file
     *            a file to be written to.
     * @param content
     *            the content to be written to the file.
     */
    public static void appendFile(File file, String content) {
        writeToFile(file, content, true);
    }

    private static void writeToFile(File file, String content, boolean append) {
        try (PrintStream stream = getFilePrintStream(file, append)) {
            stream.println(content);
        }
    }
}
