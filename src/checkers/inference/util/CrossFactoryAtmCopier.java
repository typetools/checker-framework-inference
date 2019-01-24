package checkers.inference.util;

import org.checkerframework.framework.type.AnnotatedTypeCopier;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;

/**
 * Copies annotated type mirrors as per AnnotatedTypeCopier but, in the new type, it replaces the
 * original underlying type factory with a new type factory (passed via constructor)
 */
public class CrossFactoryAtmCopier extends AnnotatedTypeCopier {

    public static <ATM extends AnnotatedTypeMirror> ATM copy(
            ATM annotatedType, AnnotatedTypeFactory newTypeFactory, boolean copyAnnotations) {
        return (ATM)
                new CrossFactoryAtmCopier(newTypeFactory, copyAnnotations).visit(annotatedType);
    }

    private final AnnotatedTypeFactory newTypeFactory;

    public CrossFactoryAtmCopier(
            final AnnotatedTypeFactory newTypeFactory, final boolean copyAnnotations) {
        super(copyAnnotations);
        this.newTypeFactory = newTypeFactory;
    }

    @Override
    protected <T extends AnnotatedTypeMirror> T makeCopy(T original) {
        final T copy =
                (T)
                        AnnotatedTypeMirror.createType(
                                original.getUnderlyingType(),
                                newTypeFactory,
                                original.isDeclaration());
        maybeCopyPrimaryAnnotations(original, copy);

        return copy;
    }
}
