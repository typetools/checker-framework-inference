import checkers.nullness.quals.*;

interface List<L> {}
class ArrayList<AL> implements List<AL> {}

class Prev<E extends List<Integer>> {
	void method1( E arg1 ) {}
}

class LinkingTest<T extends List<String>> extends Prev<List<Integer>> {
	void method2(List<T> arg2) {}
	void method3(List<String> arg3) {}
	void method4(List<String> arg4) {}
	
	void context(T t) {
	
		method1( new ArrayList<Integer>() );
     	//method2( new ArrayList<List<String>>()  );
	    method3( t );	
	    method4( new ArrayList<String>() );
	}
	
}