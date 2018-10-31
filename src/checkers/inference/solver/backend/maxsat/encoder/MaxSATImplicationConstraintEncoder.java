package checkers.inference.solver.backend.maxsat.encoder;

import checkers.inference.model.Constraint;
import checkers.inference.model.ImplicationConstraint;
import checkers.inference.solver.backend.encoder.implication.ImplicationConstraintEncoder;
import checkers.inference.solver.backend.maxsat.MaxSatFormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import org.sat4j.core.VecInt;

import javax.lang.model.element.AnnotationMirror;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MaxSATImplicationConstraintEncoder
        extends MaxSATAbstractConstraintEncoder
        implements ImplicationConstraintEncoder<VecInt[]> {

    /**
     * {@link MaxSatFormatTranslator} instance to delegate format translating
     * base {@link Constraint}s ({@code Constraint}s that are not
     * {@link ImplicationConstraint}).
     */
    private final MaxSatFormatTranslator formatTranslator;

    public MaxSATImplicationConstraintEncoder(Lattice lattice,
            Map<AnnotationMirror, Integer> typeToInt,
            MaxSatFormatTranslator formatTranslator) {
        super(lattice, typeToInt);
        this.formatTranslator = formatTranslator;
    }

    // High level procedures:
    // 1) Format translate all Constraint from assumptions to a list of VecInt:
    // [(a|b),(c|d)]
    //
    // 2) Convert the list in step 1 to a list of list. The sublist is
    // essentially a set (should have no duplicates) of variables: [[a,b],[c,d]]
    //
    // 3) Get the cartesian set(use list for implementation):
    // [[a,c],[a,d],[b,c],[b,d]]
    //
    // 4) Iterate over cartesian set in step 3, create a new VecInt, which is
    // composed by this way: negate each variable from each set from cartesian
    // set, combine it with every VecInt from conclusion clauses.
    // For example, (~a | ~c | h | i), (~a | ~c | j | k),
    // (~a | ~d | h | i), (~a | ~d | j | k), (~b | ~c | h | i),
    // (~b | ~c | j | k), (~b | ~d | h | i), (~b | ~d | j | k)
    //
    // 5) Convert the list of VecInt in step 4 to array of VecInt and return it
    // as final format translated result.
    //
    // Note:
    // (a | b) & (c | d) => (h | i) & (j | k) is equivalent to cnf form:
    // (~a | ~c | h | i) & (~a | ~c | j | k) & (~a | ~d | h | i) &
    // (~a | ~d | j | k) & (~b | ~c | h | i) & (~b | ~c | j | k) &
    // (~b | ~d | h | i) & (~b | ~d | j | k)
    @Override
    public VecInt[] encode(ImplicationConstraint constraint) {

        // Step 1
        // A list of VecInts/clauses from lhs of implication, which are
        // conjuncted together in cnf
        List<VecInt> assumptions = new ArrayList<>();
        for (Constraint a : constraint.getAssumptions()) {
            assumptions.addAll(Arrays.asList(a.serialize(formatTranslator)));
        }

        // Step 2
        // l is a set of set, in which each subset contains every variable that
        // a VecInt contains, for example, l = {{a,b}, {c,d}}
        List<List<Integer>> l = new ArrayList<>();
        for (VecInt clause : assumptions) {
            int[] a = clause.toArray();
            List<Integer> list = new ArrayList<>();
            for (int each : a) {
                list.add(each);
            }
            l.add(list);
        }

        // Step 3
        // cartesian is cartesian set of every variable from different VecInt,
        // for example, cartesian = {{a,c}, {a,d}, {b,c}, {b,d}}
        // cartesian should have the same length as target array
        List<List<Integer>> cartesian = cartesianProduct(l);

        // Concatenate with every pair at the end
        VecInt[] conclusionClauses = constraint.getConclusion()
                .serialize(formatTranslator);

        int expectedSize = cartesian.size() * conclusionClauses.length;

        List<VecInt> serializedTemp = new ArrayList<>();

        // Step 4
        for (int i = 0; i < cartesian.size(); i++) {
            for (int j = 0; j < conclusionClauses.length; j++) {
                List<Integer> toNegate = cartesian.get(i);
                VecInt targetClause = new VecInt();
                for (Integer var : toNegate) {
                    // Push the negation, of variable because it's from lhs
                    // assumption for example, they are {!a, !c} and {!a,!d} and
                    // {!b, !c} and {!b, !d}
                    targetClause.push(-var);
                }
                targetClause.pushAll(conclusionClauses[j]);
                serializedTemp.add(targetClause);
            }
        }

        assert serializedTemp.size() == expectedSize;

        // Step 5
        VecInt[] finalSerializedResult = new VecInt[expectedSize];
        return serializedTemp.toArray(finalSerializedResult);
    }

    /**
     * Method to get cartesian set of input set.
     *
     * For example, if the input is [[1,2], [3,4,5]], this method returns
     * [[1,3],[1,4],[1,5],[2,3],[2,4],[2,5]]
     *
     * @param lists
     *            a set of set of elements
     * @param <T>
     *            type of element
     * @return cartesian set of elements
     */
    protected <T> List<List<T>> cartesianProduct(List<List<T>> lists) {
        List<List<T>> resultLists = new ArrayList<List<T>>();
        if (lists.size() == 0) {
            resultLists.add(new ArrayList<T>());
            return resultLists;
        } else {
            List<T> firstList = lists.get(0);
            List<List<T>> remainingLists = cartesianProduct(
                    lists.subList(1, lists.size()));
            for (T condition : firstList) {
                for (List<T> remainingList : remainingLists) {
                    ArrayList<T> resultList = new ArrayList<T>();
                    resultList.add(condition);
                    resultList.addAll(remainingList);
                    resultLists.add(resultList);
                }
            }
        }
        return resultLists;
    }
}
