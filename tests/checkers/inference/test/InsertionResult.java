package checkers.inference.test;

public class InsertionResult {
    private boolean failed;
    private String [] options;
    private String output;

    public InsertionResult(String [] options, boolean failed, String output) {
        this.failed = failed;
        this.output = output;
        this.options = options;
    }

    public String [] getOptions() {
        return options;
    }

    public String getCommand() {
        return String.join(" ", options);
    }

    public boolean didFail() {
        return failed;
    }

    public String summarize() {
        return "AFU Insertion " + (didFail() ? " succeeeded "  : " failed\n")
             + "Output\n\n" + output + "\non command: " + getCommand() + "\n\n";
    }

}
