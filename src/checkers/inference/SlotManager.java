package checkers.inference;

import org.checkerframework.framework.type.AnnotatedTypeMirror;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.model.AnnotationLocation;
import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;

/**
 * SlotManager stores variables for later access, provides ids for creating variables and
 * provides helper method for converting back and forth between Slots and the AnnotationMirrors
 * that represent them.
 */
public interface SlotManager {

    /**
     * Returns the next unique variable id.  These id's are monotonically increasing.
     * @return the next variable id to be used in VariableCreation
     */
    int nextId();

    /**
     *
     * @return total number of slots cached in SlotManager
     */
    int getNumberOfSlots();
    /**
     * New APIs of adding slots in this SlotManager If the slot is not in the
     * cache of SlotManager, the following five method create new Slots and
     * return them. But if the target slots are already in the cache, calling
     * these methods simply returns them without creating them again.
     */
    /**
     * @param location used to locate this variable in code
     * @return VariableSlot which corresponds to this location, Implementation
     * of this method might create a new VariableSlot if it doesn't exist, or
     * return the existing one from the cache. Same logic for the following five
     * addXXXSlot methods
     */
    VariableSlot addVariableSlot(AnnotationLocation location);

    /**
     * @param location used to locate this variable in code.
     * @param refined a potential downward refinement of an existing VariableSlot
     * @return the RefinementVariableSlot newly created or that from cache
     */
    RefinementVariableSlot addRefinementVariableSlot(AnnotationLocation location, Slot refined);

    /**
     * @param value The actual AnnotationMirror that this ConstantSlot
     * represents. This AnnotationMirror should be valid within the type system
     * for which we are inferring values.
     * @return the ConstantSlot newly created or that from cache
     */
    ConstantSlot addConstantSlot(AnnotationMirror value);

    /**
     * @param first receiver slot
     * @param second declared slot
     * @return CombVariableSlot newly created or that from cache
     */
    CombVariableSlot addCombVariableSlot(Slot first, Slot second);

    /**
     * @param potentialSlot a variable whose annotation may or may not exist in
     * source code
     * @param alternativeSlot the variable which would take part in a constraint
     * if potentialSlot does not exist
     * @return the ExistentialVariableSlot newly created or that from cache
     */
    ExistentialVariableSlot addExistentialVariableSlot(VariableSlot potentialSlot, VariableSlot alternativeSlot);



    /** Return the variable identified by the given id or null if no such variable has been added */
    VariableSlot getVariable( int id );

    /**
     * Given a slot return an annotation that represents the slot when added to an AnnotatedTypeMirror.
     * If A is the annotation returned by getAnnotation( S ) where is a slot.  Then getSlot( A ) will
     * return S (or an equivalent Slot in case of Constants ).
     * @param slot A slot to convert to an annotation
     * @return An annotation representing the slot
     */
    AnnotationMirror getAnnotation( Slot slot );

    /**
     * Return the Slot (or an equivalent Slot) that is represented by the given AnnotationMirror.  A RuntimeException
     * is thrown if the annotation isn't a VarAnnot, RefVarAnnot, CombVarAnnot or a member of one of the
     * REAL_QUALIFIER set provided by InferenceChecker.
     * @param am The annotationMirror representing a Slot
     * @return The Slot (on an equivalent Slot) represented by annotationMirror
     */
    Slot getSlot( AnnotationMirror am );

    /**
     * Return the VariableSlot in the primary annotation location of annotated type mirror.  If
     * there is no VariableSlot this method throws an exception
     * @param atm An annotated type mirror with a VarAnnot in its primary annotations list
     */
    VariableSlot getVariableSlot(AnnotatedTypeMirror atm);

    /**
     * Return all slots collected by this SlotManager
     * @return a list of slots
     */
    List<Slot> getSlots();

    /**
     * Return all VariableSlots collected by this SlotManager
     * @return a lit of VariableSlots
     */
    List<VariableSlot> getVariableSlots();

    List<ConstantSlot> getConstantSlots();
}
