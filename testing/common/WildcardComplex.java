interface Collection<T0> {}
interface List<T1> extends Collection<T1> {}
class ArrayList<T1> implements List<T1> {}

class WildcardComplex<T> {
	List<? extends Collection<T>> something;

	public static void method( String param ) {
	}
	
}