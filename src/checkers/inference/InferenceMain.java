package checkers.inference;

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

import javax.lang.model.element.AnnotationMirror;

import joptsimple.OptionSet;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import annotations.io.ASTIndex.ASTRecord;
import checkers.inference.model.VariableSlot;
import checkers.inference.quals.VarAnnot;
import checkers.inference.util.JaifBuilder;

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

    private OptionSet options;
    private InferenceChecker inferenceChecker;
    private boolean performingFlow;

    private InferenceVisitor<?, InferenceAnnotatedTypeFactory> visitor;
    private InferrableChecker realChecker;
    private BaseAnnotatedTypeFactory realTypeFactory;
    private InferenceAnnotatedTypeFactory inferenceTypeFactory;

    private ConstraintManager constraintManager = new ConstraintManager();
    private SlotManager slotManager;

    // Hold the results of solving.
    private Map<Integer, AnnotationMirror> solverResult;


    /**
     * Create an InferenceMain instance configured with options.
     */
    public InferenceMain(OptionSet options) {
        this.options = options;
    }

    /**
     * Kick off the inference process.
     */
    public void run() {
        logger.trace("Starting InferenceMain");
        inferenceMainInstance = this;

        // Start up javac
        startCheckerFramework();
        solve();
        writeJaif();
    }

    /**
     * Run the Checker-Framework using InferenceChecker
     */
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
        if (options.has("javac-args")) {
            checkerFrameworkArgs.add("" + options.valueOf("javac-args"));
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
    }

    /**
     * Check result and error/exit if Checker-Framework call was not successful.
     */
    private void handleCompilerResult(boolean success, String javacOutStr) {
        if (!success) {
            logger.error("Error return code from javac! Quitting.");
            logger.debug(javacOutStr);
            System.exit(1);
          }
    }

    /**
     * Give the InferenceMain instance a reference to the InferenceChecker
     * that is being run by Checker-Framework.
     *
     * @param inferenceChecker The InferenceChecker being run by Checker Framework.
     */
    public void recordInferenceCheckerInstance(InferenceChecker inferenceChecker) {
        this.inferenceChecker = inferenceChecker;
        logger.trace("Received inferneceChecker callback");
    }

    /**
     * Create a jaif file that records the mapping of VariableSlots to their code positions.
     * The output file can be configured by the command-line argument jaiffile.
     */
    private void writeJaif() {
        try (PrintWriter writer
                = new PrintWriter(new FileOutputStream((String) options.valueOf("jaiffile")))) {

            List<VariableSlot> varSlots = slotManager.getVariableSlots();
            Map<ASTRecord, String> values = new HashMap<>();
            Set<Class<? extends Annotation>> annotationClasses = new HashSet<>();
            if (solverResult == null) {
                annotationClasses.add(VarAnnot.class);
            } else {
                for (Class<? extends Annotation> annotation : realTypeFactory.getSupportedTypeQualifiers()) {
                    annotationClasses.add(annotation);
                }
            }
            for (VariableSlot slot : varSlots) {
                if (slot.getASTRecord() != null && slot.isInsertable()) {
                    // TOOD: String serialization of annotations.
                    if (solverResult != null) {
                        // Not all VariableSlots will have an inferred value.
                        // This happens for VariableSlots that have no constraints.
                        if (solverResult.containsKey(slot.getId())) {
                            String value = solverResult.get(slot.getId()).toString();
                            values.put(slot.getASTRecord(), value);
                        }
                    } else {
                        // Just use the VarAnnot in the jaif.
                        String value = slotManager.getAnnotation(slot).toString();
                        values.put(slot.getASTRecord(), value);
                    }
                }
            }

            JaifBuilder builder = new JaifBuilder(values, annotationClasses);
            String jaif = builder.createJaif();
            writer.println(jaif);

        } catch (Exception e) {
            logger.error("Failed to write out jaif file!", e);
        }
    }

    /**
     * Solve the generated constraints using the solver specified on the command line.
     */
    private void solve() {
        // TODO: Support multiple solvers or serialize before or after solving
        // TODO: Prune out unneeded variables
        // TODO: Options to type-check after this.

        if (options.has("solver")) {
            InferenceSolver solver = getSolver();
            this.solverResult = solver.solve(
                    parseSolverArgs(),
                    slotManager.getSlots(),
                    constraintManager.getConstraints(),
                    getRealTypeFactory().getQualifierHierarchy(),
                    inferenceChecker.getProcessingEnvironment());
        }
    }

    //================================================================================
    // Component Initialization
    //================================================================================

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


    public SlotManager getSlotManager() {
        if( slotManager == null ) {
            slotManager = new DefaultSlotManager( inferenceChecker.getProcessingEnvironment(),
                    realTypeFactory.getSupportedTypeQualifiers() );
            logger.trace("Create slot manager", slotManager );
        }
        return slotManager;
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

    /**
     * Parse solver-args from a comma separated list of 
     * key=value pairs into a Map.
     * @return Map of string keys and values
     */
    private Map<String, String> parseSolverArgs() {
        Map<String, String> processed = new HashMap<>();
        if (options.has("solver-args")) {
            String solverArgs = (String) options.valueOf("solver-args");
            String[] split = solverArgs.split(",");
            for(String part : split) {
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

    public static InferenceMain getInstance() {
        return inferenceMainInstance;
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
