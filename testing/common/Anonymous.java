
// Test that parameters inside an anonymous class get annotated.
interface Interface {
    String get(String param);
}

class TestAnon {
    void context() {
        Object o = new Interface() {
            @Override
            public String get(String param) {
                return null;
            }
        };
    }
}
