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
import org.checkerframework.javacutil.SystemUtil;
import checkers.inference.InferenceMain;
import checkers.inference.model.ArithmeticConstraint;
import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.CombineConstraint;
import checkers.inference.model.ComparableConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.ExistentialConstraint;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.ImplicationConstraint;
import checkers.inference.model.InequalityConstraint;
import checkers.inference.model.LubVariableSlot;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Serializer;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;

/**
 * This Serializer converts constraints and variables to human readable strings.
 */
public class ToStringSerializer implements Serializer<String, String> {
    private final boolean showAstPaths;
    private boolean showVerboseVars;

    /**
     * Stores the current indentation level, where level 0 means no indentations at all, level 1
     * means 1 indentation and so on. Each indentation is equivalent to the number of spaces stored
     * in {@link #INDENT}.
     */
    private int indentationLevel = 0;

    /**
     * This string constant stores the number of spaces per indentation. It is set to 2 spaces per
     * indentation.
     */
    private static final String INDENT = "  ";

    /**
     * This list holds the indent strings for each indentation level, where it stores N
     * concatenations of INDENT at index N. The index is the indentation level. Index 0 stores empty
     * string.
     */
    private final List<String> indentStrings = new ArrayList<>();

    // used to format constant slots
    private final AnnotationFormatter formatter;

    public ToStringSerializer(boolean showAstPaths) {
        this.showAstPaths = showAstPaths;
        this.showVerboseVars = true;
        formatter = InferenceMain.getInstance().getRealTypeFactory().getAnnotationFormatter();

        // set first value to ""
        indentStrings.add("");
    }

