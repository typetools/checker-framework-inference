package sparta.checkers;

import static org.checkerframework.framework.qual.DefaultLocation.FIELD;
import static org.checkerframework.framework.qual.DefaultLocation.LOCAL_VARIABLE;
import static org.checkerframework.framework.qual.DefaultLocation.OTHERWISE;
import static org.checkerframework.framework.qual.DefaultLocation.RECEIVERS;
import static org.checkerframework.framework.qual.DefaultLocation.RESOURCE_VARIABLE;
import static org.checkerframework.framework.qual.DefaultLocation.RETURNS;
import static org.checkerframework.framework.qual.DefaultLocation.UPPER_BOUNDS;

import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.qual.DefaultLocation;
import org.checkerframework.framework.qual.FromStubFile;
import org.checkerframework.framework.source.SourceChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.ImplicitsTreeAnnotator;
import org.checkerframework.framework.type.ListTreeAnnotator;
import org.checkerframework.framework.type.PropagationTreeAnnotator;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.TreeAnnotator;
import org.checkerframework.framework.util.AnnotationBuilder;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.framework.util.QualifierDefaults;
import org.checkerframework.framework.util.QualifierDefaults.DefaultApplierElement;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.InternalUtils;

import sparta.checkers.quals.FlowPermission;
import sparta.checkers.quals.PolyFlow;
import sparta.checkers.quals.PolyFlowReceiver;
import sparta.checkers.quals.PolySink;
import sparta.checkers.quals.PolySource;
import sparta.checkers.quals.Sink;
import sparta.checkers.quals.Source;

import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;

/**
 * Created by mcarthur on 4/3/14.
 */
