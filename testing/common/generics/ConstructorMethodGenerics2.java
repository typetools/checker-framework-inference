class MyConstructor {
	
	<E extends Object> MyConstructor(E e) {
	}
	
	public static void main() {
		final MyConstructor ms = new MyConstructor("string");
	}
}