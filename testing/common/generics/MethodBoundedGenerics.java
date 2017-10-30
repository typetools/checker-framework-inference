import trusted.qual.*;

import java.util.List;
import java.util.ArrayList;

class MethodBoundedGenerics {

    // TODO: DOes this say, that the arguments must be subtypes of @Trusted but
    //treat them as @Untrusted
    public <@Trusted T extends @Trusted Object> @Untrusted T method(@Untrusted T inc, T inc2) {
        inc.toString();
        T loc1 = inc2;
        loc1 = inc;
        inc = null;

        @Trusted T loc3 = inc2;
        loc3 = inc;
        @Untrusted T loc2 = inc;

        // TODO: Can't use it there
        //List<@Untrusted T> list = new ArrayList<@Untrusted T>();
        List<@Trusted T> list = new ArrayList<@Trusted T>();
        list.add(loc2);

        return loc3;
    }

    public void test() {
        @Untrusted String s  = null;
        @Untrusted String s2 = this.method(s, "t");
    }
}
