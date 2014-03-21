import checkers.inference.quals.VarAnnot;
import checkers.nullness.quals.*;

class NNTest extends @NonNull Object {
  @NonNull @VarAnnot(1) String nn;
}