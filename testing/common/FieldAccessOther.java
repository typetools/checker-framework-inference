class FieldAccessOther<T extends Object> {
    public String field = "";

    public void locAccess() {
        String la = field;
        String la2 = this.field;
    }
}

class OtherAccess {
    public void method(FieldAccessOther<String> fao) {
        String loc = fao.field;
    }
}
