package checkers.inference.solver.frontend;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.QualifierHierarchy;

/**
 * Lattice class pre-cache necessary qualifier information from qualifier hierarchy for
 * constraint constraint solving.
 * 
 * It is convenient to get all subtypes and supertypes of a specific type
 * qualifier, all type qualifier, and bottom and top qualifiers from an instance
 * of this class.
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

    /**
     * All concrete qualifiers information that collected from the program
     * CF Inference running on.
     * This field is useful for type systems that has a dynamic number
     * of type qualifiers.
     */
    public final Collection<AnnotationMirror> allAnnotations;

    /**
     * Underlying qualifier hierarchy that this lattice built based on.
     * This field is nullable, it will be null if this lattice doesn't built based on
     * a real qualifier hierarchy. (E.g. TwoQualifierLattice).
     */
    /* @Nullable */
    private final QualifierHierarchy underlyingQualifierHierarchy;

    public Lattice(Map<AnnotationMirror, Collection<AnnotationMirror>> subType,
            Map<AnnotationMirror, Collection<AnnotationMirror>> superType,
            Map<AnnotationMirror, Collection<AnnotationMirror>> incomparableType,
            Set<? extends AnnotationMirror> allTypes, AnnotationMirror top, AnnotationMirror bottom,
            int numTypes, Collection<AnnotationMirror> runtimeAMs, /* @Nullable */ QualifierHierarchy qualifierHierarchy) {
        this.subType = Collections.unmodifiableMap(subType);
        this.superType = Collections.unmodifiableMap(superType);
        this.incomparableType = Collections.unmodifiableMap(incomparableType);
        this.allTypes = Collections.unmodifiableSet(allTypes);
        this.top = top;
        this.bottom = bottom;
        this.numTypes = numTypes;
        this.underlyingQualifierHierarchy = qualifierHierarchy;
        this.allAnnotations = runtimeAMs;
    }

    public boolean isSubtype(AnnotationMirror a1, AnnotationMirror a2) {
        return underlyingQualifierHierarchy.isSubtype(a1, a2);
    }
}
