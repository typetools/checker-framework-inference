import java.util.List;
import java.util.ArrayList;

class Wildcards2 {
	private List<? extends List<String>> other;
	private List<? extends List<String>> test;
	
	public void context() {
		test.toString();
		test = new ArrayList<ArrayList<String>>();
	
		test = other;	
	}
	
}