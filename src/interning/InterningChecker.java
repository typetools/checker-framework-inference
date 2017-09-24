package interning;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.framework.qual.StubFiles;
import org.checkerframework.framework.source.SupportedLintOptions;
import org.checkerframework.javacutil.AnnotationBuilder;

import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;

import checkers.inference.BaseInferrableChecker;
import checkers.inference.InferenceChecker;
import interning.qual.Interned;

/**
 * A type-checker plug-in for the {@link Interned} qualifier that
 * finds (and verifies the absence of) equality-testing and interning errors.
 *
 * <p>
 *
 * The {@link Interned} annotation indicates that a variable
 * refers to the canonical instance of an object, meaning that it is safe to
 * compare that object using the "==" operator. This plugin warns whenever
 * "==" is used in cases where one or both operands are not
 * {@link Interned}.  Optionally, it suggests using "=="
 * instead of ".equals" where possible.
 *
 * @checker_framework.manual #interning-checker Interning Checker
 */
@StubFiles({"com-sun.astub", "org-jcp.astub", "org-xml.astub", "sun.astub"})
@SupportedLintOptions({"dotequals"})
@SupportedOptions({"checkclass"})
public final class InterningChecker extends BaseInferrableChecker {
    public AnnotationMirror INTERNED;

    @Override
    public void initChecker() {
        final Elements elements = processingEnv.getElementUtils();
        INTERNED = AnnotationBuilder.fromClass(elements, Interned.class);

        super.initChecker();
    }

    @Override
    public InterningVisitor createVisitor(InferenceChecker ichecker, BaseAnnotatedTypeFactory factory, boolean infer)  {
        return new InterningVisitor(this, ichecker, factory, infer);
    }

    @Override
    public InterningAnnotatedTypeFactory createRealTypeFactory() {
        return new InterningAnnotatedTypeFactory(this);
    }

}
