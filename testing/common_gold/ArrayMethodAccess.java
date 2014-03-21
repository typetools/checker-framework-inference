import checkers.inference.quals.VarAnnot;
class ArrayMethodAccess {
	private @VarAnnot(2) String @VarAnnot(1) [] array;
	
	public void context() {
		System.out.println(array.toString());
	}
}