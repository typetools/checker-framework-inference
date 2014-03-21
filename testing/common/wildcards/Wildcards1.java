interface List<L> {
	L getEle(int i);
}

interface Interf<T> {
	void set(T t);
	T get();
}

class ConcreteInterf<CI> implements Interf<CI> {
	CI ci = null;
	
	public CI get() {
		return ci;
	}
	
	public void set(CI ci) {
		this.ci = ci;
	}
}

class ConcreteWCInterf implements Interf<List<?>> {
	List<?> lq;

	public List<?> get() {
		return lq;
	}	
	
	public void set(List<?> lq ) {
		this.lq = lq;
	}
}

abstract class ComplexWcInterf implements Interf<List<? extends List<?>>> {
	List<List<?>> lq;

	public List<List<?>> get() {
		return lq;
	}	
	
	public void set(List<List<?>> lq ) {
		this.lq = lq;
	}
}

class Wildcards1 {
	private Interf<?> fieldInterf;
	
	void context() {
		Interf<?> interf = new ConcreteInterf<String>();
	}
}