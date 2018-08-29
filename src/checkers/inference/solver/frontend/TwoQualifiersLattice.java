package checkers.inference.solver.frontend;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.BugInCF;

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
            Set<? extends AnnotationMirror> allTypes, AnnotationMirror top, AnnotationMirror bottom,
            int numTypes) {
        super(subType, superType, incomparableType, allTypes, top, bottom, numTypes, null, null);
    }

    @Override
    public boolean isSubtype(AnnotationMirror a1, AnnotationMirror a2) {
        if (!AnnotationUtils.containsSame(allTypes, a1) || !AnnotationUtils.containsSame(allTypes, a2)) {
            throw new BugInCF("Enconture invalid type when perform isSubtype judgement: " +
                    " all type qualifiers in this lattice are: " + allTypes +
                    " but a1 is : + " + a1 + " and a2 is: " + a2);
        }

        return AnnotationUtils.areSame(bottom, a1) || AnnotationUtils.areSame(top, a2);
    }
}
