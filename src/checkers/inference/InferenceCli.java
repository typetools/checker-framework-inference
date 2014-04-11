package checkers.inference;

import java.io.IOException;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

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

    private static final Logger logger = LoggerFactory.getLogger(InferenceCli.class);

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
        parser.accepts("showchecks");

        OptionSet options = parser.parse(args);
        if (options.hasArgument("log-level")) {
            setLoggingLevel(Level.toLevel(options.valueOf("log-level").toString().toUpperCase()));
        } else {
            setLoggingLevel(Level.INFO);
        }

        if (options.has("version")) {
            System.out.println("Checker-framework-inference version: " + VERSION);
        } else if (options.has("help")) {
            System.out.println("Running help");
            parser.printHelpOn(System.out);
        } else {
            logger.debug("Running inference with options: {}", options.asMap());
            InferenceMain inferenceMain = new InferenceMain(options);
            inferenceMain.run();
        }
    }

    public static void setLoggingLevel(Level level) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(level);
    }
}









