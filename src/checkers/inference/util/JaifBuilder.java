package checkers.inference.util;

import scenelib.annotations.io.ASTRecord;
import scenelib.annotations.io.ASTPath;
import scenelib.annotations.io.ASTPath.ASTEntry;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import checkers.inference.model.AnnotationLocation;
import checkers.inference.model.AnnotationLocation.AstPathLocation;
import checkers.inference.model.AnnotationLocation.ClassDeclLocation;

import com.sun.source.tree.Tree;

import org.checkerframework.framework.util.PluginUtil;
import org.checkerframework.javacutil.Pair;

/**
 * JaifBuilder creates Jaifs from a Map of ASTRecords to AnnotationMirrors.
 *
 * JaifBuilder first organizes ASTRecords by class and top level member, and then
 * builds a Jaif string.
 *
 * @author mcarthur
 *
 */
public class JaifBuilder {

    /**
     * Data structure that maps a class to its members (fields, variables, initializer)
     * The nested Map maps a member to the List of VariableSlots for that member.
     */
    private Map<String, ClassEntry> classesMap;

    /**
     * Represents a map of AnnotationLocation to the serialized form of the annotation
     * that should be inserted at that location
     */
    private final Map<AnnotationLocation, String> locationToAnno;


    /**
     * Used to build the import section of the Jaif and import all annotations that
     * are referenced in locationToAnnos
     */
    private final Set<? extends Class<? extends Annotation>> supportedAnnotations;
    private final boolean insertMainModOfLocalVar;

    private StringBuilder builder;

    public JaifBuilder(Map<AnnotationLocation, String> locationToAnno,
                        Set<? extends Class<? extends Annotation>> annotationMirrors) {
        this(locationToAnno, annotationMirrors, false);
    }
    public JaifBuilder(Map<AnnotationLocation, String> locationToAnno,
                        Set<? extends Class<? extends Annotation>> annotationMirrors, boolean insertMethodBodies) {
        this.locationToAnno = locationToAnno;
        this.supportedAnnotations = annotationMirrors;
        this.insertMainModOfLocalVar = insertMethodBodies;
    }

    /**
     * Creates a Jaif based on input slots.
     *
     * @return Jaif String
     */
    public String createJaif() {
        classesMap = new HashMap<>();
        builder = new StringBuilder();

        // Organize by classes

        buildClassEntries();

        // Write out annotation definition
        writeAnnotationHeader();

        // Write out each class
        for (Map.Entry<String, ClassEntry> entry: classesMap.entrySet()) {
            writeClassJaif(entry.getValue());
        }

        classesMap = null;
        return builder.toString();
    }

    /**
     * Add a header for all supported annotation mirrors.
     */
    private void writeAnnotationHeader() {
        for (Class<? extends Annotation> annotation : supportedAnnotations) {
            builder.append(buildAnnotationHeader(annotation));
            builder.append("\n");
        }
    }

    /**
     * Create annotation headers for an Annotation.
     *
     * @param annotation the Annotation to create the header for
     * @return the header
     */
    private String buildAnnotationHeader(Class<? extends Annotation> annotation) {
        // TODO: rewrite this method from scratch. We don't account for all possible cases of
        // outputs at this moment (eg annotation-field arrays). A clean rewrite should
        // exercise and be tested for all valid annotation field types.
        String result = "";
        String packageName = annotation.getPackage().toString();
        result += packageName + ":\n";
        String className = annotation.getSimpleName().toString();
        result += "  annotation @" + className + ":\n";
        for (Method method : annotation.getMethods()) {
            if (method.getDeclaringClass() == annotation) {
                result += "    ";
                if (Enum[].class.isAssignableFrom(method.getReturnType())) {
                    result += "enum ";
                }
                if (method.getReturnType().isArray()) {
                    result += method.getReturnType().getComponentType().getSimpleName() + "[]";
                } else if (method.getReturnType().isPrimitive() || method.getReturnType()
                        .getCanonicalName().contentEquals(String.class.getCanonicalName())) {
                    result += method.getReturnType().getSimpleName();
                } else {
                    result += method.getReturnType().getCanonicalName();
                }
                result += " " + method.getName().toString();
                result += "\n";
            }
        }

        return result;
    }

