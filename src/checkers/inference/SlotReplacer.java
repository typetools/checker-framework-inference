package checkers.inference;

import checkers.inference.model.Slot;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.visitor.AnnotatedTypeScanner;
import org.checkerframework.framework.util.AnnotatedTypes;
import org.checkerframework.javacutil.ErrorReporter;

import javax.lang.model.element.AnnotationMirror;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Given a set of replacement mappings (oldSlot -> newSlot),
 * SlotReplacer will replace each oldSlot in an AnnotatedTypeMirror with newSlot.
 *
 * Usage:
 * new SlotReplacer(slotManager)
 *     .addReplacement(oldSlot1, newSlot1)
 *     .addReplacement(oldSlot2, newSlot2)
 *     .addReplacement(oldSlot3, newSlot3)
 *     .replaceSlots(atmToUpdate)
 *
 * or
 * new SlotReplacer(slotManager,
 *     Arrays.asList(
 *          new Replacement(oldSlot1, newSlot2),
 *          new Replacement(oldSlot2, newSlot2),
 *          new Replacement(oldSlot3, newSlot3)
 *      )
 * ).replaceSlots(atmToUpdate)
 */
public class SlotReplacer {
    private final Set<Replacement> replacements = new HashSet<Replacement>();
    private final SlotManager slotManager;

    public SlotReplacer(final SlotManager slotManager) {
        this.slotManager = slotManager;
    }

    public SlotReplacer(final SlotManager slotManager, final Collection<Replacement> initialReplacements) {
        replacements.addAll(initialReplacements);
        this.slotManager = slotManager;
    }

    public SlotReplacer addReplacement(final Slot oldSlot, final Slot newSlot) {
        this.replacements.add(new Replacement(oldSlot, newSlot));
        return this;
    }

    /**
     * For each replacement mapping (oldSlot -> newSlot) configured in this slotReplacer,
     * replace each oldSlot in type with its corresponding newSlot
     * @param type
     */
    public void replaceSlots(final AnnotatedTypeMirror type) {
        type.accept(new BoundReplacementVisitor(), replacements);
    }

    /**
     * Create a copy of type then for each replacement mapping (oldSlot -> newSlot)
     * configured in this slotReplacer, replace each oldSlot in type with its corresponding newSlot
     * @param type
     * @return
     */
    public AnnotatedTypeMirror copyAndReplaceSlots(final AnnotatedTypeMirror type) {
        final AnnotatedTypeMirror copy = type.deepCopy();
        replaceSlots(copy);
        return copy;
    }


    private class BoundReplacementVisitor extends AnnotatedTypeScanner<Void, Set<Replacement>> {

        @Override
        protected Void scan(AnnotatedTypeMirror type, Set<Replacement> replacements) {

            for(final Replacement replacement : replacements) {
                if (slotManager.getVariableSlot(type).equals(replacement.oldSlot)) {
                   final AnnotationMirror newAnnotation = slotManager.getAnnotation(replacement.newSlot);
                   type.replaceAnnotation(newAnnotation);
                }
            }

            return super.scan(type, replacements);
        }
    }

    public static class Replacement {
        public final Slot oldSlot;
        public final Slot newSlot;

        public Replacement(final Slot oldSlot, final Slot newSlot) {
            this.oldSlot = oldSlot;
            this.newSlot = newSlot;

            if(oldSlot == null || newSlot == null) {
                ErrorReporter.errorAbort("Replacement includes null Slot: \n"
                        + this.toString());
            }
        }

        @Override
        public String toString() {
            return "Replacement( " + oldSlot + " -> " + newSlot + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj ) return true;

            if (obj == null || !(obj.getClass().equals(Replacement.class))) {
                return false;
            }

            final Replacement that = (Replacement) obj;
            return this.oldSlot.equals(that.oldSlot)
                    && this.newSlot.equals(that.newSlot);
        }

        @Override
        public int hashCode() {
            return 31 * (oldSlot.hashCode() + newSlot.hashCode());
        }
    }

}
