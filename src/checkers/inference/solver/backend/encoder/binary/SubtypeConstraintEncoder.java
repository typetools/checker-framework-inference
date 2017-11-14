package checkers.inference.solver.backend.encoder.binary;

import checkers.inference.solver.backend.encoder.AbstractConstraintEncoderFactory;

/**
 * A marker interface that all constraint encoders that support encoding {@link checkers.inference.model.SubtypeConstraint}
 * should implement. Otherwise, the encoder will be considered not supporting encoding subtype constraint and rejected by the
 * {@link AbstractConstraintEncoderFactory#createSubtypeConstraintEncoder()}
 *
 * @see checkers.inference.model.SubtypeConstraint
 * @see AbstractConstraintEncoderFactory#createSubtypeConstraintEncoder()
 */
public interface SubtypeConstraintEncoder<ConstraintEncodingT> extends BinaryConstraintEncoder<ConstraintEncodingT> {
}
