// PR: https://github.com/opprop/generic-type-inference-solver/pull/17

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dataflow.qual.DataFlow;

class TestByteCodeMethodArrayComponentType {
    public void test(String path) {
        // :: fixable-error: (assignment.type.incompatible)
@DataFlow(typeNameRoots={"java.lang.String"}) String str = getPath(path);
    }

    public String getPath(String pathPart){
        String pathFull = "";
String [] arrOfPathPart = pathPart.split("");
        pathFull = pathFull + arrOfPathPart[0];
        return pathFull;
    }

    public void checkFile(String filePath) throws Exception {
        String pathFull = getPath(filePath);
        // :: warning: (cast.unsafe.constructor.invocation)
        FileInputStream fstream = new FileInputStream(filePath);
    }
 }
