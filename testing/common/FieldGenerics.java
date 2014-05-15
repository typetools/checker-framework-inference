
public class FieldGenerics {
    private Generic<String> gen;

	public FieldGenerics(){
		gen = new Generic<String>( "str" );
	}
	
    private void accessGen() {
        gen.accessor();
    }

}

class Generic<T extends Object> {
    private T t;

    public Generic( T t) {
        this.t = t;
    }

    public T accessor() {
        return t;
    }
}