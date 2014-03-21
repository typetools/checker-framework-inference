import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

class IteratorAssignment {
	
	public void containerMethod() {
		final List<Integer> intList = new ArrayList<Integer>();
		final Iterator<Integer> intIterator = intList.iterator();
		
		while (intIterator.hasNext()) {
        	final int quality = intIterator.next();
    	}
	}
}