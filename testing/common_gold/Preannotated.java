import checkers.inference.quals.VarAnnot;
import nninf.qual.*;

class Preannotated {
    @NonNull @VarAnnot(2) String s = "not null";
}
