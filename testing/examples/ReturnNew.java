class ReturnNew {
  void foo() {
    Object c = m();
  }
	
  Object m() {
    return new Object();
  }
}
