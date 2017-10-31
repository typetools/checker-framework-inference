package project;

import libs.Foo;
import java.util.*;

public class Bar {

    Foo fieldOfBar;

    char thisIsChar = 'c';

    String thisIsString = "String!";
    
    Object thisShouldbeString = thisIsString;

    Date date = new Date();

    Integer autoboxing = 1;

    int unboxing = new Integer(1);

    int noAutoboxing = 1;

    Integer noUnboxing = new Integer(1);

    Bar(){
        this.fieldOfBar = new Foo(10);
    }

    Object testingUpperBound1() {

        if (autoboxing == 1) {
            return 3;
        } else if (unboxing == 2) {
            return 3.14;
        } else if (noAutoboxing == 3) {
            return 3.1415926;
        }
        return "I am a String!";
    }
    
    Object testingUpperBound2() {
        if(autoboxing > 0) {
            return new LinkedList<String>();
        } else if (unboxing < 0) {
            return new ArrayList<String>();
        } else if (noAutoboxing > 0) {
            return Foo.foo1(); 
        } else if (noUnboxing > 0) {
            return new Object().toString();
        } else {
            return new Object();
        }
    }

    Object o = new Object();

    void testingUpperBound3() {
        if(autoboxing > 0) {
            o = new Integer[1];
        } else if (unboxing < 0) {
            o = new Double[1];
        } else if (noAutoboxing > 0) {
            o = Foo.foo2(); 
        } else if (noUnboxing > 0) {
            o = new String[1];
        } else {
            o = new Object().toString();
        }
    }
}
