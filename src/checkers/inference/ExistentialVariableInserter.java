package checkers.inference;

import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedIntersectionType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedNullType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedWildcardType;
import org.checkerframework.framework.type.visitor.AnnotatedTypeReplacer;
import org.checkerframework.framework.type.visitor.EquivalentAtmComboScanner;
import org.checkerframework.javacutil.BugInCF;

import java.util.Iterator;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.model.ConstraintManager;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;

/**
 *
 * Inserts "ExistentialVariables" onto an AnnotatedTypeMirror.
 *
 * Note: We DON'T put the existential variable in the primary annotation location of type variables.
 * if we do this, all of the ExistentialVariableSlots will be overridden by the one
 * primary annotation.  Instead, we traverse the bounds and add them there
 * We go through and remove the potentialVariable from all of these locations
 * and only replace them if they are NOT in non-defaultable locations

 * Also, any type variables we encounter in the declaration should already be fully annotated
 * possibly with ExistentialVariables already, so no need to kick-off the existential variable inserter
 * recursively
 *
 * An example appears below:
 *
 * Assume we have a potentialVariable p, an AnnotatedTypeVariable corresponding to the declaration of
 * a type parameter T, and an AnnotatedTypeVariable corresponding to a use of T:
 * {@code
 *     p              :=  @VarAnnot(3)
 *     typeParam      := <@VarAnnot(1) T extends @VarAnnot(2) Object>
 *     useOfTypeParam := <@VarAnnot(1) T extends @VarAnnot(2) Object> // corresponds to declaration T t;
 * }
 *
 *
 * Recall from the class comments of ExistentialVariable that we use the shorthand:
 * (@3 | @1) - to indicate an Existential variable that states: if (@3 exists) use @3 else use @1
 *
 * After a call to {@code existentialVariableInserter.insert(p, useOfTypeParam, typeParam)} we want
 * useOfTypeParam := <(@3 | @1) T extends (@3 | @2) object>
 *
 * However, recall that to represent (@3 | @1) we create an ExistentialVariable which is
 * represented by a VarAnnot.  E.g.
 * (@4 (@3 | @1) represents an ExistentialVariable with an id of 4 which states:
 * if (@3 exists) use @3 else use @1
 *
 * Using the above shorthand, a call to
 * {@code existentialVariableInserter.insert(p, useOfTypeParam, typeParam)} will result in
 * the following state:
 *
 * {@code
 * Let e1 and e2 be instances of existential variable.
 * Let e1 = (@4 (@3 | @1))    // see ExistentialVariable for the meaning of this notation
 * Let e2 = (@5 (@3 | @2))
 *
 * State after call to insert:
 *     e1             := @VarAnnot(4)
 *     e2             := @VarAnnot(5)
 *     p              := @VarAnnot(3)
 *     typeParam      := <@VarAnnot(1) T extends @VarAnnot(2) Object>
 *     useOfTypeParam := <@VarAnnot(4) T extends @VarAnnot(5) Object>
 * }
 */
public class ExistentialVariableInserter {
    private final SlotManager slotManager;
    private final VariableAnnotator varAnnotator;
    private final ConstraintManager constraintMangaer;
    private final AnnotationMirror realTop;
    private final AnnotationMirror varAnnot;

    public ExistentialVariableInserter(final SlotManager slotManager, final ConstraintManager constraintManager,
                                       final AnnotationMirror realTop, final AnnotationMirror varAnnot,
                                       final VariableAnnotator varAnnotator) {
        // bottom is used to force an annotation to exist in a non-defaultable location if it was written explicitly
        this.slotManager = slotManager;
        this.constraintMangaer = constraintManager;
        this.realTop = realTop;
        this.varAnnot = varAnnot;
        this.varAnnotator = varAnnotator;
    }

    /**
     * See class comments for information on insert
     */
    public void insert(final VariableSlot potentialVariable, final AnnotatedTypeMirror typeUse,
                       final AnnotatedTypeMirror declaration) {
         insert(potentialVariable, typeUse, declaration, false);
    }

    /**
     * See class comments for information on insert
     */
    public void insert(final VariableSlot potentialVariable, final AnnotatedTypeMirror typeUse,
                       final AnnotatedTypeMirror declaration,  boolean mustExist) {
        if (potentialVariable == null || !(potentialVariable instanceof VariableSlot)) {
            throw new BugInCF("Bad type variable slot: slot=" + potentialVariable);
        }

        // propagates the potentialVariable in all of the locations that will be replaced by an ExistentialVariable
        final AnnotationMirror potentialVarAnno = slotManager.getAnnotation(potentialVariable);
        typeUse.addAnnotation(slotManager.getAnnotation(potentialVariable));

        // now remove only the primary (which has already been propagated to the bounds by fixUpBoundAnnotations)
        typeUse.removeAnnotation(potentialVarAnno);


        final InsertionVisitor insertionVisitor = new InsertionVisitor(potentialVariable, potentialVarAnno, mustExist);
        insertionVisitor.visit(typeUse, declaration, null);
    }

    private class InsertionVisitor extends EquivalentAtmComboScanner<Void, Void> {
        private VariableSlot potentialVariable;
        private AnnotationMirror potentialVarAnno;

        public InsertionVisitor(final VariableSlot potentialVariable,
                                final AnnotationMirror potentialVarAnno,
                                final boolean mustExist) {
            this.potentialVariable = potentialVariable;
            this.potentialVarAnno = potentialVarAnno;
        }

