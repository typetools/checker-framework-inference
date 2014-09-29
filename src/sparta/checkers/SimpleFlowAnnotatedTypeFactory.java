package sparta.checkers;

import static org.checkerframework.framework.qual.DefaultLocation.EXCEPTION_PARAMETER;
import static org.checkerframework.framework.qual.DefaultLocation.FIELD;
import static org.checkerframework.framework.qual.DefaultLocation.LOCAL_VARIABLE;
import static org.checkerframework.framework.qual.DefaultLocation.OTHERWISE;
import static org.checkerframework.framework.qual.DefaultLocation.PARAMETERS;
import static org.checkerframework.framework.qual.DefaultLocation.RECEIVERS;
import static org.checkerframework.framework.qual.DefaultLocation.RESOURCE_VARIABLE;
import static org.checkerframework.framework.qual.DefaultLocation.RETURNS;
import static org.checkerframework.framework.qual.DefaultLocation.UPPER_BOUNDS;

import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.type.TypeKind;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.qual.DefaultLocation;
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
        ANYSOURCE = buildAnnotationMirrorFlowPermission(Source.class, FlowPermission.ANY);
        NOSINK = buildAnnotationMirrorFlowPermission(Sink.class);
        ANYSINK = buildAnnotationMirrorFlowPermission(Sink.class, FlowPermission.ANY);
        POLYSOURCE = buildAnnotationMirror(PolySource.class);
        POLYSINK = buildAnnotationMirror(PolySink.class);
        this.postInit();
    }
    @Override
    protected void postInit() {
    	super.postInit();
    	//Has to be called after postInit 
    	//has been called for every subclass.
        initQualifierDefaults();
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
	/**
	 * Initializes qualifier defaults for 
	 * @PolyFlow, @PolyFlowReceiver, and @FromByteCode
	 */
	private void initQualifierDefaults(){
		// Final fields from byte code are {} -> ANY
		byteCodeFieldDefault.addAbsoluteDefault(NOSOURCE, OTHERWISE);
		byteCodeFieldDefault.addAbsoluteDefault(ANYSINK, OTHERWISE);

		// All locations besides non-final fields in byte code are
		// conservatively ANY -> ANY
		byteCodeDefaults.addAbsoluteDefault(ANYSOURCE,
				DefaultLocation.OTHERWISE);
		byteCodeDefaults.addAbsoluteDefault(ANYSINK, DefaultLocation.OTHERWISE);

		// Use poly flow sources and sinks for return types and
		// parameter types (This is excluding receivers).
		DefaultLocation[] polyFlowLoc = { DefaultLocation.RETURNS,
				DefaultLocation.PARAMETERS };
		polyFlowDefaults.addAbsoluteDefaults(POLYSOURCE, polyFlowLoc);
		polyFlowDefaults.addAbsoluteDefaults(POLYSINK, polyFlowLoc);

		// Use poly flow sources and sinks for return types and
		// parameter types and receivers).
		DefaultLocation[] polyFlowReceiverLoc = { DefaultLocation.RETURNS,
				DefaultLocation.PARAMETERS, DefaultLocation.RECEIVERS };
		polyFlowReceiverDefaults.addAbsoluteDefaults(POLYSOURCE,
				polyFlowReceiverLoc);
		polyFlowReceiverDefaults.addAbsoluteDefaults(POLYSINK,
				polyFlowReceiverLoc);
	}

    @Override
    protected QualifierDefaults createQualifierDefaults() {
        QualifierDefaults defaults =  super.createQualifierDefaults();
        //CLIMB-to-the-top defaults
        DefaultLocation[] topLocations = { LOCAL_VARIABLE, RESOURCE_VARIABLE, UPPER_BOUNDS };
        defaults.addAbsoluteDefaults(ANYSOURCE, topLocations);
        defaults.addAbsoluteDefaults(NOSINK, topLocations);

        // Default for receivers is top
        DefaultLocation[] conditionalSinkLocs = { RECEIVERS, PARAMETERS,
                EXCEPTION_PARAMETER };
        defaults.addAbsoluteDefaults(ANYSOURCE, conditionalSinkLocs);
        defaults.addAbsoluteDefaults(NOSINK, conditionalSinkLocs);

        // Default for returns and fields is {}->ANY (bottom)
        DefaultLocation[] bottomLocs = { RETURNS, FIELD };
        defaults.addAbsoluteDefaults(NOSOURCE, bottomLocs);
        defaults.addAbsoluteDefaults(ANYSINK, bottomLocs);

        // Default is {} -> ANY for everything else
        defaults.addAbsoluteDefault(ANYSINK, OTHERWISE);
        defaults.addAbsoluteDefault(NOSOURCE, OTHERWISE);

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
//                if (ElementUtils.hasReceiver(element)) {
//                    polyFlowReceiverDefaults.annotate(element, type);
//                } else {
                    polyFlowDefaults.annotate(element, type);
//                }
//                return;
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
