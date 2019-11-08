package sparta.checkers;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.qual.LiteralKind;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.framework.source.SourceChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.LiteralTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.PropagationTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.framework.util.defaults.QualifierDefaults;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.type.TypeKind;

import sparta.checkers.iflow.util.IFlowUtils;
import sparta.checkers.iflow.util.PFPermission;
import sparta.checkers.qual.FlowPermission;
import sparta.checkers.qual.PolyFlow;
import sparta.checkers.qual.PolyFlowReceiver;
import sparta.checkers.qual.PolySink;
import sparta.checkers.qual.PolySource;
import sparta.checkers.qual.Sink;
import sparta.checkers.qual.Source;

import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;

/**
 * Created by mcarthur on 4/3/14.
 */
public class SimpleFlowAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    static AnnotationMirror ANYSOURCE, NOSOURCE, ANYSINK, NOSINK;
    private final AnnotationMirror POLYSOURCE;
    private final AnnotationMirror POLYSINK;

    // Qualifier defaults for byte code and poly flow defaulting
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

    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        Set<Class<? extends Annotation>> res = new HashSet<>();
        if (checker instanceof IFlowSinkChecker) {
            res.add(Sink.class);
            res.add(PolySink.class);
        } else {
            res.add(Source.class);
            res.add(PolySource.class);
        }
        return res;
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

        LiteralTreeAnnotator implicits = new LiteralTreeAnnotator(this);
        // All literals are bottom
        implicits.addLiteralKind(LiteralKind.ALL, NOSOURCE);
        implicits.addLiteralKind(LiteralKind.ALL, ANYSINK);

        return new ListTreeAnnotator(new PropagationTreeAnnotator(this),
                implicits,
                new TreeAnnotator(this) {
            @Override
            public Void visitNewClass(NewClassTree node, AnnotatedTypeMirror p) {
                // This is a horrible hack around the bad implementation of constructor results
                // (CF treats annotations on constructor results in stub files as if it were a
                // default and therefore ignores it.)
                AnnotatedTypeMirror defaulted = atypeFactory.constructorFromUse(node).executableType.getReturnType();
                Set<AnnotationMirror> defaultedSet = defaulted.getAnnotations();
                // The default of OTHERWISE locations such as constructor results
                // is {}{}, but for constructor results we really want bottom.
                // So if the result is {}{}, then change it to {}->ANY (bottom)

                boolean empty = true;
                for (AnnotationMirror am: defaultedSet) {
                   List<String> s = AnnotationUtils.getElementValueArray(am, "value",
                            String.class, true);
                   empty = s.isEmpty() && empty;
                }

                if (empty) {
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
        byteCodeFieldDefault.addCheckedCodeDefault(NOSOURCE, TypeUseLocation.OTHERWISE);
        byteCodeFieldDefault.addCheckedCodeDefault(ANYSINK, TypeUseLocation.OTHERWISE);

        // All locations besides non-final fields in byte code are
        // conservatively ANY -> ANY
        byteCodeDefaults.addCheckedCodeDefault(ANYSOURCE, TypeUseLocation.OTHERWISE);
        byteCodeDefaults.addCheckedCodeDefault(ANYSINK, TypeUseLocation.OTHERWISE);

        // Use poly flow sources and sinks for return types and
        // parameter types (This is excluding receivers).
        TypeUseLocation[] polyFlowLoc = { TypeUseLocation.RETURN, TypeUseLocation.PARAMETER };
        polyFlowDefaults.addCheckedCodeDefaults(POLYSOURCE, polyFlowLoc);
        polyFlowDefaults.addCheckedCodeDefaults(POLYSINK, polyFlowLoc);

        // Use poly flow sources and sinks for return types and
        // parameter types and receivers).
        TypeUseLocation[] polyFlowReceiverLoc = { TypeUseLocation.RETURN, TypeUseLocation.PARAMETER,
                TypeUseLocation.RECEIVER };
        polyFlowReceiverDefaults.addCheckedCodeDefaults(POLYSOURCE, polyFlowReceiverLoc);
        polyFlowReceiverDefaults.addCheckedCodeDefaults(POLYSINK, polyFlowReceiverLoc);
    }

    @Override
    protected void addCheckedCodeDefaults(QualifierDefaults defaults) {
        // CLIMB-to-the-top defaults
        TypeUseLocation[] topLocations = { TypeUseLocation.LOCAL_VARIABLE, TypeUseLocation.RESOURCE_VARIABLE,
                TypeUseLocation.UPPER_BOUND };
        defaults.addCheckedCodeDefaults(ANYSOURCE, topLocations);
        defaults.addCheckedCodeDefaults(NOSINK, topLocations);

        // Default for receivers is top
        TypeUseLocation[] conditionalSinkLocs = { TypeUseLocation.RECEIVER, TypeUseLocation.PARAMETER,
                TypeUseLocation.EXCEPTION_PARAMETER };
        defaults.addCheckedCodeDefaults(ANYSOURCE, conditionalSinkLocs);
        defaults.addCheckedCodeDefaults(NOSINK, conditionalSinkLocs);

        // Default for returns and fields is {}->ANY (bottom)
        TypeUseLocation[] bottomLocs = { TypeUseLocation.RETURN, TypeUseLocation.FIELD };
        defaults.addCheckedCodeDefaults(NOSOURCE, bottomLocs);
        defaults.addCheckedCodeDefaults(ANYSINK, bottomLocs);

        // Default is {} -> ANY for everything else
        defaults.addCheckedCodeDefault(ANYSINK, TypeUseLocation.OTHERWISE);
        defaults.addCheckedCodeDefault(NOSOURCE, TypeUseLocation.OTHERWISE);
    }

    @Override
    protected void addComputedTypeAnnotations(Tree tree, AnnotatedTypeMirror type,
            boolean useFlow) {
        Element element = TreeUtils.elementFromTree(tree);
        handleDefaulting(element, type);
        super.addComputedTypeAnnotations(tree, type, useFlow);
    }

    @Override
    public void addComputedTypeAnnotations(Element element, AnnotatedTypeMirror type) {
        handleDefaulting(element, type);
        super.addComputedTypeAnnotations(element, type);
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

        if (isFromByteCode(element)) {
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
                iter = ElementUtils.parentPackage((PackageElement) iter,
                        this.elements);
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
            polyQualifiers.clear();
            polyQualifiers.put(NOSINK, POLYSINK);
            polyQualifiers.put(ANYSOURCE, POLYSOURCE);
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
        public Set<? extends AnnotationMirror> getBottomAnnotations() {
            return Collections.singleton(checker instanceof IFlowSinkChecker ?
                    ANYSINK :
                    NOSOURCE);
        }

        @Override
        public AnnotationMirror getBottomAnnotation(AnnotationMirror start) {
            if (start.toString().contains("Sink")) {
                return ANYSINK;
            } else {
                return NOSOURCE;
            }
        }

        @Override
        public boolean isSubtype(AnnotationMirror subtype, AnnotationMirror supertype) {
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
            return AnnotationUtils.areSameByClass(anno, PolySource.class);
        }

        private boolean isSinkQualifier(AnnotationMirror anno) {
            return isPolySinkQualifier(anno) || AnnotationUtils.areSameByClass(anno, Sink.class);
        }

        private boolean isPolySinkQualifier(AnnotationMirror anno) {
            return AnnotationUtils.areSameByClass(anno, PolySink.class);
        }

    }
}
