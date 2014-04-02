package checkers.inference.model.serialization;

import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;

import junit.framework.Assert;

import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.json.simple.parser.ParseException;
import org.junit.BeforeClass;
import org.junit.Test;

import checkers.inference.model.ComparableConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.InequalityConstraint;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;

public class TestSerialization {

    private static AnnotationMirror top;
    private static AnnotationMirror bottom;

    @BeforeClass
    public static void initMirrors() {
        top = mock(TestAnnotationMirror.class);
        mock(AnnotatedTypeMirror.class);
        bottom = mock(TestAnnotationMirror.class);
    }

    /**
     * Test that order doesn't matter in equality constraints.
     */
    @Test
    public void testEquality() {
        VariableSlot slot1 = new VariableSlot(null, 1);
        VariableSlot slot1a = new VariableSlot(null, 1);
        Assert.assertEquals(slot1, slot1a);

        VariableSlot slot2 = new VariableSlot(null, 2);

        // Test that order does not matter
        EqualityConstraint eqConst1 = new EqualityConstraint(slot1, slot2);
        EqualityConstraint eqConst1a = new EqualityConstraint(slot2, slot1);
        Assert.assertEquals(eqConst1, eqConst1a);
    }

    /**
     * Test that serialization and deserialization produces the same java objects
     *
     * @throws ParseException If something has really, really gone wrong.
     */
    @Test
    public void testSerialization() throws ParseException {

        AnnotationMirrorSerializer annotationSerializer = new SimpleAnnotationMirrorSerializer(top, bottom);

        List<Constraint> constraints = new ArrayList<Constraint>();
        VariableSlot slot1 = new VariableSlot(null, 1);
        VariableSlot slot2 = new VariableSlot(null, 2);
        ConstantSlot topSlot = new ConstantSlot(top);
        ConstantSlot botSlot = new ConstantSlot(bottom);

        constraints.add(new SubtypeConstraint(slot1, slot2));
        constraints.add(new SubtypeConstraint(slot1, topSlot));
        constraints.add(new SubtypeConstraint(botSlot, slot2));
        constraints.add(new EqualityConstraint(slot1, slot2));
        constraints.add(new InequalityConstraint(topSlot, botSlot));
        constraints.add(new ComparableConstraint(slot1, slot2));

        JsonSerializer serializer = new JsonSerializer(null, constraints, null, annotationSerializer);
        String serialized = serializer.generateConstraintFile().toJSONString();
        JsonDeserializer deserializer = new JsonDeserializer(annotationSerializer, serialized);
        List<Constraint> results = deserializer.parseConstraints();

        Assert.assertEquals(new HashSet<>(constraints), new HashSet<>(results));
    }

    // Use reference equality to have two distinct annotation mirrors (top and bottom).
    // Mockito allows us to not stub out other abstract methods in AnnotationMirror.
    private abstract class TestAnnotationMirror implements AnnotationMirror {

        @Override
        public boolean equals(Object o) {
            return o == this;
        }
    }
}

