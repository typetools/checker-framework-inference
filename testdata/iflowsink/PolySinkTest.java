import sparta.checkers.quals.*;

class PolySinkTest {

   void writeTime(@Sink("WRITE_TIME") int time) {
        
   }
   
   int absTime;
   void context(int toWrite) {
      absTime = Math.abs(toWrite);
      writeTime(absTime);   
   }
}