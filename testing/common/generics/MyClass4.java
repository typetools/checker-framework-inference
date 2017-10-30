
interface mList<M extends Object, M2 extends Object> {
}

class MyList<E extends Object, E2 extends Object> extends Object { // TODO JB: Need to add "Missing Extends"
}


class MyClass<T extends MyList<String, Integer>> extends MyList<T, Double> implements mList<String, T> {
    MyClass() {}
    void m( MyClass<T> this, T arg ) {
        this.<T, MyList<T, Integer>>whatever(null, null);
    }

    <F extends Object, G extends MyList<F, Integer>> void whatever(F f, G g) {}

    void context() {
        MyList<String, Integer> s = new MyList<String, Integer>();
        MyClass<MyList<String, Integer>> myc;
        myc = new MyClass<MyList<String, Integer>>();

        myc.m(s);

        whatever("Sam", s);
    }

}