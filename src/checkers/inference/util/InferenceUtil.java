package checkers.inference.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.javacutil.AnnotationUtils;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

public class InferenceUtil {

    /**
     * Clear all primary annotations on atm
     * @return the set of cleared annotations
     */
    public static Set<AnnotationMirror> clearAnnos(final AnnotatedTypeMirror atm) {

        final Set<AnnotationMirror> oldAnnos = AnnotationUtils.createAnnotationSet();
        oldAnnos.addAll(atm.getAnnotations());

        atm.clearAnnotations();
        return oldAnnos;
    }

    /**
     * If the given condition isn't true throw an illegal argument exception with the given message
     */
    public static void testArgument(boolean condition, final String message) {
        if(!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Is the given classTree a declaration of an anonymous class
     */
    public static boolean isAnonymousClass(ClassTree classTree) {
        return (classTree.getSimpleName() == null) || (classTree.getSimpleName().toString().equals(""));
    }

    /**
     * Is this newClassTree a declaration of an anonymous class
     */
    public static boolean isAnonymousClass(NewClassTree newClassTree) {
        if(newClassTree.getClassBody() == null) {
            return false;
        }

        return isAnonymousClass(newClassTree.getClassBody());
    }

    /**
     * Converts the collection to a string using its natural ordering.
     * Objects are converted using their toString method and then delimited by a comma (,)
     * @param toPrint The collection to joined together as a string.
     * @return toPrint in string form
     */
    public static String join(Collection<?> toPrint) {
        return join(toPrint, ", ");
    }

    /**
     * Converts the collection to a string using its natural ordering.
     * Objects are converted using their toString method and then delimited by separator
     * @param toPrint The collection to joined together as a string.
     * @return all elements of toPrint converted to strings and separated by separator
     */
    public static String join(Collection<?> toPrint, final String separator) {
        if(toPrint == null) {
            return null;
        }

        if(toPrint.isEmpty()) {
            return "";
        }

        Iterator<?> iterator = toPrint.iterator();

        StringBuilder sb = new StringBuilder();
        sb.append(iterator.next());
        while(iterator.hasNext()) {
            sb.append(separator);
            sb.append(iterator.next().toString());
        }

        return sb.toString();
    }

    /**
     * For the given map, create a string from all entries in the map's natural order, separated by
     * ", "
     * @see checkers.inference.util.InferenceUtil#join(java.util.Map, String)
     * @param toPrint The Map we wish to create a string from
     * @return A string containing the entries of toPrint separated by separator
     */
    public static String join(Map<?,?> toPrint) {
        return join(toPrint, ", ");
    }


    /**
     * For the given map, create a string from all entries in the map's natural order arranged as follows:
     * Key1 -> Value1<separator>Key2 -> Value2<separator>...KeyN -> ValueN
     * e.g for a Map( 1 -> "One", 2 -> "Two", 3 -> "Three" ) and a separator of "_sep_"
     * the output would be "1 -> One_sep_2 -> Two_sep_3 -> Three
     * @param toPrint The Map we wish to create a string from
     * @return A string containing the entries of toPrint separated by separator
     */
    public static String join(Map<?,?> toPrint, final String separator) {
        if(toPrint == null) {
            return null;
        }

        if(toPrint.isEmpty()) {
                return "";
        }

        final Iterator<? extends Map.Entry<?,?>> iterator = toPrint.entrySet().iterator();

        final StringBuilder sb = new StringBuilder();
        final Map.Entry<?,?> first = iterator.next();

        sb.append(first.getKey() + " -> " + first.getValue());

        for (Map.Entry<?,?> entry : toPrint.entrySet()) {
            sb.append(separator);
            sb.append(entry.getKey() + " -> " + entry.getValue());
        }

        return sb.toString();
    }

    private static List<String> detachedVarSymbols = Arrays.asList("index#num", "iter#num", "assertionsEnabled#num", "array#num");

    public static boolean isDetachedVariable(Tree targetTree) {
        String name;
        if (targetTree instanceof VariableTree) {
            name = ((VariableTree) targetTree).getName().toString();
        } else if (targetTree instanceof IdentifierTree) {
            name = ((IdentifierTree) targetTree).getName().toString();
        } else {
            return false;
        }

        for (String str : detachedVarSymbols) {
            if (name.startsWith(str)) {
                return true;
            }
        }
        return false;
    }
}
