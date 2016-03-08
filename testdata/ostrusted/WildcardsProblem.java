//@skip-test
class WildcardsProblem<A>{
    A field;
    public void method(WildcardsProblem t){
        Object t_field = t.field;
    }
}
