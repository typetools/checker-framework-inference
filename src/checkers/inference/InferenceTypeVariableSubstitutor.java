package checkers.inference;

import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.util.InferenceUtil;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.TypeVariableSubstitutor;
import org.checkerframework.javacutil.ErrorReporter;

import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Types;

/**
 * See ExistentialVariableSlot for a key to the shorthand used in this class.
 * {@code
 * Assuming we have a method:
 *      <@0 T extends @1 Object> (@2) T method((@3) T t) {}
 *      Where the return type is annotated as: <(@2 | 0) T extends (@2 | 1) Object>
 *      And the argument t is annotated as:    <(@3 | 0) T extends (@3 | 1) Object>
 *
 * Given the following use of method:
 *      @4 String arg;
 *      this.<@5 String>method(arg);
 *
 * We want the substitutor to return a method of type:
 *
 *   (@2 | @5) String method((@3 | @5) String t)
 *
 * In other words, this method states:
 * if (@2 exists) then
 *    the return type has @2 as a primary annotation
 * else
 *    the return type has @5 as a primary annotation
 *
 * if (@3 exists) then
 *    the parameter has @3 as a primary annotation
 * else
 *    the return type has @5 as a primary annotation
 * }
 *
 */
public class InferenceTypeVariableSubstitutor extends TypeVariableSubstitutor {

    private final SlotManager slotManager;
    private final ExistentialVariableInserter existentialInserter;
    private final Types types;

    public InferenceTypeVariableSubstitutor(InferenceAnnotatedTypeFactory typeFactory,
                                            ExistentialVariableInserter existentialInserter) {
        this.existentialInserter = existentialInserter;
        this.slotManager = InferenceMain.getInstance().getSlotManager();
        this.types = typeFactory.getProcessingEnv().getTypeUtils();

    }

    @Override
    protected AnnotatedTypeMirror substituteTypeVariable(AnnotatedTypeMirror argument, AnnotatedTypeVariable use) {
        final AnnotatedTypeMirror useUpperBound = InferenceUtil.findUpperBoundType(use, true);

        if ( !useUpperBound.getAnnotations().isEmpty()) {
            final Slot upperBoundSlot = slotManager.getVariableSlot(useUpperBound);
            if (upperBoundSlot instanceof ExistentialVariableSlot) {
                //the type of the use may already have an existential variable inserted for its declaration
                //we remove it (because it's between the potential variable and the bounds) and replace it
                //with one that is between the SAME potential variable but the argumenht instead

                final VariableSlot potentialSlot = ((ExistentialVariableSlot) upperBoundSlot).getPotentialSlot();

                if (argument.getKind() != TypeKind.TYPEVAR) {
                    final Slot altSlot = slotManager.getVariableSlot(argument);

                    final VariableSlot alternative = (VariableSlot) altSlot;
                    if (alternative != null) {
                        final ExistentialVariableSlot slot = slotManager.createExistentialVariableSlot(potentialSlot, alternative);
                        argument.replaceAnnotation(slotManager.getAnnotation(slot));
                    } else {
                        if (!InferenceMain.isHackMode()) {
                            ErrorReporter.errorAbort("Null alternative: " + argument + ", use=" + use);
                        }
                    }
                } else {
                    AnnotatedTypeMirror originalValue = argument.deepCopy();
                    existentialInserter.insert(potentialSlot, argument, originalValue);
                }
            }
        } else {
            //this occurs when you call a method within its own class body and therefore are
            //substituting a typevar for itself
            //the type should have a potential variable on it already from the VariableAnnotator
            //we need to continue on with the substitution because it will replace the
            //use's potential variables with the argument's potential variable
            //e.g.  we have a method:
            //<@0 T extends @1> void method(T t)
            //where typeof(t) == <(@2 | @0) T extends (@2 | @1)>
            //
            //if method is recursive and we have a use:
            // this.<(@3) T> method(someT)
            // The adapted formal parameter type should be:
            //    typeof(t) == (@3 | (@2 | @0)) T extends (@3 | (@2 | @1))>
            //
            // This type states:
            //  if (@3 exists)
            //     use @3
            //  else if (@2 exists)
            //     use @2
            //  else
            //     use the declared bounds
            //

            if (!types.isSameType(use.getUnderlyingType(), argument.getUnderlyingType())) {

                if (!InferenceMain.isHackMode()) {
                    ErrorReporter.errorAbort("Expected ExistentialTypeVariable to substitute:\n"
                                    + "use=" + use + "\n"
                                    + "argument=" + argument + "\n"
                    );
                }
            }
        }
        return super.substituteTypeVariable(argument, use);
    }
}
