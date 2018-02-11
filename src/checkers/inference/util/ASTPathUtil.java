package checkers.inference.util;

import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedIntersectionType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedNoType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedNullType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedUnionType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedWildcardType;
import org.checkerframework.framework.type.visitor.AnnotatedTypeScanner;
import org.checkerframework.framework.util.AnnotatedTypes;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.Pair;

import java.util.IdentityHashMap;
import java.util.logging.Logger;

import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreePath;

import scenelib.annotations.io.ASTIndex;
import scenelib.annotations.io.ASTPath;
import scenelib.annotations.io.ASTRecord;

/**
 * ASTPathUtil is a collection of utilities to create ASTRecord for existing trees, as well
 * as trees that are implied to exist but are not required by the compiler (e.g. extends Object).
 */
public class ASTPathUtil {

    protected static final Logger logger = Logger.getLogger(ASTPathUtil.class.getName());


    public static final String AFU_CONSTRUCTOR_ID = "<init>()V";

    public static ASTRecord getASTRecordForNode(final AnnotatedTypeFactory typeFactory, Tree node) {
        return getASTRecordForPath(typeFactory, typeFactory.getPath(node));
    }


    /**
     * Look up an ASTRecord for a node.
     * @param typeFactory Type factory to look up tree path (and CompilationUnit)
     * @return The ASTRecord for node
     */
    public static ASTRecord getASTRecordForPath(final AnnotatedTypeFactory typeFactory, TreePath pathToNode) {
        if (pathToNode == null) {
            return null;
        }

        Tree node = pathToNode.getLeaf();

        // ASTIndex caches the lookups, so we don't.
        if (ASTIndex.indexOf(pathToNode.getCompilationUnit()).containsKey(node)) {
            ASTRecord record = ASTIndex.indexOf(pathToNode.getCompilationUnit()).get(node);
            if (record == null) {
                logger.warning("ASTIndex returned null for record: " + node);
            }
            return record;
        } else {
            logger.fine("Did not find ASTRecord for node: " + node);
            return null;
        }
    }

    /**
     * Given the record for a class, return a new record that maps to the no-arg constructor for
     * this class.
     */
    public static ASTRecord getConstructorRecord(ASTRecord classRecord) {
        return new ASTRecord(classRecord.ast, classRecord.className, AFU_CONSTRUCTOR_ID, null, ASTPath.empty());
    }

    /**
     * Some times there are trees that are implied by the source code but not explicitly written.  (e.g. extends Object)
     * The AFU will create these trees if we try to insert an annotation on them.  Therefore, we need to create
     * an AFU record that corresponds to the location on the "implied" (non-existant) tree.
     *
     * This method creates a mapping of AnnotatedTypeMirrors that are children of type and an ASTRecord that
     * corresponds to annotating the primary annotation of that type.
     *
     * @param parent The parent of the implied tree
     * @param type The type on which we are storing annotations.
     * @return A mapping of annotated type mirror to the ASTRecord that locates the primary annotation of the
     * annotated type mirror in source code.  i.e. (ATM -> locatation of primary annotation on ATM in source code)
     */
    public static IdentityHashMap<AnnotatedTypeMirror, ASTRecord> getImpliedRecordForUse(final ASTRecord parent, final AnnotatedTypeMirror type) {
        AFUPathMapper pathMapper = new AFUPathMapper();
        pathMapper.visit(type, parent);

        return pathMapper.mapping;
    }

    /**
     * A scanner that builds a mapping of
     * ATM -> (AstRecord representing the location of the primary annotation of ATM)
     */
    protected static class AFUPathMapper extends AnnotatedTypeScanner<Void, ASTRecord> {

        final IdentityHashMap<AnnotatedTypeMirror, ASTRecord> mapping = new IdentityHashMap<>();

        public  ASTRecord extendParent(final ASTRecord parent, Tree.Kind treeKind, String type, int arg) {
            return parent.extend(treeKind, type, arg);
        }

        /**
         * @return true if this is the first time we have encountered type and therefore we stored type -> path
         */
        public boolean storeCurrent(final AnnotatedTypeMirror type, ASTRecord currentPath) {
            if (mapping.containsKey(type)) {
                return false;
            } else {
                mapping.put(type, currentPath);
                return true;
            }
        }