    /**
     * Add the jaif for the given classname and members.
     * @param classEntry A unique entry for all members of a class that will be converted to
     *                   a jaif entry for that class
     */
    private void writeClassJaif(ClassEntry classEntry) {
        builder.append("package " + classEntry.packageName + ":\n");
        builder.append("class " + classEntry.className + ":");
        if (!classEntry.declAnnos.isEmpty()) {
            builder.append(PluginUtil.join(" ", classEntry.declAnnos));
        }
        builder.append("\n");

        // Need to output members in a specific order.
        List<Entry<String, MemberRecords>> initializers = new ArrayList<>();
        List<Entry<String, MemberRecords>> fields = new ArrayList<>();
        List<Entry<String, MemberRecords>> methods = new ArrayList<>();

        for (Entry<String, MemberRecords> entry : classEntry.members.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() == 0) {
                initializers.add(entry);
            } else if (entry.getKey().startsWith("field")) {
                fields.add(entry);
            } else if (entry.getKey().startsWith("method")) {
                methods.add(entry);
            }
        }

        for (Entry<String, MemberRecords> entry : initializers) {
            writeMemberJaif(entry.getKey(), entry.getValue());
        }

        for (Entry<String, MemberRecords> entry : fields) {
            writeMemberJaif(entry.getKey(), entry.getValue());
        }

