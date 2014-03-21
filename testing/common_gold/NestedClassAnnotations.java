import checkers.inference.quals.VarAnnot;
class NestedClassAnnotations {
    O1.O2.O3.@VarAnnot(1) NestedStatic n;
}

class O1 {
    static class O2 {
        static class O3 {
            static class NestedStatic {}
        }
    }
}