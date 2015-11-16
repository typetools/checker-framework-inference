package checkers.inference;

import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.*;

import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.Slot;
import org.checkerframework.javacutil.ErrorReporter;

import static checkers.inference.InferenceQualifierHierarchy.findVarAnnot;

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
    //TODO: Think this through, add any missing constraints


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
              checker.hasOption("ignoreRawTypeArguments"),
              checker.hasOption("invariantArrays"));
        this.varAnnot = varAnnot;
    }

    //this is solely to make it public, we should consider adding areEqual to the TypeHierarchy interface
    @Override
    public boolean areEqual(AnnotatedTypeMirror type1, AnnotatedTypeMirror type2) {
        return super.areEqual(type1, type2);
    }

    @Override
    public boolean isSubtype(AnnotatedTypeMirror subtype, AnnotatedTypeMirror supertype) {
        return super.isSubtype(subtype, supertype, varAnnot);
    }

    @Override
    public StructuralEqualityComparer createEqualityComparer() {
        return new InferenceEqualityComparer(rawnessComparer, findVarAnnot(qualifierHierarchy.getTopAnnotations()));
    }
}

class InferenceEqualityComparer extends StructuralEqualityComparer {

    private final AnnotationMirror varAnnot;

    public InferenceEqualityComparer(DefaultRawnessComparer rawnessComparer, AnnotationMirror varAnnot) {
            super(rawnessComparer);
            this.varAnnot = varAnnot;
    }

    @Override
    protected boolean arePrimeAnnosEqual(AnnotatedTypeMirror type1, AnnotatedTypeMirror type2) {
        final InferenceMain inferenceMain = InferenceMain.getInstance();
        final AnnotationMirror varAnnot1 = type1.getAnnotationInHierarchy(varAnnot);
        final AnnotationMirror varAnnot2 = type2.getAnnotationInHierarchy(varAnnot);

        // TODO: HackMode
        if (InferenceMain.isHackMode((varAnnot1 == null || varAnnot2 == null))) {
            InferenceMain.getInstance().logger.warning(
                "Hack:InferenceTYpeHierarchy:66\n"
              + "type1=" + type1 + "\n"
              + "type2=" + type2 + "\n"
            );
            return true;
        }

        if (varAnnot1 == null || varAnnot2 == null) {
            ErrorReporter.errorAbort("Calling InferenceTypeHierarchy.arePrimeAnnosEqual on type with"
                    + "no varAnnots.!\n"
                    + "type1=" + type1 + "\n"
                    + "type2=" + type2);
        }

        if (!inferenceMain.isPerformingFlow()) {
            final Slot leftSlot  = inferenceMain.getSlotManager().getSlot( varAnnot1 );
            final Slot rightSlot = inferenceMain.getSlotManager().getSlot( varAnnot2 );
            inferenceMain.getConstraintManager().add(new EqualityConstraint(leftSlot, rightSlot));
        }

        return true;
    }
}