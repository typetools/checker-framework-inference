package checkers.inference.solver.frontend;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

/**
 * Special Lattice class for two qualifier type system.
 * 
 * @author jianchu
 *
 */
public class TwoQualifiersLattice extends Lattice {

    public TwoQualifiersLattice(Map<AnnotationMirror, Collection<AnnotationMirror>> subType,
            Map<AnnotationMirror, Collection<AnnotationMirror>> superType,
            Map<AnnotationMirror, Collection<AnnotationMirror>> incomparableType,
            Map<AnnotationMirror, Integer> typeToInt, Map<Integer, AnnotationMirror> intToType,
            Set<? extends AnnotationMirror> allTypes, AnnotationMirror top, AnnotationMirror bottom,
            int numTypes) {
        super(subType, superType, incomparableType, typeToInt, intToType, allTypes, top, bottom,
                numTypes);
    }
}
