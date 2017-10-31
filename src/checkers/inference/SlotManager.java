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
     * Return number of slots collected by this SlotManager
     *
     * @return number of slots collected by this SlotManager
     */
    int getNumberOfSlots();

    /**
     * Create new VariableSlot and return the reference to it if no VariableSlot
     * on this location exists. Otherwise return the reference to existing
     * VariableSlot on this location. Each location uniquely identifies a
     * VariableSlot
     *
     * @param location
     *            used to locate this variable in code
     * @return VariableSlot that corresponds to this location
     */
    VariableSlot createVariableSlot(AnnotationLocation location);

    /**
     * Create new RefinementVariableSlot and return the reference to it if no
     * RefinementVariableSlot on this location exists. Otherwise return the
     * reference to existing RefinementVariableSlot on this location. Each
     * location uniquely identifies a RefinementVariableSlot
     *
     * @param location
     *            used to locate this variable in code.
     * @param refined
     *            a potential downward refinement of an existing VariableSlot
     * @return RefinementVariableSlot that corresponds to this location
     */
    RefinementVariableSlot createRefinementVariableSlot(AnnotationLocation location, Slot refined);

    /**
     * Create new ConstrantSlot and returns the reference to it if no
     * ConstantSlot representing this AnnotationMirror exists. Otherwise, return
     * the reference to existing ConstantSlot. An AnnotationMirror uniquely
     * identifies a ConstantSlot
     *
     * @param value
     *            The actual AnnotationMirror that this ConstantSlot represents.
     *            This AnnotationMirror should be valid within the type system
     *            for which we are inferring values.
     * @return the ConstantSlot that represents this AnnotationMirror
     */
    ConstantSlot createConstantSlot(AnnotationMirror value);

    /**
     * Create new CombVariableSlot using receiver slot and declared slot, and
     * return reference to it if no CombVariableSlot representing result of
     * adapting declared slot to receiver slot exists. Otherwise, returns the
     * existing CombVariableSlot. Receiver slot and declared slot can uniquely
     * identify a CombVariableSlot
     *
     * @param receiver
     *            receiver slot
     * @param declared
     *            declared slot
     * @return CombVariableSlot that represents the viewpoint adaptation result
     *         of adapting declared slot to receiver slot
     */
    CombVariableSlot createCombVariableSlot(Slot receiver, Slot declared);

    /**
     * Create new ExistentialVariableSlot using potential slot and alternative
     * slot, and return reference to it if no ExistentialVariableSlot that wraps
     * this potentialSlot and alternativeSlot exists. Otherwise, returns the
     * existing ExistentialVariableSlot. Potential slot and alternative slot can
     * uniquely identify an ExistentialVariableSlot
     *
     * @param potentialSlot
     *            a variable whose annotation may or may not exist in source
     *            code
     * @param alternativeSlot
     *            the variable which would take part in a constraint if
     *            potentialSlot does not exist
     * @return the ExistentialVariableSlot that wraps this potentialSlot and
     *         alternativeSlot
     */
    ExistentialVariableSlot createExistentialVariableSlot(VariableSlot potentialSlot, VariableSlot alternativeSlot);

    /** Return the variable identified by the given id or null if no such variable has been added */
    VariableSlot getVariable( int id );

    /**
     * Given a slot return an annotation that represents the slot when added to an AnnotatedTypeMirror.
     * If A is the annotation returned by getAnnotation( S ) where is a slot.  Then getSlot( A ) will
     * return S (or an equivalent Slot in case of Constants ).
     * For {@code ConstantSlot}, this method should return the {@code VariableAnnotation} that represents
     * the underlying constant, and one should use {@link ConstantSlot#getValue()} to get the real annotation.
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
