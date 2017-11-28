package checkers.inference;

import org.checkerframework.javacutil.ErrorReporter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import checkers.inference.model.BinaryConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;

/**
 * This class currently just removes ExistentialVariables from the set of constraints
 * and replaces them with ExistentialConstraints.  In the future, we may want to make
 * this an interface or make it customizable
 */
public class ConstraintNormalizer {

    public final Logger logger = Logger.getLogger(ConstraintNormalizer.class.getName());

    protected interface Normalizer {
        boolean accept(Constraint constraint);
    }

    public ConstraintNormalizer() {

    }

    public Set<Constraint> normalize(Set<Constraint> constraints) {
        Set<Constraint> filteredConstraints = new LinkedHashSet<>(constraints);
        logger.info("-- Normalization : NULL SLOT --");
        filteredConstraints = filter(filteredConstraints, new NullSlotNormalizer());

        logger.info("-- Normalization : EXISTENTIAl CONSTRAINT --");
        ExistentialVariableNormalizer existentialNormalizer = new ExistentialVariableNormalizer();
        filteredConstraints = filter(filteredConstraints, existentialNormalizer);
        filteredConstraints.addAll(existentialNormalizer.getConstraints());

        return filteredConstraints;
    }

    private static Set<Constraint> filter(Set<Constraint> constraints, Normalizer normalizer) {
        final Set<Constraint> normalizedConstraints = new HashSet<>(constraints.size());
        for (final Constraint constraint : constraints) {
            if (!normalizer.accept(constraint)) {
                normalizedConstraints.add(constraint);
            }
        }

        return normalizedConstraints;
    }

    private static class ExistentialVariableNormalizer implements Normalizer {
        private ExistentialTree existentialTree = new ExistentialTree();

        public Set<Constraint> getConstraints() {
            return existentialTree.toConstraints();
        }

        /**
         * Returns true if this constraint contains an ExistentialVariable and
         * this constraint will be replaced by this normalizer
         * @param constraint
         * @return
         */
        @Override
        public boolean accept(final Constraint constraint) {
            boolean hasExistential = false;
            if (constraint instanceof BinaryConstraint) {
                for (final Slot slot : constraint.getSlots()) {
                    if (slot instanceof ExistentialVariableSlot) {
                        hasExistential = true;
                        break;
                    }
                }

                if (hasExistential) {
                    collectConstraints((BinaryConstraint) constraint);
                }
            }

            return hasExistential;
        }

        protected void collectConstraints(final BinaryConstraint constraint) {
            final List<Slot> leftSlot = slotsToConditionals(constraint.getFirst());
            final List<Slot> rightSlot = slotsToConditionals(constraint.getSecond());
            addToTree(leftSlot, rightSlot, constraint);
        }

        // TODO: DOCUMENT THAT WE BASICALLY (FOR BINARY CONSTRAINTS) BUILD UP ALL POSSIBLE EXISTS/DOESN'T EXISTS
        // TODO: FOR EITHER SIDE OF THE CONSTRAINT THEN TAKE THE CARTESIAN PRODUCT

        protected List<Slot> slotsToConditionals(final Slot existentialVariableSlot) {
            final List<Slot> slots = new ArrayList<>();

            Slot current = existentialVariableSlot;
            while (current instanceof ExistentialVariableSlot) {
                slots.add(((ExistentialVariableSlot) current).getPotentialSlot());
                current = ((ExistentialVariableSlot) current).getAlternativeSlot();
            }

            slots.add(current);
            return slots;
        }

        // We have two existential constraints like:
        // (1 | (2 | (3 | 4))               ( 5 | (1 | (6 | 7) )
        // Implications:
        // 1 | 5  => 1 < 5
        // 1 !5 1 => 1 < 1 -- filtered out as you get 1 < 1
        // 1 !5 !1 .. => filtered out as this is unsatisfiable
        // !1 2 5 => 2 < 5
        // !1 2 !5 1 => [ filtered out as it unsatisfiable
        // !1 2 !5 !1 6 => 2 [ 6
        //  ...
        protected void addToTree(final List<Slot> leftSlots,
                              final List<Slot> rightSlots,
                              final BinaryConstraint constraint) {

            final int initialSize = (int) Math.floor((Math.pow(leftSlots.size() * leftSlots.size(), 2) * 3/4));

            // a list of values from leftSlots, where exist == false
            // we have already added their positive cases to implications
            final HashSet<Value> previouslyEncountered = new HashSet<>(initialSize);

            final List<Slot> currentLeft = new ArrayList<Slot>(leftSlots.size());
            final int lastLeftIndex  = leftSlots.size() - 1;
            final int lastRightIndex = rightSlots.size() - 1;

            for (int leftIndex = 0; leftIndex < leftSlots.size(); leftIndex++) {
                final Slot left  = leftSlots.get(leftIndex);
                final boolean lastLeft = leftIndex == lastLeftIndex;

                currentLeft.add(left);
                final TreeSet<Value> encountered = new TreeSet<>(previouslyEncountered);
                final Value lhsValue = new Value(left, true, lastLeft);

                if (!previouslyEncountered.contains(lhsValue.negate())) {
                    encountered.add(lhsValue);

                    for (int rightIndex = 0; rightIndex < rightSlots.size(); rightIndex++) {
                        final Slot right = rightSlots.get(rightIndex);
                        final boolean lastRight = rightIndex == lastRightIndex;

                        final Value rhsValue = new Value(right, true, lastRight);
                        if (!left.equals(right) && !encountered.contains(rhsValue.negate())) {
                            final TreeSet<Value> path = new TreeSet<>(encountered);
                            path.add(rhsValue);
                            existentialTree.addConstraints(path, constraint.make(left, right));
                        }
                        encountered.add(rhsValue.negate());
                    }

                    previouslyEncountered.add(lhsValue.negate());
                }
            }
        }


    }

