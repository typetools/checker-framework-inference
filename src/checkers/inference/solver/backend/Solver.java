package checkers.inference.solver.backend;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.SolverEnvironment;

/**
 * Solver adapts a concrete underlying solver, e.g. Sat4j, LogiQL, Z3, etc.
 * This class is the super class for all concrete Solver sub-classes.
 * For each concrete Solver, it adapts the type constraint solving process to
 * the underlying solver implementation.
 *
 * A Solver takes type constraints from {@link checkers.inference.solver.SolverEngine}},
 * then delegates solving constraints responsibility to the underlying solver, and transform
 * underlying solver solution as a map between an integer and an annotation mirror as
 * the inferred result.
 *
 * Method {@link #solve()} is responsible for coordinating
 * above steps.
 * 
 * {@link #solve()} method is the entry point of the solver adapter, and it is got
 * called in class {@link checkers.inference.solver.SolverEngine}}. See
 * {@link checkers.inference.solver.SolverEngine#solveInparall()} and
 * {@link checkers.inference.solver.SolverEngine#solveInSequential()}.
 * 
 * @author jianchu
 *
 * @param <T> type of FormatTranslator required by this Solver
 */
public abstract class Solver<T extends FormatTranslator<?, ?, ?>> {

    /**
     * SolverOptions, an argument manager for getting options from user.
     */
    protected final SolverEnvironment solverEnvironment;

    /**
     * Collection of all slots will be used by underlying solver
     */
    protected final Collection<Slot> slots;

    /**
     * Collection of all constraints will be solved by underlying solver
     */
    protected final Collection<Constraint> constraints;

    /**
     * translator for encoding inference slots and constraints to underlying solver's constraints,
     * and decoding underlying solver's solution back to AnnotationMirrors.
     */
    protected final T formatTranslator;

    /**
     * Set of ids of all variable solts will be used by underlying solver
     */
    protected final Set<Integer> varSlotIds;

    /**
     * Target qualifier lattice
     */
    protected final Lattice lattice;

    public Solver(SolverEnvironment solverEnvironment, Collection<Slot> slots,
            Collection<Constraint> constraints, T formatTranslator, Lattice lattice) {
        this.solverEnvironment = solverEnvironment;
        this.slots = slots;
        this.constraints = constraints;
        this.formatTranslator = formatTranslator;
        this.varSlotIds = new HashSet<Integer>();
        this.lattice = lattice;
    }

    /**
     * A concrete solver adapter needs to override this method and implements its own
     * constraint-solving strategy. In general, there will be three steps in this method:
     * 1. Calls {@link #convertAll()}, let {@link FormatTranslator} to convert constraints into
     * the corresponding encoding form.
     * 2. Calls the underlying solver to solve the encoding.
     * 3. Let {@link FormatTranslator} decodes the solution from the underlying solver and create a map between an 
     * Integer(Slot Id) and an AnnotationMirror as it's inferred annotation. 
     * 
     * It is the concrete solver adapter's responsibility to implemented the logic of above instructions and statistic collection. 
     * See {@link checkers.inference.solver.backend.maxsat.MaxSatSolver#solve()}} for an example.
     */
    public abstract Map<Integer, AnnotationMirror> solve();

    /**
     * Calls formatTranslator to convert constraints into the corresponding encoding
     * form. See {@link checkers.inference.solver.backend.maxsat.MaxSatSolver#convertAll()}} for an example.
     */
    protected abstract void convertAll();

    /**
     * Get slot id from variable slot.
     *
     * @param constraint
     */
    protected void collectVarSlots(Constraint constraint) {
        for (Slot slot : constraint.getSlots()) {
            if (!(slot instanceof ConstantSlot)) {
                this.varSlotIds.add(((VariableSlot) slot).getId());
            }
        }
    }
}
