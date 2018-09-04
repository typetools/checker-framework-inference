package checkers.inference;

import org.checkerframework.common.subtyping.qual.Unqualified;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.BugInCF;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;

import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.qual.VarAnnot;

/**
 * The default implementation of SlotManager.
 * @see checkers.inference.SlotManager
 */
public class DefaultSlotManager implements SlotManager {

    private final AnnotationMirror unqualified;
    private final AnnotationMirror varAnnot;

    // Whether or not a call to getSlot on a real annotation mirror should generate
    // a new AnnotationMirror each time or test whether or not we already have the
    // given annotation and pull it from a store.
    // This should only be used when annotations are NOT parameterized
    // TODO: If we wrapped all annotations used by the framework in a special
    // TODO: smart AnnotationMirror interface that has a useful equals
    // TODO: We could instead create an LRU for the cases of parameterized annotations
    private final boolean storeConstants;
    private final Map<String, ConstantSlot> constantStore;

    // this id starts at 1 because sin ome serializer's (CnfSerializer) 0 is used as line delimiters
    // monotonically increasing id for all VariableSlots (including subtypes of VariableSlots)
    private int nextId = 1;

    // a map of variable id to variable for ALL variables (including subtypes of VariableSlots)
    private final Map<Integer, VariableSlot> variables;

    private final Set<Class<? extends Annotation>> realQualifiers;
    private final ProcessingEnvironment processingEnvironment;

    public DefaultSlotManager( final ProcessingEnvironment processingEnvironment,
                               final Set<Class<? extends Annotation>> realQualifiers,
                               boolean storeConstants) {
        this.processingEnvironment = processingEnvironment;
        // sort the qualifiers so that they are always assigned the same varId
        this.realQualifiers = sortAnnotationClasses(realQualifiers);
        variables = new LinkedHashMap<>();

        AnnotationBuilder builder = new AnnotationBuilder(processingEnvironment, VarAnnot.class);
        builder.setValue("value", -1 );
        this.varAnnot = builder.build();

        AnnotationBuilder unqualifiedBuilder = new AnnotationBuilder(processingEnvironment, Unqualified.class);
        this.unqualified = unqualifiedBuilder.build();

        this.storeConstants = storeConstants;
        if (storeConstants) {
            constantStore = new HashMap<>();
            for (Class<? extends Annotation> annoClass : this.realQualifiers) {
                AnnotationBuilder constantBuilder = new AnnotationBuilder(processingEnvironment, annoClass);
                ConstantSlot constantSlot = new ConstantSlot(constantBuilder.build(), nextId());
                addVariable(constantSlot);

                constantStore.put(annoClass.getCanonicalName(), constantSlot);
            }
        } else {
            constantStore = null;
        }
    }

    private Set<Class<? extends Annotation>> sortAnnotationClasses(Set<Class<? extends Annotation>> annotations) {

        TreeSet<Class<? extends Annotation>> set = new TreeSet<>(new Comparator<Class<? extends Annotation>>() {
            @Override
            public int compare(Class<? extends Annotation> o1, Class<? extends Annotation> o2) {
                if (o1 == o2) return 0;
                if (o1 == null) return -1;
                if (o2 == null) return 1;
                return o1.getCanonicalName().compareTo(o2.getCanonicalName());
            }
        });
        set.addAll(annotations);
        return set;
    }

