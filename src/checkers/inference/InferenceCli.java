package checkers.inference;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.plumelib.options.Option;
import org.plumelib.options.Options;

/**
 * Command line launcher for Checker-Framework-Inference.
 *
 * Parses command line options and creates InferenceMain
 * instance to start inference system.
 *
 * @author mcarthur
 *
 */
public class InferenceCli {

    private static Logger logger = Logger.getLogger(InferenceCli.class.getName());
    public static final String VERSION = "2";
    public static final String DEFAULT_JAIF = "default.jaif";

    // Modes
    @Option("-v print version")
    public static boolean version;
    @Option("-h print help")
    public static boolean help;

    // Required
    @Option("[InferrableChecker] the checker to run")
    public static String checker;

    @Option("[Level] set the log level")
    public static String log_level;
    @Option("[InferenceSolver] solver to use on constraints")
    public static String solver;
    @Option("[path] path to write jaif")
    public static String jaiffile = DEFAULT_JAIF;
    @Option("encoding")
    public static String encoding;
    @Option("[dir] directory to write dataflow diagrams")
    public static String flowdotdir;
    @Option("Args to pass to javac compiler")
    public static String javac_args;
    @Option("Args to pass to solver")
    public static String solver_args;
    @Option("bootclasspath to use for compiling")
    public static String bootclasspath;
    @Option("showchecks")
    public static boolean showchecks;
    @Option("ignore logs of exceptions")
    public static boolean hackmode;
    @Option("only perform type checking -- don't generate class files")
    public static boolean proconly = true;
    @Option("[path] stubfiles to use for type checking")
    public static String stubs;

    // All other options passed in.
    public static String[] otherOptions;

    public static void main(String[] args) throws IOException {
        initCli(args);
        InferenceMain inferenceMain = new InferenceMain();
        inferenceMain.run();
    }

    public static void initCli(String [] args) {
        Options options = new Options("InferenceCli [options]", InferenceCli.class);
        otherOptions = options.parse(true, args);

        if (help) {
            options.printUsage();
            System.exit(0);
        }

        if (version) {
            System.out.println("Checker-framework-inference version: " + VERSION);
            System.exit(0);
        }

        if (log_level == null) {
            setLoggingLevel(Level.INFO);
        } else {
            setLoggingLevel(Level.parse(log_level));
        }

        String optionsStr = "";
        for (String arg : args) {
            optionsStr += arg + " ";
        }
    }

    /**
     * Set the root logging level and handler level.
     */
    public static void setLoggingLevel(Level level) {
        Logger root = Logger.getLogger("");
        root.setLevel(level);

        // Handler for console (reuse it if it already exists)
        Handler consoleHandler = null;
        // see if there is already a console handler
        for (Handler handler : root.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                // found the console handler
                consoleHandler = handler;
                break;
            }
        }

        if (consoleHandler == null) {
            // there was no console handler found, create a new one
            consoleHandler = new ConsoleHandler();
            root.addHandler(consoleHandler);
        }
        // set the console handler to fine:
        consoleHandler.setLevel(level);
    }
}
