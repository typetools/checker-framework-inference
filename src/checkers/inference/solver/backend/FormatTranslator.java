package checkers.inference.solver.backend;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

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
import checkers.inference.model.Serializer;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.BinaryConstraintEncoderDispatcher;
import checkers.inference.solver.backend.encoder.binary.ComparableConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.EqualityConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.InequalityConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.SubtypeConstraintEncoder;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoder;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoderDispatcher;
import checkers.inference.solver.backend.encoder.existential.ExistentialConstraintEncoder;
import checkers.inference.solver.backend.encoder.preference.PreferenceConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import org.sat4j.core.VecInt;

/**
 * Translator is responsible for encoding/decoding work for Backend.
 *
 * It encode Slot and Constraint to specific types needed by underlying solver,
 * and decode solver solution to AnnotationMirror.
 *
 * @author charleszhuochen
 *
 * @param <SlotEncodingT> encoding type for slot.
 * @param <ConstraintEncodingT> encoding type for constraint.
 * @param <SlotSolutionT> type for underlying solver's solution of a Slot
 */
public abstract class FormatTranslator<SlotEncodingT, ConstraintEncodingT, SlotSolutionT> implements Serializer<SlotEncodingT, ConstraintEncodingT> {

    protected final Lattice lattice;
    private final ConstraintVerifier verifier;

    protected SubtypeConstraintEncoder<ConstraintEncodingT> subtypeConstraintEncoder;
    protected EqualityConstraintEncoder<ConstraintEncodingT> equalityConstraintEncoder;
    protected InequalityConstraintEncoder<ConstraintEncodingT> inequalityConstraintEncoder;
    protected ComparableConstraintEncoder<ConstraintEncodingT> comparableConstraintEncoder;
    protected PreferenceConstraintEncoder<ConstraintEncodingT> preferenceConstraintEncoder;
    protected CombineConstraintEncoder<ConstraintEncodingT> combineConstraintEncoder;
    protected ExistentialConstraintEncoder<ConstraintEncodingT> existentialConstraintEncoder;

    public FormatTranslator(Lattice lattice, ConstraintVerifier verifier) {
        this.lattice = lattice;
        this.verifier = verifier;
    }

    /**Remember to call this method at the end of subclass FormatTranslator constructor(MaxSAT and LogiQL) or at the last
     * step of initializing FormatTranslator - in Z3BitVector backend, it's initSolver() method. Because the creation of
     * encoders might not only need the parameters - lattice and verifier, but also need fields depending on concrete backend
     * So after those dependant fields are initialized, call this method to finish initializing encoders*/
    protected void postInit() {
        subtypeConstraintEncoder = createSubtypeConstraintEncoder(lattice, verifier);
        equalityConstraintEncoder = createEqualityConstraintEncoder(lattice, verifier);
        inequalityConstraintEncoder = createInequalityConstraintEncoder(lattice, verifier);
        comparableConstraintEncoder = createComparableConstraintEncoder(lattice, verifier);
        preferenceConstraintEncoder = createPreferenceConstraintEncoder(lattice, verifier);
        combineConstraintEncoder = createCombineConstraintEncoder(lattice, verifier);
        existentialConstraintEncoder = createExistentialConstraintEncoder(lattice, verifier);
    }

    protected abstract SubtypeConstraintEncoder<ConstraintEncodingT> createSubtypeConstraintEncoder(Lattice lattice, ConstraintVerifier verifier);

    protected abstract EqualityConstraintEncoder<ConstraintEncodingT> createEqualityConstraintEncoder(Lattice lattice, ConstraintVerifier verifier);

    protected abstract InequalityConstraintEncoder<ConstraintEncodingT> createInequalityConstraintEncoder(Lattice lattice, ConstraintVerifier verifier);

    protected abstract ComparableConstraintEncoder<ConstraintEncodingT> createComparableConstraintEncoder(Lattice lattice, ConstraintVerifier verifier);

    protected abstract CombineConstraintEncoder<ConstraintEncodingT> createCombineConstraintEncoder(Lattice lattice, ConstraintVerifier verifier);

    protected abstract PreferenceConstraintEncoder<ConstraintEncodingT> createPreferenceConstraintEncoder(Lattice lattice, ConstraintVerifier verifier);

    protected abstract ExistentialConstraintEncoder<ConstraintEncodingT> createExistentialConstraintEncoder(Lattice lattice, ConstraintVerifier verifier);

    @Override
    public ConstraintEncodingT serialize(SubtypeConstraint constraint) {
        return BinaryConstraintEncoderDispatcher.dispatch(constraint, subtypeConstraintEncoder);
    }

    @Override
    public ConstraintEncodingT serialize(EqualityConstraint constraint) {
        return BinaryConstraintEncoderDispatcher.dispatch(constraint, equalityConstraintEncoder);
    }

    @Override
    public ConstraintEncodingT serialize(InequalityConstraint constraint) {
        return BinaryConstraintEncoderDispatcher.dispatch(constraint, inequalityConstraintEncoder);
    }

    @Override
    public ConstraintEncodingT serialize(ComparableConstraint constraint) {
        return BinaryConstraintEncoderDispatcher.dispatch(constraint, comparableConstraintEncoder);
    }

    @Override
    public ConstraintEncodingT serialize(PreferenceConstraint preferenceConstraint) {
        return preferenceConstraintEncoder.encode(preferenceConstraint);
    }

    @Override
    public ConstraintEncodingT serialize(CombineConstraint combineConstraint) {
        return CombineConstraintEncoderDispatcher.dispatch(combineConstraint, combineConstraintEncoder);
    }

    @Override
    public ConstraintEncodingT serialize(ExistentialConstraint constraint) {
        return existentialConstraintEncoder.encode(constraint);
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

    /**
     * Decode solver's solution of a Slot to an AnnotationMirror represent this solution.
     *
     * @param solution solver's solution of a Slot
     * @param processingEnvironment the process environment for creating the AnnotationMirror, if needed
     * @return AnnotationMirror represent this solution
     */
    protected abstract AnnotationMirror decodeSolution(SlotSolutionT solution, ProcessingEnvironment processingEnvironment);
}
