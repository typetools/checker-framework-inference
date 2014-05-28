import checkers.inference.quals.VarAnnot;
import nninf.quals.*;

class Preannotated {
    @NonNull @VarAnnot(2) String s = "not null";
}