        @Override
        public Void visitDeclared(AnnotatedDeclaredType type, ASTRecord current) {
            if (storeCurrent(type, current)) {

                int typeArgIndex = 0;
                for (AnnotatedTypeMirror typeArg : type.getTypeArguments()) {
                    ASTRecord next = extendParent(current, Kind.PARAMETERIZED_TYPE, ASTPath.TYPE_ARGUMENT, typeArgIndex);
                    visit(typeArg, next);
                    typeArgIndex++;
                }
            }

            return null;
        }

        @Override
        public Void visitIntersection(AnnotatedIntersectionType type, ASTRecord current) {

            int boundIndex = 0;
            for (AnnotatedTypeMirror bound : type.directSuperTypes()) {
                ASTRecord toBound = extendParent(current, Kind.INTERSECTION_TYPE, ASTPath.BOUND, boundIndex);
                visit(bound, toBound);
                boundIndex++;
            }

            return null;
        }

        @Override
        public Void visitUnion(AnnotatedUnionType type, ASTRecord current) {

            int alternativeIndex = 0;
            for (AnnotatedDeclaredType bound : type.getAlternatives()) {
                ASTRecord toBound = extendParent(current, Kind.UNION_TYPE, ASTPath.TYPE_ALTERNATIVE, alternativeIndex);
                visit(bound, toBound);
                alternativeIndex++;
            }

            return null;
        }

        @Override
        public Void visitArray(AnnotatedArrayType type, ASTRecord current) {

            ASTRecord toArrayType = extendParent(current, Kind.ARRAY_TYPE, ASTPath.TYPE, -1);
            storeCurrent(type, toArrayType);

            // TODO: THERE DOESN'T SEEM TO BE A WAY TO REFERENCE THE COMPONENT TYPE, TALK TO DAN
            ErrorReporter.errorAbort("Not implemented!");
            return null;
        }

        @Override
        public Void visitExecutable(AnnotatedExecutableType type, ASTRecord parent) {
            throw new UnsupportedOperationException("This class was not intended to create paths for methods.");
        }

        @Override
        public Void visitTypeVariable(AnnotatedTypeVariable type, ASTRecord current) {
            storeCurrent(type, current);
            // nothing to visit because this is a use and not a declaration
            return null;
        }

        @Override
        public Void visitNoType(AnnotatedNoType type, ASTRecord current) {
            return null;
        }

        @Override
        public Void visitNull(AnnotatedNullType type, ASTRecord current) {
            storeCurrent(type, current);
            return null;
        }

        @Override
        public Void visitPrimitive(AnnotatedPrimitiveType type, ASTRecord current) {
            storeCurrent(type, current);
            return null;
        }

        @Override
        public Void visitWildcard(AnnotatedWildcardType type, ASTRecord current) {
            if (mapping.containsKey(type)) {
                return null;
            }

            if (!AnnotatedTypes.isExplicitlySuperBounded(type)) {
                mapping.put(type.getSuperBound(), current);

                if (AnnotatedTypes.isExplicitlyExtendsBounded(type)) {
                    final ASTRecord toBound = extendParent(current, Kind.EXTENDS_WILDCARD, ASTPath.BOUND, 0);
                    visit(type.getExtendsBound(), toBound);
                } else {
                    final ASTRecord toBound = extendParent(current, Kind.UNBOUNDED_WILDCARD, ASTPath.BOUND, 0);
                    visit(type.getExtendsBound(), toBound);
                }
            } else {
                mapping.put(type.getExtendsBound(), current);
                final ASTRecord toBound = extendParent(current, Kind.SUPER_WILDCARD, ASTPath.BOUND, 0);
                visit(type.getSuperBound(), toBound);
            }

            return null;
        }
    }

    /**
     * Converts fully qualified class name into a pair of Strings (packageName -> className)
     */
    public static Pair<String, String> splitFullyQualifiedClass(String fullClassname) {
        String pkgName;
        String className;
        int lastPeriod = fullClassname.lastIndexOf(".");
        if (lastPeriod == -1) {
            // default package
            pkgName = "";
            className = fullClassname;
        } else {
            pkgName = fullClassname.substring(0, lastPeriod);
            className = fullClassname.substring(lastPeriod + 1, fullClassname.length());
        }

        return Pair.of(pkgName, className);
    }
}
