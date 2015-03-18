
import trusted.quals.*;

class GenericsAndLocals<@Trusted T> {
	public void method() {
		@Untrusted T t = null;
	}
}

class GenericsAndLocals2<@Untrusted T> {

	public void method(@Trusted T incoming) {
		@Untrusted T t = null;
		@Trusted T t2 = incoming;
	}
}

class GenericsAndLocals3<@Trusted T> {

	public void method(@Trusted T incoming) {
		@Untrusted T t = null;
		@Trusted T t2 = incoming;
	}
}
