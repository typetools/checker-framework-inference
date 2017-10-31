package checkers.inference.solver.backend.logiqlbackend;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.QualifierHierarchy;

import checkers.inference.model.Constraint;
import checkers.inference.model.Serializer;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.BackEnd;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.NameUtils;
import checkers.inference.solver.util.StatisticRecorder;
import checkers.inference.solver.util.StatisticRecorder.StatisticKey;

/**
 * LogiQLBackEnd first creates LogiQL predicates text, then calls serializer
 * converts constraint into LogiQL data. With both predicate and data created,
 * it calls LogicBloxRunner that runs logicblox to solve the LogiQL, and reads
 * the output. Finally the output will be sent to DecodingTool and get decoded.
 * 
 * @author jianchu
 *
 */
public class LogiQLBackEnd extends BackEnd<String, String> {

    private final StringBuilder logiQLText = new StringBuilder();
    private final File logiqldata = new File(new File("").getAbsolutePath() + "/logiqldata");
    private static AtomicInteger nth = new AtomicInteger(0);
    private long serializationStart;
    private long serializationEnd;
    private long solvingStart;
    private long solvingEnd;
    public LogiQLBackEnd(Map<String, String> configuration, Collection<Slot> slots,
            Collection<Constraint> constraints, QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment, Serializer<String, String> realSerializer,
            Lattice lattice) {
        super(configuration, slots, constraints, qualHierarchy, processingEnvironment, realSerializer,
                lattice);
        logiqldata.mkdir();
    }

    @Override
    public Map<Integer, AnnotationMirror> solve() {
        int localNth = nth.incrementAndGet();
        String logiqldataPath = logiqldata.getAbsolutePath();
        Map<Integer, AnnotationMirror> result = new HashMap<>();
        /**
         * creating a instance of LogiqlConstraintGenerator and running
         * GenerateLogiqlEncoding method, in order to generate the logiql fixed
         * encoding part of current type system.
         */
        LogiQLPredicateGenerator constraintGenerator = new LogiQLPredicateGenerator(logiqldataPath,
                lattice, localNth);
        constraintGenerator.GenerateLogiqlEncoding();
        this.serializationStart = System.currentTimeMillis();
        this.convertAll();
        this.serializationEnd = System.currentTimeMillis();
        StatisticRecorder.record(StatisticKey.LOGIQL_SERIALIZATION_TIME,
                (serializationEnd - serializationStart));
        addVariables();
        addConstants();
        writeLogiQLData(logiqldataPath, localNth);

        this.solvingStart = System.currentTimeMillis();
        LogicBloxRunner runLogicBlox = new LogicBloxRunner(logiqldataPath, localNth);
        runLogicBlox.runLogicBlox();
        this.solvingEnd = System.currentTimeMillis();

        StatisticRecorder.record(StatisticKey.LOGIQL_SOLVING_TIME, (solvingEnd - solvingStart));
        DecodingTool DecodeTool = new DecodingTool(varSlotIds, logiqldataPath, lattice, localNth);
        result = DecodeTool.decodeResult();
        // PrintUtils.printResult(result);
        return result;
    }

    @Override
    public void convertAll() {
        for (Constraint constraint : constraints) {
            collectVarSlots(constraint);
            String serializedConstrant = constraint.serialize(realSerializer);
            if (serializedConstrant != null) {
                logiQLText.append(serializedConstrant);
            }
        }
    }

    private void addConstants() {
        for (AnnotationMirror annoMirror : lattice.allTypes) {
            String constant = NameUtils.getSimpleName(annoMirror);
            logiQLText.insert(0, "+constant(c), +hasconstantName[c] = \"" + constant + "\".\n");
        }
    }

    private void addVariables() {
        for (Integer variable : varSlotIds) {
            logiQLText.insert(0, "+variable(v), +hasvariableName[v] = " + variable + ".\n");
        }
    }

    private void writeLogiQLData(String path, int nth) {
        String[] lines = logiQLText.toString().split("\r\n|\r|\n");
        StatisticRecorder.record(StatisticKey.LOGIQL_DATA_SIZE, (long) lines.length);
        try {
            String writePath = path + "/data" + nth + ".logic";
            File f = new File(writePath);
            PrintWriter pw = new PrintWriter(f);
            pw.write(logiQLText.toString());
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
