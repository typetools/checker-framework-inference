import nninf.quals.*;
import trusted.quals.*;

class Preannotated extends @Trusted Object {
    @NonNull @Trusted String s = "not null";
    @Trusted String s = "not null";
}
