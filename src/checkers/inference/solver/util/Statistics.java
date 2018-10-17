package checkers.inference.solver.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;

/**
 * Recorder for statistics.
 */
public class Statistics {
    // statistics are sorted by insertion order
    private final static Map<String, Long> statistics = new LinkedHashMap<>();

    /**
     * Adds or increments the given value to the statistics for the given key.
     *
     * @param key
     *            a statistic key. The key is treated case-insensitive: it will always be considered
     *            in terms of its lower-case equivalent.
     * @param value
     *            a value
     */
    public static void addOrIncrementEntry(String key, long value) {
        synchronized (statistics) {
            // always use the lower-case version of the given key
            key = key.toLowerCase();

            if (statistics.get(key) == null) {
                statistics.put(key, value);
            } else {
                statistics.put(key, statistics.get(key) + value);
            }
        }
    }

    /**
     * Adds a count of each kind of slot to the statistics.
     *
     * @param slots
     */
    public static void recordSlotsStatistics(final Collection<Slot> slots) {
        // Record total number of slots
        addOrIncrementEntry("total_slots", slots.size());

        // Record slot counts
        Map<Class<? extends Slot>, Long> slotCounts = new LinkedHashMap<>();
        // Total number of non-constant slots
        long totalVariableSlots = 0;

        for (Slot slot : slots) {
            if (slot instanceof VariableSlot && !(slot instanceof ConstantSlot)) {
                totalVariableSlots++;
            }

            Class<? extends Slot> slotClass = slot.getClass();

            if (!slotCounts.containsKey(slotClass)) {
                slotCounts.put(slotClass, 1L);
            } else {
                slotCounts.put(slotClass, slotCounts.get(slotClass) + 1L);
            }
        }

        addOrIncrementEntry("total_variable_slots", totalVariableSlots);

        for (Entry<Class<? extends Slot>, Long> entry : slotCounts.entrySet()) {
            addOrIncrementEntry(entry.getKey().getSimpleName(), entry.getValue());
        }
    }

    /**
     * Adds a count of each kind of constraint to the statistics.
     *
     * @param constraints
     */
    public static void recordConstraintsStatistics(final Collection<Constraint> constraints) {
        // Record total number of constraints
        addOrIncrementEntry("total_constraints", constraints.size());

        // Record constraint counts
        Map<Class<? extends Constraint>, Long> constraintCounts = new LinkedHashMap<>();

        for (Constraint constraint : constraints) {
            Class<? extends Constraint> constraintClass = constraint.getClass();

            if (!constraintCounts.containsKey(constraintClass)) {
                constraintCounts.put(constraintClass, 1L);
            } else {
                constraintCounts.put(constraintClass, constraintCounts.get(constraintClass) + 1L);
            }
        }

        for (Entry<Class<? extends Constraint>, Long> entry : constraintCounts.entrySet()) {
            addOrIncrementEntry(entry.getKey().getSimpleName(), entry.getValue());
        }
    }

    /**
     * Returns an immutable map of the collected statistics.
     *
     * @return the immutable map.
     */
    public static Map<String, Long> getStatistics() {
        return Collections.unmodifiableMap(statistics);
    }

    /**
     * Erases all collected statistics.
     */
    public static void clearStatistics() {
        statistics.clear();
    }
}
