package checkers.inference.solver.backend.logiql;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.NameUtils;
import checkers.inference.solver.util.Statistics;

/**
 * LogiqlConstraintGenerator take QualifierHierarchy of current type system as
 * input, and generate the logiql encoding of all constraint, and write the
 * result in a .logic file.
 *
 * @author Jianchu Li
 *
 */
public class LogiQLPredicateGenerator {

    private final String path;
    private final StringBuilder allEncodings = new StringBuilder();
    private final Lattice lattice;
    private final int nth;

    public LogiQLPredicateGenerator(String path, Lattice lattice, int nth) {
        this.path = path;
        this.lattice = lattice;
        this.nth = nth;
    }

    public void GenerateLogiqlEncoding() {
        allEncodings.append(getBasicEncoding());
        allEncodings.append(getEqualityConstraintEncoding());
        allEncodings.append(getInequalityConstraintEncoding());
        allEncodings.append(getSubTypeConstraintEncoding());
        allEncodings.append(getComparableConstraintEncoding());

        // System.out.println(allEncodings.toString());

        writeFile(allEncodings.toString());

    }


    private String getEqualityConstraintEncoding() {
        StringBuilder equalityEncoding = new StringBuilder();
        for (AnnotationMirror annoMirror : lattice.allTypes) {
            String simpleName = NameUtils.getSimpleName(annoMirror);
            equalityEncoding.append("is" + simpleName + "[v2] = true <- equalityConstraint(v1, v2), is"
                    + simpleName + "[v1] = true.\n");
            equalityEncoding.append("is" + simpleName
                    + "[v2] = true <- equalityConstraintContainsConstant(v1, v2), hasconstantName(v1:\""
                    + simpleName + "\").\n");
        }
        return equalityEncoding.toString();
    }
    
    private String getInequalityConstraintEncoding() {
        StringBuilder inequalityEncoding = new StringBuilder();
        for (AnnotationMirror annoMirror : lattice.allTypes) {
            String simpleName = NameUtils.getSimpleName(annoMirror);
            inequalityEncoding.append("is" + simpleName + "[v2] = false <- inequalityConstraint(v1, v2), is"
                    + simpleName + "[v1] = true.\n");
            inequalityEncoding.append("is" + simpleName
                    + "[v2] = false <- inequalityConstraintContainsConstant(v1, v2), hasconstantName(v1:\""
                    + simpleName + "\").\n");
        }
        return inequalityEncoding.toString();
    }
    
    private String getSubTypeConstraintEncoding() {
        StringBuilder subtypeEncoding = new StringBuilder();
        String topTypeStr = NameUtils.getSimpleName(lattice.top);
        String bottomTypeStr = NameUtils.getSimpleName(lattice.bottom);
        subtypeEncoding.append("is" + topTypeStr + "[v2] = true <- subtypeConstraint(v1, v2), is" + topTypeStr + "[v1] = true.\n");
        subtypeEncoding.append("is" + topTypeStr+ "[v2] = true <- subtypeConstraintLeftConstant(v1, v2), hasconstantName(v1:\"" + topTypeStr + "\").\n");
        subtypeEncoding.append("is" + bottomTypeStr + "[v1] = true <- subtypeConstraint(v1, v2), is" + bottomTypeStr + "[v2] = true.\n");
        subtypeEncoding.append("is" + bottomTypeStr+ "[v1] = true <- subtypeConstraintRightConstant(v1, v2), hasconstantName(v2:\"" + bottomTypeStr + "\").\n\n");
        
        for (Map.Entry<AnnotationMirror, Collection<AnnotationMirror>> entry : lattice.subType.entrySet()) {
            String superTypeName = NameUtils.getSimpleName(entry.getKey());
            for (AnnotationMirror subType : entry.getValue()) {
                String subTypeName = NameUtils.getSimpleName(subType);
                if (!superTypeName.equals(subTypeName)) {
                    subtypeEncoding.append("is" + subTypeName + "[v2] = false <- subtypeConstraint(v1, v2), is" + superTypeName + "[v1] = true.\n");
                    subtypeEncoding.append("is" + subTypeName + "[v2] = false <- subtypeConstraintLeftConstant(v1, v2), hasconstantName(v1:\"" + superTypeName + "\").\n");
                }
            }
        }
        subtypeEncoding.append("\n");
        // duplicate code for easy understanding
        for (Map.Entry<AnnotationMirror, Collection<AnnotationMirror>> entry : lattice.superType.entrySet()) {
            String subTypeName = NameUtils.getSimpleName(entry.getKey());
            for (AnnotationMirror superType : entry.getValue()) {
                String superTypeName = NameUtils.getSimpleName(superType);
                if (!superTypeName.equals(subTypeName)) {
                    subtypeEncoding.append("is" + superTypeName + "[v1] = false <- subtypeConstraintRightConstant(v1, v2), hasconstantName(v2:\"" + subTypeName + "\").\n");
                }
            }
        }
        subtypeEncoding.append("\n");
        return subtypeEncoding.toString();
    }
    
