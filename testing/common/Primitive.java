class Primitive {
  int i = 5;

  // Note that method m has no variables and we
  // need to make sure that a board gets created.
  int m(int p) { return p; }

  void call() {
    int i = m(9);
  }

  // To have at least one variable.
  Object o = null;
}
