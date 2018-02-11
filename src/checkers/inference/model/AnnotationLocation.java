package checkers.inference.model;

import scenelib.annotations.io.ASTPath;
import scenelib.annotations.io.ASTRecord;

/**
 * Describes a location in Java Source Code.
 */
public abstract class AnnotationLocation {

    /**
     * The default instance of MISSING_LOCATION, the constructor is private.
     * TODO: Maybe we should have MISSING_LOCATION information that contains strings or even
     * TODO: path information that identifies the locations that cannot be inserted BUT can
     * TODO: can be
     */
    public static AnnotationLocation MISSING_LOCATION = new MissingLocation();

    /**
     * Annotation locations can be of 3 kinds:
     *   a) a general location described by an Annotation File Utilities AST Path,
     *   this covers all locations except or annotations on class declarations
     *
     *   b) class declaration annotations (e.g. @HERE class MyClass {...}
     *
     *   c) missing, that is there is no source code location for a specified annotation
     */
    public enum Kind {
        /** The most common Annotation kind specified by an AstPath, see AstPathLocation*/
        AST_PATH,

        /** Applicable only for annotations in front of class declarations, see ClassDeclLocation */
        CLASS_DECL,

        /** Missing indicates that the associated annotation cannot be inserted into source code */
        MISSING
    }

    private final Kind kind;

    public AnnotationLocation(Kind kind) {
        this.kind = kind;
    }

    public abstract boolean isInsertable();

    public Kind getKind() {
        return kind;
    }

    /**
     * Associates an annotation with an exact location in source using Annotation File Utilities ASTRecords
     */
    public static class AstPathLocation extends AnnotationLocation {
        private final ASTRecord astRecord;

        public AstPathLocation(ASTRecord astRecord) {
            super(AnnotationLocation.Kind.AST_PATH);
            this.astRecord = astRecord;
        }

        public ASTRecord getAstRecord() {
            return astRecord;
        }

        public ASTPath getAstPath() {
            return astRecord.astPath;
        }

        @Override
        public boolean isInsertable() {
            return true;
        }

        public boolean equals(Object otherObj) {
            if (otherObj == this) {
                return true;
            }

            if (otherObj == null || !otherObj.getClass().equals(AstPathLocation.class)) {
                return false;
            }

            final AstPathLocation other = (AstPathLocation) otherObj;
            return astRecord.equals(other.astRecord);
        }

        public int hashCode() {
            return 3299 * astRecord.hashCode();
        }

        @Override
        public String toString() {
            return "AstPathLocation( " + astRecord.className + "." + astRecord.methodName + "."
                                       + astRecord.varName   + ":" + astRecord.toString()   + " )";
        }
    }

    /**
     * Associates an annotation on a class declaration in source using class and package names
     */
    public static class ClassDeclLocation extends AnnotationLocation {
        private final String fullyQualifiedClassName;

        public ClassDeclLocation(String fullyQualifiedClassName) {
            super(AnnotationLocation.Kind.CLASS_DECL);
            this.fullyQualifiedClassName = fullyQualifiedClassName;
        }

        public String getFullyQualifiedClassName() {
            return fullyQualifiedClassName;
        }

        @Override
        public boolean isInsertable() {
            return true;
        }

        public boolean equals(Object otherObj) {
            if (otherObj == this) {
                return true;
            }

            if (otherObj == null || !otherObj.getClass().equals(ClassDeclLocation.class)) {
                return false;
            }

            final ClassDeclLocation other = (ClassDeclLocation) otherObj;
            return fullyQualifiedClassName.equals(other.fullyQualifiedClassName);
        }

        public int hashCode() {
            return 3343 * (fullyQualifiedClassName.hashCode());
        }

        @Override
        public String toString() {
            return "ClassDeclLocation( " + fullyQualifiedClassName + " )";
        }
    }

    /**
     * Indicates that a annotation is not insertable in source.  It is also used in location
     * where we haven't yet created the code to create an "implied tree", i.e. a place
     * where the AFU will create source code and insert annotations there but we haven't
     * created the code to make the AFU String to place in the output JAIF
     */
    private static class MissingLocation extends AnnotationLocation {

        public MissingLocation() {
            super(AnnotationLocation.Kind.MISSING);
        }

        @Override
        public boolean isInsertable() {
            return false;
        }

        public boolean equals(Object otherObj) {
            if (otherObj == this || otherObj == MISSING_LOCATION) {
                return true;
            }

            return otherObj.getClass().equals(MissingLocation.class);
        }

        public int hashCode() {
            return 6427 * MissingLocation.class.hashCode();
        }

        public String toString() {
            return MissingLocation.class.getSimpleName();
        }
    }
}
