import sparta.checkers.quals.*;

public class PolySourceTest{

//@PolySource String poly( @PolySource String param) { return param; }
int readSmsField;
void foo(@Source("READ_SMS") int readSMS){
   readSmsField = Math.abs(readSMS);
   bar(Math.abs(readSMS));
   testReadSms(readSmsField);
}
void bar(int test){
    testReadSms(test);
}

void testReadSms(@Source("READ_SMS") int sms) {}

}

