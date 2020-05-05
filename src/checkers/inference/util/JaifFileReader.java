package checkers.inference.util;

import checkers.inference.util.JaifFileReader.JaifPackage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A JaifFileIterator will open a jaif and provide an iterator that reads
 * Jaif information package by package.  Note, if a package occurs multiple times
 * then there will be multiple Package objects for this package; they will NOT
 * be aggregated into 1
 *
 * To use JaifIterator
 *
 * 1.  Create a new iterator:  new JaifFileIterator(jaifFile)
 * 2.  Open the iterator (this opens the file, creates a file handle)
 * 3.  Iterate over the iterator
 * 4.  Close the iterator (this will close the file handle)
 */
public class JaifFileReader implements Iterable<JaifPackage> {
    private final File jaifFile;
    private final Pattern PACKAGE_REGEX = Pattern.compile("package (.*):");

    @Override
    public JaifFileIterator iterator() {
        try {
            return new JaifFileIterator(jaifFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public class Block{
        public final List<String> lines;

        Block(List<String> lines) {
            this.lines = lines;
        }

        @Override
        public String toString() {
            return String.join("\n", lines);
        }
    }

    public class JaifPackage {
        public final String name;
        public final List<Block> entries;

        JaifPackage(String name, List<Block> entries) {
            this.name = name;
            this.entries = entries;
        }

        public List<String> getLines() {
            List<String> lines = new ArrayList<>();
            lines.add("package " + name + ":");

            for (Block block : entries) {
                lines.add(block.toString());
                lines.add("\n");
            }

            return lines;
        }
    }

    public JaifFileReader(File jaifFile) {
        this.jaifFile = jaifFile;
    }

    /*
    * Breaks a file into Packages.  Each package consists of a list of Blocks,
    * a block is a sequential set of lines in the original JAIF file ending in a newline,
    * which is excluded.
    */
    public class JaifFileIterator implements Iterator<JaifPackage> {
        private final BufferedReader bufferedReader;
        private boolean endOfStream = false;
        private boolean closed = false;
        private String nextPackageName = null;

        private JaifPackage nextPackage;

        public JaifFileIterator(File file) throws IOException {
            bufferedReader = new BufferedReader(new FileReader(file));
            nextPackage = readNext();
        }

        public void close() throws IOException {
            if (!closed) {
                closed = true;
                bufferedReader.close();
            }
        }

        @Override
        public boolean hasNext() {
            return nextPackage != null;
        }

        @Override
        public JaifPackage next() {
            JaifPackage current = nextPackage;
            try {
                nextPackage = readNext();
            } catch (IOException e) {
                e.printStackTrace();

                // if we don't exit and someone caught a stack trace then
                // the iterator might never be closed (if used in a for loop)
                System.exit(1);
            }

            return current;
        }

        protected JaifPackage readNext() throws IOException {
            JaifPackage next = null;
            if (!closed && !endOfStream) {
                List<String> lineBuffer = new ArrayList<>();
                List<Block> blockBuffer = new ArrayList<>();

                String line = null;
                while ((line = bufferedReader.readLine()) != null) {
                    line = line.trim();

                    Matcher matcher = PACKAGE_REGEX.matcher(line);

                    // if we have a packageName (and it isn't the first one)
                    // create a package and stop reading lines
                    if (matcher.matches()) {
                        if (nextPackageName != null) {
                            addBlocks(lineBuffer, blockBuffer);
                            next = new JaifPackage(nextPackageName, blockBuffer);
                            nextPackageName = matcher.group(1);
                            return next;
                        } else {
                            // it is the first package's name, save it and continue
                            nextPackageName = matcher.group(1);
                        }
                    } else if (line.isEmpty()) {
                        addBlocks(lineBuffer, blockBuffer);

                    } else {
                        lineBuffer.add(line);
                    }

                }

                if (line == null) {
                    addBlocks(lineBuffer, blockBuffer);
                    next = new JaifPackage(nextPackageName, blockBuffer);
                    endOfStream = true;
                    close();
                }
            }

            // return null if none-remain
            return next;
        }


        /**
         * Creates a Block object using the lines in lineBuffer and clears the lineBuffer.  The resulting
         * Block object is added to blockBuffer.  If lineBuffer is empty, then no object is created or added
         */
        private void addBlocks(List<String> lineBuffer, List<Block> blockBuffer) {
            if (!lineBuffer.isEmpty()) {
                blockBuffer.add( new Block(new ArrayList<>(lineBuffer)));
                lineBuffer.clear();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
