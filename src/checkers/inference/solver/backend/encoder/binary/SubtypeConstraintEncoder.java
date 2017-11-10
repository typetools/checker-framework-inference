package checkers.inference.solver.backend.encoder.binary;

import checkers.inference.util.ConstraintVerifier;

/**
 * A marker interface that all constraint encoder that supports encoding subtype constraint
 * should implement. Otherwise, your encoder will be considered not supporting encoding subtype
 * constraint and rejected by the FormatTranslator factory method
 * @see checkers.inference.solver.backend.AbstractFormatTranslator#createSubtypeConstraintEncoder(ConstraintVerifier)
 */
public interface SubtypeConstraintEncoder<ConstraintEncodingT> extends BinaryConstraintEncoder<ConstraintEncodingT> {
}
