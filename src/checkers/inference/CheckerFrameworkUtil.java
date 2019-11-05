package checkers.inference;

import java.io.PrintWriter;
import java.nio.charset.Charset;

import javax.tools.JavaFileManager;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.main.Main.Result;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Context.Factory;

public class CheckerFrameworkUtil {

    public static boolean invokeCheckerFramework(String[] args, PrintWriter outputCapture) {
        Main compiler = new Main("javac", outputCapture);

        // see https://github.com/google/error-prone-javac/blob/a53d069bbdb2c60232ed3811c19b65e41c3e60e0/src/jdk.compiler/share/classes/com/sun/tools/javac/main/Main.java#L159
        Context context = new Context();
        DummyJavacFileManager.preRegister(context);
        Result compilerResult = compiler.compile(args, context);

        return compilerResult == Result.OK;
    }

    /**
     * This class prevents InferenceMain from being loaded by the bootstrap class
     * loader, resulting in two instances of the class of InferenceMain in both
     * AppClassLoader and bootstrap class loader. See
     * https://github.com/eisop/checker-framework-inference/pull/7 and
     * https://github.com/eisop/checker-framework-inference/pull/9
     */
    private static class DummyJavacFileManager extends JavacFileManager {
        public DummyJavacFileManager(Context context, boolean register, Charset charset) {
            super(context, register, charset);
        }

        public static void preRegister(Context context) {
            context.put(JavaFileManager.class,
                    (Factory<JavaFileManager>)c -> new DummyJavacFileManager(c, true, null));
        }
    }
}
