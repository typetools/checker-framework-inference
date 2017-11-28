interface List<T extends Object> {}

class ListImp implements List<String> {}

class CallMethod {
    public <T extends List<String>> String call(T t) {
        return "any";
    }

    public static void main() {
        List<String> s = null;
        CallMethod cm = new CallMethod();

        cm.call(new ListImp()); // TODO: HERE THE VALUE ON List<String> in ListImp is not becoming the type arg
        // TODO: Either it should be or the type arg should be created anew not reused
        String retStore = cm.call(null);

        cm.<List<String>>call(null);
        cm.<ListImp>call(null);
    }
}