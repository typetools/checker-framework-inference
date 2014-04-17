package sparta.checkers;

import com.sun.source.tree.*;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.TreeAnnotator;
import org.checkerframework.framework.util.AnnotationBuilder;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import sparta.checkers.quals.FlowPermission;
import sparta.checkers.quals.Sink;
import sparta.checkers.quals.Source;

import javax.lang.model.element.AnnotationMirror;
import java.util.Set;

/**
 * Created by mcarthur on 4/3/14.
 */
public class SimpleFlowAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    protected final AnnotationMirror ANYSOURCE, NOSOURCE, ANYSINK, NOSINK, LITERALSOURCE, FROMLITERALSINK;

    /**
     * Constructs a factory from the given {@link ProcessingEnvironment}
     * instance and syntax tree root. (These parameters are required so that
     * the factory may conduct the appropriate annotation-gathering analyses on
     * certain tree types.)
     * <p/>
     * Root can be {@code null} if the factory does not operate on trees.
     * <p/>
     * A subclass must call postInit at the end of its constructor.
     *
     * @param checker the {@link SourceChecker} to which this factory belongs
     * @throws IllegalArgumentException if either argument is {@code null}
     */
    public SimpleFlowAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);

        NOSOURCE = buildAnnotationMirror(Source.class);
        ANYSOURCE = buildAnnotationMirror(Source.class, FlowPermission.ANY);
        NOSINK = buildAnnotationMirror(Sink.class);
        ANYSINK = buildAnnotationMirror(Sink.class, FlowPermission.ANY);

        LITERALSOURCE = buildAnnotationMirror(Source.class, FlowPermission.LITERAL);
        FROMLITERALSINK = buildAnnotationMirror(Sink.class);
        this.postInit();
    }

    private AnnotationMirror buildAnnotationMirror( Class<? extends java.lang.annotation.Annotation> clazz, FlowPermission ... flowPermission) {
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, clazz);
        builder.setValue("value", flowPermission);
        return builder.build();
    }


    @Override
    protected TreeAnnotator createTreeAnnotator() {
        FlowPolicyTreeAnnotator treeAnnotator = new FlowPolicyTreeAnnotator(this);

        // But let's send null down any sink and give it no sources.
        treeAnnotator.addTreeKind(Tree.Kind.NULL_LITERAL, ANYSINK);
        treeAnnotator.addTreeKind(Tree.Kind.NULL_LITERAL, NOSOURCE);

        // Literals, other than null are different too
        // There are no Byte or Short literal types in java (0b is treated as an
        // int),
        // so there does not need to be a mapping for them here.
        treeAnnotator.addTreeKind(Tree.Kind.INT_LITERAL, LITERALSOURCE);
        treeAnnotator.addTreeKind(Tree.Kind.LONG_LITERAL, LITERALSOURCE);
        treeAnnotator.addTreeKind(Tree.Kind.FLOAT_LITERAL, LITERALSOURCE);
        treeAnnotator.addTreeKind(Tree.Kind.DOUBLE_LITERAL, LITERALSOURCE);
        treeAnnotator.addTreeKind(Tree.Kind.BOOLEAN_LITERAL, LITERALSOURCE);
        treeAnnotator.addTreeKind(Tree.Kind.CHAR_LITERAL, LITERALSOURCE);
        treeAnnotator.addTreeKind(Tree.Kind.STRING_LITERAL, LITERALSOURCE);

        treeAnnotator.addTreeKind(Tree.Kind.INT_LITERAL, ANYSINK);
        treeAnnotator.addTreeKind(Tree.Kind.LONG_LITERAL, ANYSINK);
        treeAnnotator.addTreeKind(Tree.Kind.FLOAT_LITERAL, ANYSINK);
        treeAnnotator.addTreeKind(Tree.Kind.DOUBLE_LITERAL, ANYSINK);
        treeAnnotator.addTreeKind(Tree.Kind.BOOLEAN_LITERAL, ANYSINK);
        treeAnnotator.addTreeKind(Tree.Kind.CHAR_LITERAL, ANYSINK);
        treeAnnotator.addTreeKind(Tree.Kind.STRING_LITERAL, ANYSINK);
        return treeAnnotator;
    }

    class FlowPolicyTreeAnnotator extends TreeAnnotator {

        public FlowPolicyTreeAnnotator(SimpleFlowAnnotatedTypeFactory atypeFactory) {
            super(atypeFactory);
        }

        @Override
        public Void defaultAction(Tree tree, AnnotatedTypeMirror type) {
            return super.defaultAction(tree, type);
        }

        @Override
        public Void visitTypeCast(TypeCastTree node, AnnotatedTypeMirror type) {
            // TODO should super do this call?
            defaultAction(node, type);
            return super.visitTypeCast(node, type);
        }

        @Override
        public Void visitUnary(UnaryTree node, AnnotatedTypeMirror type) {
            // TODO should super do this call?
            defaultAction(node, type);
            return super.visitUnary(node, type);
        }

        @Override
        public Void visitBinary(BinaryTree node, AnnotatedTypeMirror type) {
            // TODO should super do this call?
            defaultAction(node, type);
            return super.visitBinary(node, type);
        }

        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree node, AnnotatedTypeMirror type) {
            // TODO should super do this call?
            defaultAction(node, type);
            return super.visitCompoundAssignment(node, type);
        }

        @Override
        public Void visitNewArray(NewArrayTree tree, AnnotatedTypeMirror type) {
            // TODO should super do this call?
            defaultAction(tree, type);
            return super.visitNewArray(tree, type);
        }

    }

    @Override
    protected MultiGraphQualifierHierarchy.MultiGraphFactory createQualifierHierarchyFactory() {
        return new MultiGraphQualifierHierarchy.MultiGraphFactory(this);
    }

    @Override
    public QualifierHierarchy createQualifierHierarchy(MultiGraphQualifierHierarchy.MultiGraphFactory factory) {
        return new FlowQualifierHierarchy(factory);
    }

    protected class FlowQualifierHierarchy extends MultiGraphQualifierHierarchy {

        public FlowQualifierHierarchy(MultiGraphFactory f) {
            super(f);
        }

        @Override
        public AnnotationMirror getTopAnnotation(AnnotationMirror start) {
            if (start.toString().contains("Sink")) {
                return NOSINK;
            } else {
                return ANYSOURCE;
            }
        }

        @Override
        public boolean isSubtype(AnnotationMirror subtype, AnnotationMirror supertype){
            return getTopAnnotation(subtype) == getTopAnnotation(supertype);
        }
    }
}