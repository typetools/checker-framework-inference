
import java.util.HashMap;
import java.util.List;
import java.util.Map;

interface Interface<E extends Map<String, List<String>>> {
	public E get();
}

class CaptureConversionGlb {
	Interface<? extends HashMap<String, List<String>>> field;
	void context() {
		field.toString();
	}
}