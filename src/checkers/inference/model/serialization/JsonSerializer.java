package checkers.inference.model.serialization;

import java.util.Collection;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.model.LubVariableSlot;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import checkers.inference.model.ArithmeticConstraint;
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
 *

// Scores are numeric
// Everything else is a string (including version and qualifier ids)
// Game side ignores any key in a map prefixed with "system-"
// Variables are prefixed with "var:"
// Types are prefixed with "type:"

// Variable values are set in the "variables": "var:ID": "type_value": key.

{
  "version": "1",

  "scoring": {
    "constraints": 1000,
    "variables": { "type:0" : 0, "type:1": 100 }
  },

  // Extra configurations on variables
  // Listing variables here is optional
  "variables": {
    "var:10" : {
      "type_value": "type:0",
      "keyfor_value" : [],'
      "score": { "type:0" : 0, "type:1": 1000 },
      "possible_keyfor": ["mymap1", "mymap2"]
    }
  },

  "constraints": [
    // Format 1
    "var:10 <= type:0",

    // Subtype
    { "constraint" : "subtype", // subtype, equality, inequality
      "lhs" : "var:1",
      "rhs": "var:2"
      "score": 100
    },

    // Map.get
    { "constraint": "map.get",
      "name": "mymap1",
      "value_type": "var:1",
      "key": "var:2",
      "result": "var:3"
    },

    // If Node
    { "constraint": "selection_check",
      "id": "var:11",
      "type": "type:0",
      "then": [ ... ], // Nested list of constraints
      "else": [ ... ],
    },

    // Generics
    { "constraint": "enabled_check",
      "id" : "var:12",
      "then": [ ... ],
      "else": [ ... ],
    }
  ]
}

 * @author mcarthur
 *
 */

public class JsonSerializer implements Serializer<String, JSONObject> {

    // Version of this format
    protected static final String VERSION_KEY = "version";

    // Constraints
    protected static final String CONSTRAINTS_KEY = "constraints";
    protected static final String CONSTRAINT_KEY = "constraint";

    protected static final String SUBTYPE_CONSTRAINT_KEY = "subtype";
    protected static final String SUBTYPE_SUB_KEY = "sub";
    protected static final String SUBTYPE_SUPER_KEY = "sup";

    protected static final String EQUALITY_CONSTRAINT_KEY = "equality";
    protected static final String EQUALITY_RHS = "rhs";
    protected static final String EQUALITY_LHS = "lhs";

    protected static final String INEQUALITY_CONSTRAINT_KEY = "inequality";
    protected static final String INEQUALITY_RHS = "rhs";
    protected static final String INEQUALITY_LHS = "lhs";

    protected static final String COMP_CONSTRAINT_KEY = "comparable";
    protected static final String COMP_RHS = "rhs";
    protected static final String COMP_LHS = "lhs";

    protected static final String COMB_CONSTRAINT_KEY = "combine";
    protected static final String COMB_TARGET = "target";
    protected static final String COMB_DECL = "declared";
    protected static final String COMB_RESULT = "result";

    protected static final String PREFERENCE_CONSTRAINT_KEY = "preference";
    protected static final String PREFERENCE_VARIABLE = "variable";
    protected static final String PREFERENCE_GOAL = "goal";
    protected static final String PREFERENCE_WEIGHT = "weight";

    protected static final String VARIABLES_KEY = "variables";
    protected static final String VARIABLES_VALUE_KEY = "type_value";

    protected static final String EXISTENTIAL_VARIABLES_KEY = "enabled_vars";
    protected static final String EXISTENTIAL_CONSTRAINT_KEY = "enabled_check";
    protected static final String EXISTENTIAL_ID = "id";
    protected static final String EXISTENTIAL_THEN = "then";
    protected static final String EXISTENTIAL_ELSE = "else";

    protected static final String ARITH_LEFT_OPERAND = "left_operand";
    protected static final String ARITH_RIGHT_OPERAND = "right_operand";
    protected static final String ARITH_RESULT = "result";

    protected static final String VERSION = "2";

    protected static final String VAR_PREFIX = "var:";

    @SuppressWarnings("unused")
    private final Collection<Slot> slots;
    private final Collection<Constraint> constraints;
    private final Map<Integer, AnnotationMirror> solutions;

    private AnnotationMirrorSerializer annotationSerializer;

    public JsonSerializer(Collection<Slot> slots,
                          Collection<Constraint> constraints,
                          Map<Integer, AnnotationMirror> solutions,
                          AnnotationMirrorSerializer annotationSerializer) {

        this.slots = slots;
        this.constraints = constraints;
        this.solutions = solutions;
        this.annotationSerializer = annotationSerializer;
    }

    @SuppressWarnings("unchecked")
    public JSONObject generateConstraintFile() {
        JSONObject result = new JSONObject();
        result.put(VERSION_KEY,  VERSION);

        if (solutions != null && solutions.size() > 0) {
            result.put(VARIABLES_KEY, generateVariablesSection());
        }

        result.put(CONSTRAINTS_KEY, constraintsToJsonArray(constraints));
        return result;
    }

    @SuppressWarnings("unchecked")
    protected JSONObject generateVariablesSection() {
        JSONObject variables = new JSONObject();
        for (Map.Entry<Integer, AnnotationMirror> entry: solutions.entrySet()) {
            JSONObject variable = new JSONObject();
            variable.put(VARIABLES_VALUE_KEY, getConstantString(entry.getValue()));
            variables.put(VAR_PREFIX + entry.getKey(), variable);
        }

        return variables;
    }

