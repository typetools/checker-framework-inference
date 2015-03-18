import nninf.quals.*;
import trusted.quals.*;

class Preannotated extends @Trusted Object {
    @NonNull @Trusted String f1 = "not null";
    @Trusted String f2 = "not null";
}
