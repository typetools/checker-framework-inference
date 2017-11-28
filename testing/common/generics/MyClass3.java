
class MyList<E extends Object> extends Object { // TODO JB: Need to add "Missing Extends"
}

class MyClass<T extends String> extends MyList<T> {
    MyClass() {}
    void m( MyClass<T> this, T arg ) {
    }

    void context() {
        String s = "mc";
        MyClass<String> myc;
        myc = new MyClass<String>();
        
        myc.m(s);
    }
}