import checkers.inference.quals.VarAnnot;
class SuperConstructor {
}

class SubConstructor extends @VarAnnot(2) SuperConstructor {
    public SubConstructor() {
        super();
    }
}