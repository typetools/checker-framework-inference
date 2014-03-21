import java.util.List;
import java.util.ArrayList;

class StaticMethod {
	public static List<String> something;

	public static void method( String param ) {
		String other = param;
		List<String> otherList = something;
		//something = new ArrayList<String>();
	}
	
	public void instMethod() {
		method( "YUM" );
	}
}