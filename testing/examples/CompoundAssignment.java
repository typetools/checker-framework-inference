class CompoundAssignmentTest {
	
	public static void main() {
		Integer i = 2;
		i += method();
	}
	
	public static int method() {
		return 1;
	}
	
}