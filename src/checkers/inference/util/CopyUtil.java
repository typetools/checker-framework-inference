package checkers.inference.util;

import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.EXECUTABLE;
import static javax.lang.model.type.TypeKind.NONE;
import static javax.lang.model.type.TypeKind.NULL;
import static javax.lang.model.type.TypeKind.PACKAGE;
import static javax.lang.model.type.TypeKind.TYPEVAR;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.lang.model.type.TypeKind.WILDCARD;

import java.util.IdentityHashMap;
import java.util.List;

import javacutils.ErrorReporter;

import javax.lang.model.type.TypeKind;

import checkers.types.AnnotatedTypeMirror;
import checkers.types.AnnotatedTypeMirror.AnnotatedArrayType;
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType;
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType;
import checkers.types.AnnotatedTypeMirror.AnnotatedTypeVariable;
import checkers.types.AnnotatedTypeMirror.AnnotatedWildcardType;

/**
 * Contains utility methods and classes for copying annotaitons from one type to another.
 */
public class CopyUtil {

    public static interface CopyMethod {
        public void copy(final AnnotatedTypeMirror from, final AnnotatedTypeMirror to);
    }

    public static class ClearAndCopy implements CopyMethod {

        /** Clear to of any annotations and copy those from 'from' to 'to'
         * Does not descend into nested types if any
         * @param from  AnnotatedTypeMirror to copy
         * @param to AnnotatedTypeMirror to clear then add to
         * @return The original set of annotations on Mod
         */
        @Override
        public void copy(AnnotatedTypeMirror from, AnnotatedTypeMirror to) {
            to.clearAnnotations();
            to.addAnnotations(from.getAnnotations());
        }
    }

    /**
     * Copy annotations from in to mod, descending into any nested types in
     * the two AnnotatedTypeMirrors.  Any existing annotations will be cleared first
     * @param from The AnnotatedTypeMirror that should be copied
     * @param to The AnnotatedTypeMirror to which annotations will be copied
     */
    public static void copyAnnotations(final AnnotatedTypeMirror from, final AnnotatedTypeMirror to) {
        copyAnnotationsImpl(from, to, new ClearAndCopy(), new IdentityHashMap<AnnotatedTypeMirror, AnnotatedTypeMirror>());
    }

    /**
     * Use copyAnnotations to deep copy the return type and parameter type annotations
     * from one AnnotatedExecutableType to another
     * @param from The executable type with annotations to copy
     * @param to   The executable type to which annotations will be copied
     */
    public static void copyParameterAndReturnTypes(final AnnotatedExecutableType from, AnnotatedExecutableType to) {

        if (from.getReturnType().getKind() != TypeKind.NONE) {
            copyAnnotations(from.getReturnType(), to.getReturnType());
        }

        final List<AnnotatedTypeMirror> fromParams  = from.getParameterTypes();
        final List<AnnotatedTypeMirror> toParams    = to.getParameterTypes();

        assert(fromParams.size() == toParams.size());

        for(int i = 0; i < toParams.size(); i++) {
            copyAnnotations(fromParams.get(i), toParams.get(i));
        }
    }

    private static void copyAnnotationsImpl(final AnnotatedTypeMirror from, final AnnotatedTypeMirror to,
                                             final CopyMethod copyMethod, final IdentityHashMap<AnnotatedTypeMirror, AnnotatedTypeMirror> visited) {
        if(from == null) {
            return;
        }

        if(visited.keySet().contains(from)) {
            return;
        }
        visited.put(from, from);

        if(!from.getAnnotations().isEmpty()) {
            copyMethod.copy(from, to);
        }

        final TypeKind fromKind = from.getKind();
        final TypeKind toKind = to.getKind();

        if (fromKind == PACKAGE) {
            // Do nothing.
            return;
        } else if(fromKind == DECLARED && toKind == DECLARED) {
            copyAnnotationsTogether(((AnnotatedDeclaredType) from).getTypeArguments(),
                                     ((AnnotatedDeclaredType)   to).getTypeArguments(),
                                     copyMethod, visited);

        } else if(fromKind == EXECUTABLE && toKind == EXECUTABLE) {
            final AnnotatedExecutableType fromExeType = (AnnotatedExecutableType) from;
            final AnnotatedExecutableType toExeType  = (AnnotatedExecutableType) to;

            copyAnnotationsImpl(fromExeType.getReturnType(), toExeType.getReturnType(), copyMethod, visited);
            copyAnnotationsImpl(fromExeType.getReceiverType(),  toExeType.getReceiverType(), copyMethod, visited);
            copyAnnotationsTogether(fromExeType.getParameterTypes(), toExeType.getParameterTypes(), copyMethod, visited);
            copyAnnotationsTogether(fromExeType.getTypeVariables(),  toExeType.getTypeVariables(), copyMethod, visited);

        } else if(fromKind == ARRAY && toKind == ARRAY) {
            copyAnnotationsImpl(((AnnotatedArrayType) from).getComponentType(),
                                 ((AnnotatedArrayType)   to).getComponentType(),
                                 copyMethod, visited);

        } else if(fromKind == TYPEVAR && toKind == TYPEVAR) {
            final AnnotatedTypeVariable fromAtv = (AnnotatedTypeVariable) from;
            final AnnotatedTypeVariable toAtv   = (AnnotatedTypeVariable) to;
            if (fromAtv.equals(toAtv)) { //&& fromAtv.getUpperBound().equals(toAtv.getUpperBound())
//                    && fromAtv.getLowerBound().equals(toAtv.getLowerBound())) {
                return;
            }
            copyAnnotationsImpl(fromAtv.getUpperBound(), toAtv.getUpperBound(), copyMethod, visited);
            copyAnnotationsImpl(fromAtv.getLowerBound(), toAtv.getLowerBound(), copyMethod, visited);

        } else if(toKind == TYPEVAR) {
            // Why is sometimes the mod a type variable, but in is Declared or Wildcard?
            // For declared, the annotations match. For wildcards, in is unannotated?
            // TODO. Look at tests/Interfaces.java

        } else if(fromKind == WILDCARD && toKind == WILDCARD) {
            final AnnotatedWildcardType fromWct = (AnnotatedWildcardType) from;
            final AnnotatedWildcardType tpWct   = (AnnotatedWildcardType) to;
            copyAnnotationsImpl(fromWct.getExtendsBound(), tpWct.getExtendsBound(), copyMethod, visited);
            copyAnnotationsImpl(fromWct.getSuperBound(),   tpWct.getSuperBound(),   copyMethod, visited);

        } else if(fromKind.isPrimitive() && toKind.isPrimitive()) {
             // Primitives only take one annotation, which was already copied

        } else if(fromKind == NONE || fromKind == NULL || fromKind == VOID ||
                  toKind   == NONE || toKind   == NULL || fromKind == NULL) {
             // No annotations
        } else {
            // TODO: DON"T COMMIT !
            if (true) {
                return;
            }
            ErrorReporter.errorAbort("InferenceUtils.copyAnnotationsImpl: unhandled getKind results: " + from +
                    " and " + to + "\n    of kinds: " + fromKind + " and " + toKind);
        }
    }

    private static void copyAnnotationsTogether(final List<? extends AnnotatedTypeMirror> from,
                                                final List<? extends AnnotatedTypeMirror> to,
                                                final CopyMethod copyMethod,
                                                final IdentityHashMap<AnnotatedTypeMirror, AnnotatedTypeMirror> visited) {
        for(int i = 0; i < from.size(); i++) {
            if (i == to.size()) {
                break;
            }
            copyAnnotationsImpl(from.get(i), to.get(i), copyMethod, visited);
        }
    }
}
