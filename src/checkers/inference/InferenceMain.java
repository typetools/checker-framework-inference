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
 * InferenceCli creates an instance of InferenceMain to handle the rest of the inference process.
 * This InferenceMain instance is made accessible by the rest of Checker-Framework-Inference through a static method.
 * InferenceMain uses the InferrableChecker of the target checker to instantiate components and wire them together.
 * It creates and holds instances to the InferenceVisitor, the InferenceAnnotatedTypeFactory, the InferrableChecker, etc.
 *
 * InferenceMain invokes the Checker-Framework programmatically using the javac api to run the InferenceChecker.
 * Checker-Framework runs the InferenceChecker as a normal checker. Since javac is running in the same JVM
 * and with the same classloader as InferenceMain, the InferenceChecker can access the static InferenceMain instance.
 *
 * During its initialization, InferenceChecker uses InferenceMain to get an instance of the InferenceVisitor.
 * The Checker-Framework then uses this visitor to type-check the source code. For every compilation unit (source file) in the program,
 * the InferenceVisitor scans the AST and generates constraints where each check would have occurred.
 * InferenceMain manages a ConstraintManager instance to store all constraints generated.
 *
 * After the last compilation unit has been scanned by the visitor, the Checker-Framework call completes and
 * control returns to InferenceMain. InferenceMain checks the return code of javac. 
 * The Checker-Framework will return an error if no source files were specified, if the specified source files did not exist,
 * or if the source files fail to compile. Error codes for other reasons generally result from bugs in Checker-Framework-Inference;
 * inference only generates constraints, it does not enforce type-checking rules.
 *
 * If the Checker-Framework call does not return an error, Checker-Framework-Inference will then process the generated constraints.
 * The constraints are solved using an InferenceSolver and then a JAIF is created to allow insertion of inferred annotations back into the input program.
 * InferenceSolver is an interface that all solvers must implement. Checker-Framework-Inference can also serialize the constraints for processing later (by a solver or by Verigames).
 *
 * In the future, Checker-Framework-Inference might be able to use the inferred annotations for type-checking without first inserting the annotations into the input program. 
 *
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
    private SlotManager slotManager;

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
                "-Xmaxwarns", "1000",
                "-Xmaxerrs", "1000",
                "-AprintErrorStack",
                "-XDignore.symbol.file",
                "-AsuppressWarnings=purity",
                "-Awarns"));

        if (options.has("stubs")) {
            checkerFrameworkArgs.add("-Astubs=" + options.valueOf("stubs"));
        }
        if (options.has("flowdotdir")) {
            checkerFrameworkArgs.add("-Aflowdotdir=" + options.valueOf("flowdotdir"));
        }
        if (options.has("showchecks")) {
            checkerFrameworkArgs.add("-Ashowchecks");
        }

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
            visitor = getRealChecker().createVisitor(inferenceChecker, getInferenceTypeFactory(), true);
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

    @SuppressWarnings( "deprecation" )
    private InferenceAnnotatedTypeFactory getInferenceTypeFactory() {
        if (inferenceTypeFactory == null) {
            inferenceTypeFactory = new InferenceAnnotatedTypeFactory(inferenceChecker,
                    getRealChecker().withCombineConstraints(),
                    getRealTypeFactory(),
                    getRealChecker(),
                    getSlotManager(),
                    getConstraintManager());
            logger.trace("Created InferenceAnnotatedTypeFactory");
        }
        return inferenceTypeFactory;
    }


    /**
     * This method is NOT deprecated but SHOULD NOT BE USED other than in getInferenceTypeFactory AND
     * InferenceAnnotatedTypeFactory.getSupportedQualifierTypes.  We have made it deprecated in order to bring
     * this to the attention of future programmers.  We would make it private if it weren't for the fact that
     * we need the realTypeFactory qualifiers in getSupportedQualifierTypes and it is called in the super class.
     */
    @Deprecated
    BaseAnnotatedTypeFactory getRealTypeFactory() {
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
        if( slotManager == null ) {
            slotManager = new DefaultSlotManager( inferenceChecker.getProcessingEnvironment(),
                                                  getRealTypeFactory().getSupportedTypeQualifiers() );
            logger.trace("Create slot manager", slotManager );
        }
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
