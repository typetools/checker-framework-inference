package checkers.inference.solver.backend.encoder.implication;

import checkers.inference.model.ImplicationConstraint;

/**
 * Interface that defines operations to encode a {@link ImplicationConstraint}.
 *
 * @param <ConstraintEncodingT> solver encoding type for {@link ImplicationConstraint}
 */
public interface ImplicationConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encode(ImplicationConstraint constraint);
}
