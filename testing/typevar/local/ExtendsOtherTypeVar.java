package local;

public class ExtendsOtherTypeVar {
    class Gen<T extends Object, E extends T> {

        public void context(T t0, T t1, E e0) {
            t0 = t1;
            t0 = e0;
            method(e0);
        }

        public void method(T in_t) {

        }
    }

    //reverse the order of the type args
    class GenRev<EE extends TT, TT extends Object> {

        public void context(TT tt0, TT tt1, EE ee0) {
            tt0 = tt1;
            tt0 = ee0;
            method(ee0);
        }

        public void method(TT in_tt) {

        }
    }
}
