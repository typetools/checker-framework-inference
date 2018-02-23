package checkers.inference.model.serialization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.checkerframework.framework.util.AnnotationFormatter;
import checkers.inference.InferenceMain;
import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.CombineConstraint;
import checkers.inference.model.ComparableConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.ExistentialConstraint;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.InequalityConstraint;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Serializer;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;

/**
 * This Serializer is meant only to convert constraints and variables to
 * human readable strings.  It is used currently by the DebugSolver.
 */
public class ToStringSerializer implements Serializer<String, String> {
    private final boolean showAstPaths;
    private boolean showVerboseVars;

    private int indent = 0;

    // 4 spaces per indentation
    public static final String INDENT_STRING = "    ";

    // stores N concatenations of INDENT_STRING at index N, where index 0 stores empty string
    // the index is the indentation level
    public static final List<String> indentStrings = new ArrayList<>();

    // sets the maximum number of indentation levels to generate strings for upon instantiation of
    // the ToStringSerializer
    private static final int INITIAL_INDENTATION_LEVELS = 2;

    // used to format constant slots
    protected final AnnotationFormatter formatter;

    public ToStringSerializer(boolean showAstPaths) {
        this.showAstPaths = showAstPaths;
        this.showVerboseVars = true;
        formatter = InferenceMain.getInstance().getRealTypeFactory().getAnnotationFormatter();

        // set first value to ""
        indentStrings.add("");
        // set subsequent values to be 1 more INDENT_STRING compared to the previous index
        // by default do this for indentation levels 1 to INITIAL_INDENTATION_LEVELS
        for (int i = 1; i <= INITIAL_INDENTATION_LEVELS; i++) {
            indentStrings.add(indentStrings.get(i - 1) + INDENT_STRING);
        }
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    public int getIndent() {
        return indent;
    }

    public String serializeSlots(Iterable<Slot> slots, String delimiter) {
        // Split slots into two sublists, one for all VariableSlots (and subclasses), and the other
        // for any other kinds of slots
        Map<Integer, String> serializedVarSlots = new TreeMap<>();
        Set<String> serializedOtherSlots = new TreeSet<>();

        for (Slot slot : slots) {
            if (slot instanceof VariableSlot) {
                // sort the varSlots by ID through insertion to TreeMap
                VariableSlot varSlot = (VariableSlot) slot;
                serializedVarSlots.put(varSlot.getId(), varSlot.serialize(this));
            } else {
                // sort all other slots by serialized string content through insertion to TreeSet
                serializedOtherSlots.add(slot.serialize(this));
            }
        }

        List<String> serializedSlots = new ArrayList<>();
        serializedSlots.addAll(serializedVarSlots.values());
        serializedSlots.addAll(serializedOtherSlots);

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String serializedSlot : serializedSlots) {
            if (first) {
                sb.append(serializedSlot);
                first = false;
            } else {
                sb.append(delimiter)
                  .append(serializedSlot);
            }
        }

        return sb.toString();
    }

    public String serializeConstraints(Iterable<Constraint> constraints, String delimiter) {
        List<String> constraintStrings = new ArrayList<>();

        for (Constraint constraint : constraints) {
            constraintStrings.add(constraint.serialize(this).toString());
        }

        // Sort list so that the output string is always in the same order
        Collections.sort(constraintStrings);

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String string : constraintStrings) {
            String constraintString = first ? "" : delimiter;
            constraintString += string;
            sb.append(constraintString);
            first = false;
        }

