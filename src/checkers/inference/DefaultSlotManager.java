package checkers.inference;

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

import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ErrorReporter;

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

    private final AnnotationMirror varAnnot;

    //this id starts at 1 because sin ome serializer's (CnfSerializer) 0 is used as line delimiters
    //monotonically increasing id for all VariableSlots (including subtypes of VariableSlots)
    private int nextId = 1;

    /**
     * A map for storing all the slots encountered by this slot manager. Key is
     * an {@link Integer}, representing a slot id. Value is a
     * {@link VariableSlot} that corresponds to this slot id. Note that
     * ConstantSlots are also stored in this map, since ConstantSlot is subclass
     * of VariableSlot.
     */
    private final Map<Integer, VariableSlot> variables;

    /**
     * A map of {@link AnnotationMirror} to {@link Integer} for caching
     * ConstantSlot. Each {@link AnnotationMirror} uniquely identify a
     * ConstantSlot. {@link Integer} is the id of the corresponding ConstantSlot
     */
    private final Map<AnnotationMirror, Integer> constantCache;

    /**
     * A map of {@link AnnotationLocation} to {@link Integer} for caching
     * VariableSlot and RefinementVariableSlot. Those two kinds of slots can be
     * uniquely identified by their {@link AnnotationLocation}(Except MissingLocation).
     * {@link Integer} is the id of the corresponding VariableSlot or RefinementVariableSlot
     */
    private final Map<AnnotationLocation, Integer> locationCache;

    /**
     * A map of {@link Pair} of {@link VariableSlot} to {@link Integer} for
     * caching ExistentialVariableSlot. Each ExistentialVariableSlot can be
     * uniquely identified by its potential and alternative VariablesSlots.
     * {@link Integer} is the id of the corresponding ExistentialVariableSlot
     */
    private final Map<Pair<VariableSlot, VariableSlot>, Integer> existentialSlotPairCache;

    /**
     * A map of {@link Pair} of {@link Slot} to {@link Integer} for caching
     * CombVariableSlot. Each combination of receiver slot and declared slot
     * uniquely identifies a CombVariableSlot. {@link Integer} is the id of the
     * corresponding CombVariableSlott
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

        // Construct empty caches
        constantCache = AnnotationUtils.createAnnotationMap();
        locationCache = new LinkedHashMap<>();
        existentialSlotPairCache = new LinkedHashMap<>();
        combSlotPairCache = new LinkedHashMap<>();
        if (storeConstants) {
            Set<? extends AnnotationMirror> mirrors = InferenceMain.getInstance().getRealTypeFactory().getQualifierHierarchy().getTypeQualifiers();
            for (AnnotationMirror am : mirrors) {
                ConstantSlot constantSlot = new ConstantSlot(am, nextId());
                addToVariables(constantSlot);
                constantCache.put(am, constantSlot.getId());
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
     * Returns the next unique variable id.  These id's are monotonically increasing.
     * @return the next variable id to be used in VariableCreation
     */
    private int nextId() {
        return nextId++;
    }

    private void addToVariables(final VariableSlot slot) {
        variables.put(slot.getId(), slot);
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
            if (InferenceMain.isHackMode()) {
                return null;
            }

            ErrorReporter.errorAbort("Missing VarAnnot annotation: " + atm);
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
                ConstantSlot constantSlot = (ConstantSlot) getVariable(
                        constantCache.get(annotationMirror));
                return constantSlot;

            } else {
                for (Class<? extends Annotation> realAnno : realQualifiers) {
                    if (AnnotationUtils.areSameByClass(annotationMirror, realAnno)) {
                        return createConstantSlot(annotationMirror);
                    }
                }
            }
        }

        if (InferenceMain.isHackMode()) {
            return createConstantSlot(InferenceMain.getInstance().getRealTypeFactory().
                    getQualifierHierarchy().getTopAnnotations().iterator().next());
        }
        ErrorReporter.errorAbort( annotationMirror + " is a type of AnnotationMirror not handled by getVariableSlot." );
        return null; // Dead
    }

    /**
     * @inheritDoc
     */
    @Override
    public List<Slot> getSlots() {
        return new ArrayList<Slot>(this.variables.values());
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
        return nextId - 1;
    }

    @Override
    public VariableSlot createVariableSlot(AnnotationLocation location) {
        VariableSlot variableSlot;
        if (location.getKind() == AnnotationLocation.Kind.MISSING) {
            //Don't cache slot for MISSING LOCATION. Just create a new one and return.
            variableSlot = new VariableSlot(location, nextId());
            addToVariables(variableSlot);
        } else if (locationCache.containsKey(location)) {
            int id = locationCache.get(location);
            variableSlot = getVariable(id);
        } else {
            variableSlot = new VariableSlot(location, nextId());
            addToVariables(variableSlot);
            locationCache.put(location, variableSlot.getId());
        }
        return variableSlot;
    }

    @Override
    public RefinementVariableSlot createRefinementVariableSlot(AnnotationLocation location, Slot refined) {
        RefinementVariableSlot refinementVariableSlot;
        if (location.getKind() == AnnotationLocation.Kind.MISSING) {
            //Don't cache slot for MISSING LOCATION. Just create a new one and return.
            refinementVariableSlot = new RefinementVariableSlot(location, nextId(), refined);
            addToVariables(refinementVariableSlot);
        } else if (locationCache.containsKey(location)) {
            int id = locationCache.get(location);
            refinementVariableSlot = (RefinementVariableSlot) getVariable(id);
        } else {
            refinementVariableSlot = new RefinementVariableSlot(location, nextId(), refined);
            addToVariables(refinementVariableSlot);
            locationCache.put(location, refinementVariableSlot.getId());
        }
        return refinementVariableSlot;
    }

    @Override
    public ConstantSlot createConstantSlot(AnnotationMirror value) {
        ConstantSlot constantSlot;
        if (constantCache.containsKey(value)) {
            int id = constantCache.get(value);
            constantSlot = (ConstantSlot) getVariable(id);
        } else {
            constantSlot = new ConstantSlot(value, nextId());
            addToVariables(constantSlot);
            constantCache.put(value, constantSlot.getId());
        }
        return constantSlot;
    }

    @Override
    public CombVariableSlot createCombVariableSlot(Slot receiver, Slot declared) {
        CombVariableSlot combVariableSlot;
        Pair<Slot, Slot> pair = new Pair<>(receiver, declared);
        if (combSlotPairCache.containsKey(pair)) {
            int id = combSlotPairCache.get(pair);
            combVariableSlot = (CombVariableSlot) getVariable(id);
        } else {
            combVariableSlot = new CombVariableSlot(null, nextId(), receiver, declared);
            addToVariables(combVariableSlot);
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
            existentialVariableSlot = (ExistentialVariableSlot) getVariable(id);
        } else {
            existentialVariableSlot = new ExistentialVariableSlot(nextId(), potentialSlot, alternativeSlot);
            addToVariables(existentialVariableSlot);
            existentialSlotPairCache.put(pair, existentialVariableSlot.getId());
        }
        return existentialVariableSlot;
    }

}
