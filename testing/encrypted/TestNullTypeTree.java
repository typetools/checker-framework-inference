
import encrypted.quals.*;

class Token<T extends @Encrypted Integer, I extends @Plaintext T, J extends T> {
    void test(T t, I i, J j) { }
}

