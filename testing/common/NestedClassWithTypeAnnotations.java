class NestedClassWithTypeAnnotations {
    O1.O2<String, Object>.O3.Nested<Foo, Bar> o;
}

class O1 {
    class O2<S, T> {
        class O3 {
            class Nested<U, V> {}
        }
    }
}

class Foo {}

class Bar {}