    protected JSONArray constraintsToJsonArray(final Collection<Constraint> constraints) {
        JSONArray jsonConstraints = new JSONArray();
        for (Constraint constraint : constraints) {
            JSONObject constraintObj = constraint.serialize(this);
            if (constraintObj != null) {
                jsonConstraints.add(constraintObj);
            }
        }
        return jsonConstraints;
    }

    protected String getConstantString(AnnotationMirror value) {
        return annotationSerializer.serialize(value);
    }

    @Override
    public String serialize(VariableSlot slot) {
        return VAR_PREFIX + slot.getId();
    }

    @Override
    public String serialize(RefinementVariableSlot slot) {
        return serialize((VariableSlot) slot);
    }

    @Override
    public String serialize(ExistentialVariableSlot slot) {
        throw new UnsupportedOperationException("Existential slots should be normalized away before serialization.");
    }


    @Override
    public String serialize(ConstantSlot slot) {
        return getConstantString(slot.getValue());
    }

    @Override
    public String serialize(CombVariableSlot slot) {
        return serialize((VariableSlot) slot);
    }

    @Override
    public String serialize(LubVariableSlot slot) {
        return serialize((VariableSlot) slot);
    }

    @SuppressWarnings("unchecked")
    @Override
    public JSONObject serialize(SubtypeConstraint constraint) {
        if (constraint.getSubtype() == null || constraint.getSupertype() == null) {
            return null;
        }

        JSONObject obj = new JSONObject();
        obj.put(CONSTRAINT_KEY, SUBTYPE_CONSTRAINT_KEY);
        obj.put(SUBTYPE_SUB_KEY, constraint.getSubtype().serialize(this));
        obj.put(SUBTYPE_SUPER_KEY, constraint.getSupertype().serialize(this));
        return obj;
    }

    @SuppressWarnings("unchecked")
    @Override
    public JSONObject serialize(EqualityConstraint constraint) {
        if (constraint.getFirst() == null || constraint.getSecond() == null) {
            return null;
        }

        JSONObject obj = new JSONObject();
        obj.put(CONSTRAINT_KEY, EQUALITY_CONSTRAINT_KEY);
        obj.put(EQUALITY_LHS, constraint.getFirst().serialize(this));
        obj.put(EQUALITY_RHS, constraint.getSecond().serialize(this));
        return obj;
    }


    @Override
    public JSONObject serialize(ExistentialConstraint constraint) {

        JSONObject obj = new JSONObject();
        obj.put(CONSTRAINT_KEY, EXISTENTIAL_CONSTRAINT_KEY);
        obj.put(EXISTENTIAL_ID,   constraint.getPotentialVariable().serialize(this));
        obj.put(EXISTENTIAL_THEN, constraintsToJsonArray(constraint.potentialConstraints()));
        obj.put(EXISTENTIAL_ELSE, constraintsToJsonArray(constraint.getAlternateConstraints()));
        return obj;
    }

    @SuppressWarnings("unchecked")
    @Override
    public JSONObject serialize(InequalityConstraint constraint) {
        if (constraint.getFirst() == null || constraint.getSecond() == null) {
            return null;
        }

        JSONObject obj = new JSONObject();
        obj.put(CONSTRAINT_KEY, INEQUALITY_CONSTRAINT_KEY);
        obj.put(INEQUALITY_LHS, constraint.getFirst().serialize(this));
        obj.put(INEQUALITY_RHS, constraint.getSecond().serialize(this));
        return obj;
    }

    @SuppressWarnings("unchecked")
    @Override
    public JSONObject serialize(ComparableConstraint constraint) {
        if (constraint.getFirst() == null || constraint.getSecond() == null) {
            return null;
        }

        JSONObject obj = new JSONObject();
        obj.put(CONSTRAINT_KEY, COMP_CONSTRAINT_KEY);
        obj.put(COMP_LHS, constraint.getFirst().serialize(this));
        obj.put(COMP_RHS, constraint.getSecond().serialize(this));
        return obj;
    }

    @SuppressWarnings("unchecked")
    @Override
    public JSONObject serialize(CombineConstraint constraint) {
        if (constraint.getTarget() == null || constraint.getDeclared() == null || constraint.getResult() == null) {
            return null;
        }

        JSONObject obj = new JSONObject();
        obj.put(CONSTRAINT_KEY, COMB_CONSTRAINT_KEY);
        obj.put(COMB_TARGET, constraint.getTarget().serialize(this));
        obj.put(COMB_DECL, constraint.getDeclared().serialize(this));
        obj.put(COMB_RESULT, constraint.getResult().serialize(this));
        return obj;
    }

    @SuppressWarnings("unchecked")
    @Override
    public JSONObject serialize(PreferenceConstraint constraint) {
        if (constraint.getVariable() == null || constraint.getGoal() == null) {
            return null;
        }

        JSONObject obj = new JSONObject();
        obj.put(CONSTRAINT_KEY, PREFERENCE_CONSTRAINT_KEY);
        obj.put(PREFERENCE_VARIABLE, constraint.getVariable().serialize(this));
        obj.put(PREFERENCE_GOAL, constraint.getGoal().serialize(this));
        // TODO: is the int showing up correctly in JSON?
        obj.put(PREFERENCE_WEIGHT, constraint.getWeight());
        return obj;
    }

    @SuppressWarnings("unchecked")
    @Override
    public JSONObject serialize(ArithmeticConstraint constraint) {
        JSONObject obj = new JSONObject();
        obj.put(CONSTRAINT_KEY, constraint.getOperation().name().toLowerCase());
        obj.put(ARITH_LEFT_OPERAND, constraint.getLeftOperand().serialize(this));
        obj.put(ARITH_RIGHT_OPERAND, constraint.getRightOperand().serialize(this));
        obj.put(ARITH_RESULT, constraint.getResult().serialize(this));
        return obj;
    }
}
