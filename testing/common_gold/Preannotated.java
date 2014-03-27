import checkers.inference.quals.VarAnnot;
import checkers.nullness.quals.*;

class Preannotated {
	
	@NonNull @VarAnnot(3) String s = "not null";
}