package checkers.inference.model.serialization;

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

import java.util.*;

/**
 * This Serializer is meant only to convert constraints and variables to
 * human readable strings.  It is used currently by the DebugSolver.
 */
public class ToStringSerializer implements Serializer {
    private final boolean showAstPaths;
    private boolean showVerboseVars;

    private int indent = 0;
    public static final String INDENT_STRING = "    ";

    public ToStringSerializer(boolean showAstPaths) {
        this.showAstPaths = showAstPaths;
        this.showVerboseVars = true;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    public int getIndent() {
        return indent;
    }

    public String serializeSlots(Iterable<Slot> slots, String delimiter) {
        List<String> slotStrings = new ArrayList<>();

        boolean first = true;
        for (Slot slot : slots) {
            String constraintString = first ? "" : delimiter;
            constraintString += slot.serialize(this);
            slotStrings.add(constraintString);
            first = false;
        }

        // Sort list so that the output string is always in the same order
        Collections.sort(slotStrings);

        StringBuilder sb = new StringBuilder();
        for (String string : slotStrings) {
            sb.append(string);
        }

        return sb.toString();
    }

    public String serializeConstraints(Iterable<Constraint> constraints, String delimiter) {
        List<String> constraintStrings = new ArrayList<>();

        boolean first = true;
        for (Constraint constraint : constraints) {
            String constraintString = first ? "" : delimiter +
                    constraint.serialize(this);
            constraintStrings.add(constraintString);
            first = false;
        }

        // Sort list so that the output string is always in the same order
        Collections.sort(constraintStrings);

        StringBuilder sb = new StringBuilder();
        for (String string : constraintStrings) {
            sb.append(string);
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
    public Object serialize(CombineConstraint constraint) {
        boolean prevShowVerboseVars = showVerboseVars;
        showVerboseVars = false;
        String result = indent(constraint.getResult().serialize(this) + " = ( "
                + constraint.getDeclared().serialize(this) + " + " + constraint.getTarget() + " )");
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

    //variables
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
        RefinementVariableSlot refSlot = (RefinementVariableSlot) slot;
        sb.append(refSlot.getId());
        sb.append(": refines ");
        sb.append(Arrays.asList(refSlot.getRefined()));
        optionallyShowVerbose(refSlot, sb);
        return sb.toString();
    }

    @Override
    public String serialize(ConstantSlot slot) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("( ");
        stringBuilder.append(slot.getId());
        stringBuilder.append(" => ");
        String fullAnno = slot.getValue().toString();

        int index = fullAnno.lastIndexOf('.');
        if (index > -1) {
            stringBuilder.append("@");
            stringBuilder.append(fullAnno.substring(index + 1, fullAnno.length()));
        } else {
            stringBuilder.append(fullAnno);
        }

        stringBuilder.append(" )");
        return stringBuilder.toString();
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
        if (indent == 0) {
            return str;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append(INDENT_STRING);
        }

        sb.append(str);
        return sb.toString();
    }

    protected void formatMerges(final VariableSlot slot, final StringBuilder sb) {
        if(!slot.getMergedToSlots().isEmpty()) {
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
        if(showAstPaths && (varSlot.isInsertable() || (varSlot.getLocation() != null))) {
            sb.append("\n:AstPath:\n");
            if(varSlot.getLocation() == null) {
                sb.append("<NULL PATH>");
            } else {
                sb.append(varSlot.getLocation().toString());
            }
            sb.append("\n");
        }
    }
}
