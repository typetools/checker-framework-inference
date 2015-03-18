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


class PrintBase<X2> extends Base<X2, String> {
	public PrintBase(final X2 x, final String y) {
		super( x, y );
	}
	
	public void set(X2 x) {
		this.x = x;
	}
}

class Instantiate {
	public void create() {
		final Base<Integer, String> bis = new PrintBase<Integer>(new Integer(0), "Y");
		bis.set(new Integer(3));
	}

}