        return sb.toString();
    }

    @Override
    public String serialize(SubtypeConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        String result = indent(constraint.getSubtype().serialize(this) + " <: " + constraint.getSupertype().serialize(this));
        showVerboseVars = prevShowVerboseVars;
        return result;
    }

    @Override
    public String serialize(EqualityConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        String result = indent(constraint.getFirst().serialize(this) + " == " + constraint.getSecond().serialize(this));
        showVerboseVars = prevShowVerboseVars;
        return result;
    }

    @Override
    public String serialize(ExistentialConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append( indent("if ( " + constraint.getPotentialVariable().serialize(this) + " exists ) {\n") );
        indent += 1;
        sb.append(serializeConstraints(constraint.potentialConstraints(), "\n"));
        indent -= 1;

        sb.append("\n");
        sb.append( indent("} else {\n"));
        indent += 1;
        sb.append(serializeConstraints(constraint.getAlternateConstraints(), "\n"));
        indent -= 1;

        sb.append("\n");
        sb.append(indent("}"));
        sb.append("\n");
        showVerboseVars = prevShowVerboseVars;
        return sb.toString();
    }

    @Override
    public String serialize(InequalityConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        String result = indent(constraint.getFirst().serialize(this) + " != " + constraint.getSecond().serialize(this));
        showVerboseVars = prevShowVerboseVars;
        return result;
    }

    @Override
    public String serialize(ComparableConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        String result = indent(constraint.getFirst().serialize(this) + " <~> " + constraint.getSecond().serialize(this));
        showVerboseVars = prevShowVerboseVars;
        return result;
    }

    @Override
    public String serialize(CombineConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        // "\u25B7" is unicode representation of viewpoint adaptation sign |>
        String result = indent(constraint.getResult().serialize(this) + " = ( "
                + constraint.getTarget().serialize(this) + " \u25B7 "
                + constraint.getDeclared().serialize(this) + " )");
        showVerboseVars = prevShowVerboseVars;
        return result;
    }

    @Override
    public String serialize(PreferenceConstraint preferenceConstraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        String result = indent(preferenceConstraint.getVariable().serialize(this) + " ~= "
                + preferenceConstraint.getGoal().serialize(this)
                + " w(" + preferenceConstraint.getWeight() + " )");
        showVerboseVars = prevShowVerboseVars;
        return result;
    }

    @Override
    public String serialize(ConstantSlot slot) {
        StringBuilder sb = new StringBuilder();

        sb.append(slot.getId())
          .append(" ")
          .append(formatter.formatAnnotationMirror(slot.getValue()));

        return sb.toString();
    }

    // variables
    @Override
    public String serialize(VariableSlot slot) {
        final StringBuilder sb = new StringBuilder();
        sb.append(slot.getId());
        optionallyShowVerbose(slot, sb);
        return sb.toString();
    }

    @Override
    public String serialize(RefinementVariableSlot slot) {
        final StringBuilder sb = new StringBuilder();
        sb.append(slot.getId());
        sb.append(": refines ");
        sb.append(Arrays.asList(slot.getRefined()));
        optionallyShowVerbose(slot, sb);
        return sb.toString();
    }

    @Override
    public String serialize(ExistentialVariableSlot slot) {
        final StringBuilder sb = new StringBuilder();
        sb.append(slot.getId());
        sb.append(": ( " );
        sb.append(slot.getPotentialSlot().getId());
        sb.append(" | ");
        sb.append(slot.getAlternativeSlot().getId());
        sb.append(" ) ");
        optionallyShowVerbose(slot, sb);
        return sb.toString();
    }

    @Override
    public String serialize(CombVariableSlot slot) {
        final StringBuilder sb = new StringBuilder();
        sb.append(slot.getId());

        if (showVerboseVars) {
            sb.append(": merges ");
            sb.append(Arrays.asList(slot.getFirst(), slot.getSecond()));
            formatMerges(slot, sb);
            optionallyFormatAstPath(slot, sb);
        }
        return sb.toString();
    }

    protected String indent(String str) {
        // create additional indentation strings up to the current level of indentation, if it
        // doesn't exist in the indentStrings array list
        for (int i = indentStrings.size(); i <= indent; i++) {
            indentStrings.add(indentStrings.get(i - 1) + INDENT_STRING);
        }

        return indentStrings.get(indent) + str;
    }

    protected void formatMerges(final VariableSlot slot, final StringBuilder sb) {
        if (!slot.getMergedToSlots().isEmpty()) {
            sb.append(": merged to -> ");
            sb.append(slot.getMergedToSlots());
        }
    }

    protected void optionallyShowVerbose(final VariableSlot varSlot, final StringBuilder sb) {
        if (showVerboseVars) {
            formatMerges(varSlot, sb);
            optionallyFormatAstPath(varSlot, sb);
        }
    }

    protected void optionallyFormatAstPath(final VariableSlot varSlot, final StringBuilder sb) {
        if (showAstPaths && (varSlot.isInsertable() || (varSlot.getLocation() != null))) {
            sb.append("\n:AstPath: ");
            if (varSlot.getLocation() == null) {
                sb.append("<NULL PATH>");
            } else {
                sb.append(varSlot.getLocation().toString());
            }
        }
    }
}
