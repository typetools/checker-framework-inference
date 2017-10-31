package checkers.inference.solver.frontend;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.AnnotationUtils;

public class LatticeBuilder {

    /**
     * subType maps each type qualifier to its sub types.
     */
    private final Map<AnnotationMirror, Collection<AnnotationMirror>> subType;

    /**
     * superType maps each type qualifier to its super types.
     */
    private final Map<AnnotationMirror, Collection<AnnotationMirror>> superType;

    /**
     * incomparableType maps each type qualifier to its incomparable types.
     */
    private final Map<AnnotationMirror, Collection<AnnotationMirror>> incomparableType;

    /**
     * typeToInt maps each type qualifier to an unique integer value starts from
     * 0 on continuous basis.
     */
    private final Map<AnnotationMirror, Integer> typeToInt;

    /**
     * intToType maps an integer value to each type qualifier, which is a
     * reversed map of typeToInt.
     */
    private final Map<Integer, AnnotationMirror> intToType;

    /**
     * A qualifier hierarchy comes from InferenceSolver.
     */
    private QualifierHierarchy qualHierarchy;

    /**
     * All type qualifiers in underling type system.
     */
    private Set<? extends AnnotationMirror> allTypes;

    /**
     * Top qualifier of underling type system.
     */
    private AnnotationMirror top;

    /**
     * Bottom type qualifier of underling type system.
     */
    private AnnotationMirror bottom;

    /**
     * Number of type qualifier in underling type system.
     */
    private int numTypes;

    public LatticeBuilder() {
        subType = AnnotationUtils .createAnnotationMap();
        superType = AnnotationUtils.createAnnotationMap();
        incomparableType = AnnotationUtils.createAnnotationMap();
        typeToInt = AnnotationUtils.createAnnotationMap();
        intToType = new HashMap<Integer, AnnotationMirror>();
    }

    /**
     * Build a normal lattice with all fields configured.
     * 
     * @param qualHierarchy of underling type system.
     * @return a new Lattice instance.
     */
    public Lattice buildLattice(QualifierHierarchy qualHierarchy) {
        clear();
        this.qualHierarchy = qualHierarchy;
        allTypes = qualHierarchy.getTypeQualifiers();
        top = qualHierarchy.getTopAnnotations().iterator().next();
        bottom = qualHierarchy.getBottomAnnotations().iterator().next();
        numTypes = qualHierarchy.getTypeQualifiers().size();
        calculateSubSupertypes();
        calculateIncomparableTypes();
        return new Lattice(subType, superType, incomparableType, typeToInt, intToType, allTypes, top,
                bottom, numTypes);
    }

    /**
     * Build a two-qualifier lattice with all fields configured.
     * 
     * @param top type qualifier of underling type system.
     * @param bottom type qualifier of underling type system.
     * @return a new TwoQualifiersLattice instance.
     */
    public TwoQualifiersLattice buildTwoTypeLattice(AnnotationMirror top, AnnotationMirror bottom) {
        clear();
        Set<AnnotationMirror> tempSet = AnnotationUtils.createAnnotationSet();
        tempSet.add(top);
        tempSet.add(bottom);
        allTypes = Collections.unmodifiableSet(tempSet);
        this.top = top;
        this.bottom = bottom;
        numTypes = 2;
        calculateSubSupertypesForTwoQuals();
        return new TwoQualifiersLattice(subType, superType, incomparableType, typeToInt, intToType,
                allTypes, top, bottom, numTypes);
    }

    /**
     * Clear all fields. Will be called when build a new lattice to make sure
     * the old values are gone.
     */
    private void clear() {
        this.subType.clear();
        this.superType.clear();
        this.incomparableType.clear();
        this.typeToInt.clear();
        this.intToType.clear();
        qualHierarchy = null;
        allTypes = null;
        top = null;
        bottom = null;
        numTypes = 0;
    }

    /**
     * For each type qualifier, map it to a list of it's super types and
     * subtypes in two maps.
     */
    private void calculateSubSupertypes() {
        int num = 0;
        for (AnnotationMirror i : allTypes) {
            Set<AnnotationMirror> subtypeOfi = new HashSet<AnnotationMirror>();
            Set<AnnotationMirror> supertypeOfi = new HashSet<AnnotationMirror>();
            for (AnnotationMirror j : allTypes) {
                if (qualHierarchy.isSubtype(j, i)) {
                    subtypeOfi.add(j);
                }
                if (qualHierarchy.isSubtype(i, j)) {
                    supertypeOfi.add(j);
                }
            }
            subType.put(i, subtypeOfi);
            superType.put(i, supertypeOfi);
            typeToInt.put(i, num);
            intToType.put(num, i);
            num++;
        }
    }

    /**
     * For each type qualifier, map it to a list of it's incomparable types.
     */
    private void calculateIncomparableTypes() {
        for (AnnotationMirror i : allTypes) {
            Set<AnnotationMirror> incomparableOfi = new HashSet<AnnotationMirror>();
            for (AnnotationMirror j : allTypes) {
                if (!subType.get(i).contains(j) && !subType.get(j).contains(i)) {
                    incomparableOfi.add(j);
                }
            }
            if (!incomparableOfi.isEmpty()) {
                incomparableType.put(i, incomparableOfi);
            }
        }
    }

    /**
     * This method calculates the subtype and supertype relation for the type
     * systems only contain two type qualifiers.
     */
    private void calculateSubSupertypesForTwoQuals() {
        Set<AnnotationMirror> topSet = AnnotationUtils.createAnnotationSet();
        Set<AnnotationMirror> bottomSet = AnnotationUtils.createAnnotationSet();
        topSet.add(top);
        bottomSet.add(bottom);
        this.typeToInt.put(top, 0);
        this.typeToInt.put(bottom, 1);
        this.intToType.put(0, top);
        this.intToType.put(1, bottom);
        subType.put(top, Collections.unmodifiableSet(allTypes));
        superType.put(top, topSet);
        subType.put(bottom, bottomSet);
        superType.put(bottom, Collections.unmodifiableSet(allTypes));
    }
}
