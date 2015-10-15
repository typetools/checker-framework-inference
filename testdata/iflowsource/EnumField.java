import sparta.checkers.quals.*;
public enum EnumField{
    CONST("hello");
    public String enumField;
    private EnumField(@Source("READ_SMS") @Sink({}) String field){
       this.enumField = field;
   }
}
