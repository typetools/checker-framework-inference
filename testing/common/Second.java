
public class Second {
  void m(boolean b) {
    Object o = new Object();
    if (b) {
      o = null;
    }
    Object y = o;
  }
}
