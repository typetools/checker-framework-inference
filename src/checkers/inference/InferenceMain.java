package checkers.inference;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.InferenceOptions.InitStatus;
import checkers.inference.model.AnnotationLocation;
import checkers.inference.model.Constraint;
import checkers.inference.model.ConstraintManager;
import checkers.inference.model.VariableSlot;
import checkers.inference.qual.VarAnnot;
import checkers.inference.util.InferenceUtil;
import checkers.inference.util.JaifBuilder;
import org.checkerframework.javacutil.SystemUtil;

/**
 * InferenceMain is the central coordinator to the inference system.
 *
 * The main method creates an instance of InferenceMain to handle the rest of the inference process.
 * This InferenceMain instance is made accessible by the rest of Checker-Framework-Inference through a static method
 * getInstance.
 * InferenceMain uses the InferrableChecker of the target checker to instantiate components and wire them together.
 * It creates and holds instances to the InferenceVisitor, the InferenceAnnotatedTypeFactory, the InferrableChecker, etc.
 *
 * InferenceMain invokes the Checker-Framework programmatically using the javac api to run the InferenceChecker.
 * Checker-Framework runs the InferenceChecker as a normal checker. Since javac is running in the same JVM
 * and with the same classloader as InferenceMain, the InferenceChecker can access the static InferenceMain instance.
 *
 * During its initialization, InferenceChecker uses InferenceMain to get an instance of the InferenceVisitor.
 * The Checker-Framework then uses this visitor to type-check the source code. For every compilation unit (source file)
 * in the program, the InferenceVisitor scans the AST and generates constraints where each check would have occurred.
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

    public final Logger logger = Logger.getLogger(InferenceMain.class.getName());

    /**
     * Return the single instance of this class.
     *
     * Consumers need an instance to look up
     * Visitors/TypeFactories and to use the InferenceRunContext
     *
     */
    private static InferenceMain inferenceMainInstance;

    private InferenceChecker inferenceChecker;

    /**
     * When we are inferring annotations we do not generate all constraints because
     * a type may not yet have it's flow-refined type (and therefore RefinementVariable)
     * applied to it.  This flag is set to true while flow is being performed.
     *
     * It is queried with isPerformingFlow.  Every location from which this method is
     * called is a location we omit from generating constraints during flow.
     */
    private boolean performingFlow;

    private InferenceVisitor<?, ? extends BaseAnnotatedTypeFactory> visitor;
    private InferrableChecker realChecker;
    private BaseAnnotatedTypeFactory realTypeFactory;
    private InferenceAnnotatedTypeFactory inferenceTypeFactory;

    private ConstraintManager constraintManager;
    private SlotManager slotManager;

    // Hold the results of solving.
    private InferenceResult solverResult;

    // Turn off some of the checks so that more bodies of code pass.
    // Eventually we will get rid of this.
    private boolean hackMode;

    private ResultHandler resultHandler;

    public void setResultHandler(ResultHandler resultHandler) {
        this.resultHandler = resultHandler;
    }

    public static void main(String [] args) {
        InitStatus status = InferenceOptions.init(args, false);
        status.validateOrExit();

        InferenceMain inferenceMain = new InferenceMain();
        inferenceMain.run();
    }

    /**
     * Create an InferenceMain instance.
     * Options are pulled from InferenceCli static fields.
     */
    public InferenceMain() {
        if (inferenceMainInstance != null) {
            logger.warning("Only a single instance of InferenceMain should ever be created!");
        }
        inferenceMainInstance = this;
        resultHandler = new DefaultResultHandler(logger);
    }

    public static InferenceMain resetInstance() {
        inferenceMainInstance = null;
        inferenceMainInstance = new InferenceMain();
        return inferenceMainInstance;
    }

    /**
     * Kick off the inference process.
     */
    public void run() {
        logger.finer("Starting InferenceMain");

        // Start up javac
        startCheckerFramework();
        solve();
        // solverResult = null covers case when debug solver is used, but in this case
        // shouldn't exit
        if (solverResult != null && !solverResult.hasSolution()) {
            logger.info("No solution, exiting...");
            System.exit(1);
        }
        writeJaif();
    }

    /**
     * Run the Checker-Framework using InferenceChecker
     */
    private void startCheckerFramework() {
        List<String> checkerFrameworkArgs = new ArrayList<>(Arrays.asList(
                "-processor", "checkers.inference.InferenceChecker",
                "-Xmaxwarns", "1000",
                "-Xmaxerrs", "1000",
                "-XDignore.symbol.file",
                "-Awarns"));

        if (SystemUtil.getJreVersion() == 8) {
            checkerFrameworkArgs.addAll(Arrays.asList("-source", "8", "-target", "8"));
        }

        if (InferenceOptions.cfArgs != null) {
            checkerFrameworkArgs.addAll(parseCfArgs());
        }

        if (InferenceOptions.logLevel == null) {
            InferenceUtil.setLoggingLevel(Level.FINE);
        } else {
            InferenceUtil.setLoggingLevel(Level.parse(InferenceOptions.logLevel));
        }

        if (InferenceOptions.hacks) {
            hackMode = true;
        }

        if (InferenceOptions.javacOptions != null) {
            checkerFrameworkArgs.addAll(InferenceOptions.javacOptions);
        }

        if (InferenceOptions.javaFiles != null) {
            checkerFrameworkArgs.addAll(Arrays.asList(InferenceOptions.javaFiles));
        }

        logger.fine(String.format("Starting checker framework with options: %s", checkerFrameworkArgs));

        StringWriter javacoutput = new StringWriter();
        boolean success = CheckerFrameworkUtil.invokeCheckerFramework(checkerFrameworkArgs.toArray(new String[checkerFrameworkArgs.size()]),
                new PrintWriter(javacoutput, true));

        resultHandler.handleCompilerResult(success, javacoutput.toString());
    }


    /**
     * Give the InferenceMain instance a reference to the InferenceChecker
     * that is being run by Checker-Framework.
     *
     * @param inferenceChecker The InferenceChecker being run by Checker Framework.
     */
    public void recordInferenceCheckerInstance(InferenceChecker inferenceChecker) {
        this.inferenceChecker = inferenceChecker;
        logger.finer("Received InferenceChecker callback");
    }

    /**
     * Create a jaif file that records the mapping of VariableSlots to their code positions.
     * The output file can be configured by the command-line argument jaiffile.
     */
    private void writeJaif() {
        try (PrintWriter writer
                = new PrintWriter(new FileOutputStream(InferenceOptions.jaifFile))) {

            List<VariableSlot> varSlots = slotManager.getVariableSlots();
            Map<AnnotationLocation, String> values = new HashMap<>();
            Set<Class<? extends Annotation>> annotationClasses = new HashSet<>();

            if (solverResult == null) {
                annotationClasses.add(VarAnnot.class);
            } else {
                for (Class<? extends Annotation> annotation : realTypeFactory.getSupportedTypeQualifiers()) {
                    annotationClasses.add(annotation);
                }
                // add any custom annotations that must be inserted to the JAIF header, such as alias annotations
                for (Class<? extends Annotation> annotation : realChecker.additionalAnnotationsForJaifHeaderInsertion()) {
                    annotationClasses.add(annotation);
                }
            }
            for (VariableSlot slot : varSlots) {
                if (slot.getLocation() != null && slot.isInsertable()
                 && (solverResult == null || solverResult.containsSolutionForVariable(slot.getId()))) {
                    // TODO: String serialization of annotations.
                    if (solverResult != null) {
                        // Not all VariableSlots will have an inferred value.
                        // This happens for VariableSlots that have no constraints.
                        AnnotationMirror result = solverResult.getSolutionForVariable(slot.getId());
                        if (result != null) {
                            values.put(slot.getLocation(), result.toString());
                        }
                    } else {
                        // Just use the VarAnnot in the jaif.
                        String value = slotManager.getAnnotation(slot).toString();
                        values.put(slot.getLocation(), value);
                    }
                }
            }

            JaifBuilder builder = new JaifBuilder(values, annotationClasses, realChecker.isInsertMainModOfLocalVar());
            String jaif = builder.createJaif();
            writer.println(jaif);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to write out jaif file!", e);
        }
    }

    /**
     * Solve the generated constraints using the solver specified on the command line.
     */
    private void solve() {
        // TODO: PERHAPS ALLOW SOLVERS TO DECIDE IF/HOW THEY WANT CONSTRAINTS NORMALIZED

        final ConstraintNormalizer constraintNormalizer = new ConstraintNormalizer();
        Set<Constraint> normalizedConstraints = constraintNormalizer.normalize(constraintManager.getConstraints());

        // TODO: Support multiple solvers or serialize before or after solving
        // TODO: Prune out unneeded variables
        // TODO: Options to type-check after this.

        if (InferenceOptions.solver != null) {
            InferenceSolver solver = getSolver();
            this.solverResult = solver.solve(
                    parseSolverArgs(),
                    slotManager.getSlots(),
                    normalizedConstraints,
                    getRealTypeFactory().getQualifierHierarchy(),
                    inferenceChecker.getProcessingEnvironment());
        }
    }

    // ================================================================================
    // Component Initialization
    // ================================================================================

    public InferenceVisitor<?, ? extends BaseAnnotatedTypeFactory> getVisitor() {
        if (visitor == null) {
            visitor = getRealChecker().createVisitor(inferenceChecker, getInferenceTypeFactory(), true);
            logger.finer("Created InferenceVisitor");
        }
        return visitor;
    }

    private InferrableChecker getRealChecker() {
        if (realChecker == null) {
            try {
                realChecker = (InferrableChecker) Class.forName(
                        InferenceOptions.checker, true, ClassLoader.getSystemClassLoader()).getDeclaredConstructor().newInstance();
                realChecker.init(inferenceChecker.getProcessingEnvironment());
                realChecker.initChecker();
                logger.finer(String.format("Created real checker: %s", realChecker));
            } catch (Throwable e) {
              logger.log(Level.SEVERE, "Error instantiating checker class \"" + InferenceOptions.checker + "\".", e);
              System.exit(5);
          }
        }
        return realChecker;
    }

    private InferenceAnnotatedTypeFactory getInferenceTypeFactory() {
        if (inferenceTypeFactory == null) {
            inferenceTypeFactory = realChecker.createInferenceATF(inferenceChecker, getRealChecker(),
                    getRealTypeFactory(), getSlotManager(), getConstraintManager());
            this.getConstraintManager().init(inferenceTypeFactory);
            logger.finer("Created InferenceAnnotatedTypeFactory");
        }
        return inferenceTypeFactory;
    }


    /**
     * This method is NOT deprecated but SHOULD NOT BE USED other than in getInferenceTypeFactory AND
     * InferenceAnnotatedTypeFactory.getSupportedQualifierTypes.  We have made it deprecated in order to bring
     * this to the attention of future programmers.  We would make it private if it weren't for the fact that
     * we need the realTypeFactory qualifiers in getSupportedQualifierTypes and it is called in the super class.
     */
    public BaseAnnotatedTypeFactory getRealTypeFactory() {
        if (realTypeFactory == null) {
            realTypeFactory = getRealChecker().createRealTypeFactory();
            logger.finer(String.format("Created real type factory: %s", realTypeFactory));
        }
        return realTypeFactory;
    }

    public SlotManager getSlotManager() {
        if (slotManager == null ) {
            slotManager = new DefaultSlotManager(inferenceChecker.getProcessingEnvironment(),
                    realTypeFactory.getSupportedTypeQualifiers(), true );
            logger.finer("Created slot manager" + slotManager);
        }
        return slotManager;
    }

    protected InferenceSolver getSolver() {
        try {
            InferenceSolver solver = (InferenceSolver) Class.forName(
                    InferenceOptions.solver, true, ClassLoader.getSystemClassLoader()).getDeclaredConstructor().newInstance();
            logger.finer("Created solver: " + solver);
            return solver;
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Error instantiating solver class \"" + InferenceOptions.solver + "\".", e);
            System.exit(5);
            return null; // Dead code
        }
    }

    /**
     * Parse solver-args from a comma separated list of
     * key=value pairs into a Map.
     * @return Map of string keys and values
     */
    private Map<String, String> parseSolverArgs() {
        Map<String, String> processed = new HashMap<>();
        if (InferenceOptions.solverArgs != null) {
            String solverArgs = InferenceOptions.solverArgs;
            String[] split = solverArgs.split(",");
            for (String part : split) {
                int index;
                part = part.trim();
                if ((index = part.indexOf("=")) > 0) {
                    processed.put(part.substring(0, index), part.substring(index + 1, part.length()));
                } else {
                    processed.put(part, null);
                }
            }
        }
        return processed;
    }

    /**
     * Parse checker framework args from a space separated list of
     * -Axxx=xxx,y=y -Azzz=zzz
     * @return List of Strings, each string is a checker framework argument
     * in the format: -Axxx=xxx,y=y or -Azzz or -Azzz=zzz
     */
    private List<String> parseCfArgs() {
        List<String> argList = new ArrayList<>();
        if (InferenceOptions.cfArgs != null) {
            String cfArgs = InferenceOptions.cfArgs;
            String[] split = cfArgs.split(" ");
            argList.addAll(Arrays.asList(split));
        }
        return argList;
    }

    public static InferenceMain getInstance() {
        return inferenceMainInstance;
    }

    public ConstraintManager getConstraintManager() {
        if (this.constraintManager == null) {
            this.constraintManager = new ConstraintManager();
        }
        return constraintManager;
    }

    public boolean isPerformingFlow() {
        return performingFlow;
    }

    public void setPerformingFlow(boolean performingFlow) {
        this.performingFlow = performingFlow;
    }

    public static boolean isHackMode() {
        return isHackMode(true);
    }

    /**
     * @param condition if some condition is true, do some sort of hack
     */
    public static boolean isHackMode(boolean condition) {
        // getInstance is null during type checking.
        if (getInstance() != null && getInstance().hackMode && condition) {
            StackTraceElement[] traces = Thread.currentThread().getStackTrace();
            StackTraceElement hackLocation = traces[2];
            if (traces[2].getMethodName().equals("isHackMode")) {
                hackLocation = traces[3];
            }
            getInstance().logger.warning("Encountered hack: " + hackLocation);
            return true;
        } else {
            return false;
        }
    }

    public static abstract interface ResultHandler {
        void handleCompilerResult(boolean success, String javacOutStr);
    }

    protected static class DefaultResultHandler implements ResultHandler {

        private final Logger logger;

        public DefaultResultHandler(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void handleCompilerResult(boolean success, String javacOutStr) {
            if (!success) {
                logger.severe("Error return code from javac! Quitting.");
                logger.info(javacOutStr);
                System.exit(1);
            }
        }
    }
}
