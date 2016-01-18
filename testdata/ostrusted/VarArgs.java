//@skip-test
//Currently we didn't remotely try to make this typecheck and it emits a lot of warnings, fix it up

import java.util.List;
import java.util.ArrayList;
import ostrusted.qual.*;

class TestVarArgs<E> {

    TestVarArgs(String ... args) {
    }
    void cTest() {
        new TestVarArgs<String>();
        new TestVarArgs<String>("");
        new TestVarArgs<String>("", "");
    }

    void test0(@OsTrusted String[]... args) {
        test0();
        test0(new String[0]);
        test0(new String[0], new String[0]);
    }

    void test1(String... args) {
        test1();
        test1("");
        test1("", "");
        String s = new String();
        test1(s);
    }

    void test2(List... args) {
        test2();
        test2(new ArrayList());
        test2(new ArrayList<String>());
        test2(new ArrayList(), new ArrayList());
        test2(new ArrayList<String>(), new ArrayList<String>());
    }

    void test5(List<@OsTrusted String>... args) {
        test5();
        test5(new ArrayList());
        test5(new ArrayList<String>());
        test5(new ArrayList(), new ArrayList());
        test5(new ArrayList<String>(), new ArrayList<String>());
   }

    <T> void test3(T... args) {
        test3();
        test3("", "");
        test3("", new Object());
        test3(new ArrayList<String>(), new ArrayList<String>());
        test3(new ArrayList(), new ArrayList<String>());
    }

    void test4 (E ee, E... args) {
        test4(ee);
        test4(ee, ee);
        test4(ee, ee, ee);
    }
}

