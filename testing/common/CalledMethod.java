interface List<T extends Object> {}

class ListImp implements List<String> {}

class CallMethod {
    public String call(List<String> str) {
        return "any";
    }

    public static void main() {
        CallMethod cm = new CallMethod();

        cm.call(new ListImp());
    }
}
