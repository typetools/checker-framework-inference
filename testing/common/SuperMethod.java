class SuperMethod {
	public void m() {
	}
}

class SubMethod extends SuperMethod {
	public void m() {
		super.m();
	}
}