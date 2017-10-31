package checkers.inference.solver.backend;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.QualifierHierarchy;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.Serializer;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.frontend.Lattice;

/**
 * BackEnd class is the super class for all concrete back ends. Type constraint
 * solving process happens inside this class. A back end takes type constraints
 * from {@link checkers.inference.solver.GeneralSolver}}, solves the
 * constraints, and return a map between an integer and a annotation mirror as
 * the inferred result. Method {@link #solve()} is responsible for coordinating
 * above steps.
 * 
 * {@link #solve()} method is the entry point of the back end, and it is got
 * called in class {@link checkers.inference.solver.GeneralSolver}}. See
 * {@link checkers.inference.solver.GeneralSolver#solveInparall()} and
 * {@link checkers.inference.solver.GeneralSolver#solveInSequential()}.
 * 
 * @author jianchu
 *
 * @param <S> Encoding type for slot.
 * @param <T> Encdoing type for constraint.
 */
public abstract class BackEnd<S, T> {

    protected final Map<String, String> configuration;
    protected final Collection<Slot> slots;
    protected final Collection<Constraint> constraints;
    protected final QualifierHierarchy qualHierarchy;
    protected final ProcessingEnvironment processingEnvironment;
    protected final Serializer<S, T> realSerializer;
    protected final Set<Integer> varSlotIds;
    protected final Lattice lattice;

    public BackEnd(Map<String, String> configuration, Collection<Slot> slots,
            Collection<Constraint> constraints, QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment, Serializer<S, T> realSerializer, Lattice lattice) {
        this.configuration = configuration;
        this.slots = slots;
        this.constraints = constraints;
        this.qualHierarchy = qualHierarchy;
        this.processingEnvironment = processingEnvironment;
        this.realSerializer = realSerializer;
        this.varSlotIds = new HashSet<Integer>();
        this.lattice = lattice;
    }

    /**
     * A concrete back end needs to override this method and implements its own
     * constraint-solving strategy. In general, there will be three steps in this method:
     * 1. Calls {@link #convertAll()} to convert constraints into
     * the corresponding encoding form.
     * 2. Calls an external solver to solve the encoding.
     * 3. Decodes the solution from the external solver and create a map between an 
     * Integer(Slot Id) and an AnnotationMirror as it's inferred annotation. 
     * 
     * It is the concrete back end's responsibility to implemented the logic of above instructions and statistic collection. 
     * See {@link checkers.inference.solver.backend.maxsatbackend.MaxSatBackEnd#solve()}} for an example.
     */
    public abstract Map<Integer, AnnotationMirror> solve();

    /**
     * Calls serializer to convert constraints into the corresponding encoding
     * form. See {@link checkers.inference.solver.backend.maxsatbackend.MaxSatBackEnd#convertAll()}} for an example.
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
