package checkers.inference.solver.backend.encoder;

import checkers.inference.solver.backend.encoder.binary.ComparableConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.EqualityConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.InequalityConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.SubtypeConstraintEncoder;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoder;
import checkers.inference.solver.backend.encoder.existential.ExistentialConstraintEncoder;
import checkers.inference.solver.backend.encoder.preference.PreferenceConstraintEncoder;

/**
 * Factory that creates constraint encoders.
 *
 * <p>
 * Right now, {@link ConstraintEncoderFactory} interface supports creation of these encoders:
 * <ul>
 *     <li>{@link SubtypeConstraintEncoder}</li>
 *     <li>{@link EqualityConstraintEncoder}</li>
 *     <li>{@link InequalityConstraintEncoder}</li>
 *     <li>{@link ComparableConstraintEncoder}</li>
 *     <li>{@link PreferenceConstraintEncoder}</li>
 *     <li>{@link CombineConstraintEncoder}</li>
 *     <li>{@link ExistentialConstraintEncoder}</li>
 *     <li>{@link ArithmeticConstraintEncoder}</li>
 * </ul>
 * <p>
 * User of this interface is {@link checkers.inference.solver.backend.AbstractFormatTranslator}
 * and its subclasses.
 *
 * @see checkers.inference.solver.backend.AbstractFormatTranslator
 */
public interface ConstraintEncoderFactory<ConstraintEncodingT> {

    SubtypeConstraintEncoder<ConstraintEncodingT> createSubtypeConstraintEncoder();

    EqualityConstraintEncoder<ConstraintEncodingT> createEqualityConstraintEncoder();

    InequalityConstraintEncoder<ConstraintEncodingT> createInequalityConstraintEncoder();

    ComparableConstraintEncoder<ConstraintEncodingT> createComparableConstraintEncoder();

    PreferenceConstraintEncoder<ConstraintEncodingT> createPreferenceConstraintEncoder();

    CombineConstraintEncoder<ConstraintEncodingT> createCombineConstraintEncoder();

    ExistentialConstraintEncoder<ConstraintEncodingT> createExistentialConstraintEncoder();

    ArithmeticConstraintEncoder<ConstraintEncodingT> createArithmeticConstraintEncoder();
}
