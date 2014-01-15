package checkers.inference;

import checkers.inference.model.*;
import checkers.inference.quals.CombVarAnnot;
import checkers.inference.quals.RefineVarAnnot;
import checkers.inference.quals.VarAnnot;
import checkers.types.AnnotatedTypeMirror;
import checkers.util.AnnotationBuilder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * The default implementation of SlotManager.
 * @see checkers.inference.SlotManager
 */
public class DefaultSlotManager implements SlotManager {

    //monotonically increasing id for all VariableSlots (including subtypes of VariableSlots)
    private int nextId = 0;

    //a map of variable id to variable for ALL variables ( including subtypes of VariableSlots )
    private final Map<Integer, VariableSlot> variables;

    //used to create annotations for the different annotation types
    private final AnnotationBuilder varBuilder;
    private final AnnotationBuilder refVarBuilder;
    private final AnnotationBuilder combVarBuilder;
    private final Set<Class<? extends Annotation>> realQualifiers;

    public DefaultSlotManager( final ProcessingEnvironment processingEnvironment,
                               final Set<Class<? extends Annotation>> realQualifiers ) {
        this.varBuilder     = new AnnotationBuilder( processingEnvironment, VarAnnot.class );
        this.refVarBuilder  = new AnnotationBuilder( processingEnvironment, RefineVarAnnot.class );
        this.combVarBuilder = new AnnotationBuilder( processingEnvironment, CombVarAnnot.class );
        this.realQualifiers = realQualifiers;
        variables = new LinkedHashMap<>();
    }

    /**
     * @inheritDoc
     */
    public int nextId() {
        return nextId++;
    }

    /**
     * @inheritDoc
     */
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

        if( slotClass.equals( RefinementVariableSlot.class ) ) {
            return convertVariable( (VariableSlot) slot, refVarBuilder );
        }

        if( slotClass.equals( CombVariableSlot.class ) ) {
            return convertVariable( (VariableSlot) slot, combVarBuilder );
        }

        if( slotClass.equals( VariableSlot.class ) ) {
            return convertVariable( (VariableSlot) slot, varBuilder );
        }

        if( slotClass.equals( ConstantSlot.class ) ) {
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

    /**
     * @inheritDoc
     */
    @Override
    public Slot getSlot( final AnnotatedTypeMirror atm ) {

        final Set<AnnotationMirror> annos = atm.getAnnotations();
        assert annos.size() <= 1 : "Too many annotations on type: " + atm;

        if( annos.isEmpty() ) {
            return null;
        }

        return getSlot( annos.iterator().next() );
    }

    /**
     * @inheritDoc
     */
    @Override
    public Slot getSlot( final AnnotationMirror annotationMirror ) {

        final String annoName = annotationMirror.getClass().getName().toString();

        final int id;
        if( annoName.equals( VarAnnot.class.getName()       ) ||
            annoName.equals( CombVarAnnot.class.getName()   ) ||
            annoName.equals( RefineVarAnnot.class.getName() ) ) {
            if(annotationMirror.getElementValues().isEmpty() ) {
                return null; //TODO: should we instead throw an exception?
            } else {
                final AnnotationValue annoValue = annotationMirror.getElementValues().values().iterator().next();
                id = Integer.valueOf( annoValue.toString() );
            }

            return getVariable( id );

        } else {
            for( Class<? extends Annotation> realAnno : realQualifiers ) {
                if( annoName.equals( realAnno.getClass().getName() ) ) {
                    return new ConstantSlot( annotationMirror );
                }
            }
        }

        throw new RuntimeException( annoName + " is a type of AnnotationMirror not handled by getSlot." );
    }

    /**
     * @inheritDoc
     */
    @Override
    public List<Slot> getSlots() {
        return new ArrayList<Slot>( this.variables.values() );
    }

}
