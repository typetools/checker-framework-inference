package checkers.inference.test;

import org.checkerframework.javacutil.SystemUtil;

/**
 * This is the output from running inference NOT the result of a test.
 */
public class InferenceResult {
    private final InferenceTestConfiguration configuration;
    private final String output;
    private final boolean failed;

    public InferenceResult(InferenceTestConfiguration config,
                           String output, boolean failed) {
        this.configuration = config;
        this.output = output;
        this.failed = failed;
    }

    public InferenceTestConfiguration getConfiguration() {
        return configuration;
    }

    public boolean didFail() {
        return failed;
    }

    public String getOutput() {
        return output;
    }

    public String summarize() {
        return "Inference process: " + (failed ? "failed" : "succeeded")
             + "\n\nOutput\n\n" + getOutput()
             + "\n\nWhile inferring over source files: \n"
             + SystemUtil.join("\n", configuration.getInitialTypecheckConfig().getTestSourceFiles()) + "\n\n";
    }
}
