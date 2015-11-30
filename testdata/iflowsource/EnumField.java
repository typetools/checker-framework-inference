import sparta.checkers.quals.*;
public enum EnumField{
    CONST("hello");
    public String enumField;
    private @Source({}) EnumField(@Source("READ_SMS") @Sink({}) String field){
       //:: fixable-error: (assignment.type.incompatible)
       this.enumField = field;
   }
}
