import java.util.List;

class WilcardInTypeParameter<T extends List<?>> {
    private void m1(WilcardInTypeParameter<List<? extends String>> test) {}
}
