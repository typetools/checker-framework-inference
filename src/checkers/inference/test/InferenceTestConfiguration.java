package checkers.inference.test;

import org.checkerframework.framework.test.TestConfiguration;

import java.io.File;
import java.util.List;
import java.util.Map;

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
    String getPathToInferenceScript();

    TestConfiguration getInitialTypecheckConfig();
    TestConfiguration getFinalTypecheckConfig();
}
