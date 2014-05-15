package ostrusted;

import javax.lang.model.util.Elements;

import org.checkerframework.framework.qual.TypeQualifiers;
import org.checkerframework.javacutil.AnnotationUtils;

import ostrusted.quals.OsTrusted;
import ostrusted.quals.OsUntrusted;
import trusted.TrustedChecker;

/**
 * 
 * @author sdietzel
 * [2]  CWE-78  Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
 */

@TypeQualifiers({ OsTrusted.class, OsUntrusted.class/*, PolyOsTrusted.class*/ })
public class OsTrustedChecker extends TrustedChecker {

    @Override
    protected void setAnnotations() {
        final Elements elements = processingEnv.getElementUtils();      //TODO: Makes you think a utils is being returned

        UNTRUSTED = AnnotationUtils.fromClass(elements, OsUntrusted.class);
        TRUSTED   = AnnotationUtils.fromClass(elements, OsTrusted.class);
    }
}