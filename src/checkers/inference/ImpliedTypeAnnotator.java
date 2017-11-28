package checkers.inference;

import scenelib.annotations.io.ASTRecord;
import checkers.inference.model.AnnotationLocation;
import checkers.inference.model.AnnotationLocation.AstPathLocation;
import checkers.inference.model.VariableSlot;
import checkers.inference.util.ASTPathUtil;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.visitor.AnnotatedTypeScanner;

import java.util.HashMap;
import java.util.Map;

/**
 * The ImpliedTypeAnnotator will create variables and add VarAnnots to all definite type use locations
 * on an input type.  If a parent ASTRecord is passed to the ImpliedTypeAnnotator it will also create
 * new ASTPathLocations representing the location of these annotations for each variable created.
 *
 */
public class ImpliedTypeAnnotator {

    private final SlotManager slotManager;
    private final ExistentialVariableInserter existentialVariableInserter;
    private final InferenceAnnotatedTypeFactory typeFactory;

    public ImpliedTypeAnnotator(InferenceAnnotatedTypeFactory typeFactory, SlotManager slotManager,
                                ExistentialVariableInserter existentialVariableInserter) {
        this.slotManager = slotManager;
        this.existentialVariableInserter = existentialVariableInserter;
        this.typeFactory = typeFactory;
    }

    /**
     * Applies annotations to all definite type use locations on impliedType.
     * @param impliedType The type to annotate
     * @param isUse Whether or not this type should be treated as a use
     * @param parent The AST Path to the parent tree on which type would be added
     */
    public void annotate(AnnotatedTypeMirror impliedType, boolean isUse, final ASTRecord parent) {
        Map<AnnotatedTypeMirror, ASTRecord> typeToRecord =
            (parent == null) ? new HashMap<AnnotatedTypeMirror, ASTRecord>() :  ASTPathUtil.getImpliedRecordForUse(parent, impliedType);
        new Visitor(isUse).scan(impliedType, typeToRecord);
    }

    protected class Visitor extends AnnotatedTypeScanner<Void, Map<AnnotatedTypeMirror, ASTRecord>> {

        // When a type variable is passed to annotate the caller may or may not want to
        // treat it as a type use, for type parameters should not be treated as uses
        private boolean isUse;

        public Visitor(boolean isUse) {
            this.isUse = isUse;
        }

        @Override
        public Void visitExecutable(AnnotatedExecutableType type, Map<AnnotatedTypeMirror, ASTRecord> astRecords) {
            scan(type.getReturnType(), null);
            if (type.getReceiverType() != null) {
                scanAndReduce(type.getReceiverType(), null, null);
            }
            scanAndReduce(type.getParameterTypes(), null, null);
            scanAndReduce(type.getThrownTypes(), null, null);
            scanAndReduce(type.getTypeVariables(), null, null);
            return null;
        }

        protected void insertExistentialVariable(AnnotatedTypeVariable typeVariableUse, Map<AnnotatedTypeMirror, ASTRecord> astRecords) {
            if (typeVariableUse.isDeclaration()) {
                throw new RuntimeException(
                        "ExistentialVariables should only be placed on type variable uses, not their declarations!\n"
                                + "typeVariableUse=" + typeVariableUse + "\n"
                                + "record=" + astRecords + "\n");
            }

            AnnotatedTypeVariable declaration = (AnnotatedTypeVariable)
                    typeFactory.getAnnotatedType(typeVariableUse.getUnderlyingType().asElement());

            AnnotationLocation location = getLocation(typeVariableUse, astRecords);
            VariableSlot potentialVar = slotManager.createVariableSlot(location);
            existentialVariableInserter.insert(potentialVar, typeVariableUse, declaration);
        }

        @Override
        protected Void scan(AnnotatedTypeMirror type, Map<AnnotatedTypeMirror, ASTRecord> astRecords) {

            switch (type.getKind()) {
                case TYPEVAR:
                    if (isUse) {
                        insertExistentialVariable((AnnotatedTypeVariable) type, astRecords);
                    } else {
                        // we don't delve any deeper after we find a use of a type variable because
                        // the lower levels are not locations that can be annotated from an implied ASTPosition
                        super.scan(type, null);
                    }
                    break;

                case WILDCARD:
                    // everything below the top level in the type's tree structure must be a use
                    isUse = true;
                    super.scan(type, null);
                    break;

                default:
                    addVariablePrimaryAnnotation(type, astRecords);
                    // everything below the top level in the type's tree structure must be a use
                    isUse = true;
                    super.scan(type, null);
                    break;

            }

            return null;
        }

        /**
         * Creates a variable for the primary location of type using astRecords to identify the annotation location
         * and adds it as a primary annotation
         */
        protected void addVariablePrimaryAnnotation(final AnnotatedTypeMirror type, Map<AnnotatedTypeMirror, ASTRecord> astRecords) {
            AnnotationLocation location = getLocation(type, astRecords);
            VariableSlot slot = slotManager.createVariableSlot(location);
            type.addAnnotation(slotManager.getAnnotation(slot));
        }

        /**
         * searches for an ASTRecord for type in AstRecords.  If none is found, AnnotationLocation.MISSING_LOCATION
         * is returned, otherwise an AstPathLocation is returned.
         */
        protected AnnotationLocation getLocation(AnnotatedTypeMirror type, Map<AnnotatedTypeMirror, ASTRecord> astRecords) {
            ASTRecord record = astRecords.get(type);
            return record == null ? AnnotationLocation.MISSING_LOCATION : new AstPathLocation(record);
        }
    }
}
