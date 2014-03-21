class Outer {

	public static class Inner<T1> {
	}

	public Inner<String> method() {
		return new Implementor<String>();
	}
}

class Implementor<T2> extends Outer.Inner<T2> {
}