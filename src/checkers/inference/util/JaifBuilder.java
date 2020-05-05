package checkers.inference.util;

import scenelib.annotations.io.ASTRecord;
import scenelib.annotations.io.ASTPath;
import scenelib.annotations.io.ASTPath.ASTEntry;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    /**
     * Caches annotation definitions that have been written
     */
    private final Set<Class<? extends Annotation>> writeAnnotationHeaderCache = new HashSet<>();

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

        // Write out annotation definitions for all supported annotation mirrors
        for (Class<? extends Annotation> annotation : supportedAnnotations) {
            writeAnnotationHeader(annotation);
        }

        // Write out each class
        for (Map.Entry<String, ClassEntry> entry: classesMap.entrySet()) {
            writeClassJaif(entry.getValue());
        }

        classesMap = null;
        return builder.toString();
    }

    /**
     * Add a header for a single supported annotation mirror, and recursively adds headers for any
     * annotations used as the return type of this annotation's methods. The annotations used as
     * return types must always be added as a header before the annotation using it in a method.
     */
    private void writeAnnotationHeader(Class<? extends Annotation> annotation) {
        // each annotation only needs to be written once to the header, skip if already written
        if (writeAnnotationHeaderCache.contains(annotation)) {
            // this case happens if a supported annotation contains multiple methods with the same
            // annotation return type
            return;
        }
        // cache early to prevent infinite recursion in case there are any mutually-dependent pairs
        // of annotations (Java forbids mutually-dependent annotations)
        writeAnnotationHeaderCache.add(annotation);

        // for each supported annotation, we also create headers for any annotation classes used as
        // the return type of a method of the supported annotation
        for (Method method : annotation.getDeclaredMethods()) {
            Class<?> methodReturnType = method.getReturnType();

            // de-sugar 1D array return types for their array component type
            // note: Java only permits 1D arrays as the return types of methods in an annotation
            if (methodReturnType.isArray()) {
                methodReturnType = methodReturnType.getComponentType();
            }

            // if any return type is an annotation, then recursively create a header for the return
            // type and check the return type's fields for annotations
            if (methodReturnType.isAnnotation()) {
                writeAnnotationHeader(methodReturnType.asSubclass(Annotation.class));
            }
        }

        // write the header for the given annotation
        builder.append(buildAnnotationHeader(annotation))
               .append("\n");
    }

    /**
     * Create annotation headers for an Annotation.
     *
     * @param annotation the Annotation to create the header for
     * @return the header
     */
    private String buildAnnotationHeader(Class<? extends Annotation> annotation) {
        StringBuilder sb = new StringBuilder();
        // insert package name
        sb.append(annotation.getPackage())
          .append(":\n  annotation @")
          // insert class name
          .append(annotation.getSimpleName())
          .append(":\n");

        for (Method method : annotation.getDeclaredMethods()) {
            // insert 4 space indentation for each return type
            sb.append("    ")
              // insert the return type
              .append(getAnnotationHeaderReturnType(method.getReturnType()))
              .append(" ")
              // insert method name
              .append(method.getName())
              .append("\n");
        }

        return sb.toString();
    }

    /**
     * Java allows the method return types in an annotation to be:
     *
     * 1) Enums
     *
     * 2) Annotations
     *
     * 3) String types
     *
     * 4) Class types
     *
     * 5) primitive types
     *
     * 6) 1D arrays with a component type of one of the above
     *
     * This method returns the appropriate return type name according to the JAIF specification for
     * each of these scenarios for the given returnType argument.
     */
    private String getAnnotationHeaderReturnType(final Class<?> returnType) {
        // de-sugar array return types
        boolean isArray = returnType.isArray();
        final Class<?> actualReturnType = isArray ? returnType.getComponentType() : returnType;

        String result;

        if (Enum.class.isAssignableFrom(actualReturnType)) {
            result = "enum " + actualReturnType.getCanonicalName();
        } else if (actualReturnType.isAnnotation()) {
            result = "annotation-field " + actualReturnType.getCanonicalName();
        } else if (actualReturnType.getCanonicalName().equals(String.class.getCanonicalName())
                || actualReturnType.getCanonicalName().equals(Class.class.getCanonicalName())) {
            // TODO: AFU should support "java.lang.String" and "java.lang.Class" in its
            // specification
            result = actualReturnType.getSimpleName();
        } else {
            // this case is for all primitives
            result = actualReturnType.getCanonicalName();
        }

        // append "[]" if return type is an array
        return isArray ? result + "[]" : result;
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
            builder.append(String.join(" ", classEntry.declAnnos));
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
        return getClassEntry(location.getFullyQualifiedClassName());
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
