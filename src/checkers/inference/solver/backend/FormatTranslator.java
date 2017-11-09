package checkers.inference.solver.backend;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import checkers.inference.model.Serializer;

/**
 * Translator is responsible for encoding/decoding work for Backend.
 *
 * It encode Slot and Constraint to specific types needed by underlying solver,
 * and decode solver solution to AnnotationMirror.
 *
 * @author charleszhuochen
 *
 * @param <SlotEncodingT> encoding type for slot.
 * @param <ConstraintEncodingT> encoding type for constraint.
 * @param <SlotSolutionT> type for underlying solver's solution of a Slot
 */
public interface FormatTranslator<SlotEncodingT, ConstraintEncodingT, SlotSolutionT> extends Serializer<SlotEncodingT, ConstraintEncodingT> {

    /**
     * Decode solver's solution of a Slot to an AnnotationMirror represent this solution.
     *
     * @param solution solver's solution of a Slot
     * @param processingEnvironment the process environment for creating the AnnotationMirror, if needed
     * @return AnnotationMirror represent this solution
     */
    AnnotationMirror decodeSolution(SlotSolutionT solution, ProcessingEnvironment processingEnvironment);
}
