import java.util.*;

class UseMap {
  Map<String, String> sm = new HashMap<String, String>();

  void m() {
    sm.put("ha!", "val");
    String s = sm.get("xxx");
  }
}
