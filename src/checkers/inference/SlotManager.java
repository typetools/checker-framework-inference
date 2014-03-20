package checkers.inference;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.AnnotatedTypeMirror;

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

    /** Store the given Variable within this SlotManager */
    void addVariable( VariableSlot variableSlot );

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
     * Return the Slot (or an equivalent Slot) that is represented by the primary annotation on atm
     * @param atm An annotated type mirror with 0 or 1 primary annotations
     * @return Null if atm.getAnnotations is empty, otherwise a slot represented by the primary annotation on atm
     */
    Slot getSlot( AnnotatedTypeMirror atm );

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
}
