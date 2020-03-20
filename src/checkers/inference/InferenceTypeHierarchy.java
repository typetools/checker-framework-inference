package checkers.inference;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.DefaultTypeHierarchy;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.StructuralEqualityComparer;

import javax.lang.model.element.AnnotationMirror;

/**
 *  The InferenceTypeHierarchy along with the InferenceQualifierHierarchy is responsible for
 *  creating a subtype and equality constraints. Normally the methods of these two classes are queried
 *  in order to verify that two types have a required subtype relationship or to determine what to do
 *  based on the relationship between the two types.  However, in the InferenceQualifierHierarchy
 *  calls to isSubtype generate subtype/equality constraints between the input parameters and returns true.
 *
 *  This class generally delegates calls to the InferenceQualifierHierarchy which in turn generates
 *  the correct constraints.
 */
public class InferenceTypeHierarchy extends DefaultTypeHierarchy {
    private final AnnotationMirror varAnnot;
    // TODO: Think this through, add any missing constraints


    /**
     * Constructs an instance of {@code TypeHierarchy} for the type system
     * whose qualifiers represented in qualifierHierarchy.
     *
     * @param checker The type-checker to use
     * @param qualifierHierarchy The qualifier hierarchy to use
     */
    public InferenceTypeHierarchy(final BaseTypeChecker checker, final QualifierHierarchy qualifierHierarchy,
                                  final AnnotationMirror varAnnot) {
        super(checker, qualifierHierarchy,
              checker.getOption("ignoreRawTypeArguments", "true").equals("true"),
              checker.hasOption("invariantArrays"));
        this.varAnnot = varAnnot;
    }

    public boolean areEqual(AnnotatedTypeMirror type1, AnnotatedTypeMirror type2) {
        return equalityComparer.areEqualInHierarchy(type1, type2, varAnnot);
    }

    @Override
    public StructuralEqualityComparer createEqualityComparer() {
        return new InferenceEqualityComparer(this.typeargVisitHistory,
                InferenceQualifierHierarchy.findVarAnnot(qualifierHierarchy.getTopAnnotations()));
    }
}
