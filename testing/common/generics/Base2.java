package generics;


abstract class Base<X, Y> {
	protected X x;
	protected Y y;

	public Base(final X x, final Y y) {
		this.x = x;
		this.y = y;
	}

	abstract public void set(X x);
}


class PrintBase<X, Y> extends Base<X,Y> {
	public PrintBase(final X x, final Y y) {
		super( x, y );
	}
	
	public void set(X x) {
		this.x = x;
	}
}

/*class Other<D> {
	private D d;
	
	public Other(D d) {
		this.d = d;
	}
	
	public D get() {
		return d;
	}
}

class BaseList<X, Z> extends Base<X, Other<Z>> {
	public void doSomething(X x) {
		Z z = y.get();
	}
}*/

class Instantiate {
	public void create() {
		final Base<Integer, String> bis = new PrintBase<Integer,String>(new Integer(0), "Y");
		bis.set(new Integer(3));
	}

}


