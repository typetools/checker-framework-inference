

class MyList<E extends Object> extends Object { // TODO JB: Need to add "Missing Extends"
}

class MyClass<T extends MyList<String>> extends Object {
    MyClass() {}
    void m( MyClass<T> this, T arg ) {
    }

    void context() {
        MyList<String> s = new MyList<String>();
        MyClass<MyList<String>> myc;
        myc = new MyClass<MyList<String>>();
        
        myc.m(s);
    }
}