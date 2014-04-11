import java.util.ArrayList;
import java.util.List;

class NBGeneric<T extends List<?>> {
	private T t;
	
	public T get() {
		return t;
	}
	
}

class NoBoundsTest {
	private NBGeneric<List<String>> bgStr;
	private List<?> listF;
	
	public void context() {
		List<String> t = bgStr.get();
		listF = new ArrayList<Integer>();
		Object obj = listF.get(0);
	}
}
