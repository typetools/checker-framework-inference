package checkers.inference.model.serialization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import checkers.inference.InferenceMain;
import checkers.inference.model.Constraint;
import checkers.inference.model.ConstraintManager;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import static checkers.inference.model.serialization.JsonSerializer.COMP_CONSTRAINT_KEY;
import static checkers.inference.model.serialization.JsonSerializer.CONSTRAINTS_KEY;
import static checkers.inference.model.serialization.JsonSerializer.CONSTRAINT_KEY;
import static checkers.inference.model.serialization.JsonSerializer.EQUALITY_CONSTRAINT_KEY;
import static checkers.inference.model.serialization.JsonSerializer.EQUALITY_LHS;
import static checkers.inference.model.serialization.JsonSerializer.EQUALITY_RHS;
import static checkers.inference.model.serialization.JsonSerializer.EXISTENTIAL_CONSTRAINT_KEY;
import static checkers.inference.model.serialization.JsonSerializer.EXISTENTIAL_ELSE;
import static checkers.inference.model.serialization.JsonSerializer.EXISTENTIAL_ID;
import static checkers.inference.model.serialization.JsonSerializer.EXISTENTIAL_THEN;
import static checkers.inference.model.serialization.JsonSerializer.EXISTENTIAL_VARIABLES_KEY;
import static checkers.inference.model.serialization.JsonSerializer.INEQUALITY_CONSTRAINT_KEY;
import static checkers.inference.model.serialization.JsonSerializer.INEQUALITY_LHS;
import static checkers.inference.model.serialization.JsonSerializer.INEQUALITY_RHS;
import static checkers.inference.model.serialization.JsonSerializer.SUBTYPE_CONSTRAINT_KEY;
import static checkers.inference.model.serialization.JsonSerializer.SUBTYPE_SUB_KEY;
import static checkers.inference.model.serialization.JsonSerializer.SUBTYPE_SUPER_KEY;
import static checkers.inference.model.serialization.JsonSerializer.VARIABLES_KEY;
import static checkers.inference.model.serialization.JsonSerializer.VARIABLES_VALUE_KEY;
import static checkers.inference.model.serialization.JsonSerializer.VAR_PREFIX;

/**
 * Class to convert a String (this is a formatted json constraint file) into a list of inference Constraints.
 *
 * The format of the json constraint file is documented in JsonSerializer.java.
 *
 * TODO: Support nested constraints
 *
 * @author mcarthur
 *
 */
public class JsonDeserializer {

    private static final String SUBTYPE_STR = "<=";

    protected AnnotationMirrorSerializer annotationSerializer;

    protected JSONObject root;

    private ConstraintManager constraintManager;

    public JsonDeserializer(AnnotationMirrorSerializer annotationSerializer, String json) throws ParseException {
        this.annotationSerializer = annotationSerializer;
        JSONParser parser = new JSONParser();
        this.root = (JSONObject) parser.parse(json);
        this.constraintManager = InferenceMain.getInstance().getConstraintManager();
    }

    public List<Constraint> parseConstraints() throws ParseException {
        JSONArray constraints = (JSONArray) root.get(CONSTRAINTS_KEY);
        List<Constraint> results = jsonArrayToConstraints(constraints);
        return results;
    }

