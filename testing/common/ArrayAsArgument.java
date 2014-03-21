class MyObj {
}

class ArrayAsArgument {
	private MyObj [] exArray;
	
	public MyObj [] arrayIdentity(MyObj [] arr ) {
		return arr;	
	}
	
	public void context() {
		arrayIdentity( exArray );
	}
}