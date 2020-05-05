package checkers.inference;


import org.checkerframework.framework.util.CheckerMain;
import org.checkerframework.javacutil.SystemUtil;

import interning.InterningChecker;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ostrusted.OsTrustedChecker;
import org.plumelib.options.Option;
import org.plumelib.options.OptionGroup;
import org.plumelib.options.Options;
import sparta.checkers.IFlowSinkChecker;
import sparta.checkers.IFlowSourceChecker;
import sparta.checkers.propagation.IFlowSinkSolver;
import sparta.checkers.propagation.IFlowSourceSolver;
import sparta.checkers.sat.SinkSolver;
import sparta.checkers.sat.SourceSolver;
import checkers.inference.InferenceLauncher.Mode;
import checkers.inference.model.serialization.JsonSerializerSolver;
import checkers.inference.solver.MaxSat2TypeSolver;

/**
 * Options for the InferenceLauncher and InferenceMain (though InferenceMain uses only the subset
 * of options that apply to inference).
 */
public class InferenceOptions {
    public static final String VERSION = "2";
    public static final String DEFAULT_JAIF = "default.jaif";


    // ------------------------------------------------------
    // Command-line options
    // ------------------------------------------------------

    @OptionGroup("General Options")

    // TODO: The mode variable should be an enum rather than a string.
    @Option(value = "-m Modes of operation: TYPECHECK, INFER, ROUNDTRIP, ROUNDTRIP_TYPECHECK")
    public static String mode;

    @Option("Should we log certain exceptions rather than crash")
    public static boolean hacks;

    /**
     * The type system to use for checker, solver, and related command-line
     * options.  If you use this option, all required command-line
     * arguments except --mode will have values and the only other option
     * you need to include is a list of source files. <p>
     *
     * All legal options are listed in InferenceOptions.typesystems.keySet()
     */
    @Option("-t Type system whose checker and solver to use")
    public static String typesystem;

    // ------------------------------------------------------
    @OptionGroup("Typechecking/Inference arguments")

    @Option("[path] path to write jaif")
    public static String jaifFile = DEFAULT_JAIF;

    @Option("[InferrableChecker] the fully-qualified name of the checker to run; overrides --typesystem.")
    public static String checker;

    @Option("[InferenceSolver] the fully-qualified name of the solver to use on constraints; overrides --typesystem.")
    public static String solver;

    @Option("The fully-qualified name of the classpath for target program; overrides --targetclasspath.")
    public static String targetclasspath = ".";

    @Option("Args to pass to solver, in the format key1=value,key2=value")
    public static String solverArgs;

    @Option("Args to pass to checker framework, in the format -Axxx=xxx -Ayyy=yyy,z=z")
    public static String cfArgs;

    /** If jsonFile is specified this will be set to the JsonSerializerSolver */
    @Option("The JSON file to which constraints should be dumped.  This field is mutually exclusive with solver.")
    public static String jsonFile;

    // ------------------------------------------------------
    @OptionGroup("Annotation File Utilities options")

    @Option(value = "Path to AFU scripts directory.")
    public static String pathToAfuScripts;

    @Option(value = "Annotation file utilities output directory.  WARNING: This directory must be empty.", aliases = "-afud")
    public static String afuOutputDir;

    @Option("Whether or not the annoations should be inserted in the original source code.")
    public static boolean inPlace;

    @Option("Additional AFU options")
    public static String afuOptions;

    // ------------------------------------------------------
    @OptionGroup("Help")

    @Option("-v print version")
    public static boolean version;

    @Option(value="-h Print a help message", aliases={"-help"})
    public static boolean help;

    // ------------------------------------------------------
    @OptionGroup("Debugging")

    @Option("[Level] set the log level (from Java logging)")
    public static String logLevel;

    @Option(value="-p Print all commands before executing them")
    public static boolean printCommands;

    // TODO: change to int
    @Option("For inference, add debug on the port indicated")
    public static String debug;

    // end of command-line options
    // ------------------------------------------------------

    public static List<String> javacOptions;
    public static String [] javaFiles;