    public List<Constraint> jsonArrayToConstraints(final JSONArray jsonConstraints) {
        List<Constraint> results = new LinkedList<Constraint>();

        for (Object obj: jsonConstraints) {
            if (obj instanceof String) {
                String constraintStr = (String) obj;
                String[] parts = constraintStr.trim().split(" ");
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Parse error: could not parse constraint: " + obj);
                } else if (!SUBTYPE_STR.equals(parts[1])) {
                    throw new IllegalArgumentException("Parse error: found unexpected constraint operation: " + obj);
                }
                Slot sub = parseSlot(parts[0]);
                Slot sup = parseSlot(parts[2]);
                results.add(constraintManager.createSubtypeConstraint(sub, sup));
            } else if (obj instanceof JSONObject) {
                JSONObject constraint = (JSONObject) obj;
                String constraintType = (String) constraint.get(CONSTRAINT_KEY);
                if (SUBTYPE_CONSTRAINT_KEY.equals(constraintType)) {
                    Slot lhs = parseSlot((String) constraint.get(SUBTYPE_SUPER_KEY));
                    Slot rhs = parseSlot((String) constraint.get(SUBTYPE_SUB_KEY));
                    results.add(constraintManager.createSubtypeConstraint(rhs, lhs));
                } else if (EQUALITY_CONSTRAINT_KEY.equals(constraintType)) {
                    Slot lhs = parseSlot((String) constraint.get(EQUALITY_LHS));
                    Slot rhs = parseSlot((String) constraint.get(EQUALITY_RHS));
                    results.add(constraintManager.createEqualityConstraint(lhs, rhs));
                } else if (INEQUALITY_CONSTRAINT_KEY.equals(constraintType)) {
                    Slot lhs = parseSlot((String) constraint.get(INEQUALITY_LHS));
                    Slot rhs = parseSlot((String) constraint.get(INEQUALITY_RHS));
                    results.add(constraintManager.createInequalityConstraint(lhs, rhs));
                } else if (COMP_CONSTRAINT_KEY.equals(constraintType)) {
                    Slot lhs = parseSlot((String) constraint.get(INEQUALITY_LHS));
                    Slot rhs = parseSlot((String) constraint.get(INEQUALITY_RHS));
                    results.add(constraintManager.createComparableConstraint(lhs, rhs));
                } else if (EXISTENTIAL_CONSTRAINT_KEY.equals(constraintType)) {
                    Slot potential = parseSlot((String) constraint.get(EXISTENTIAL_ID));
                    List<Constraint> thenConstraints =
                            jsonArrayToConstraints((JSONArray) constraint.get(EXISTENTIAL_THEN));
                    List<Constraint> elseConstraints =
                            jsonArrayToConstraints((JSONArray) constraint.get(EXISTENTIAL_ELSE));
                    results.add(constraintManager.createExistentialConstraint((VariableSlot) potential,
                            thenConstraints, elseConstraints));
                }  else {
                    throw new IllegalArgumentException("Parse error: unknown constraint type: " + obj);
                }
                // TODO: map.get, enabled_check, selection_check
            } else {
                throw new IllegalArgumentException("Parse error: unexpected json value: " + obj);
            }
        }
        return results;
    }

    public List<String> getPotentialVariables() {
        Set<String> potentialVars = findPotentialVars((JSONArray) root.get(CONSTRAINTS_KEY), new LinkedHashSet<String>());
        return new ArrayList<>(potentialVars);

    }

    private Set<String> findPotentialVars(JSONArray constraints, Set<String> potentialVariableIds) {

        for (Object jsonObj : constraints) {
            if (jsonObj instanceof JSONObject) {
                JSONObject constraint = (JSONObject) jsonObj;

                String constraintType = (String) constraint.get(CONSTRAINT_KEY);
                if (constraintType.equals(EXISTENTIAL_CONSTRAINT_KEY)) {
                    VariableSlot potential = (VariableSlot) parseSlot((String) constraint.get(EXISTENTIAL_ID));
                    potentialVariableIds.add(String.valueOf(potential.getId()));

                    Object thenConstraints = constraint.get(EXISTENTIAL_THEN);
                    if (thenConstraints != null && thenConstraints instanceof JSONArray) {
                        findPotentialVars((JSONArray) thenConstraints, potentialVariableIds);
                    }

                    Object elseConstraints = constraint.get(EXISTENTIAL_ELSE);
                    if (elseConstraints != null && elseConstraints instanceof JSONArray) {
                        findPotentialVars((JSONArray) elseConstraints, potentialVariableIds);
                    }
                }
            }
        }

        return potentialVariableIds;
    }

    public Set<String> getEnabledVars() {
        HashSet<String> enabledVars = new HashSet<>();

        JSONArray enabledVarsArray = (JSONArray) root.get(EXISTENTIAL_VARIABLES_KEY);

        for (Object varObj : enabledVarsArray) {
            String var = (String) varObj;

            String variableId = var.split(":")[1];
            enabledVars.add(variableId);
        }

        return enabledVars;
    }

    public Map<String, String> getAnnotationValues() {
        Map<String, String> results = new HashMap<>();

        JSONObject variablesMap = (JSONObject) root.get(VARIABLES_KEY);
        @SuppressWarnings("unchecked")
        Set<Map.Entry<?, ?>> entries = variablesMap.entrySet();
        for (Map.Entry<?, ?> e : entries) {
            String variableId = (String) e.getKey();

            // in the first results from v.12 the output solved JSON had two different formats in the Variables section
            String variableType;
            if (e.getValue() instanceof JSONObject) {
                variableType = (String) ((JSONObject) e.getValue()).get(VARIABLES_VALUE_KEY);
            } else {
                variableType = (String) e.getValue();
            }

            variableId = variableId.split(":")[1];
            variableType = variableType.split(":")[1];
            results.put(variableId, variableType);
        }

        return results;
    }

    private Slot parseSlot(String slot) {
        if (slot.startsWith(VAR_PREFIX)) {
            int id = Integer.valueOf(slot.split(":")[1]);
            return new VariableSlot(id);
        } else {
            // TODO: THIS NEEDS FIXING
            AnnotationMirror value = annotationSerializer.deserialize(slot);
            return InferenceMain.getInstance().getSlotManager().createConstantSlot(value);
        }
    }
}
