package checkers.inference.solver.backend;

import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.CombineConstraint;
import checkers.inference.model.ComparableConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.ExistentialConstraint;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.InequalityConstraint;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.ConstraintEncoderCoordinator;
import checkers.inference.solver.backend.encoder.ConstraintEncoderFactory;
import checkers.inference.solver.backend.encoder.binary.ComparableConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.EqualityConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.InequalityConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.SubtypeConstraintEncoder;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoder;
import checkers.inference.solver.backend.encoder.existential.ExistentialConstraintEncoder;
import checkers.inference.solver.backend.encoder.preference.PreferenceConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;

/**
 * Abstract base class for all concrete {@link FormatTranslator}.
 *
 * Class {@code AbstractFormatTranslator} provides default implementation for both serializing
 * {@link checkers.inference.model.Slot slot} and {@link checkers.inference.model.Constraint constraint}:
 * <p>
 * {@link checkers.inference.model.Slot Slot} serialization methods does nothing but returns null.
 * Subclasses of {@code AbstractFormatTranslator} should override corresponding {@code Slot}
 * serialization methods if subclasses have concrete serialization logic.
 * <p>
 * {@link checkers.inference.model.Constraint Constraint} serialization methods first check
 * whether corresponding encoder is null. If yes, returns null as the encoding otherwise, delegates
 * encoding job to that encoder.
 * <p>
 * Usually, subclasses of {@code AbstractFormatTranslator} only need to override method
 * {@link #createConstraintEncoderFactory(ConstraintVerifier)} to provide the concrete {@code
 * ConstraintEncoderFactory} and the initialization of encoders are automatically done in this class.
 * So subclasses don't need to care about initializing encoders in their scope. The only exception
 * is {@link checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator Z3BitVectorFormatTranslator}.
 * It's {@code Z3BitVectorFormatTranslator} and all its subclasses's responsibility to initialize encoders
 * at the correct timing in its own constructor. See
 * {@link checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator Z3BitVectorFormatTranslator}
 * for the reason.
 *
 * @see ConstraintEncoderFactory
 */
public abstract class AbstractFormatTranslator<SlotEncodingT, ConstraintEncodingT, SlotSolutionT>
        implements FormatTranslator<SlotEncodingT, ConstraintEncodingT, SlotSolutionT>{

    protected final Lattice lattice;

    /**
     * {code ConstraintEncoderFactory} that creates constraint encoders
     */
    protected final ConstraintEncoderFactory<ConstraintEncodingT> encoderFactory;

    /**
     * {@code SubtypeConstraintEncoder} to which encoding of {@link SubtypeConstraint} is delegated.
     */
    protected SubtypeConstraintEncoder<ConstraintEncodingT> subtypeConstraintEncoder;

    /**
     * {@code EqualityConstraintEncoder} to which encoding of {@link EqualityConstraint} is delegated.
     */
    protected EqualityConstraintEncoder<ConstraintEncodingT> equalityConstraintEncoder;

    /**
     * {@code InequalityConstraintEncoder} to which encoding of {@link InequalityConstraint} is delegated.
     */
    protected InequalityConstraintEncoder<ConstraintEncodingT> inequalityConstraintEncoder;

    /**
     * {@code ComparableConstraintEncoder} to which encoding of {@link ComparableConstraint} is delegated.
     */
    protected ComparableConstraintEncoder<ConstraintEncodingT> comparableConstraintEncoder;

    /**
     * {@code PreferenceConstraintEncoder} to which encoding of {@link PreferenceConstraint} is delegated.
     */
    protected PreferenceConstraintEncoder<ConstraintEncodingT> preferenceConstraintEncoder;

    /**
     * {@code CombineConstraintEncoder} to which encoding of {@link CombineConstraint} is delegated.
     */
    protected CombineConstraintEncoder<ConstraintEncodingT> combineConstraintEncoder;

    /**
     * {@code ExistentialConstraintEncoder} to which encoding of {@link ExistentialConstraint} is delegated.
     */
    protected ExistentialConstraintEncoder<ConstraintEncodingT> existentialConstraintEncoder;

    public AbstractFormatTranslator(Lattice lattice, ConstraintVerifier verifier) {
        this.lattice = lattice;
        encoderFactory = createConstraintEncoderFactory(verifier);
        subtypeConstraintEncoder = encoderFactory.createSubtypeConstraintEncoder();
        equalityConstraintEncoder = encoderFactory.createEqualityConstraintEncoder();
        inequalityConstraintEncoder = encoderFactory.createInequalityConstraintEncoder();
        comparableConstraintEncoder = encoderFactory.createComparableConstraintEncoder();
        preferenceConstraintEncoder = encoderFactory.createPreferenceConstraintEncoder();
        combineConstraintEncoder = encoderFactory.createCombineConstraintEncoder();
        existentialConstraintEncoder = encoderFactory.createExistentialConstraintEncoder();
    }

    /**
     * Creates concrete implementation of {@link ConstraintEncoderFactory}. Subclasses should implement this method
     * to provide their concrete {@code ConstraintEncoderFactory}.
     *
     * @param verifier {@link ConstraintVerifier} to pass to {@code ConstraintEncoderFactory}
     * @return Concrete implementation of {@link ConstraintEncoderFactory} for a particular {@link SolverType}
     */
    protected abstract ConstraintEncoderFactory<ConstraintEncodingT> createConstraintEncoderFactory(ConstraintVerifier verifier);

    @Override
    public ConstraintEncodingT serialize(SubtypeConstraint constraint) {
        return subtypeConstraintEncoder == null ? null :
                ConstraintEncoderCoordinator.dispatch(constraint, subtypeConstraintEncoder);
    }

    @Override
    public ConstraintEncodingT serialize(EqualityConstraint constraint) {
        return equalityConstraintEncoder == null ? null :
                ConstraintEncoderCoordinator.dispatch(constraint, equalityConstraintEncoder);
    }

    @Override
    public ConstraintEncodingT serialize(InequalityConstraint constraint) {
        return inequalityConstraintEncoder == null ? null :
                ConstraintEncoderCoordinator.dispatch(constraint, inequalityConstraintEncoder);
    }

    @Override
    public ConstraintEncodingT serialize(ComparableConstraint constraint) {
        return comparableConstraintEncoder == null ? null :
                ConstraintEncoderCoordinator.dispatch(constraint, comparableConstraintEncoder);
    }

    @Override
    public ConstraintEncodingT serialize(PreferenceConstraint constraint) {
        return constraint == null ? null :
                ConstraintEncoderCoordinator.redirect(constraint, preferenceConstraintEncoder);
    }

    @Override
    public ConstraintEncodingT serialize(CombineConstraint combineConstraint) {
        return comparableConstraintEncoder == null ? null :
                ConstraintEncoderCoordinator.dispatch(combineConstraint, combineConstraintEncoder);
    }

    @Override
    public ConstraintEncodingT serialize(ExistentialConstraint constraint) {
        return existentialConstraintEncoder == null ? null :
                ConstraintEncoderCoordinator.redirect(constraint, existentialConstraintEncoder);
    }

    @Override
    public SlotEncodingT serialize(VariableSlot slot) {
        return null;
    }

    @Override
    public SlotEncodingT serialize(ConstantSlot slot) {
        return null;
    }

    @Override
    public SlotEncodingT serialize(ExistentialVariableSlot slot) {
        return null;
    }

    @Override
    public SlotEncodingT serialize(RefinementVariableSlot slot) {
        return null;
    }

    @Override
    public SlotEncodingT serialize(CombVariableSlot slot) {
        return null;
    }
}
