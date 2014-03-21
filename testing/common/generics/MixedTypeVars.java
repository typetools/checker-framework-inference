class Test<A,B> {
	void method() {}
}

class Harness<C,D> {
	void context1(Test<String, D> td) {
		
		td.method();
	}
}