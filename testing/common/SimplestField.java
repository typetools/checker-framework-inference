
import ostrusted.quals.*;

class SimplestField {
	private @OsUntrusted String s;

	public void s() {
		final String s2 = "a";
		s = s2;
	}
}
