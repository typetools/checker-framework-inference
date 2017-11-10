package checkers.inference.solver.backend.encoder.binary;

import checkers.inference.util.ConstraintVerifier;

/**
 * A marker interface that all constraint encoder that supports encoding equality constraint
 * should implement. Otherwise, your encoder will be considered not supporting encoding equality
 * constraint and rejected by the FormatTranslator factory method
 * @see checkers.inference.solver.backend.AbstractFormatTranslator#createEqualityConstraintEncoder(ConstraintVerifier)
 */
public interface EqualityConstraintEncoder<ConstraintEncodingT> extends BinaryConstraintEncoder<ConstraintEncodingT> {
}
