package checkers.inference;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Develop Launcher for checker-framework-inference developers.
 *
 * Instead of using jar files of checker-framework-inference and checker-framework,
 * {@code InferenceDevelLauncher} uses the eclipse build class files to invoke {@code InferenceMain}.
 * Similar to {@code CheckerDevelMain}, this class is associated with a shell script called {@link inference-dev},
 * which set the proper class files locations that this class would use.
 *
 * TODO: We need to think how to enable {@code InferenceDevelLauncher} to find all necessary locations by itself, so that we could
 * remove the dependency of a shell script. After achieving this, we could also apply the similar solution to {@code CheckerDevelMain}.
 * @author charleszhuochen
 *
 */
public class InferenceDevelLauncher extends InferenceLauncher {

    private static final String PROP_PREFIX  = "InferenceDevelLauncher";
    private static final String BINARY      = PROP_PREFIX + ".binary";
    private static final String RUNTIME_BCP = PROP_PREFIX + ".runtime.bcp";
    private static final String VERBOSE = PROP_PREFIX + ".verbose";
    private static final String ANNOTATED_JDK = PROP_PREFIX + ".annotated.jdk";

    public InferenceDevelLauncher(PrintStream outStream, PrintStream errStream) {
        super(outStream, errStream);
    }

    public static void main(String [] args) {
        final String runtimeBcp  = System.getProperty( RUNTIME_BCP );
        final String binaryDir  = System.getProperty( BINARY  );
        final String verbose = System.getProperty( VERBOSE );
        final String annotatedJDK = System.getProperty( ANNOTATED_JDK );

        if (verbose != null && verbose.equalsIgnoreCase("TRUE")) {
            System.out.print("CheckerDevelMain:\n" +
                    "Prepended to runtime bootclasspath: " + runtimeBcp +  "\n" +
                    "annotated jdk:              " + annotatedJDK + "\n" +
                    "Binary Dir:                 " + binaryDir     +  "\n"
            );
        }

        assert (binaryDir != null) :
                BINARY + " must specify a binary directory in which " +
                "checker.jar, javac.jar, etc... are usually built";

        assert (runtimeBcp != null) : RUNTIME_BCP + " must specify a path entry to prepend to the Java bootclasspath when running Javac";  //TODO: Fix the assert messages
        assert (annotatedJDK != null) : ANNOTATED_JDK + " must specify a path entry to prepend to the annotated JDK";

        new InferenceDevelLauncher(System.out, System.err).launch(args);
    }

    @Override
    protected void initInferenceOptions(String [] args) {
        super.initInferenceOptions(args);
        //overwrite distributed dirs and jars to the location that {@code InferenceDevelLauncher.BINARY} indicates.
        InferenceOptions.pathToThisJar = null; // {@code InferenceDevelLauncher} should not be called from jar. set to null in case of wrong use.
        InferenceOptions.distDir = new File(System.getProperty( BINARY ));
        InferenceOptions.checkersInferenceDir = InferenceOptions.distDir.getParentFile();
        InferenceOptions.checkerJar = new File (InferenceOptions.distDir, "checker.jar");
    }

    @Override
    /**
     * return the eclipse output directory instead of jars.
     * the eclipse output directory is set by {@code InferenceDevelLauncher.RUNTIME_BCP}
     */
    public  List<String> getInferenceRuntimeBootJars() {
        return prependPathOpts(RUNTIME_BCP, new ArrayList<String> ());
    }

    @Override
    // return jdkFile path
    public String getInferenceCompilationBootclassPath() {
        return "-Xbootclasspath/p:" + System.getProperty( ANNOTATED_JDK );
    }

    /**
     * TODO: we need to extract the utility methods in {@code CheckerMain} and {@code CheckerDevelMain} out to an Util Class, 
     * change their visibility to public, then we can reuse them in {@code InferenceLauncher}, {@code InferenceDevelLauncher}
     * and {@code InferenceMain}.
     *
     * This method is copied from CheckerDevelMain
     * @param pathProp
     * @param pathOpts
     * @param otherPaths
     * @return
     */
    private static List<String> prependPathOpts(final String pathProp, final List<String> pathOpts, final String ... otherPaths) {
        final String cp     = System.getProperty(pathProp);

        final List<String> newPathOpts = new ArrayList<String>();

        if (!cp.trim().isEmpty()) {
            newPathOpts.addAll(Arrays.asList(cp.split(File.pathSeparator)));
        }

        newPathOpts.addAll(Arrays.asList(otherPaths));
        newPathOpts.addAll(pathOpts);

        return newPathOpts;
    }
}