public class SimpleFlowAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    static AnnotationMirror ANYSOURCE, NOSOURCE, ANYSINK, NOSINK;
    private final AnnotationMirror POLYSOURCE;
    private final AnnotationMirror POLYSINK;

    /**
     * Constructs a factory from the given {@link ProcessingEnvironment}
     * instance and syntax tree root. (These parameters are required so that the
     * factory may conduct the appropriate annotation-gathering analyses on
     * certain tree types.)
     * <p/>
     * Root can be {@code null} if the factory does not operate on trees.
     * <p/>
     * A subclass must call postInit at the end of its constructor.
     *
     * @param checker
     *            the {@link SourceChecker} to which this factory belongs
     * @throws IllegalArgumentException
     *             if either argument is {@code null}
     */
    public SimpleFlowAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);

        NOSOURCE = buildAnnotationMirrorFlowPermission(Source.class);
        ANYSOURCE = buildAnnotationMirrorFlowPermission(Source.class, FlowPermission.ANY);
        NOSINK = buildAnnotationMirrorFlowPermission(Sink.class);
        ANYSINK = buildAnnotationMirrorFlowPermission(Sink.class, FlowPermission.ANY);
        POLYSOURCE = buildAnnotationMirror(PolySource.class);
        POLYSINK = buildAnnotationMirror(PolySink.class);
        this.postInit();
    }

    private AnnotationMirror buildAnnotationMirrorFlowPermission(
            Class<? extends java.lang.annotation.Annotation> clazz,
            FlowPermission... flowPermission) {
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, clazz);
        builder.setValue("value", flowPermission);
        return builder.build();
    }
    private AnnotationMirror buildAnnotationMirror(
            Class<? extends java.lang.annotation.Annotation> clazz) {
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, clazz);
        return builder.build();
    }


    @Override
    protected TreeAnnotator createTreeAnnotator() {

        ImplicitsTreeAnnotator implicits = new ImplicitsTreeAnnotator(this);
        // But let's send null down any sink and give it no sources.
        //        treeAnnotator.addTreeKind(Tree.Kind.NULL_LITERAL, ANYSINK);
        //        treeAnnotator.addTreeKind(Tree.Kind.NULL_LITERAL, NOSOURCE);

        // Literals, other than null are different too
        // There are no Byte or Short literal types in java (0b is treated as an
        // int),
        // so there does not need to be a mapping for them here.
        implicits.addTreeKind(Tree.Kind.INT_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.LONG_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.FLOAT_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.DOUBLE_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.BOOLEAN_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.CHAR_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.STRING_LITERAL, NOSOURCE);

        implicits.addTreeKind(Tree.Kind.INT_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.LONG_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.FLOAT_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.DOUBLE_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.BOOLEAN_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.CHAR_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.STRING_LITERAL, ANYSINK);

        return new ListTreeAnnotator(new PropagationTreeAnnotator(this),
                implicits,
                new TreeAnnotator(this) {
            @Override
            public Void visitNewClass(NewClassTree node, AnnotatedTypeMirror p) {
                //This is a horrible hack around the bad implementation of constructor results
                //(CF treats annotations on constructor results in stub files as if it were a
                //default and therefore ignores it.)
                AnnotatedTypeMirror defaulted = atypeFactory.constructorFromUse(node).first.getReturnType();
                Set<AnnotationMirror> defaultedSet = defaulted.getAnnotations();
                //The default of OTHERWISE locations such as constructor results
                //is {}{}, but for constructor results we really want bottom.
                //So if the result is {}{}, then change it to {}->ANY (bottom)

                boolean empty = true;
                for(AnnotationMirror am: defaultedSet){
                   List<FlowPermission> s = AnnotationUtils.getElementValueEnumArray(am, "value",
                            FlowPermission.class, true);
                   empty = s.isEmpty() && empty;
                }

                if(empty){
                    defaultedSet = AnnotationUtils.createAnnotationSet();
                    defaultedSet.add(NOSOURCE);
                    defaultedSet.add(ANYSINK);
                }

                p.replaceAnnotations(defaultedSet);
                return null;
            }
                });
    }

    @Override
    protected QualifierDefaults createQualifierDefaults() {
        QualifierDefaults defaults = super.createQualifierDefaults();
        // Use the top type for local variables and let flow refine the type.
        //Upper bounds should be top too.
        DefaultLocation[] topLocations = { LOCAL_VARIABLE, RESOURCE_VARIABLE,
                UPPER_BOUNDS };

        defaults.addAbsoluteDefaults(ANYSOURCE, topLocations);
        defaults.addAbsoluteDefaults(NOSINK, topLocations);

        //Default for receivers and parameters is (All sources allowed) -> CONDITIONAL
        DefaultLocation[] conditionalSinkLocs = { RECEIVERS,
                DefaultLocation.PARAMETERS };
        defaults.addAbsoluteDefaults(NOSINK, conditionalSinkLocs);
        defaults.addAbsoluteDefaults(ANYSOURCE, conditionalSinkLocs);

        defaults.addAbsoluteDefault(ANYSINK, RETURNS);
        defaults.addAbsoluteDefault(NOSOURCE, RETURNS);

        // Default is LITERAL -> (ALL MAPPED SINKS) for everything else
        defaults.addAbsoluteDefault(NOSINK, OTHERWISE);
        defaults.addAbsoluteDefault(NOSOURCE, OTHERWISE);

        defaults.addAbsoluteDefault(ANYSINK, FIELD);

        return defaults;
    }

    @Override
    protected void annotateImplicit(Tree tree, AnnotatedTypeMirror type,
            boolean useFlow) {
        Element element = InternalUtils.symbol(tree);
        handleDefaulting(element, type);
        super.annotateImplicit(tree, type, useFlow);
    }

    @Override
    public void annotateImplicit(Element element, AnnotatedTypeMirror type) {
        handleDefaulting(element, type);
        super.annotateImplicit(element, type);
    }

    protected void handleDefaulting(final Element element,
            final AnnotatedTypeMirror type) {
        if( element == null) return;
        DefaultApplierElement applier = new DefaultApplierElement(this,
                element, type);

        handlePolyFlow(element, applier);
        if (this.isFromByteCode(element)) {

            //A parameter of an not reviewed method could go any where
            applier.apply(ANYSINK, DefaultLocation.PARAMETERS);
            //Don't add a source, this way it will be
            //defaulted based on what is in the flow policy
            //applier.apply(NOSOURCE, DefaultLocation.PARAMETERS);

            //A return type could be from any source
            applier.apply(ANYSOURCE, DefaultLocation.RETURNS);
            //Don't add a sink, this way it will be
            //defaulted based on what is in the flow policy
            //applier.apply(ANYSINK, DefaultLocation.RETURNS);

            //All other types could be from any where or go any where.
            applier.apply(ANYSOURCE, DefaultLocation.OTHERWISE);
            applier.apply(ANYSINK, DefaultLocation.OTHERWISE);
            return;
        } else if (this.getDeclAnnotation(element, FromStubFile.class) != null) {
            applier.apply(NOSINK, DefaultLocation.PARAMETERS);
            applier.apply(ANYSOURCE, DefaultLocation.PARAMETERS);
        }
    }

    private void handlePolyFlow(Element iter, DefaultApplierElement applier) {
        while (iter != null) {
            if (this.getDeclAnnotation(iter, PolyFlow.class) != null) {
                // Use poly flow sources and sinks for return types .
                applier.apply(POLYSOURCE, DefaultLocation.RETURNS);
                applier.apply(POLYSINK, DefaultLocation.RETURNS);

                // Use poly flow sources and sinks for Parameter types (This is
                // excluding receivers)
                applier.apply(POLYSINK, DefaultLocation.PARAMETERS);
                applier.apply(POLYSOURCE, DefaultLocation.PARAMETERS);

                return;

            } else if (this.getDeclAnnotation(iter, PolyFlowReceiver.class) != null) {
                // Use poly flow sources and sinks for return types .
                applier.apply(POLYSOURCE, DefaultLocation.RETURNS);
                applier.apply(POLYSINK, DefaultLocation.RETURNS);

                // Use poly flow sources and sinks for Parameter types (This is
                // excluding receivers)
                applier.apply(POLYSINK, DefaultLocation.PARAMETERS);
                applier.apply(POLYSOURCE, DefaultLocation.PARAMETERS);

                // Use poly flow sources and sinks for receiver types
                applier.apply(POLYSINK, DefaultLocation.RECEIVERS);
                applier.apply(POLYSOURCE, DefaultLocation.RECEIVERS);

                return;
            }

            if (iter instanceof PackageElement) {
                iter = ElementUtils.parentPackage(this.elements,
                        (PackageElement) iter);
            } else {
                iter = iter.getEnclosingElement();
            }
        }
    }

    @Override
    protected MultiGraphQualifierHierarchy.MultiGraphFactory createQualifierHierarchyFactory() {
        return new MultiGraphQualifierHierarchy.MultiGraphFactory(this);
    }

    @Override
    public QualifierHierarchy createQualifierHierarchy(
            MultiGraphQualifierHierarchy.MultiGraphFactory factory) {
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
        public boolean isSubtype(AnnotationMirror subtype,
                AnnotationMirror supertype) {
            return getTopAnnotation(subtype) == getTopAnnotation(supertype);
        }
    }
}
