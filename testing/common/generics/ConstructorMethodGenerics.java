
class MyClass<T extends Object> extends Object {
    <E extends Object> MyClass() {}
    void m( MyClass<T> this, T arg ) {
    }

    void context() {
        String s = "non null";
        MyClass<String> myc;
        myc = new MyClass<String>();
        
        myc.m(s);
    }
}