import java.util.List;
import java.util.ArrayList;

class Wildcards4 {
	private List<? extends List<? extends CharSequence>> other;
	private List<? extends List<? extends String>> test;
	
	public void context() {
		other = test;
		
		other.toString();
	}
	
}