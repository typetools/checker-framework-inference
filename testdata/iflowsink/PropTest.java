import sparta.checkers.quals.*;

public class PropTest{
String s;
void otherTest(){
  s= "hello";
}
void test(String x){
  send(s);
  send(x);
}

void send(@Sink("INTERNET") String s){}

}
