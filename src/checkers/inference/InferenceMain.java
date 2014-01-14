package checkers.inference;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import joptsimple.OptionSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import checkers.basetype.BaseAnnotatedTypeFactory;

/**
 * InferenceMain is the central coordinator to the inference system.
 *
 * InferenceMain uses the programmatic javac library to launch the
 * InferenceChecker. This typechecker interacts with a staticly
 * available instance of InferenceMain.
 *
 * InferenceMain wires the components of the system together:
 * A real TypeChecker
 * The InferenceVisitor
 * The InferenceAnnotatedTypeFactory
 * The real AnnotatedTypeFactory
 *
 * // TODO: Timing
 * @author mcarthur
 *
 */

public class InferenceMain {

    private static final Logger logger = LoggerFactory.getLogger(InferenceCli.class);

    /**
     * Return the single instance of this class.
     *
     * Consumers need an instance to look up
     * Visitors/TypeFactories and to use the InferenceRunContext
     *
     */
    private static InferenceMain inferenceMainInstance;
    public static InferenceMain getInstance() {
        return inferenceMainInstance;
    }

    private OptionSet options;
    private InferenceChecker inferenceChecker;

    private InferenceVisitor<?, InferenceAnnotatedTypeFactory> visitor;
    private InferrableChecker realChecker;
    private BaseAnnotatedTypeFactory realTypeFactory;
    private InferenceAnnotatedTypeFactory inferenceTypeFactory;

    private ConstraintManager constraintManager = new ConstraintManager();
    private SlotManager slotManager = new SlotManager();

    private boolean performingFlow;

    public InferenceMain(OptionSet options) {
        this.options = options;
    }

    public void run() {
        logger.trace("Starting InferenceMain");
        inferenceMainInstance = this;

        // Start up javac
        startCheckerFramework();
    }

    private void startCheckerFramework() {
        List<String> checkerFrameworkArgs = new ArrayList<>(Arrays.asList(
                "-processor", "checkers.inference.InferenceChecker",
                "-proc:only",
                "-encoding", "ISO8859-1", // TODO MAIN4: needed for JabRef only, make optional
                "-Xmaxwarns", "1000",
                "-Xmaxerrs", "1000",
                "-AprintErrorStack",
                // "-Astubs=" + options.valueOf("stubs"),
                // "-Aflowdotdir=dotfiles/",
                // "-Ashowchecks",
                "-Awarns"));

        // Non option arguments (like file names)
        // and any options specified after a -- in the command line
        for (Object arg : options.nonOptionArguments()) {
            checkerFrameworkArgs.add(arg.toString());
        }
        logger.debug("Starting checker framwork with options: {}", checkerFrameworkArgs);

        StringWriter javacoutput = new StringWriter();
        boolean success = CheckerFrameworkUtil.invokeCheckerFramework(checkerFrameworkArgs.toArray(new String[]{}),
                new PrintWriter(javacoutput, true));

        handleCompilerResult(success, javacoutput.toString());

        solve();
    }

    private void handleCompilerResult(boolean success, String javacOutStr) {
        if (!success) {
            logger.error("Error return code from javac! Quitting.");
            logger.debug(javacOutStr);
            System.exit(1);
          }
    }

    public void initInference(InferenceChecker inferenceChecker) {
        this.inferenceChecker = inferenceChecker;
        logger.trace("Received inferneceChecker callback");
    }


    public InferenceVisitor<?, InferenceAnnotatedTypeFactory> getVisitor() {
        if (visitor == null) {
            getRealChecker().createVisitor(inferenceChecker, getInferenceTypeFactory(), true);
            logger.trace("Created InferenceVisitor");
        }
        return visitor;
    }

    private InferrableChecker getRealChecker() {
        if (realChecker == null) {
            try {
                realChecker = (InferrableChecker) Class.forName((String)options.valueOf("checker")).newInstance();
                realChecker.init(inferenceChecker.getProcessingEnvironment());
                realChecker.initChecker();

                logger.trace("Created real checker: {}", realChecker);
            } catch (Throwable e) {
              logger.error("Error instantiating checker class \"" + options.valueOf("checker") + "\".", e);
              System.exit(5);
          }
        }
        return realChecker;
    }

    private InferenceAnnotatedTypeFactory getInferenceTypeFactory() {
        if (inferenceTypeFactory == null) {
            inferenceTypeFactory = new InferenceAnnotatedTypeFactory(inferenceChecker,
                    getRealChecker().withCombineConstraints(),
                    getRealTypeFactory(),
                    getRealChecker());
            logger.trace("Created InferenceAnnotatedTypeFactory");
        }
        return inferenceTypeFactory;
    }

    private BaseAnnotatedTypeFactory getRealTypeFactory() {
        if (realTypeFactory == null) {
            realTypeFactory = getRealChecker().createRealTypeFactory();
            logger.trace("Created real type factory: {}", realTypeFactory);
        }
        return realTypeFactory;
    }

    private void solve() {
        // TODO: Serialize before solving
        // TODO: Prune out unneeded variables

        InferenceSolver solver = getSolver();
        solver.solve(slotManager.getSlots(), constraintManager.getConstraints(),
                options, getRealTypeFactory().getQualifierHierarchy());
    }

    protected InferenceSolver getSolver() {
        try {
            InferenceSolver solver = (InferenceSolver) Class.forName((String)options.valueOf("solver")).newInstance();
            logger.trace("Created solver: {}", solver);
            return solver;
        } catch (Throwable e) {
            logger.error("Error instantiating solver class \"" + options.valueOf("solver") + "\".", e);
            System.exit(5);
            return null; // Dead code
        }
    }

    public SlotManager getSlotManager() {
        return slotManager;
    }

    public ConstraintManager getConstraintManager() {
        return constraintManager;
    }

    public boolean isPerformingFlow() {
        return performingFlow;
    }

    public void setPerformingFlow(boolean performingFlow) {
        this.performingFlow = performingFlow;
    }
}
