
import checkers.tainting.quals.*;
import java.util.List;

class GenericClass<@Untainted T, @Tainted E> {

    void test() {
        new GenericClass<T, T>();
    }
}
