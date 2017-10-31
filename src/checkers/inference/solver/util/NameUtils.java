package checkers.inference.solver.util;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

public class NameUtils {

    public static String getSimpleName(AnnotationMirror annoMirror) {
        final DeclaredType annoType = annoMirror.getAnnotationType();
        final TypeElement elm = (TypeElement) annoType.asElement();
        return elm.getSimpleName().toString().intern();
    }
}
