
import ostrusted.quals.*;

class GenericsAndLocals<@OsTrusted T> {
	public void method() {
		@OsUntrusted T t = null;
	}
}

class GenericsAndLocals2<@OsUntrusted T> {

	public void method(@OsTrusted T incoming) {
		@OsUntrusted T t = null;
		@OsTrusted T t2 = incoming;
	}
}

class GenericsAndLocals3<@OsTrusted T> {

	public void method(@OsTrusted T incoming) {
		@OsUntrusted T t = null;
		@OsTrusted T t2 = incoming;
	}
}