    public void setIndentationLevel(int indentationLevel) {
        // Ensure the indentation level is always >= 0
        this.indentationLevel = indentationLevel > 0 ? indentationLevel : 0;

        // if the new indentation level is higher than the max index of the indentStrings list,
        // update the list
        if (indentationLevel >= indentStrings.size()) {
            // create additional indentation strings up to the current level of indentation, if it
            // doesn't exist in the indentStrings array list
            // subsequent indentation string values contain 1 more copy of INDENT compared to the
            // previous index
            for (int i = indentStrings.size() - 1; i < indentationLevel; i++) {
                indentStrings.add(indentStrings.get(i) + INDENT);
            }
        }
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
                serializedVarSlots.put(varSlot.getId(),
                        getCurrentIndentString() + varSlot.serialize(this));
            } else {
                // sort all other slots by serialized string content through insertion to TreeSet
                serializedOtherSlots.add(getCurrentIndentString() + slot.serialize(this));
            }
        }

        List<String> serializedSlots = new ArrayList<>();
        serializedSlots.addAll(serializedVarSlots.values());
        serializedSlots.addAll(serializedOtherSlots);

        return SystemUtil.join(delimiter, serializedSlots);
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

        return SystemUtil.join(delimiter, constraintStrings);
    }

    @Override
    public String serialize(SubtypeConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        final StringBuilder sb = new StringBuilder();
        sb.append(getCurrentIndentString())
          .append(constraint.getSubtype().serialize(this))
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
        sb.append(getCurrentIndentString())
          .append(constraint.getFirst().serialize(this))
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
        sb.append(getCurrentIndentString())
          .append("if ( ")
          .append(constraint.getPotentialVariable().serialize(this))
          .append(" exists ) {\n");
        setIndentationLevel(indentationLevel + 1);
        sb.append(serializeConstraints(constraint.potentialConstraints(), "\n"));
        setIndentationLevel(indentationLevel - 1);

        sb.append("\n")
          .append(getCurrentIndentString())
          .append("} else {\n");
        setIndentationLevel(indentationLevel + 1);
        sb.append(serializeConstraints(constraint.getAlternateConstraints(), "\n"));
        setIndentationLevel(indentationLevel - 1);

        sb.append("\n")
          .append(getCurrentIndentString())
          .append("}");
        showVerboseVars = prevShowVerboseVars;
        return sb.toString();
    }

    @Override
    public String serialize(InequalityConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        final StringBuilder sb = new StringBuilder();
        sb.append(getCurrentIndentString())
          .append(constraint.getFirst().serialize(this))
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
        sb.append(getCurrentIndentString())
          .append(constraint.getFirst().serialize(this))
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
        sb.append(getCurrentIndentString())
          .append(constraint.getResult().serialize(this))
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
        sb.append(getCurrentIndentString())
          .append(constraint.getVariable().serialize(this))
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

    @Override
    public String serialize(ArithmeticConstraint arithmeticConstraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        // format: result = ( leftOperand op rightOperand )
        final StringBuilder sb = new StringBuilder();
        sb.append(getCurrentIndentString())
          .append(arithmeticConstraint.getResult().serialize(this))
          .append(" = ( ")
          .append(arithmeticConstraint.getLeftOperand().serialize(this))
          .append(" ")
          .append(arithmeticConstraint.getOperation().getSymbol())
          .append(" ")
          .append(arithmeticConstraint.getRightOperand().serialize(this))
          .append(" )");
        optionallyFormatAstPath(arithmeticConstraint, sb);
        showVerboseVars = prevShowVerboseVars;
        return sb.toString();
    }

    @Override
    public String serialize(ImplicationConstraint implicationConstraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        // format(non-empty): assumption1 & assumption 2 & ... & assumption n -> conclusion
        // format(empty): [ ] -> conclusion
        final StringBuilder sb = new StringBuilder();
        sb.append(getCurrentIndentString());
        int oldIndentationLevel = indentationLevel;
        // This is to avoid indentation for each sub constraint that comprises the implicationConstraint
        indentationLevel = 0;
        String assumptions = getAssumptionsString(implicationConstraint);
        String conclusion = implicationConstraint.getConclusion().serialize(this);
        sb.append(assumptions).append(" -> ").append(conclusion);
        // Recover the previous indentation level to not affect further serializations
        indentationLevel = oldIndentationLevel;
        showVerboseVars = prevShowVerboseVars;
        return sb.toString();
    }

    private String getAssumptionsString(ImplicationConstraint implicationConstraint) {
        if (implicationConstraint.getAssumptions().isEmpty()) {
            return "[ ]";
        }

        List<String> serializedAssumptions = new ArrayList<>();
        for (Constraint assumption : implicationConstraint.getAssumptions()) {
            serializedAssumptions.add(assumption.serialize(this));
        }

        return String.join(" & ", serializedAssumptions);
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
        // \u21A7 is a downward arrow symbol
        sb.append("[ \u21A7 "+ slot.getRefined().serialize(this) + " ]");
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
            sb.append(": combines ")
              .append(Arrays.asList(slot.getFirst(), slot.getSecond()));
            formatMerges(slot, sb);
            optionallyFormatAstPath(slot, sb);
        }
        return sb.toString();
    }

    @Override
    public String serialize(LubVariableSlot slot) {
        final StringBuilder sb = new StringBuilder();
        sb.append(slot.getId());

        if (showVerboseVars) {
            sb.append(": lub-of ");
            sb.append(Arrays.asList(slot.getLeft(), slot.getRight()));
            formatMerges(slot, sb);
            optionallyFormatAstPath(slot, sb);
        }
        return sb.toString();
    }

    /**
     * @return the indent string for the current indentation level as stored in
     *         {@link #indentationLevel}.
     */
    public String getCurrentIndentString() {
        return indentStrings.get(indentationLevel);
    }

    private void formatMerges(final VariableSlot slot, final StringBuilder sb) {
        if (!slot.getMergedToSlots().isEmpty()) {
            sb.append(": merged to -> ")
              .append(slot.getMergedToSlots());
        }
    }

    private void optionallyShowVerbose(final VariableSlot varSlot, final StringBuilder sb) {
        if (showVerboseVars) {
            formatMerges(varSlot, sb);
            optionallyFormatAstPath(varSlot, sb);
        }
    }

    private void optionallyFormatAstPath(final VariableSlot varSlot, final StringBuilder sb) {
        if (showAstPaths && (varSlot.isInsertable() || (varSlot.getLocation() != null))) {
            sb.append("\n")
              .append(getCurrentIndentString())
              .append("AstPath: ");
            if (varSlot.getLocation() == null) {
                sb.append("<NULL PATH>");
            } else {
                sb.append(varSlot.getLocation().toString());
            }
        }
    }

    private void optionallyFormatAstPath(final Constraint constraint, final StringBuilder sb) {
        if (showAstPaths) {
            sb.append("\n")
              .append(getCurrentIndentString())
              .append("AstPath: ");
            if (constraint.getLocation() == null) {
                sb.append("<NULL PATH>");
            } else {
                sb.append(constraint.getLocation().toString());
            }
        }
    }
}
