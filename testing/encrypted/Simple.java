import java.util.*;

import encrypted.quals.*;

abstract class BasicFunctionality {

    @Encrypted String encrypt(String s) {
        byte[] b = s.getBytes();
        for (int i = 0; i < b.length; b[i++]++);
        return new String(b);
    }

    abstract void sendOverTheInternet(@Encrypted String s);

    void test() {
        String s = encrypt("foo");
        sendOverTheInternet(s);

        String t = encrypt("bar");
        sendOverTheInternet(t);

        List<String> lst = new LinkedList<String>();
        lst.add(s);
        lst.add(t);

        String u = lst.get(0);
        sendOverTheInternet(u);

        for (String str : lst)
            sendOverTheInternet(str);
    }
}