        for (Entry<String, MemberRecords> entry : methods) {
            writeMemberJaif(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Add the Jaif entries for all records under memberName
     * @param memberName the member
     * @param memberRecords the records for the member
     */
    private void writeMemberJaif(String memberName, MemberRecords memberRecords) {

        // Member name is null for InstanceInitializers
        if (memberName != null) {
            // Write out the member type
            // TODO: Instance initializers
            builder.append(memberName);
        }

        for (RecordValue value: memberRecords.entries) {
            builder.append("insert-annotation ");
            builder.append(value.astPath.toString());
            builder.append(": ");
            builder.append(value.value);
            builder.append("\n");
        }
        builder.append("\n");
    }

    /**
     * Change the Enum name to a String in the format required by AFU
     */
    private String treeKindToTitleCase(Tree.Kind kind) {
        String[] parts = kind.toString().toUpperCase().split("_");
        String result = "";
        for (String part : parts) {
            result += String.valueOf(part.charAt(0)) + part.substring(1).toLowerCase();
        }

        return result;
    }

    /**
     * Iterate through each variable and add it to the appropriate Class and Member list.
     */
    private void buildClassEntries() {
        for (Entry<AnnotationLocation, String> entry: locationToAnno.entrySet()) {
            AnnotationLocation location = entry.getKey();
            String annotation = entry.getValue();
            switch (location.getKind()) {
                case AST_PATH:
                    AstPathLocation astLocation = (AstPathLocation) location;
                    ClassEntry classEntry = getClassEntry(astLocation);
                    ASTRecord astRecord = astLocation.getAstRecord();

                    MemberRecords memberRecords = classEntry.getMemberRecords(astRecord.methodName, astRecord.varName);
                    if (!insertMainModOfLocalVar && isMainModOfLocalVar(astRecord.astPath)) {
                            continue;
                    }

                    // Don't insert annotation for empty ASTPath
                    // TODO: this is not a feature but a workaround of a bug:
                    // We should create a non-empty correct ASTPath for constructor
                    if (astRecord.astPath.equals(ASTPath.empty())) {
                        continue;
                    }

                    memberRecords.entries.add(new RecordValue(astRecord.astPath,annotation));
                    break;

                case CLASS_DECL:
                    ClassDeclLocation declLocation = (ClassDeclLocation) location;
                    classEntry = getClassEntry(declLocation);
                    classEntry.addDeclarationAnnotation(annotation);
                    break;

                case MISSING:
                    break;

                default:
                    throw new RuntimeException("Unhandled AnnotationLocation " + location +
                            " with value " + annotation);

            }
        }
    }

    /**
     * @param astRecord
     * @return true if the given AST path represents a main modifier of a local variable
     * An AST Path represents a main modifier of a local variable should have pattern like
     * 1) ..., Block.statement #, ..., Variable.type
     * 2) ..., Block.statement #, ..., Variable.type, ParameterizedType.type
     * reference: Local Variable Declaration Statements in JLS8
     * https://docs.oracle.com/javase/specs/jls/se8/html/jls-14.html#jls-14.4
     */
    protected boolean isMainModOfLocalVar(ASTPath astPath) {
        Iterator<ASTEntry> iterator = astPath.iterator();

        // first determine whether this astPath is a block statement
        while (iterator.hasNext()) {
            if (isEntry(Tree.Kind.BLOCK, ASTPath.STATEMENT, iterator.next())) {
                break;
            }
        }

        if (!iterator.hasNext()) {
            // this astPath either does not has Block.statement, or end up with
            // Block.statement. in both cases it doesn't represent a main modifier
            // of a local variable
            return false;
        }

        // next get the last two entry of this AST Path
        ASTEntry prevEntry = null;
        ASTEntry leafEntry = null;
        while (iterator.hasNext()) {
            leafEntry = iterator.next();
            if (!iterator.hasNext()) {
                break;
            }
            prevEntry = leafEntry;
        }

        assert leafEntry != null;

        if (isEntry(Tree.Kind.VARIABLE, ASTPath.TYPE, leafEntry)) {
            // the first kind of AST path of main modifier of local variable
            return true;
        } else if (prevEntry != null && isEntry(Tree.Kind.VARIABLE, ASTPath.TYPE, prevEntry) &&
            isEntry(Tree.Kind.PARAMETERIZED_TYPE, ASTPath.TYPE, leafEntry)) {
            // the second kind
            return true;
        }

        return false;
    }

    /**
     * determine whether a given {@code ASTEntry} represents
     * {@code (Tree.Kind).childSelector }, e.g. given an ASTEntry entry:
     * <pre>
     * {@code
     * Block.statement #
     * }</pre>
     * the tree kind is "Block", the childSelector is "statement"
     * thus, {@code isEntry(Tree.BLOCK, ASTPATH.STATEMENT, entry) } will return true
     * @param kind
     * @param childSelector
     * @param entry
     * @return true if the given Entry represents {@code (Tree.Kind).childSelector }
     */
    protected static boolean isEntry(Tree.Kind kind, String childSelector, ASTEntry entry) {
        return entry.getTreeKind() == kind && entry.getChildSelector().equals(childSelector);
    }

    private ClassEntry getClassEntry(AstPathLocation location) {
        return getClassEntry(location.getAstRecord().className);
    }

    private ClassEntry getClassEntry(ClassDeclLocation location) {
        String fullyQualifiedClass = ASTPathUtil.combinePackageAndClass(location.getPackageName(), location.getClassName());
        return getClassEntry(fullyQualifiedClass);
    }

    /**
     * Lookup or create, for a given class, a map of Members of that class
     * to a list of VariableSlots for those members.
     *
     * @param fullyQualified The class to look up.
     */
    private ClassEntry getClassEntry(String fullyQualified) {
        ClassEntry classEntry = this.classesMap.get(fullyQualified);
        if (classEntry == null) {
            Pair<String, String> packageToClass = ASTPathUtil.splitFullyQualifiedClass(fullyQualified);
            classEntry = new ClassEntry(packageToClass.first, packageToClass.second);
            this.classesMap.put(fullyQualified, classEntry);
        }
        return classEntry;
    }

    /**
     * Lookup or create, for a given class, a map of Members of that class
     * to a list of VariableSlots for those members.
     *
     * @param record a record identifying a unique class
     */
    private ClassEntry getClassMembers(ASTRecord record) {
        return getClassEntry(record.className);
    }

    private static String getMemberString(String methodName, String variableName) {
        String result = "";
        // Write out the member type
        if (methodName != null && variableName != null) {
            result += "method " + methodName + ":\n";
            if (variableName.equals("-1")) {
                result += "receiver:\n";
            } else {
                result += "parameter " + variableName + ":\n";
            }

        } else if (methodName != null) {
            result += "method " + methodName + ":\n";
        } else if (variableName != null) {
            result += "field " + variableName + ":\n";
        } else {
            return null;
        }

        return result;
    }

    private static class ClassEntry {
        final Set<String> declAnnos;
        final String packageName;
        final String className;

        public ClassEntry(String packageName, String className) {
            this.packageName = packageName;
            this.className = className;
            declAnnos = new LinkedHashSet<>();
        }

        /**
         * Add an annotation that should go on the declaration of the class
         */
        public void addDeclarationAnnotation(String annotation) {
            declAnnos.add(annotation);
        }

        /**
         * Lookup or create the List of VariableSLots for a Class and Member
         * @param memberName The top-level member name to look up
         * @param variableName If the record occurs in relation to a variable, this specifies the variable name
         * @return
         */
        public MemberRecords getMemberRecords(String memberName, String variableName) {
            MemberRecords memberRecords = members.get(getMemberString(memberName, variableName));
            if (memberRecords == null) {
                memberRecords = new MemberRecords();
                members.put(getMemberString(memberName, variableName), memberRecords);
            }
            return memberRecords;
        }

        Map<String, MemberRecords> members = new HashMap<>();
    }

    /**
     * The records for a member.
     */
    private static class MemberRecords {
        List<RecordValue> entries = new ArrayList<>();
    }

    /**
     * The value for a record.
     */
    private static class RecordValue {
        ASTPath astPath;
        String value;
        RecordValue(ASTPath record, String value) {
            this.astPath = record;
            this.value = value;
        }
    }
}
