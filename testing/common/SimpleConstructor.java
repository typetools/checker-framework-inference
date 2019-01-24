class SimpleConstructor {
    private String y;

    public SimpleConstructor() {
        y = null;
    }

    public SimpleConstructor(String in) {
        y = in;
    }

    public static void noArg() {
        SimpleConstructor simpCon = new SimpleConstructor();
    }

    public static void oneArg() {
        SimpleConstructor simpCon = new SimpleConstructor("t");
    }
}
