package checkers.inference.solver.backend.encoder.binary;

import checkers.inference.solver.backend.encoder.AbstractConstraintEncoderFactory;

/**
 * A marker interface that all constraint encoders that support encoding {@link checkers.inference.model.InequalityConstraint}
 * should implement. Otherwise, the encoder will be considered not supporting encoding inequality constraint and rejected by the
 * {@link AbstractConstraintEncoderFactory#createInequalityConstraintEncoder()}
 *
 * @see checkers.inference.model.InequalityConstraint
 * @see AbstractConstraintEncoderFactory#createInequalityConstraintEncoder()
 */
public interface InequalityConstraintEncoder<ConstraintEncodingT> extends BinaryConstraintEncoder<ConstraintEncodingT> {
}
