package checkers.inference;

import org.checkerframework.framework.qual.Unqualified;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.util.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ErrorReporter;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;

import com.sun.tools.javac.util.Pair;

import checkers.inference.model.AnnotationLocation;
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

    //this id starts at 1 because sin ome serializer's (CnfSerializer) 0 is used as line delimiters
    //monotonically increasing id for all VariableSlots (including subtypes of VariableSlots)
    private int nextId = 1;

    //a map of variable id to variable for ALL variables (including subtypes of VariableSlots)
    private final Map<Integer, VariableSlot> variables;
    /**
     * A map of AnnotationMirror to ConstantSlot for caching ConstantSlot. Each
     * ConstantSlot is uniquely identified by an AnnotationMirror
     *
     * @key AnnotationMirror used to uniquely identify a ConstantSlot
     * @value ConstantSlot corresponding to this AnnotationMirror
     */
    private final Map<AnnotationMirror, ConstantSlot> constantCache;
    /**
     * A map of AnnotationLocation to Integer for caching VariableSlot and
     * RefinementVariableSlot. Those two kinds of slots can be uniquely
     * identified by their locations.
     *
     * @key AnnotatioinLocation used to uniquely identify a VariableSlot and
     *      RefinementVariableSlot
     * @value Slot id on this AnnotationLocation
     */
    private final Map<AnnotationLocation, Integer> locationCache;
    /**
     * A map of pair of VariableSlots to Integer for caching only
     * ExistentialSlot. Each ExistentialSlot can be uniquely identified by its
     * potential and alternative VariablesSlots
     *
     * @key pair of potential slot and alternative slot
     * @value id of ExistentialSlot corresponding to this pair of potential slot
     *        and alternative slot
     */
    private final Map<Pair<VariableSlot, VariableSlot>, Integer> existentialSlotPairCache;
    /**
     * A map of pair of Slots to Integer for caching CombVariableSlot. Each
     * combination of receiver slot and declared slot uniquely identifies a
     * CombVariableSlot
     *
     * @key pair of receiver slot and declared slot
     * @value id of the corresponding CombVariableSlot
     */
    private final Map<Pair<Slot, Slot>, Integer> combSlotPairCache;

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
        // Construct empty caches
        constantCache = AnnotationUtils.createAnnotationMap();
        locationCache = new LinkedHashMap<>();
        existentialSlotPairCache = new LinkedHashMap<>();
        combSlotPairCache = new LinkedHashMap<>();
        if (storeConstants) {
            Set<? extends AnnotationMirror> mirrors = InferenceMain.getInstance().getRealTypeFactory().getQualifierHierarchy().getTypeQualifiers();
            for (AnnotationMirror am : mirrors) {
                ConstantSlot constantSlot = new ConstantSlot(am, nextId());
                addVariable(constantSlot);
                constantCache.put(am, constantSlot);
            }
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

    private void addVariable(final VariableSlot slot) {
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

    //TODO: RENAME AND UPDATE DOCS
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

                ErrorReporter.errorAbort("Missing VarAnnot annotation: " + atm);
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
                return null; //TODO: should we instead throw an exception?
            } else {
                final AnnotationValue annoValue = annotationMirror.getElementValues().values().iterator().next();
                id = Integer.valueOf( annoValue.toString() );
            }

            return getVariable( id );

        } else {

            if (constantCache.containsKey(annotationMirror)) {
                return constantCache.get(annotationMirror);

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
        ErrorReporter.errorAbort( annotationMirror + " is a type of AnnotationMirror not handled by getVariableSlot." );
        return null; // Dead
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

    @Override
    public int getNumberOfSlots() {
        return nextId;
    }

    @Override
    public VariableSlot createVariableSlot(AnnotationLocation location) {
        VariableSlot variableSlot;
        if (locationCache.containsKey(location)) {
            int id = locationCache.get(location);
            variableSlot = variables.get(id);
        } else {
            variableSlot = new VariableSlot(location, nextId());
            addVariable(variableSlot);
            locationCache.put(location, variableSlot.getId());
        }
        return variableSlot;
    }

    @Override
    public RefinementVariableSlot createRefinementVariableSlot(AnnotationLocation location, Slot refined) {
        RefinementVariableSlot refinementVariableSlot;
        if (locationCache.containsKey(location)) {
            int id = locationCache.get(location);
            refinementVariableSlot = (RefinementVariableSlot) variables.get(id);
        } else {
            refinementVariableSlot = new RefinementVariableSlot(location, nextId(), refined);
            addVariable(refinementVariableSlot);
            locationCache.put(location, refinementVariableSlot.getId());
        }
        return refinementVariableSlot;
    }

    @Override
    public ConstantSlot createConstantSlot(AnnotationMirror value) {
        ConstantSlot constantSlot;
        if (constantCache.containsKey(value)) {
            constantSlot = constantCache.get(value);
        } else {
            constantSlot = new ConstantSlot(value, nextId());
            addVariable(constantSlot);
            constantCache.put(value, constantSlot);
        }
        return constantSlot;
    }

    @Override
    public CombVariableSlot createCombVariableSlot(Slot receiver, Slot declared) {
        CombVariableSlot combVariableSlot;
        Pair<Slot, Slot> pair = new Pair<>(receiver, declared);
        if (combSlotPairCache.containsKey(pair)) {
            int id = combSlotPairCache.get(pair);
            combVariableSlot = (CombVariableSlot) variables.get(id);
        } else {
            combVariableSlot = new CombVariableSlot(null, nextId(), receiver, declared);
            addVariable(combVariableSlot);
            combSlotPairCache.put(pair, combVariableSlot.getId());
        }
        return combVariableSlot;
    }

    @Override
    public ExistentialVariableSlot createExistentialVariableSlot(VariableSlot potentialSlot, VariableSlot alternativeSlot) {
        ExistentialVariableSlot existentialVariableSlot;
        Pair<VariableSlot, VariableSlot> pair = new Pair<>(potentialSlot, alternativeSlot);
        if (existentialSlotPairCache.containsKey(pair)) {
            int id = existentialSlotPairCache.get(pair);
            existentialVariableSlot = (ExistentialVariableSlot) variables.get(id);
        } else {
            existentialVariableSlot = new ExistentialVariableSlot(nextId(), potentialSlot, alternativeSlot);
            addVariable(existentialVariableSlot);
            existentialSlotPairCache.put(pair, existentialVariableSlot.getId());
        }
        return existentialVariableSlot;
    }

}
