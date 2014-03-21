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


class PrintBase<X2, Y2> extends Base<X2,Y2> {
	public PrintBase(final X2 x, final Y2 y) {
		super( x, y );
	}
	
	public void set(X2 x) {
		this.x = x;
	}
}

class Instantiate {
	public void create() {
		final Base<Integer, String> bis = new PrintBase<Integer,String>(new Integer(0), "Y");
		bis.set(new Integer(3));
	}

}


