public class TestInt {

	Integer a = 1;

	int b =
    // :: warning: (cast.unsafe.constructor.invocation)
    new Integer(1);

	int c = 1;

    Integer d =
    // :: warning: (cast.unsafe.constructor.invocation)
    new Integer(1);
}
