import trusted.quals.*;

class GenericsAndLocals<@Trusted T> {
	public void method1() {
		@Untrusted T t = null;
	}
}

class GenericsAndLocals2<@Untrusted T> {

	public void method2(@Trusted T incoming2) {
		@Untrusted T t = null;
		@Trusted T t2 = incoming2;
	}
}

class GenericsAndLocals3<@Trusted T> {

	public void method3(@Trusted T incoming3) {
		@Untrusted T t = null;
		@Trusted T t2 = incoming3;
	}
}