    private static class ExistentialTree {
        private TreeMap<Slot, ExistentialNode> nodes = new TreeMap<>(SLOT_COMPARATOR);

        public void addConstraints(final TreeSet<Value> path, final Constraint constraint) {
            final Iterator<Value> pathIterator = path.iterator();
            final Value head = pathIterator.next();

            ExistentialNode current = getOrCreateNode(nodes, head);
            Value currentValue = head;

            while (pathIterator.hasNext() && !currentValue.alwaysExists) {
                final Value nextValue = pathIterator.next();
                if (currentValue.exists) {
                    current = getOrCreateNode(current.ifExists, nextValue);
                } else {
                    current = getOrCreateNode(current.ifNotExists, nextValue);
                }
                currentValue = nextValue;
            }

            current.constraints.add(constraint);
        }

        private static ExistentialNode getOrCreateNode(final TreeMap<Slot, ExistentialNode> nodes, Value value) {
            ExistentialNode node;
            if (!nodes.containsKey(value.slot)) {
                node = new ExistentialNode(value);
                nodes.put(value.slot, node);
            } else {
                node = nodes.get(value.slot);
            }

            return node;
        }

        public Set<Constraint> toConstraints() {
            final Set<Constraint> constraints = new LinkedHashSet<>();
            // start here, traverse tree make it into existential constraints
            for (final ExistentialNode node : nodes.values()) {
                constraints.addAll(node.toConstraints());
            }
            return constraints;
        }

    }


    private static class ExistentialNode {
        private final Slot slot;
        private final TreeMap<Slot, ExistentialNode> ifExists;
        private final TreeMap<Slot, ExistentialNode> ifNotExists;
        private final Set<Constraint> constraints;
        private final boolean alwaysExists;

        public ExistentialNode(Value value) {
            slot = value.slot;
            this.alwaysExists = value.alwaysExists;
            ifExists = new TreeMap<>(SLOT_COMPARATOR);
            ifNotExists = new TreeMap<>(SLOT_COMPARATOR);

            constraints = new LinkedHashSet<Constraint>();
        }

        public Set<Constraint> toConstraints() {
            if (alwaysExists) {
                return constraints;
            }

            List<Constraint> ifExistsConstraints = new ArrayList<>();
            List<Constraint> ifNotExistsConstraints = new ArrayList<>();

            ifExistsConstraints.addAll(constraints);
            for (final ExistentialNode existNode : ifExists.values()) {
                ifExistsConstraints.addAll(existNode.toConstraints());
            }

            for (final ExistentialNode notExistNode : ifNotExists.values()) {
                ifNotExistsConstraints.addAll(notExistNode.toConstraints());
            }
            final LinkedHashSet<Constraint> ret = new LinkedHashSet<>();

            ret.add(InferenceMain
                    .getInstance()
                    .getConstraintManager()
                    .createExistentialConstraint((VariableSlot) slot, ifExistsConstraints,
                            ifNotExistsConstraints));
            return ret;
        }
    }

    private static class Value implements Comparable<Value> {
        public final Slot slot;
        public final boolean exists;
        public final boolean alwaysExists;

        public Value(final Slot slot, boolean exists, boolean alwaysExists) {
            this.slot = slot;
            this.exists = exists;
            this.alwaysExists = alwaysExists;
        }

        @Override
        public int compareTo(Value that) {
            if (that == null) {
                return -1;
            }
            if (this.alwaysExists == that.alwaysExists) {
                return SLOT_COMPARATOR.compare(this.slot, that.slot);
            } else {
                if (!this.alwaysExists) {
                    return -1;
                } else {
                    return 1;
                }
            }
        }

        public Value negate() {
            return new Value(slot, !exists, alwaysExists);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            if (alwaysExists) {
                sb.append("[");
                sb.append(
                        slot.isVariable() ? ((VariableSlot) slot).getId()
                                          : ((ConstantSlot) slot).getValue());
                sb.append("]");
            } else {
                if (!exists) {
                    sb.append("!");
                }
                sb.append(((VariableSlot) slot).getId());
            }
            return sb.toString();
        }
    }

    private static SlotComparator SLOT_COMPARATOR = new SlotComparator();
    private static class SlotComparator implements Comparator<Slot> {

        @Override
        public int compare(Slot o1, Slot o2) {
            if (o1 == o2) {
                return 0;
            }
            if (o1 instanceof ConstantSlot) {
                if (o2 instanceof ConstantSlot) {
                    return ((ConstantSlot) o1).getValue().toString().compareTo(
                            ((ConstantSlot) o2).getValue().toString()
                    );
                } else {
                    return 1;
                }
            } else {
                if (o2 instanceof ConstantSlot) {
                    return -1;
                }
            }

            return ((VariableSlot) o1).getId() - ((VariableSlot) o2).getId();
        }
    }

    private class NullSlotNormalizer implements Normalizer {

        @Override
        public boolean accept(Constraint constraint) {
            for (Slot slot : constraint.getSlots()) {
                if (slot == null) {
                    if (!InferenceMain.isHackMode()) {
                        ErrorReporter.errorAbort("Null slot in constraint " + constraint.getClass().getName() + "\n"
                                               + constraint);
                    }
                    return true;
                }
            }

            return false;
        }
    }
}
