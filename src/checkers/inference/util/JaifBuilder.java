package checkers.inference.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.checkerframework.javacutil.ErrorReporter;

import annotations.io.ASTIndex.ASTRecord;
import annotations.io.ASTPath;
import annotations.io.ASTPath.ASTEntry;

import com.sun.source.tree.Tree;

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
    private Map<String, ClassMembers> classesMap;
    private StringBuilder builder;
    private Map<ASTRecord, String> values;
    private List<? extends Class<? extends Annotation>> supportedAnnotations;

    public JaifBuilder(Map<ASTRecord, String> values,
            List<? extends Class<? extends Annotation>> annotationMirrors) {
        this.values = values;
        this.supportedAnnotations = annotationMirrors;
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
        buildMemeberMap();
        writeAnnotationHeader();

        // Write out each class
        for (Map.Entry<String, ClassMembers> entry: classesMap.entrySet()) {
           writeClassJaif(entry.getKey(), entry.getValue());
        }
        return builder.toString();
    }

    /**
     * Add a header for all supported annotation mirrors.
     */
    private void writeAnnotationHeader() {
        for (Class<? extends Annotation> annotation : supportedAnnotations) {
            builder.append(getAnnotationHeader(annotation));
            builder.append("\n");
        }
    }

    /**
     * Create annotation headers for an Annotation.
     *
     * @param annotation the Annotation to create the header for
     * @return the header
     */
    private String getAnnotationHeader(Class<? extends Annotation> annotation) {
        String result = "";
        String packageName = annotation.getPackage().toString();
        result += packageName + ":\n";
        String className = annotation.getSimpleName().toString();
        result += "  annotation @" + className + ":\n";
        for (Method method : annotation.getMethods()) {
            if (method.getDeclaringClass() == annotation){
                result += "    " + method.getReturnType().getSimpleName().toString();
                result += " " + method.getName().toString();
                result += "\n";
            }
        }

        return result;
    }

    /**
     * Add the jaif for the given classname and members.
     * @param fullClassname Flatname classname
     * @param classMembers The top-level member of classname
     */
    private void writeClassJaif(String fullClassname, ClassMembers classMembers) {

        final String pkgName;
        if (fullClassname.indexOf(".") == -1) {
            // default package
            pkgName = "";
        } else {
            pkgName = fullClassname.substring(0, fullClassname.lastIndexOf("."));
        }
        builder.append("package " + pkgName + ":\n");

        String className = fullClassname.substring(fullClassname.lastIndexOf(".") + 1);
        builder.append("class " + className + ":\n");

        for (Entry<String, MemberRecords> entry : classMembers.members.entrySet()) {
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
            builder.append(":");
            builder.append("\n");
        }

        for (RecordValue value: memberRecords.entries) {
            builder.append("insert-annotation ");
            builder.append(astPathToString(value.astPath));
            builder.append(": ");
            builder.append(value.value);
            builder.append("\n");
        }
        builder.append("\n");
    }

    /**
     * Create a string for a given ast path.
     */
    private String astPathToString(ASTPath path) {
        String pathStr = "";
        for (ASTEntry entry : path) {
            if (pathStr.length() > 0) {
                pathStr += ", ";
            }

            pathStr += treeKindToTitleCase(entry.getTreeKind()) + "." + entry.getChildSelector();
            if (entry.hasArgument()) {
                pathStr += " " + entry.getArgument();
            }
        }
        return pathStr;
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
    private void buildMemeberMap() {
        for (Entry<ASTRecord, String> entry: values.entrySet()) {
            ASTRecord record = entry.getKey();
            if (record != null) {
                // VariableSlots mights be given to library code
                // (which don't have a tree or ASTRecord).
                MemberRecords membersRecords =
                        getMemberRecords(record.className, record.otherName, record.declKind);
                membersRecords.entries.add(new RecordValue(record.astPath, entry.getValue()));
            }
        }
    }

    /**
     * Lookup or create the List of VariableSLots for a Class and Member
     *
     * @param className The class name to look up
     * @param memberName The top-level member name to look up
     * @param memberType The member type
     * @return
     */
    private MemberRecords getMemberRecords(String className, String memberName, Tree.Kind memberType) {
        ClassMembers classMembers = getClassMembers(className);
        MemberRecords memberRecords = classMembers.members.get(getMemberString(memberName, memberType));
        if (memberRecords == null) {
            memberRecords = new MemberRecords();
            classMembers.members.put(getMemberString(memberName, memberType), memberRecords);
        }
        return memberRecords;
    }

    /**
     * Lookup or create, for a given class, a map of Members of that class
     * to a list of VariableSlots for those members.
     *
     * @param className The class to look up.
     */
    private ClassMembers getClassMembers(String className) {
        ClassMembers classMembers = this.classesMap.get(className);
        if (classMembers == null) {
            classMembers = new ClassMembers();
            this.classesMap.put(className, classMembers);
        }
        return classMembers;
    }

    private String getMemberString(String name, Tree.Kind memberType) {
        if (name == null) {
            return null;

        } else {
            String result = "";
            // Write out the member type
            switch(memberType) {
                case METHOD:
                    result += "method ";
                    break;
                case VARIABLE:
                    result += "field ";
                    break;
                default:
                    ErrorReporter.errorAbort("Unhandled member type: " + memberType);
            }
            result += name;
            return result;
        }
    }

    /**
     * The members of a class.
     */
    private static class ClassMembers {
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
