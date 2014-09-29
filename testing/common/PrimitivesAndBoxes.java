class PrimitivesAndBoxes {
	public void method() {
		Integer testy = 2;
		int primitive = 1;
		Integer boxed = primitive;
		
		boxed = forceNarrow( boxed );
	}
	
	
	public int forceNarrow( int i ) {
		return i;
	}
}