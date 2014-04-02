
class MyClass<T extends Object> extends Object {
    MyClass(String s) {}
    void m( MyClass<T> this, T arg ) {
    }

    void context() {
        String s = "non null";
        MyClass<String> myc;
        myc = new MyClass<String>(s);
        
        myc.m(s);
    }
}