    /**
     * @inheritDoc
     */
    @Override
    public int nextId() {
        return nextId++;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void addVariable( final VariableSlot slot ) {
        variables.put( slot.getId(), slot );
    }

    /**
     * @inheritDoc
     */
    @Override
    public VariableSlot getVariable( int id ) {
        return variables.get(id);
    }

    /**
     * @inheritDoc
     */
    @Override
    public AnnotationMirror getAnnotation( final Slot slot ) {
        final Class<?> slotClass = slot.getClass();

        // We need to build the AnntotationBuilder each time because AnnotationBuilders are only allowed to build their annotations once
        if (slotClass.equals(VariableSlot.class) || slotClass.equals(ExistentialVariableSlot.class)
                || slotClass.equals(RefinementVariableSlot.class) || slotClass.equals(CombVariableSlot.class)
                || slotClass.equals(ConstantSlot.class)) {
            return convertVariable((VariableSlot) slot, new AnnotationBuilder(processingEnvironment, VarAnnot.class));
        }

        if (slotClass.equals(ConstantSlot.class)) {
            return ((ConstantSlot) slot).getValue();
        }

        throw new IllegalArgumentException("Slot type unrecognized( " + slot.getClass() + ") Slot=" + slot.toString() );
    }

    /**
     * Converts the given VariableSlot into an annotation using the given AnnotationBuiklder
     * @param variable VariableSlot to convert
     * @param annotationBuilder appropriate annotation for the actual class of the VariableSlot which could be subtype
     *                          of VariableSlot.  Eg.  CombVariableSlots use combVarBuilder which is parameterized to
     *                          build @CombVarAnnots
     * @return An annotation representing variable
     */
    private AnnotationMirror convertVariable( final VariableSlot variable, final AnnotationBuilder annotationBuilder) {
        annotationBuilder.setValue("value", variable.getId() );
        return annotationBuilder.build();
    }

    // TODO: RENAME AND UPDATE DOCS
    /**
     * @inheritDoc
     */
    @Override
    public VariableSlot getVariableSlot( final AnnotatedTypeMirror atm ) {

        AnnotationMirror annot = atm.getAnnotationInHierarchy(this.varAnnot);
        if (annot == null) {
            annot = atm.getAnnotationInHierarchy(this.unqualified);
            if (annot == null) {
                if (InferenceMain.isHackMode()) {
                    return null;
                }

                throw new BugInCF("Missing VarAnnot annotation: " + atm);
            }
        }

        return (VariableSlot) getSlot(annot);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Slot getSlot( final AnnotationMirror annotationMirror ) {

        final int id;
        if (InferenceQualifierHierarchy.isVarAnnot(annotationMirror)) {
            if (annotationMirror.getElementValues().isEmpty()) {
                return null; // TODO: should we instead throw an exception?
            } else {
                final AnnotationValue annoValue = annotationMirror.getElementValues().values().iterator().next();
                id = Integer.valueOf( annoValue.toString() );
            }

            return getVariable( id );

        } else {

            if (constantStore != null) {
                return constantStore.get(AnnotationUtils.annotationName(annotationMirror));

            } else {
                for (Class<? extends Annotation> realAnno : realQualifiers) {
                    if (AnnotationUtils.areSameByClass(annotationMirror, realAnno)) {
                        return new ConstantSlot(annotationMirror, nextId());
                    }
                }
            }
        }

        if (InferenceMain.isHackMode()) {
            return new ConstantSlot(InferenceMain.getInstance().getRealTypeFactory().
                    getQualifierHierarchy().getTopAnnotations().iterator().next(), nextId());
        }
        throw new BugInCF( annotationMirror + " is a type of AnnotationMirror not handled by getVariableSlot." );
    }

    /**
     * @inheritDoc
     */
    @Override
    public List<Slot> getSlots() {
        return new ArrayList<Slot>( this.variables.values() );
    }

    // Sometimes, I miss scala.
    /**
     * @inheritDoc
     */
    @Override
    public List<VariableSlot> getVariableSlots() {
        List<VariableSlot> varSlots = new ArrayList<>();
        for (Slot slot : variables.values()) {
            if (slot.isVariable()) {
                varSlots.add((VariableSlot) slot);
            }
        }
        return varSlots;
    }

    /**
     * @inheritDoc
     */
    @Override
    public List<ConstantSlot> getConstantSlots() {
        List<ConstantSlot> constants = new ArrayList<>();
        for (Slot slot : variables.values()) {
            if (!slot.isVariable()) {
                constants.add((ConstantSlot) slot);
            }
        }
        return constants;
    }
}
