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
import org.checkerframework.framework.util.PluginUtil;
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
 * This Serializer is meant only to convert constraints and variables to human readable strings. It
 * is used currently by the DebugSolver.
 */
public class ToStringSerializer implements Serializer<String, String> {
    private final boolean showAstPaths;
    private boolean showVerboseVars;

    private int indentationLevel = 0;

    // 2 spaces per indentation
    public static final String INDENT = "  ";

    // stores N concatenations of INDENT at index N, where index 0 stores empty string
    // the index is the indentation level
    protected final List<String> indentStrings = new ArrayList<>();

    // used to format constant slots
    protected final AnnotationFormatter formatter;

    public ToStringSerializer(boolean showAstPaths) {
        this.showAstPaths = showAstPaths;
        this.showVerboseVars = true;
        formatter = InferenceMain.getInstance().getRealTypeFactory().getAnnotationFormatter();

        // set first value to ""
        indentStrings.add("");
    }

    private void generateIndentations() {
        // create additional indentation strings up to the current level of indentation, if it
        // doesn't exist in the indentStrings array list
        // subsequent indentation string values are 1 more INDENT compared to the previous index
        StringBuilder sb = new StringBuilder(indentStrings.get(indentStrings.size() - 1));
        for (int i = indentStrings.size(); i <= getIndentationLevel(); i++) {
            indentStrings.add(sb.append(INDENT).toString());
        }
    }

    public void setIndentationLevel(int indentationLevel) {
        // Ensure the indentation level is always >= 0
        this.indentationLevel = indentationLevel > 0 ? indentationLevel : 0;
    }

    public int getIndentationLevel() {
        return indentationLevel;
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
                serializedVarSlots.put(varSlot.getId(), indent(varSlot.serialize(this)));
            } else {
                // sort all other slots by serialized string content through insertion to TreeSet
                serializedOtherSlots.add(indent(slot.serialize(this)));
            }
        }

        List<String> serializedSlots = new ArrayList<>();
        serializedSlots.addAll(serializedVarSlots.values());
        serializedSlots.addAll(serializedOtherSlots);

        return PluginUtil.join(delimiter, serializedSlots);
    }

    public String serializeConstraints(Iterable<Constraint> constraints, String delimiter) {
        List<String> constraintStrings = new ArrayList<>();

        for (Constraint constraint : constraints) {
            constraintStrings.add(constraint.serialize(this).toString());
        }

        // TODO: would be nice to sort constraints based on the slot IDs of the list of slots in
        // each constraint

        // Alphabetically sort list so that the output string is always in the same order
        Collections.sort(constraintStrings);

        return PluginUtil.join(delimiter, constraintStrings);
    }

    @Override
    public String serialize(SubtypeConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        final StringBuilder sb = new StringBuilder();
        sb.append(indent(constraint.getSubtype().serialize(this)))
          .append(" <: ")
          .append(constraint.getSupertype().serialize(this));
        showVerboseVars = prevShowVerboseVars;
        return sb.toString();
    }

    @Override
    public String serialize(EqualityConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        final StringBuilder sb = new StringBuilder();
        sb.append(indent(constraint.getFirst().serialize(this)))
          .append(" == ")
          .append(constraint.getSecond().serialize(this));
        showVerboseVars = prevShowVerboseVars;
        return sb.toString();
    }

    @Override
    public String serialize(ExistentialConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        final StringBuilder sb = new StringBuilder();
        sb.append(indent("if ( "))
          .append(constraint.getPotentialVariable().serialize(this))
          .append(" exists ) {\n");
        setIndentationLevel(getIndentationLevel() + 1);
        sb.append(serializeConstraints(constraint.potentialConstraints(), "\n"));
        setIndentationLevel(getIndentationLevel() - 1);

        sb.append("\n")
          .append(indent("} else {\n"));
        setIndentationLevel(getIndentationLevel() + 1);
        sb.append(serializeConstraints(constraint.getAlternateConstraints(), "\n"));
        setIndentationLevel(getIndentationLevel() - 1);

        sb.append("\n")
          .append(indent("}\n"));
        showVerboseVars = prevShowVerboseVars;
        return sb.toString();
    }

    @Override
    public String serialize(InequalityConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        final StringBuilder sb = new StringBuilder();
        sb.append(indent(constraint.getFirst().serialize(this)))
          .append(" != ")
          .append(constraint.getSecond().serialize(this));
        showVerboseVars = prevShowVerboseVars;
        return sb.toString();
    }

    @Override
    public String serialize(ComparableConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        final StringBuilder sb = new StringBuilder();
        sb.append(indent(constraint.getFirst().serialize(this)))
          .append(" <~> ")
          .append(constraint.getSecond().serialize(this));
        showVerboseVars = prevShowVerboseVars;
        return sb.toString();
    }

    @Override
    public String serialize(CombineConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        // "\u25B7" is unicode representation of viewpoint adaptation sign |>
        final StringBuilder sb = new StringBuilder();
        sb.append(indent(constraint.getResult().serialize(this)))
          .append(" = ( ")
          .append(constraint.getTarget().serialize(this))
          .append(" \u25B7 ")
          .append(constraint.getDeclared().serialize(this))
          .append(" )");
        showVerboseVars = prevShowVerboseVars;
        return sb.toString();
    }

    @Override
    public String serialize(PreferenceConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        final StringBuilder sb = new StringBuilder();
        sb.append(indent(constraint.getVariable().serialize(this)))
          .append(" ~= ")
          .append(constraint.getGoal().serialize(this))
          .append(" w(")
          .append(constraint.getWeight())
          .append(" )");
        showVerboseVars = prevShowVerboseVars;
        return sb.toString();
    }

    @Override
    public String serialize(ConstantSlot slot) {
        final StringBuilder sb = new StringBuilder();
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
        sb.append(slot.getId())
          .append(": refines ")
          .append(Arrays.asList(slot.getRefined()));
        optionallyShowVerbose(slot, sb);
        return sb.toString();
    }

    @Override
    public String serialize(ExistentialVariableSlot slot) {
        final StringBuilder sb = new StringBuilder();
        sb.append(slot.getId())
          .append(": ( ")
          .append(slot.getPotentialSlot().getId())
          .append(" | ")
          .append(slot.getAlternativeSlot().getId())
          .append(" ) ");
        optionallyShowVerbose(slot, sb);
        return sb.toString();
    }

    @Override
    public String serialize(CombVariableSlot slot) {
        final StringBuilder sb = new StringBuilder();
        sb.append(slot.getId());
        if (showVerboseVars) {
            sb.append(": merges ")
              .append(Arrays.asList(slot.getFirst(), slot.getSecond()));
            formatMerges(slot, sb);
            optionallyFormatAstPath(slot, sb);
        }
        return sb.toString();
    }

    protected String indent(String str) {
        generateIndentations();
        final StringBuilder sb = new StringBuilder();
        sb.append(indentStrings.get(getIndentationLevel()))
          .append(str);
        return sb.toString();
    }

    protected void formatMerges(final VariableSlot slot, final StringBuilder sb) {
        if (!slot.getMergedToSlots().isEmpty()) {
            sb.append(": merged to -> ")
              .append(slot.getMergedToSlots());
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
            sb.append("\n")
              .append(indent(":AstPath: "));
            if (varSlot.getLocation() == null) {
                sb.append("<NULL PATH>");
            } else {
                sb.append(varSlot.getLocation().toString());
            }
        }
    }
}
