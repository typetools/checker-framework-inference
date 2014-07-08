class MyObj {
}

class ArrayFieldAccess {
	private MyObj [] array;
	
	public void context() {
		array[0] = new MyObj();
	}
}
