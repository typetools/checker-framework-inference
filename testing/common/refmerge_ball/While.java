class While {
    void test(boolean b) {
       String a = "";
       while (b) {
           if (a == null) {
               a.toString();
           } else {
               a = null;
           }
       }
    }
}
