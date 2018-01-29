package checkers.inference;

import java.io.PrintWriter;

import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.main.Main.Result;

public class CheckerFrameworkUtil {

    public static Result invokeCheckerFramework(String[] args, PrintWriter outputCapture) {
        Main compiler = new Main("javac", outputCapture);
        Result compilerResult = compiler.compile(args);
        return compilerResult;
    }
}
