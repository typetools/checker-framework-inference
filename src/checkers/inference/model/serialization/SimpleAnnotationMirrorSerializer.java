package checkers.inference.model.serialization;

import javax.lang.model.element.AnnotationMirror;

/**
 * A simple implementation of AnnotationMirrorSerializer.
 *
 * <p>Only works on type systems with two types in a hierarchy (and one is strictly a subtype of the
 * other).
 *
 * <p>The serialized format is what the game expects: "type:0" and "type:1"
 *
 * @author mcarthur
 */
public class SimpleAnnotationMirrorSerializer implements AnnotationMirrorSerializer {

    private static final String TOP_STR = "type:1";
    private static final String BOTTOM_STR = "type:0";

    private AnnotationMirror top;
    private AnnotationMirror bottom;

    public SimpleAnnotationMirrorSerializer(AnnotationMirror top, AnnotationMirror bottom) {
        this.top = top;
        this.bottom = bottom;
    }

    @Override
    public AnnotationMirror deserialize(String amStr) {
        if (TOP_STR.equals(amStr)) {
            return top;
        } else if (BOTTOM_STR.equals(amStr)) {
            return bottom;
        } else {
            throw new IllegalArgumentException(
                    String.format(
                            "AnnotationMirror: %s could not be deserialzed by this class.", amStr));
        }
    }

    @Override
    public String serialize(AnnotationMirror am) {
        if (top.toString().equals(am.toString())) {
            return TOP_STR;
        } else if (bottom.toString().equals(am.toString())) {
            return BOTTOM_STR;
        } else {
            throw new IllegalArgumentException(
                    String.format(
                            "AnnotationMirror: %s could not be serialzed by this class.", am));
        }
    }
}
