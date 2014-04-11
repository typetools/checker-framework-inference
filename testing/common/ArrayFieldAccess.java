class MyObj {
}

class ArrayFieldAccess {
	private MyObj [] array;
	
	public void context() {
		int i = array.length;
		array[i] = new MyObj();
	}
}