        public void matchAndReplacePrimary(final AnnotatedTypeMirror typeUse, final AnnotatedTypeMirror declaration) {
            if (InferenceMain.isHackMode(slotManager.getVariableSlot(typeUse) == null)) {
                return;
            }

            if (typeUse.getAnnotationInHierarchy(realTop) == null) {
                typeUse.addAnnotation(realTop);
            }

            if (slotManager.getVariableSlot(typeUse).equals(potentialVariable)) {
                final Slot declSlot = slotManager.getVariableSlot(declaration);

                if (declSlot == null) {
                    if (!InferenceMain.isHackMode()) {
                        throw new BugInCF("Missing variable slot for declaration:" + declaration);
                    } else {
                        return;
                    }
                }

                if (declSlot instanceof VariableSlot) {
                    final VariableSlot varSlot = slotManager.getVariableSlot(declaration);
                    final ExistentialVariableSlot existVar =
                            varAnnotator.getOrCreateExistentialVariable(typeUse, potentialVariable, varSlot);

                } else if (!InferenceMain.isHackMode()) {
                        throw new BugInCF("Unexpected constant slot in:" + declaration);
                }
            }
        }

        @Override
        protected String defaultErrorMessage(org.checkerframework.framework.type.AnnotatedTypeMirror type1, org.checkerframework.framework.type.AnnotatedTypeMirror type2, Void aVoid) {
            return "Input types should have identical structures.  Input types are limited to those types" +
                    "that can appear in a type variable bound:\n"
                    +  "type1=" + type1 + "\n"
                    +  "type2=" + type2;
        }

        @Override
        protected Void scanWithNull(AnnotatedTypeMirror type1, AnnotatedTypeMirror type2, Void aVoid) {
            throw new BugInCF("Encounter null type in ExistentialVariableInserter:\n"
                                   + "type1=" + type1 + "\n"
                                   + "type2=" + type2 + "\n");
        }

        @Override
        public Void visitArray_Array(AnnotatedArrayType typeUse, AnnotatedArrayType declaration, Void aVoid) {
            if (visited.contains(typeUse, declaration)) {
                return null;
            }

            visited.add(typeUse, declaration, null);

            matchAndReplacePrimary(typeUse, declaration);

            // component types will not have the potentialVarAnno on them, so instead copy over other annotations
            // from the declared type
            AnnotatedTypeReplacer.replace(declaration.getComponentType(), typeUse.getComponentType());
            return null;
        }

        @Override
        public Void visitDeclared_Declared(AnnotatedDeclaredType typeUse, AnnotatedDeclaredType declaration, Void v) {
            if (visited.contains(typeUse, declaration)) {
                return null;
            }

            visited.add(typeUse, declaration, null);

            // Once we reach a Declared type, only the primary annotation of that declared type can possibly
            // contain the potentialVarAnno, so instead we copy over the other annotations from the declared type
            // to the typeUse
            matchAndReplacePrimary(typeUse, declaration);

            final Iterator<AnnotatedTypeMirror> typeUseArgs = typeUse.getTypeArguments().iterator();
            final Iterator<AnnotatedTypeMirror> declArgs = declaration.getTypeArguments().iterator();

            while (typeUseArgs.hasNext() && declArgs.hasNext()) {
                AnnotatedTypeMirror nextUse = typeUseArgs.next();
                AnnotatedTypeMirror nextDecl = declArgs.next();
                if (nextUse != nextDecl) { // these two can be the same when a recursive type parameter uses
                                           // itself (e.g.  <T extends List<T>>
                    AnnotatedTypeReplacer.replace(nextDecl, nextUse);
                }
            }

            if (typeUseArgs.hasNext() || declArgs.hasNext()) {
                throw new BugInCF("Mismatched number of type arguments for types:\n"
                                       + "typeUse=" + typeUse + "\n"
                                       + "declaration=" + declaration);
            }

            return null;
        }

        @Override
        public Void visitIntersection_Intersection(AnnotatedIntersectionType typeUse, AnnotatedIntersectionType declaration, Void v) {
            if (visited.contains(typeUse, declaration)) {
                return null;
            }

            visited.add(typeUse, declaration, null);

            // This might change if we actually define the semantics of primary annotations on AnnotatedIntersectionTypes
            matchAndReplacePrimary(typeUse, declaration);

            super.visitIntersection_Intersection(typeUse, declaration, null);
            return null;
        }

        @Override
        public Void visitNull_Null(AnnotatedNullType type1, AnnotatedNullType type2, Void aVoid) {
            matchAndReplacePrimary(type1, type2);
            return null;
        }

        // In situations like <T extends E, E extends Object> the addition of @PotentialVar to T
        // in ExistentialVariableInserter.insert will cause it to be applied as a primary annotation to E.
        // We want this to occur in order to propagate the @PotentialVar, but we don't want ANY primary
        // annotations on type variable when we are done or the ExistentialVariableSlots will be overwritten
        // on the first call to getUpper/LowerBound
        @Override
        public Void visitTypevar_Typevar(AnnotatedTypeVariable type1, AnnotatedTypeVariable type2, Void aVoid) {
            if (matchesSlot(type1)) {
                type1.removeAnnotation(potentialVarAnno);
            }

            return super.visitTypevar_Typevar(type1, type2, aVoid);
        }

        // see comment on visitTypevar_Typevar
        @Override
        public Void visitWildcard_Wildcard(AnnotatedWildcardType type1, AnnotatedWildcardType type2, Void aVoid) {
            if (matchesSlot(type1)) {
                type1.removeAnnotation(potentialVarAnno);
            }

            return super.visitWildcard_Wildcard(type1, type2, aVoid);
        }

        private boolean matchesSlot(final AnnotatedTypeMirror type) {
            if (type.getAnnotationInHierarchy(varAnnot) == null) {
                return false;
            }

            VariableSlot varSlot = slotManager.getVariableSlot(type);
            if (varSlot == null) {
                return false;
            }

            return varSlot.equals(potentialVariable);
        }
    }
}