    public static File pathToThisJar = new File(CheckerMain.findPathTo(InferenceOptions.class, true));
    public static File checkersInferenceDir = pathToThisJar.getParentFile().getParentFile();
    public static File distDir = new File(checkersInferenceDir, "dist");
    public static File checkerJar = new File(distDir, "checker.jar");

    public static InitStatus init(String [] args, boolean requireMode) {
        List<String> errors = new ArrayList<>();
        Options options = new Options("inference [options]", InferenceOptions.class);
        String [] otherArgs = options.parse(true, args);

        int startOfJavaFilesIndex = -1;
        for (int i = 0; i < otherArgs.length; i++) {
            if (isJavaFile(otherArgs[i])) {
                startOfJavaFilesIndex = i;
                break;
            }
        }

        if (startOfJavaFilesIndex == -1) {
            javacOptions = Arrays.asList(otherArgs);
            javaFiles = new String[0];
        } else {
            javacOptions = new ArrayList<String>(startOfJavaFilesIndex);
            for (int i = 0; i < startOfJavaFilesIndex; ++i) {
                javacOptions.add(otherArgs[i]);
            }

            javaFiles = new String[otherArgs.length - startOfJavaFilesIndex];
            System.arraycopy(otherArgs, startOfJavaFilesIndex, javaFiles, 0, javaFiles.length);
        }

        if (typesystem != null) {
            TypeSystemSpec spec = typesystems.get(typesystem);
            if (spec == null) {
                errors.add("Unrecognized typesystem.  Current typesystems:\n"
                           + SystemUtil.join("\n", typesystems.keySet()));
            } else {
                spec.apply();
            }
        }

        if (checker == null) {
            errors.add("You must specify exactly one checker using --checker");
        }

        if (mode == null) {
            if (requireMode) {
                errors.add("You must specify a mode of operation using -m or --mode");
            }

        } else {
            mode = mode.toUpperCase();

            Mode modeEnum = null;
            try {
                modeEnum = Mode.valueOf(InferenceOptions.mode);

            } catch (IllegalArgumentException iexc) {
                System.out.println("Could not recognize mode: " + InferenceOptions.mode + "\n"
                        + "valid modes: " + SystemUtil.join(", ", Mode.values()));
                System.exit(1);
            }

            if (modeEnum != Mode.TYPECHECK) {
                if (solver == null) {
                    if (jsonFile != null) {
                        solver = JsonSerializerSolver.class.getCanonicalName();
                        if (solverArgs == null || solverArgs.isEmpty()) {
                            solverArgs = "constraint-file=" + InferenceOptions.jsonFile;
                        } else {
                            solverArgs = solverArgs + "," + "constraint-file=" + InferenceOptions.jsonFile;
                        }
                    } else {
                        errors.add("You must specify a solver using --solver or a --jsonFile to write constraints in.");
                    }
                } else if (jsonFile != null) {
                    errors.add("You may specify EITHER a solver or jsonFile but not both!");
                }
            }

            if (modeEnum.ordinal() >= Mode.ROUNDTRIP.ordinal()) {
                if (afuOutputDir == null) {
                    if (!inPlace) {
                        errors.add("You must specify an Annotation File Utilities output directory (--afuOutputDir or -afud) or --inPlace.");
                    }
                } else if (inPlace) {
                    errors.add("You cannot specify both an Annotation File Utilities output directory (--afuOutputDir or -afud) and --inPlace.");
                }

                if (afuOptions != null && afuOptions.contains("\\s-d\\s")) {
                    errors.add("Annotation File Utilities output dir must be specified via (--afuOutputDir or -afud) not -d in AFU Options.");
                }
            }
        }

        return new InitStatus(options, errors, help);
    }

