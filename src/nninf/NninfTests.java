package nninf;

import java.io.File;
import java.util.Collection;

import org.checkerframework.framework.test.ParameterizedCheckerTest;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runners.Parameterized.Parameters;


public class NninfTests {

    public static void main(String[] args) {
        org.junit.runner.JUnitCore jc = new org.junit.runner.JUnitCore();
        Result run = jc.run(Tests.class);

        if (run.wasSuccessful()) {
            System.out.println("Run was successful with " + run.getRunCount()
                    + " test(s)!");
        } else {
            System.out.println("Run had " + run.getFailureCount()
                    + " failure(s) out of " + run.getRunCount() + " run(s)!");

            for (Failure f : run.getFailures()) {
                System.out.println(f.toString());
            }
        }
    }

    public static class Tests extends ParameterizedCheckerTest {
        public Tests(File testFile) {
            super(testFile, NninfChecker.class, "nninf", "-Anomsgtext");
        }

        @Parameters
        public static Collection<Object[]> data() {
            return testFiles("nninf");
        }
    }
}