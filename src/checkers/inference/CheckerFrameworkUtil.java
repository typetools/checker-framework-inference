package checkers.inference;

import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.main.Main.Result;
import java.io.PrintWriter;

public class CheckerFrameworkUtil {

    public static boolean invokeCheckerFramework(String[] args, PrintWriter outputCapture) {
        Main compiler = new Main("javac", outputCapture);
        Result compilerResult = compiler.compile(args);
        return compilerResult == Result.OK;
    }
}
