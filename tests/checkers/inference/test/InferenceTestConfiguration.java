package checkers.inference.test;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.checkerframework.framework.test.TestConfiguration;

public interface InferenceTestConfiguration {

    File getOutputJaif();

    File getAnnotatedSourceDir();

    File getTestDataDir();

    Map<String, String> getInferenceJavacArgs();

    List<String> getFlatInferenceJavacArgs();

    String getSolver();

    Map<String, String> getSolverArgs();

    List<String> getFlatSolverArgs();

    boolean shouldUseHacks();

    String getPathToAfuScripts();

    TestConfiguration getInitialTypecheckConfig();

    TestConfiguration getFinalTypecheckConfig();
}
