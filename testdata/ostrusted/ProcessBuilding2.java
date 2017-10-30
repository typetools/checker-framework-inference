import ostrusted.qual.OsUntrusted;

class ProcessBuilding2 {
    public void strArrays(@OsUntrusted String[] untrustedArr) {
        // :: error: (argument.type.incompatible)
        new ProcessBuilder(untrustedArr);
    }
}
