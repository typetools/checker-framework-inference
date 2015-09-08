package checkers.inference;

import checkers.inference.InferenceLauncher.Mode;
import checkers.inference.model.serialization.JsonSerializerSolver;
import checkers.inference.solver.MaxSat2TypeSolver;
import interning.InterningChecker;
import org.checkerframework.framework.util.PluginUtil;
import ostrusted.OsTrustedChecker;
import plume.Option;
import plume.OptionGroup;
import plume.Options;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.checkerframework.framework.util.CheckerMain.findPathTo;

/**
 * Options for the InferenceLauncher and InferenceMain.
 */
public class InferenceOptions {
    public static final String VERSION = "2";
    public static final String DEFAULT_JAIF = "default.jaif";


    //------------------------------------------------------
    @OptionGroup("General Options")

    @Option(value = "-m Modes of operation: TYPECHECK, INFER, ROUNDTRIP, ROUNDTRIP_TYPECHECK")
    public static String mode;

    @Option("-v print version")
    public static boolean version;

    @Option(value="-h Print a help message", aliases={"-help"})
    public static boolean help;

    @Option("[Level] set the log level")
    public static String logLevel;

    @Option("Should we log certain exceptions rather than crash")
    public static boolean hacks;

    @Option("typesystem Use the defaults of the type system specified for checker, solver, and related arguments.  " +
            "Any other arguments specified will overwrite these defaults. If you use this option, all required " +
            "fields except -mode  will have values and the only other option you need to include is " +
            "a list of source files.")
    public static String typesystem;

    @Option(value="-p Print all commands before executing them")
    public static boolean printCommands;

    @Option("For inference, add debug on the port indicated")
    public static String debug;

    //------------------------------------------------------
    @OptionGroup("Compiler Arguments (for typecheck/infer)")

    @Option("[path] path to write jaif")
    public static String jaifFile = DEFAULT_JAIF;

    @Option("[InferrableChecker] the checker to run")
    public static String checker;

    @Option("[InferenceSolver] solver to use on constraints.  If jsonFile is specified this will be set to the JsonSerializerSolver")
    public static String solver;

    @Option("Args to pass to solver")
    public static String solverArgs;

    @Option("The JSON file to which constraints should be dumped.  This field is mutually exclusive with solver.")
    public static String jsonFile;

    @OptionGroup("Annotation file utility options")

    @Option(value = "Annotation file utilities output directory.  WARNING: This directory must be empty.", aliases = "-afud")
    public static String afuOutputDir;

    @Option("Whether or not the annoations should be inserted in the original source code.")
    public static boolean inPlace;

    @Option("Additional AFU options")
    public static String afuOptions;

    public static String [] javacOptions;
    public static String [] javaFiles;

    public static File pathToThisJar = new File(findPathTo(InferenceOptions.class, false));
    public static File checkersInferenceDir = pathToThisJar.getParentFile().getParentFile();
    public static File distDir = new File(checkersInferenceDir, "dist");
    public static File checkerJar = new File(distDir, "checker.jar");

