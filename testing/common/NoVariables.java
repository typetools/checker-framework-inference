class NoVariables {
	private String catcher = "Here to make sure we don't just exit because there " +
							 "are no variables!";
	private int minValue;
	private int maxValue;
	private int value;
	
	public NoVariables(int min, int max, int value) {
		this.minValue = min;
		this.maxValue = max;
		this.value    = value;
	}
	
	/*public void logRange() {
		value = 5;
		if( value < minValue || value > maxValue ) {
			log();
		}
	}*/
	
	public void log() {
	}
}

class Other {
	private NoVariables nv;
	public void throwsAnExceptionInGameSolver() {
		nv.log();
	}
}