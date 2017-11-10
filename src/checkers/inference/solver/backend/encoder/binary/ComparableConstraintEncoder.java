package checkers.inference.solver.backend.encoder.binary;

import checkers.inference.util.ConstraintVerifier;

/**
 * A marker interface that all constraint encoder that supports encoding comparable constraint
 * should implement. Otherwise, your encoder will be considered not supporting encoding comparable
 * constraint and rejected by the FormatTranslator factory method
 * @see checkers.inference.solver.backend.AbstractFormatTranslator#createComparableConstraintEncoder(ConstraintVerifier)
 */
public interface ComparableConstraintEncoder<ConstraintEncodingT> extends BinaryConstraintEncoder<ConstraintEncodingT> {
}
