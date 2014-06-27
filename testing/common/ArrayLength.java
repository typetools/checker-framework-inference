
// Calls to ar.length used to fail because getAnnotatedType
// was never called on the length field.
class ArrayLength {
    String[] ar;
    int i = ar.length;
}
