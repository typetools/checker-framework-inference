package checkers.inference;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import com.sun.source.tree.Tree;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.visitor.AnnotatedTypeScanner;

import javax.lang.model.element.AnnotationMirror;

/**
 * InferenceQualifierPolymorphism handle PolymorphicQualifiers for the Inference Framework.
 * Per method call that contains a polymorphic qualifier, InferneceQualifierPolymorphism
 * will create a single Variable and substitute all Polymorphic qualifiers with that
 * variable.  This means the locations that would normally generate constraints against
 * the polymorphic qualifier will now generate them against the variable representing
 * this instance of the polymorphic qualifier.
 */
public class InferenceQualifierPolymorphism {

    private final VariableAnnotator variableAnnotator;
    private final AnnotationMirror varAnnot;
    private final SlotManager slotManager;

    public InferenceQualifierPolymorphism(final SlotManager slotManager, final VariableAnnotator variableAnnotator,
                                          final AnnotationMirror varAnnot) {
        this.slotManager = slotManager;
        this.variableAnnotator = variableAnnotator;
        this.varAnnot = varAnnot;
    }

    /**
     * Replaces all instance of polymorphic qualifiers with the annotation for a single VariableSlot
     */
    public void replacePolys(Tree callTree, AnnotatedExecutableType methodType) {
        methodType.accept(new PolyReplacer(callTree), null);
    }

    private class PolyReplacer extends AnnotatedTypeScanner<Void, Void> {

        /**
         * A method might be annotated twice, to avoid creating multiple variables representing
         * the polymorphic qualifier for the same method call, we map the tree of the method call
         * to the variable that was created for that tree.  This map is contained
         * in the VariableAnnotator because it contains other maps with similar purpose.
         */
        private final Tree methodCall;

        /**
         * The variable slot created for this method call
         */
        private VariableSlot polyVar = null;

        private PolyReplacer(Tree methodCall) {
            this.methodCall = methodCall;
        }

        private VariableSlot getOrCreatePolyVar() {
            if (polyVar == null) {
                polyVar = variableAnnotator.getOrCreatePolyVar(methodCall);
            }

            return polyVar;
        }

        public Void scan(AnnotatedTypeMirror type, Void v) {
            if (type != null) {
                AnnotationMirror varSlot = type.getAnnotationInHierarchy(varAnnot);
                if (varSlot != null) {
                    VariableSlot var = (VariableSlot) slotManager.getSlot(varSlot);
                    if(InferenceMain.isHackMode() && var == null){
                    }else
                    if (var.isConstant()) {
                        AnnotationMirror constant = ((ConstantSlot)var).getValue();
                        if (InferenceQualifierHierarchy.isPolymorphic(constant)) {
                            type.replaceAnnotation(slotManager.getAnnotation(getOrCreatePolyVar()));
                        }
                    }
                }
            }

            super.scan(type, null);
            return null;
        }
    }

}