    private String getComparableConstraintEncoding() {
        StringBuilder ComparableEncoding = new StringBuilder();
        // duplicate code for easy understanding
        for (Map.Entry<AnnotationMirror, Collection<AnnotationMirror>> entry : lattice.incomparableType.entrySet()) {
            String incompType1Name = NameUtils.getSimpleName(entry.getKey());
            for (AnnotationMirror incomparableType2 : entry.getValue()) {
                String incompType2Name = NameUtils.getSimpleName(incomparableType2);
                ComparableEncoding.append("is" + incompType2Name + "[v1] = false <- subtypeConstraintRightConstant(v1, v2), hasconstantName(v2:\"" + incompType1Name + "\").\n");
            }
        }
        
        return ComparableEncoding.toString();
    }
    
    private String getBasicEncoding() {
        StringBuilder basicEncoding = new StringBuilder();
        basicEncoding.append("variable(v), hasvariableName(v:i) -> int(i).\n");
        basicEncoding.append("constant(m), hasconstantName(m:i) -> string(i).\n");
        basicEncoding.append("AnnotationOf[v] = a -> variable(v), string(a).\n");
        //predicates for making output in order.
        basicEncoding.append("variableOrder(v) -> int(v).\n");
        basicEncoding.append("variableOrder(i) <- variable(v), hasvariableName(v:i).\n");
        basicEncoding.append("orderVariable[o] = v -> int(o), int(v).\n");
        basicEncoding.append("orderVariable[o] = v <- seq<<o=v>> variableOrder(v).\n");
        basicEncoding.append("orderedAnnotationOf[v] = a -> int(v), string(a).\n");
        basicEncoding.append("orderedAnnotationOf[v] = a <- variable(q), hasvariableName(q:v), AnnotationOf[q]=a, orderVariable[_]=v.\n");
        //predicates for constraints.
        //equality constraint
        basicEncoding.append("equalityConstraint(v1,v2) -> variable(v1), variable(v2).\n");
        basicEncoding.append("equalityConstraintContainsConstant(v1,v2) -> constant(v1), variable(v2).\n");
        //inequality constraint
        basicEncoding.append("inequalityConstraint(v1,v2) -> variable(v1), variable(v2).\n");
        basicEncoding.append("inequalityConstraintContainsConstant(v1,v2) -> constant(v1), variable(v2).\n");
        //subtype constraint
        basicEncoding.append("subtypeConstraint(v1,v2) -> variable(v1), variable(v2).\n");
        basicEncoding.append("subtypeConstraintLeftConstant(v1,v2) -> constant(v1), variable(v2).\n");
        basicEncoding.append("subtypeConstraintRightConstant(v1,v2) -> variable(v1), constant(v2).\n");
        //comparable constraint
        basicEncoding.append("comparableConstraint(v1,v2) -> variable(v1), variable(v2).\n");
        basicEncoding.append("comparableConstraintContainsConstant(v1,v2) -> constant(v1), variable(v2).\n");
        // each type
        for (AnnotationMirror annoMirror : lattice.allTypes) {
            basicEncoding.append("is" + NameUtils.getSimpleName(annoMirror)
                    + "[v] = i -> variable(v), boolean(i).\n");
        }
        for (AnnotationMirror annoMirror : lattice.allTypes) {
            String simpleName = NameUtils.getSimpleName(annoMirror);
            basicEncoding.append("AnnotationOf[v] = \"" + simpleName + "\" <- " + "is" + simpleName + "[v] = true.\n");
        }
        for (AnnotationMirror rightAnnoMirror : lattice.allTypes) {
            for (AnnotationMirror leftAnnoMirror : lattice.allTypes) {
                String leftAnnoName = NameUtils.getSimpleName(leftAnnoMirror);
                String rightAnnoName = NameUtils.getSimpleName(rightAnnoMirror);
                if (!leftAnnoName.equals(rightAnnoName)) {
                    basicEncoding.append("is" + leftAnnoName + "[v] = false <- is" + rightAnnoName + "[v] = true.\n");
                }
                
            }
        }
        return basicEncoding.toString();
    }
    
    /**
     * write all encoding generated by this class to file LogiqlEncoding.logic.
     *
     *
     */
    private void writeFile(String output) {
        String[] lines = output.split("\r\n|\r|\n");
        Statistics.addOrIncrementEntry("logiql_predicate_size", lines.length);
        try {
            String writePath = path + "/logiqlEncoding" + nth + ".logic";
            PrintWriter pw = new PrintWriter(writePath);
            pw.write(output);
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
