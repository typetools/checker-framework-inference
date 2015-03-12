
import trusted.quals.*;

class SimplestField {
	private @Untrusted String s;

	public void s() {
		final String s2 = "a";
		s = s2;
	}
}
