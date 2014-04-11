
interface GenericInterface<T extends Object> {
	public T map(T toMap);
}

class GenericArray<Z extends Object> implements GenericInterface<String []> {
	private Z z;
	public void setZ(Z z) {
		this.z = z;
	}
	public String [] map(String [] toMap) {
		return toMap;
	}
	
}

class GenericFields {
	private GenericArray<String> genArray;
}