    public static final Map<String, TypeSystemSpec> typesystems = new LinkedHashMap<>();
    static {
        final File srcDir = new File(checkersInferenceDir, "src");
        typesystems.put("ostrusted",
                new TypeSystemSpec(OsTrustedChecker.class,
                                   MaxSat2TypeSolver.class,
                                   new File(srcDir, "ostrusted" + File.separator + "jdk.astub")));
        typesystems.put("interning",
                new TypeSystemSpec(InterningChecker.class,
                                   MaxSat2TypeSolver.class,
                                   null));
        typesystems.put("sparta-source",
                new TypeSystemSpec(IFlowSourceChecker.class,
                        IFlowSourceSolver.class,
                        new File(srcDir, "sparta"+ File.separator +"checkers" + File.separator + "information_flow.astub")));
        typesystems.put("sparta-sink",
                new TypeSystemSpec(IFlowSinkChecker.class,
                        IFlowSinkSolver.class,
                        new File(srcDir, "sparta"+ File.separator +"checkers" + File.separator + "information_flow.astub")));
        typesystems.put("sparta-source-SAT",
                new TypeSystemSpec(IFlowSourceChecker.class,
                        SourceSolver.class,
                        new File(srcDir, "sparta"+ File.separator +"checkers" + File.separator + "information_flow.astub")));
        typesystems.put("sparta-sink-SAT",
                new TypeSystemSpec(IFlowSinkChecker.class,
                        SinkSolver.class,
                        new File(srcDir, "sparta"+ File.separator +"checkers" + File.separator + "information_flow.astub")));

    }

    /**
     * Specifies the defaults a particular type system would use to run typechecking/inference.
     */
    private static class TypeSystemSpec {
        public final Class<? extends InferenceChecker> qualifiedChecker;
        public final Class<? extends InferenceSolver> defaultSolver;
        public final File defaultStubs;
        public final String [] defaultJavacArgs;
        public final String defaultSolverArgs;

        private TypeSystemSpec(Class<? extends InferenceChecker> qualifiedChecker, Class<? extends InferenceSolver> defaultSolver, File defaultStubs) {
                this(qualifiedChecker, defaultSolver, defaultStubs, new String[0], "");
        }

        private TypeSystemSpec(Class<? extends InferenceChecker> qualifiedChecker, Class<? extends InferenceSolver> defaultSolver, File defaultStubs,
                               String[] defaultJavacArgs, String defaultSolverArgs) {
            this.qualifiedChecker = qualifiedChecker;
            this.defaultSolver = defaultSolver;
            this.defaultStubs = defaultStubs;
            this.defaultJavacArgs = defaultJavacArgs;
            this.defaultSolverArgs = defaultSolverArgs;
        }

        private String appendOptionIfNonNull(String str1, String str2) {
            if (str1 == null) {
                return str2;
            }

            if (str2 == null) {
                return str1;
            }

            return str1 + str2;
        }

        private void apply() {
            if (checker == null) {
                checker = qualifiedChecker.getCanonicalName();
            }

            if (InferenceOptions.jsonFile == null) {
                if (solver == null) {
                    solver = defaultSolver.getCanonicalName();
                }
            }

            if (defaultSolverArgs != null) {
                solverArgs = appendOptionIfNonNull(solverArgs, defaultSolverArgs);
            }

            if (defaultStubs != null) {
                final String stubDef = "-Astubs=" + defaultStubs;
                javacOptions.add(stubDef);
            }

            if (defaultJavacArgs != null) {
                javacOptions.addAll(Arrays.asList(defaultJavacArgs));
            }
        }
    }

    private static boolean isJavaFile(String arg) {
        return arg.endsWith(".java") && new File(arg).exists();
    }

    public static class InitStatus {
        public final Options options;
        public final List<String> errors;
        private final boolean printHelp;

        public InitStatus(Options options, List<String> errors, boolean printHelp) {
            this.options = options;
            this.errors = errors;
            this.printHelp = printHelp;
        }

        public void validateOrExit() {
            validateOrExit("\n");
        }
        public void validateOrExit(String errorDelimiter) {
            if (!errors.isEmpty()) {
                System.out.println(SystemUtil.join(errorDelimiter, errors));
                options.printUsage();
                System.exit(1);
            }

            if (printHelp) {
                options.printUsage();
                System.exit(0);
            }
        }
    }
}
