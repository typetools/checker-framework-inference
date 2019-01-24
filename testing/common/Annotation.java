@interface KeyFor {
    /**
     * Java expression(s) that evaluate to a map for which the annotated type is a key.
     * @checker_framework.manual #java-expressions-as-arguments Syntax of Java expressions
     */
    public String[] value();
}


@interface Metric {

    public enum Type {
        DEFAULT, COUNTER, GAUGE, TAG
    }

    /**
     * Shorthand for optional name and description
     * @return {description} or {name, description}
     */
    String[] value() default {};

    /**
     * @return optional description of the metric
     */
    String about() default "";

    /**
     * @return optional sample name for MutableStat/Rate/Rates
     */
    String sampleName() default "Ops";

    /**
     * @return optional value name for MutableStat/Rate/Rates
     */
    String valueName() default "Time";

    /**
     * @return true to create a metric snapshot even if unchanged.
     */
    boolean always() default false;

    /**
     * @return optional type (counter|gauge) of the metric
     */
    Type type() default Type.DEFAULT;
}


class Annotation {

   @KeyFor({"a","b","c"}) String test = "";
}
