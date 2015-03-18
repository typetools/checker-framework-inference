

import ostrusted.quals.*;

import java.util.List;
import java.util.ArrayList;

class MethodBoundedGenerics {

	//TODO: DOes this say, that the arguments must be subtypes of @OsTrusted but
	//treat them as @OsUntrusted
	public <@OsTrusted T> @OsUntrusted T method(@OsUntrusted T inc, T inc2) {
		inc.toString();
		T loc1 = inc2;
		loc1 = inc;
		inc = null;
		
		@OsTrusted T loc3 = inc2;
		loc3 = inc;
		@OsUntrusted T loc2 = inc;
		
		//TODO: Can't use it there
		//List<@OsUntrusted T> list = new ArrayList<@OsUntrusted T>();	
		List<@OsTrusted T> list = new ArrayList<@OsTrusted T>();
		list.add(loc2);	
		
		return loc3;
	}
	
	public void test() {
		@OsUntrusted String s  = null;
		@OsUntrusted String s2 = this.method(s, "t"); 
	}
}
