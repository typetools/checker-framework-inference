package checkers.inference.solver.frontend;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

/**
 * Lattice class contains necessary information about qualifier hierarchy for
 * constraint solving.
 * 
 * It is convenient to get all subtypes and supertypes of a specific type
 * qualifier, all type qualifier, and bottom and top qualifiers from an instance
 * of this class. In some back ends, for example, Max-SAT back end, a
 * relationship between a type qualifier and an integer number is used in
 * serialization stage. See
 * {@link checkers.inference.solver.backend.maxsatbackend.MaxSatSerializer}}
 * 
 * @author jianchu
 *
 */
public class Lattice {

    /**
     * subType maps each type qualifier to its sub types.
     */
    public final Map<AnnotationMirror, Collection<AnnotationMirror>> subType;

    /**
     * superType maps each type qualifier to its super types.
     */
    public final Map<AnnotationMirror, Collection<AnnotationMirror>> superType;

    /**
     * incomparableType maps each type qualifier to its incomparable types.
     */
    public final Map<AnnotationMirror, Collection<AnnotationMirror>> incomparableType;

    /**
     * typeToInt maps each type qualifier to an unique integer value starts from
     * 0 on continuous basis.
     */
    public final Map<AnnotationMirror, Integer> typeToInt;

    /**
     * intToType maps an integer value to each type qualifier, which is a
     * reversed map of typeToInt.
     */
    public final Map<Integer, AnnotationMirror> intToType;

    /**
     * All type qualifiers in underling type system.
     */
    public final Set<? extends AnnotationMirror> allTypes;

    /**
     * Top qualifier of underling type system.
     */
    public final AnnotationMirror top;

    /**
     * Bottom type qualifier of underling type system.
     */
    public final AnnotationMirror bottom;

    /**
     * Number of type qualifier in underling type system.
     */
    public final int numTypes;

    public Lattice(Map<AnnotationMirror, Collection<AnnotationMirror>> subType,
            Map<AnnotationMirror, Collection<AnnotationMirror>> superType,
            Map<AnnotationMirror, Collection<AnnotationMirror>> incomparableType,
            Map<AnnotationMirror, Integer> typeToInt, Map<Integer, AnnotationMirror> intToType,
            Set<? extends AnnotationMirror> allTypes, AnnotationMirror top, AnnotationMirror bottom,
            int numTypes) {
        this.subType = Collections.unmodifiableMap(subType);
        this.superType = Collections.unmodifiableMap(superType);
        this.incomparableType = Collections.unmodifiableMap(incomparableType);
        this.typeToInt = Collections.unmodifiableMap(typeToInt);
        this.intToType = Collections.unmodifiableMap(intToType);
        this.allTypes = Collections.unmodifiableSet(allTypes);
        this.top = top;
        this.bottom = bottom;
        this.numTypes = numTypes;
    }
}
