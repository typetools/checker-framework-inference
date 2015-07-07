import trusted.quals.*;
import java.util.List;

class RequiresPrimary {

    <T extends Object> T get(T t1, T t2) {
        return t1;
    }
    
    <S extends Object> void set(List<S> s) {
    }
    
    void context(@Trusted String s, List<@Trusted Integer> intList ) {
    
        @Trusted String local = this.<@Untrusted String>get(s, "");
       
        
        this.<@Untrusted Integer>set(intList);    
    }
}