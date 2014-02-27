import java.util.*;

public class BoxingError<A, B> {
	public <C, D>  D toAccess(C c1, D d1) {
		return d1;	
	}

	public void localAccess() {
		toAccess('c', "BLAG");
	}
}

class OtherAccess {
	private BoxingError<String, Integer> gfAccess;
	
	public OtherAccess() {
		gfAccess = new BoxingError<String, Integer>();
	}
	
	public void localAccess() {
		gfAccess.toAccess('c', new Object());
	}
}
