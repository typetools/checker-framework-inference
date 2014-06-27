import java.util.*;

public class GenericFieldAccess<A, B> {
	
	public void access() {
	}
}

class OtherAccess {
	private GenericFieldAccess<String, Integer> gfAccess;
	
	public OtherAccess() {
		gfAccess = new GenericFieldAccess<String, Integer>();
	}
	
	public void localAccess() {
		gfAccess.access();
	}
}
