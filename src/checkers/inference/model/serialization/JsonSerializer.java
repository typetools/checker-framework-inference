package checkers.inference.model.serialization;

import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import checkers.inference.model.ComparableConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.InequalityConstraint;
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

public class JsonSerializer implements Serializer {

    // Version of this format
    protected static final String VERSION_KEY = "version";

    // Constraints
    protected static final String CONSTRAINTS_KEY = "constraints";
    protected static final String CONSTRAINT_KEY = "constraint";

    protected static final String SUBTYPE_CONSTRAINT_KEY = "subtype";
    protected static final String SUBTYPE_SUB_KEY = "rhs";
    protected static final String SUBTYPE_SUPER_KEY = "lhs";

    protected static final String EQUALITY_CONSTRAINT_KEY = "equality";
    protected static final String EQUALITY_RHS = "rhs";
    protected static final String EQUALITY_LHS = "lhs";

    protected static final String INEQUALITY_CONSTRAINT_KEY = "inequality";
    protected static final String INEQUALITY_RHS = "rhs";
    protected static final String INEQUALITY_LHS = "lhs";

    protected static final String COMP_CONSTRAINT_KEY = "comparable";
    protected static final String COMP_RHS = "rhs";
    protected static final String COMP_LHS = "lhs";

    protected static final String VARIALBES_KEY = "variables";
    protected static final String VARIALBES_VALUE_KEY = "type_value";

    protected static final String VERSION = "1";

    protected static final String VAR_PREFIX = "var:";

    @SuppressWarnings("unused")
    private List<Slot> slots;
    private List<Constraint> constraints;
    private Map<Integer, AnnotationMirror> solutions;

    private AnnotationMirrorSerializer annotationSerializer;

    public JsonSerializer(List<Slot> slots,
            List<Constraint> constraints,
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
            result.put(VARIALBES_KEY, generateVariablesSection());
        }

        JSONArray constraints = new JSONArray();
        result.put(CONSTRAINTS_KEY, constraints);
        for (Constraint constraint : this.constraints) {
            JSONObject constraintObj = (JSONObject) constraint.serialize(this);
            constraints.add(constraintObj);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    protected JSONObject generateVariablesSection() {
        JSONObject variables = new JSONObject();
        for (Map.Entry<Integer, AnnotationMirror> entry: solutions.entrySet()) {
            JSONObject variable = new JSONObject();
            variable.put(VARIALBES_VALUE_KEY, getConstantString(entry.getValue()));
            variables.put(VAR_PREFIX + entry.getKey(), variable);
        }

        return variables;
    }

    protected String getConstantString(AnnotationMirror value) {
        return annotationSerializer.serialize(value);
    }

    @Override
    public String serialize(VariableSlot slot) {
        return VAR_PREFIX + slot.getId();
    }

    @Override
    public String serialize(ConstantSlot slot) {
        return getConstantString(slot.getValue());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object serialize(SubtypeConstraint constraint) {
        JSONObject obj = new JSONObject();
        obj.put(CONSTRAINT_KEY, SUBTYPE_CONSTRAINT_KEY);
        obj.put(SUBTYPE_SUB_KEY, constraint.getSubtype().serialize(this));
        obj.put(SUBTYPE_SUPER_KEY, constraint.getSupertype().serialize(this));
        return obj;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object serialize(EqualityConstraint constraint) {
        JSONObject obj = new JSONObject();
        obj.put(CONSTRAINT_KEY, EQUALITY_CONSTRAINT_KEY);
        obj.put(EQUALITY_LHS, constraint.getFirst().serialize(this));
        obj.put(EQUALITY_RHS, constraint.getSecond().serialize(this));
        return obj;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object serialize(InequalityConstraint constraint) {
        JSONObject obj = new JSONObject();
        obj.put(CONSTRAINT_KEY, INEQUALITY_CONSTRAINT_KEY);
        obj.put(INEQUALITY_LHS, constraint.getFirst().serialize(this));
        obj.put(INEQUALITY_RHS, constraint.getSecond().serialize(this));
        return obj;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object serialize(ComparableConstraint constraint) {
        JSONObject obj = new JSONObject();
        obj.put(CONSTRAINT_KEY, COMP_CONSTRAINT_KEY);
        obj.put(COMP_LHS, constraint.getFirst().serialize(this));
        obj.put(COMP_RHS, constraint.getSecond().serialize(this));
        return obj;
    }
}
