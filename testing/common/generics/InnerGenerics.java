class Outer<T, S> {

    class Inner<T2 extends T> {
        private S s;
        private T t;

        protected void initialize(S s, T t) {
            this.s = s;
            this.t = t;
        }

        public Inner(S s, T t) {
            initialize(s, t);
        }
    }
}
