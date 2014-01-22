package checkers.inference.util;

import checkers.types.AnnotatedTypeMirror;
import checkers.types.AnnotatedTypeMirror.*;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.NewClassTree;
import javacutils.AnnotationUtils;

import javax.lang.model.element.AnnotationMirror;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
            return true;
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
}
