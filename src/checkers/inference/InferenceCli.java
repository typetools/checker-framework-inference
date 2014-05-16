package checkers.inference;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.Level;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

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

    public static final String VERSION = "2";
    public static final String DEFAULT_SOLVER = "checkers.inference.floodsolver.PropagationSolver";
    public static final String DEFAULT_JAIF = "default.jaif";

    private static Logger logger = Logger.getLogger(InferenceCli.class.getName());

    public static void main(String[] args) throws IOException {



        OptionParser parser = new OptionParser();
        parser.accepts("version");
        parser.accepts("help", "show help" ).forHelp();

        parser.accepts("checker").withRequiredArg().required();

        parser.accepts("solver").withRequiredArg().defaultsTo(DEFAULT_SOLVER);
        parser.accepts("jaiffile").withRequiredArg().defaultsTo(DEFAULT_JAIF);
        parser.accepts("stubfiles").withRequiredArg();
        parser.accepts("log-level").withRequiredArg();
        parser.accepts("encoding").withRequiredArg();
        parser.accepts("stubs").withRequiredArg();
        parser.accepts("flowdotdir").withRequiredArg();
        parser.accepts("javac-args").withRequiredArg();
        parser.accepts("solver-args").withRequiredArg();
        parser.accepts("bootclasspath").withRequiredArg();
        parser.accepts("showchecks");
        parser.accepts("hackmode");
        parser.accepts("proc-only").withRequiredArg().defaultsTo("" + true);

        OptionSet options = parser.parse(args);
        if (options.hasArgument("log-level")) {
            String option=options.valueOf("log-level").toString();
            Level level = Level.parse(option);
            setLoggingLevel(level);
        } else {
            setLoggingLevel(Level.INFO);
        }

        if (options.has("version")) {
            System.out.println("Checker-framework-inference version: " + VERSION);
        } else if (options.has("help")) {
            System.out.println("Running help");
            parser.printHelpOn(System.out);
        } else {
            logger.config(String.format("Running inference with options: %s", options.asMap()));
            InferenceMain inferenceMain = new InferenceMain(options);
            inferenceMain.run();
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
        //see if there is already a console handler
        for (Handler handler : root.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                //found the console handler
                consoleHandler = handler;
                break;
            }
        }

        if (consoleHandler == null) {
            //there was no console handler found, create a new one
            consoleHandler = new ConsoleHandler();
            root.addHandler(consoleHandler);
        }
        //set the console handler to fine:
        consoleHandler.setLevel(level);
    }

}









