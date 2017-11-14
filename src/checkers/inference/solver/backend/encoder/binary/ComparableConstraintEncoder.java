package checkers.inference.solver.backend.encoder.binary;

import checkers.inference.solver.backend.encoder.AbstractConstraintEncoderFactory;

/**
 * A marker interface that all constraint encoders that support encoding {@link checkers.inference.model.ComparableConstraint}
 * should implement. Otherwise, the encoder will be considered not supporting encoding comparable
 * constraint and rejected by the {@link AbstractConstraintEncoderFactory#createComparableConstraintEncoder()}
 *
 * @see checkers.inference.model.ComparableConstraint
 * @see AbstractConstraintEncoderFactory#createComparableConstraintEncoder()
 */
public interface ComparableConstraintEncoder<ConstraintEncodingT> extends BinaryConstraintEncoder<ConstraintEncodingT> {
}
