package checkers.inference.model.serialization;

import static checkers.inference.model.serialization.JsonSerializer.CONSTRAINTS_KEY;
import static checkers.inference.model.serialization.JsonSerializer.CONSTRAINT_KEY;
import static checkers.inference.model.serialization.JsonSerializer.EQUALITY_CONSTRAINT_KEY;
import static checkers.inference.model.serialization.JsonSerializer.EQUALITY_LHS;
import static checkers.inference.model.serialization.JsonSerializer.EQUALITY_RHS;
import static checkers.inference.model.serialization.JsonSerializer.INEQUALITY_CONSTRAINT_KEY;
import static checkers.inference.model.serialization.JsonSerializer.INEQUALITY_LHS;
import static checkers.inference.model.serialization.JsonSerializer.INEQUALITY_RHS;
import static checkers.inference.model.serialization.JsonSerializer.SUBTYPE_CONSTRAINT_KEY;
import static checkers.inference.model.serialization.JsonSerializer.SUBTYPE_SUB_KEY;
import static checkers.inference.model.serialization.JsonSerializer.SUBTYPE_SUPER_KEY;
import static checkers.inference.model.serialization.JsonSerializer.VAR_PREFIX;

import java.util.LinkedList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.InequalityConstraint;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;

/**
 * Class to convert a String (this is a formatted json constraint file) into a list of inference Constraints.
 * 
 * TODO: Support nested constraints
 * 
 * @author mcarthur
 *
 */
public class JsonDeserializer {
    
    private static final String SUBTYPE_STR = "<=";

    private AnnotationMirrorSerializer annotationSerializer;

    public JsonDeserializer(AnnotationMirrorSerializer annotationSerializer) {
        this.annotationSerializer = annotationSerializer;
    }
    
    public List<Constraint> parseConstraints(String json) throws ParseException {
        
        List<Constraint> results = new LinkedList<Constraint>();
        
        JSONParser parser = new JSONParser();
        JSONObject root = (JSONObject) parser.parse(json);
        JSONArray constraints = (JSONArray) root.get(CONSTRAINTS_KEY);
        for (Object obj: constraints) {
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
                results.add(new SubtypeConstraint(sub, sup));
            } else if (obj instanceof JSONObject) {
                JSONObject constraint = (JSONObject) obj;
                String constraintType = (String) constraint.get(CONSTRAINT_KEY);
                if (SUBTYPE_CONSTRAINT_KEY.equals(constraintType)) {
                    Slot lhs = parseSlot((String) constraint.get(SUBTYPE_SUPER_KEY));
                    Slot rhs = parseSlot((String) constraint.get(SUBTYPE_SUB_KEY));
                    results.add(new SubtypeConstraint(rhs, lhs));
                } else if (EQUALITY_CONSTRAINT_KEY.equals(constraintType)) {
                    Slot lhs = parseSlot((String) constraint.get(EQUALITY_LHS));
                    Slot rhs = parseSlot((String) constraint.get(EQUALITY_RHS));
                    results.add(new EqualityConstraint(lhs, rhs));
                } else if (INEQUALITY_CONSTRAINT_KEY.equals(constraintType)) {
                    Slot lhs = parseSlot((String) constraint.get(INEQUALITY_LHS));
                    Slot rhs = parseSlot((String) constraint.get(INEQUALITY_RHS));
                    results.add(new InequalityConstraint(lhs, rhs));
                } else {
                    throw new IllegalArgumentException("Parse error: unknown constraint type: " + obj);
                }
                // TODO: map.get, enabled_check, selection_check
            } else {
                throw new IllegalArgumentException("Parse error: unexpected json value: " + obj);
            }
        }
        return results;
    }
    
    private Slot parseSlot(String slot) {
        if (slot.startsWith(VAR_PREFIX)) {
            int id = new Integer(slot.split(":")[1]);
            return new VariableSlot(id);
        } else {
            AnnotationMirror value = annotationSerializer.deserialize(slot);
            return new ConstantSlot(value);
        }
    }
}
