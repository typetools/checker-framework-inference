import checkers.nullness.quals.*;

class GenericsAndLocals<@NonNull T> {
	public void method() {
		@Nullable T t = null;
	}
}

class GenericsAndLocals2<@Nullable T> {

	public void method(@NonNull T incoming) {
		@Nullable T t = null;
		@NonNull T t2 = incoming;
	}
}

class GenericsAndLocals3<@NonNull T> {

	public void method(@NonNull T incoming) {
		@Nullable T t = null;
		@NonNull T t2 = incoming;
	}
}