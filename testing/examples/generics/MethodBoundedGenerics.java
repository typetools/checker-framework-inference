import checkers.nullness.quals.*;
import java.util.List;
import java.util.ArrayList;

class MethodBoundedGenerics {

	//TODO: DOes this say, that the arguments must be subtypes of @NonNull but
	//treat them as @Nullable
	public <@NonNull T> @Nullable T method(@Nullable T inc, T inc2) {
		inc.toString();
		T loc1 = inc2;
		loc1 = inc;
		inc = null;
		
		@NonNull T loc3 = inc2;
		loc3 = inc;
		@Nullable T loc2 = inc;
		
		//TODO: Can't use it there
		//List<@Nullable T> list = new ArrayList<@Nullable T>();	
		List<@NonNull T> list = new ArrayList<@NonNull T>();
		list.add(loc2);	
		
		return loc3;
	}
	
	public void test() {
		@Nullable String s  = null;
		@Nullable String s2 = this.method(s, "t"); 
	}
}