package sparta.checkers;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.qual.DefaultLocation;
import org.checkerframework.framework.qual.PolyAll;
import org.checkerframework.framework.source.SourceChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.treeannotator.ImplicitsTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.PropagationTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.util.AnnotationBuilder;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.framework.util.defaults.QualifierDefaults;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.InternalUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.type.TypeKind;

import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;

import sparta.checkers.iflow.util.IFlowUtils;
import sparta.checkers.iflow.util.PFPermission;
import sparta.checkers.quals.FlowPermission;
import sparta.checkers.quals.PolyFlow;
import sparta.checkers.quals.PolyFlowReceiver;
import sparta.checkers.quals.PolySink;
import sparta.checkers.quals.PolySource;
import sparta.checkers.quals.Sink;
import sparta.checkers.quals.Source;

/**
 * Created by mcarthur on 4/3/14.
 */
public class SimpleFlowAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    static AnnotationMirror ANYSOURCE, NOSOURCE, ANYSINK, NOSINK;
    private final AnnotationMirror POLYSOURCE;
    private final AnnotationMirror POLYSINK;

    //Qualifier defaults for byte code and poly flow defaulting
    final QualifierDefaults byteCodeFieldDefault = new QualifierDefaults(elements, this);
    final QualifierDefaults byteCodeDefaults = new QualifierDefaults(elements, this);
    final QualifierDefaults polyFlowDefaults = new QualifierDefaults(elements, this);
    final QualifierDefaults polyFlowReceiverDefaults = new QualifierDefaults(elements, this);

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
        ANYSOURCE = buildAnnotationMirrorFlowPermission(Source.class, FlowPermission.ANY.toString());
        NOSINK = buildAnnotationMirrorFlowPermission(Sink.class);
        ANYSINK = buildAnnotationMirrorFlowPermission(Sink.class, FlowPermission.ANY.toString());
        POLYSOURCE = buildAnnotationMirror(PolySource.class);
        POLYSINK = buildAnnotationMirror(PolySink.class);
        this.postInit();
    }
    @Override
    protected void postInit() {
        super.postInit();
        // Has to be called after postInit
        // has been called for every subclass.
        initQualifierDefaults();
    }

    private AnnotationMirror buildAnnotationMirrorFlowPermission(
            Class<? extends java.lang.annotation.Annotation> clazz,
            String... flowPermission) {
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
        //All literals are bottom
        implicits.addTreeKind(Tree.Kind.INT_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.LONG_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.FLOAT_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.DOUBLE_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.BOOLEAN_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.CHAR_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.STRING_LITERAL, NOSOURCE);
        implicits.addTreeKind(Tree.Kind.NULL_LITERAL, NOSOURCE);

        implicits.addTreeKind(Tree.Kind.INT_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.LONG_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.FLOAT_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.DOUBLE_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.BOOLEAN_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.CHAR_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.STRING_LITERAL, ANYSINK);
        implicits.addTreeKind(Tree.Kind.NULL_LITERAL, ANYSINK);
        
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
                   List<String> s = AnnotationUtils.getElementValueArray(am, "value",
                            String.class, true);
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

    /**
     * Initializes qualifier defaults for @PolyFlow, @PolyFlowReceiver, and @FromByteCode
     */
    private void initQualifierDefaults() {
        // Final fields from byte code are {} -> ANY
        byteCodeFieldDefault.addAbsoluteDefault(NOSOURCE, DefaultLocation.OTHERWISE);
        byteCodeFieldDefault.addAbsoluteDefault(ANYSINK, DefaultLocation.OTHERWISE);

        // All locations besides non-final fields in byte code are
        // conservatively ANY -> ANY
        byteCodeDefaults.addAbsoluteDefault(ANYSOURCE, DefaultLocation.OTHERWISE);
        byteCodeDefaults.addAbsoluteDefault(ANYSINK, DefaultLocation.OTHERWISE);

        // Use poly flow sources and sinks for return types and
        // parameter types (This is excluding receivers).
        DefaultLocation[] polyFlowLoc = { DefaultLocation.RETURNS, DefaultLocation.PARAMETERS };
        polyFlowDefaults.addAbsoluteDefaults(POLYSOURCE, polyFlowLoc);
        polyFlowDefaults.addAbsoluteDefaults(POLYSINK, polyFlowLoc);

        // Use poly flow sources and sinks for return types and
        // parameter types and receivers).
        DefaultLocation[] polyFlowReceiverLoc = { DefaultLocation.RETURNS, DefaultLocation.PARAMETERS,
                DefaultLocation.RECEIVERS };
        polyFlowReceiverDefaults.addAbsoluteDefaults(POLYSOURCE, polyFlowReceiverLoc);
        polyFlowReceiverDefaults.addAbsoluteDefaults(POLYSINK, polyFlowReceiverLoc);
    }

    @Override
    protected QualifierDefaults createQualifierDefaults() {
        QualifierDefaults defaults =  super.createQualifierDefaults();
        //CLIMB-to-the-top defaults
        DefaultLocation[] topLocations = { DefaultLocation.LOCAL_VARIABLE, DefaultLocation.RESOURCE_VARIABLE,
                DefaultLocation.UPPER_BOUNDS };
        defaults.addAbsoluteDefaults(ANYSOURCE, topLocations);
        defaults.addAbsoluteDefaults(NOSINK, topLocations);

        // Default for receivers is top
        DefaultLocation[] conditionalSinkLocs = { DefaultLocation.RECEIVERS, DefaultLocation.PARAMETERS,
                DefaultLocation.EXCEPTION_PARAMETER };
        defaults.addAbsoluteDefaults(ANYSOURCE, conditionalSinkLocs);
        defaults.addAbsoluteDefaults(NOSINK, conditionalSinkLocs);

        // Default for returns and fields is {}->ANY (bottom)
        DefaultLocation[] bottomLocs = { DefaultLocation.RETURNS, DefaultLocation.FIELD };
        defaults.addAbsoluteDefaults(NOSOURCE, bottomLocs);
        defaults.addAbsoluteDefaults(ANYSINK, bottomLocs);

        // Default is {} -> ANY for everything else
        defaults.addAbsoluteDefault(ANYSINK, DefaultLocation.OTHERWISE);
        defaults.addAbsoluteDefault(NOSOURCE, DefaultLocation.OTHERWISE);

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

    protected void handleDefaulting(final Element element, final AnnotatedTypeMirror type) {
        if (element == null)
            return;
        handlePolyFlow(element, type);
        
        if (isFromByteCode(element)
                && element.getKind() == ElementKind.FIELD
                && ElementUtils.isEffectivelyFinal(element)) {
            byteCodeFieldDefault.annotate(element, type);
            return;
        }
            
        if (isFromByteCode(element)){
            byteCodeDefaults.annotate(element, type);
        } 
    }

    private void handlePolyFlow(Element element, AnnotatedTypeMirror type) {
        Element iter = element;
        while (iter != null) {
            if (element.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) element;
                if (method.getReturnType().getKind() == TypeKind.VOID) {
                    return;
                }
            }
            if (this.getDeclAnnotation(iter, PolyFlow.class) != null) {
                polyFlowDefaults.annotate(element, type);
                return;
            } else if (this.getDeclAnnotation(iter, PolyFlowReceiver.class) != null) {
                if (ElementUtils.hasReceiver(element)) {
                    polyFlowReceiverDefaults.annotate(element, type);
                } else {
                    polyFlowDefaults.annotate(element, type);
                }
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

        @Override public Set<? extends AnnotationMirror> getTopAnnotations() {
            return Collections.singleton(checker instanceof IFlowSinkChecker ?
                                                 NOSINK :
                                                 ANYSOURCE);
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
            if (isPolySourceQualifier(supertype) && isPolySourceQualifier(subtype)) {
                return true;
            } else if (isPolySourceQualifier(supertype) && isSourceQualifier(subtype)) {
                // If super is poly, only bottom is a subtype
                return IFlowUtils.getSources(subtype).isEmpty();
            } else if (isSourceQualifier(supertype) && isPolySourceQualifier(subtype)) {
                // if sub is poly, only top is a supertype
                return IFlowUtils.getSources(supertype).contains(PFPermission.ANY);
            } else if (isSourceQualifier(supertype) && isSourceQualifier(subtype)) {
                // Check the set
                Set<PFPermission> superset = IFlowUtils.getSources(supertype);
                Set<PFPermission> subset = IFlowUtils.getSources(subtype);
                return isSuperSet(superset, subset);
            } else if (isPolySinkQualifier(supertype) && isPolySinkQualifier(subtype)) {
                return true;
            } else if (isPolySinkQualifier(supertype) && isSinkQualifier(subtype)) {
                // If super is poly, only bottom is a subtype
                return IFlowUtils.getSinks(subtype).contains(PFPermission.ANY);
            } else if (isSinkQualifier(supertype) && isPolySinkQualifier(subtype)) {
                // if sub is poly, only top is a supertype
                return IFlowUtils.getSinks(supertype).isEmpty();
            } else if (isSinkQualifier(supertype) && isSinkQualifier(subtype)) {
                // Check the set (sinks are backward)
                Set<PFPermission> subset = IFlowUtils.getSinks(supertype);
                Set<PFPermission> superset = IFlowUtils.getSinks(subtype);
                return isSuperSet(superset, subset);
            } else {
                // annotations should either both be sources or sinks.
                return false;
            }
        }

        private boolean isSuperSet(Set<PFPermission> superset, Set<PFPermission> subset) {
            if (superset.containsAll(subset) || superset.contains(PFPermission.ANY)) {
                return true;
            }
            for (PFPermission flow : subset) {
                if (!IFlowUtils.isMatchInSet(flow, superset)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isSourceQualifier(AnnotationMirror anno) {
            return AnnotationUtils.areSameByClass(anno, Source.class)
                    || isPolySourceQualifier(anno);
        }

        private boolean isPolySourceQualifier(AnnotationMirror anno) {
            return AnnotationUtils.areSameByClass(anno, PolySource.class)
                    || AnnotationUtils.areSameByClass(anno, PolyAll.class);
        }

        private boolean isSinkQualifier(AnnotationMirror anno) {
            return isPolySinkQualifier(anno) || AnnotationUtils.areSameByClass(anno, Sink.class);
        }

        private boolean isPolySinkQualifier(AnnotationMirror anno) {
            return AnnotationUtils.areSameByClass(anno, PolySink.class)
                    || AnnotationUtils.areSameByClass(anno, PolyAll.class);
        }

    }
}