    public static InitStatus init(String [] args, boolean requireMode) {
        List<String> errors = new ArrayList<>();

        Options options = new Options("inference [options]", InferenceOptions.class);
        String [] otherArgs = options.parse_or_usage(args);
        int startOfJavaFilesIndex = -1;
        for (int i = 0; i < otherArgs.length; i++) {
            if (isJavaFile(otherArgs[i])) {
                startOfJavaFilesIndex = i;
                break;
            }
        }

        if (startOfJavaFilesIndex == -1) {
            javacOptions = otherArgs;
            javaFiles = new String[0];
        } else {
            javacOptions = new String[startOfJavaFilesIndex];
            System.arraycopy(otherArgs, 0, javacOptions, 0, startOfJavaFilesIndex);

            javaFiles = new String[otherArgs.length - startOfJavaFilesIndex];
            System.arraycopy(otherArgs, startOfJavaFilesIndex, javaFiles, 0, javaFiles.length);
        }

        if (typesystem != null) {
            TypeSystemSpec spec = typesystems.get(typesystem.toLowerCase());
            if (spec == null) {
                errors.add("Unrecognized typesystem.  Current typesystems:\n"
                           + PluginUtil.join("\n", typesystems.keySet()));
            } else {
                spec.apply();
            }
        }

        if (checker == null) {
            errors.add("You must specify exactly one checker using --checker!");
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
                        + "valid modes: " + PluginUtil.join(", ", Mode.values()));
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
                new TypeSystemSpec(OsTrustedChecker.class.getCanonicalName(),
                                   MaxSat2TypeSolver.class.getCanonicalName(),
                                   new File(srcDir, "ostrusted" + File.separator + "jdk.astub")));
        typesystems.put("interning",
                new TypeSystemSpec(InterningChecker.class.getCanonicalName(),
                                   MaxSat2TypeSolver.class.getCanonicalName(),
                                   new File(srcDir, "interning" + File.separator + "jdk.astub")));
    }


    /**
     * Specifies the defaults a particular type system would use to run typechecking/inference.
     */
    private static class TypeSystemSpec {
        public final String qualifiedChecker;
        public final String defaultSolver;
        public final File defaultStubs;
        public final String [] defaultJavacArgs;
        public final String defaultSolverArgs;

        private TypeSystemSpec(String qualifiedChecker, String defaultSolver, File defaultStubs) {
                this(qualifiedChecker, defaultSolver, defaultStubs, new String[0], "");
        }

        private TypeSystemSpec(String qualifiedChecker, String defaultSolver, File defaultStubs,
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

        //This is a copy each time, so don't go putting this in a loop
        private String[] appendOptionIfNonNull(String[] str1, String str2) {
            if (str1 == null) {
                return new String[]{str2};
            }

            if (str2 == null) {
                return str1;
            }

            String [] out = new String[str1.length + 1];
            System.arraycopy(str1, 0, out, 0, str1.length);
            out[out.length - 1] = str2;

            return out;
        }

        private String[] appendOptionsIfNonNull(String[] str1, String[] str2) {
            if (str1 == null) {
                return str2;
            }

            if (str2 == null) {
                return str1;
            }

            String[] options = new String[str1.length + str2.length];
            System.arraycopy(str1, 0, options, 0, str1.length);
            System.arraycopy(str2, 0, options, str1.length, str2.length);
            return options;
        }

        //This is a copy each time, so don't go putting this in a loop
        private String[] prependOptionIfNonNull(String[] str1, String str2) {
            if (str1 == null) {
                return new String[]{str2};
            }

            if (str2 == null) {
                return str1;
            }

            String [] out = new String[str1.length + 1];
            out[0] = str2;
            System.arraycopy(str1, 0, out, 1, str1.length);

            return out;
        }

        private String[] prependOptionsIfNonNull(String[] str1, String[] str2) {
            if (str1 == null) {
                return str2;
            }

            if (str2 == null) {
                return str1;
            }

            String[] options = new String[str1.length + str2.length];
            System.arraycopy(str2, 0, options, 0, str2.length);
            System.arraycopy(str1, 0, options, str2.length, str1.length);
            return options;
        }

        private void apply() {
            if (checker == null) {
                checker = qualifiedChecker;
            }

            if (InferenceOptions.jsonFile == null) {
                if (solver == null) {
                    solver = defaultSolver;
                }
            }

            if (defaultSolverArgs != null) {
                solverArgs = appendOptionIfNonNull(solverArgs, defaultSolverArgs);
            }

            if (defaultStubs != null) {
                final String stubDef = "-Astubs=" + defaultStubs;
                javacOptions = prependOptionIfNonNull(javacOptions, stubDef);
            }

            if (defaultJavacArgs != null) {
                javacOptions = prependOptionsIfNonNull(javacOptions, defaultJavacArgs);
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
                options.print_usage(PluginUtil.join(errorDelimiter, errors));
                System.exit(1);
            }

            if (printHelp) {
                options.print_usage();
                System.exit(0);
            }
        }
    }
}
