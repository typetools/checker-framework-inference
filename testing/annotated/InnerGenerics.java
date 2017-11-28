class Outer<T, S> {
    // TODO: The current issue with this is that the method board as it's created
    // TODO: does not have the type representing the lower bound of T2 because
    // TODO: it's already annotated
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