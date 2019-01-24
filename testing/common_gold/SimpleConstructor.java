import checkers.inference.quals.VarAnnot;
class SimpleConstructor {
    private @VarAnnot(1) String y;

    public SimpleConstructor() {
        y = null;
    }

    public SimpleConstructor(@VarAnnot(4) String in) {
        y = in;
    }

    public static void noArg() {
        SimpleConstructor simpCon = new SimpleConstructor();
    }

    public static void oneArg() {
        SimpleConstructor simpCon = new SimpleConstructor("t");
    }
}
