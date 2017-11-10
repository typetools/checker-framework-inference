package checkers.inference.solver.backend.encoder.binary;

import checkers.inference.util.ConstraintVerifier;

/**
 * A marker interface that all constraint encoder that supports encoding inequality constraint
 * should implement. Otherwise, your encoder will be considered not supporting encoding inequality
 * constraint and rejected by the FormatTranslator factory method
 * @see checkers.inference.solver.backend.AbstractFormatTranslator#createInequalityConstraintEncoder(ConstraintVerifier)
 */
public interface InequalityConstraintEncoder<ConstraintEncodingT> extends BinaryConstraintEncoder<ConstraintEncodingT> {
}
