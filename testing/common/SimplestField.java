
import checkers.nullness.quals.Nullable;

class SimplestField {
	private @Nullable String s;

	public void s() {
		final String s2 = "a";
		s = s2;
	}
}