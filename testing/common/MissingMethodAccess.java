import java.util.*;

/**
 * This test was created to test methods that have no constraint
 * variables in them but will generate a constraint (see localAccess
 * below).
 */
 
public class MissingMethodAccess {
	
	public void access() {
	}
}

class OtherAccess {
	private MissingMethodAccess mmAccess;
	
	public OtherAccess() {
		mmAccess = new MissingMethodAccess();
	}
	
	/**
	 * Local access has no constraint variables because it has
	 * 0 local variables, 0 parameters, and a void return type (note you
	 * can also mimic this with primitive parameters as they generate no
	 * variables) 
	 * There are, however, field access constraint and CallInstanceMethod
	 * constraints generated
	 */
	 public void localAccess() {
		mmAccess.access();
	}
}
