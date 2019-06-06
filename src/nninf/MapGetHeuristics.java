package nninf;

import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;

import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;

import nninf.qual.KeyFor;

/**
 * Utilities class for handling {@code Map.get()} invocations.
 *
 * The heuristics cover the following cases:
 *
 * <ol>
 * <li value="1">Within the true condition of a map.containsKey() if statement:
 * <pre><code>if (map.containsKey(key)) { Object v = map.get(key); }</code></pre>
 * </li>
 *
 * <li value="2">Within an enhanced-for loop of the map.keySet():
 * <pre><code>for (Object key: map.keySet()) { Object v = map.get(key); }</code></pre>
 * </li>
 *
 * <li value="3">Preceded by an assertion of contains or nullness get check:
 * <pre><code>assert map.containsKey(key);
 * Object v = map.get(key);</code></pre>
 *
 * Or
 *
 * <pre><code>assert map.get(key) != null;
 * Object v = map.get(key);</code></pre>
 *
 * <li value="4">Preceded by an check of contains or nullness if
 * test that throws an exception, in the first line:
 *
 * <pre><code>if (!map.contains(key)) throw new Exception();
 * Object v = map.get(key);
 * </code></pre>
 *
 * <li value="5">Preceded by a put-if-absent pattern convention:
 *
 * <pre><code>if (!map.contains(key)) map.put(key, DEFAULT_VALUE);
 * Object v = map.get(key);</code></pre>
 *
 * </ol>
 */
/*package-scope*/ class MapGetHeuristics {

    private final ProcessingEnvironment env;
    private final NninfAnnotatedTypeFactory factory;
    private final AnnotatedTypeFactory keyForFactory;
    private final Resolver2 resolver;

    private final ExecutableElement mapGet;

    public MapGetHeuristics(ProcessingEnvironment env,
            NninfAnnotatedTypeFactory factory,
            AnnotatedTypeFactory keyForFactory) {
        this.env = env;
        this.factory = factory;
        this.keyForFactory = keyForFactory;
        this.resolver = new Resolver2(env);

        mapGet = TreeUtils.getMethod("java.util.Map", "get", 1, env);
    }

    public void handle(TreePath path, AnnotatedExecutableType method) {
        MethodInvocationTree tree = (MethodInvocationTree)path.getLeaf();
        if (TreeUtils.isMethodInvocation(tree, mapGet, env)) {
            AnnotatedTypeMirror type = method.getReturnType();
            type.removeAnnotationInHierarchy(factory.checker.NULLABLE);
            if (!isSuppressable(path)) {
                type.addAnnotation(factory.checker.NULLABLE);
            } else {
                type.addAnnotation(factory.checker.NONNULL);
            }
        }
    }

    /**
     * Checks whether the key passed to {@code Map.get(K key)} is known
     * to be in the map.
     *
     * TODO: Document when this method returns true
     */
    private boolean isSuppressable(TreePath path) {
        MethodInvocationTree tree = (MethodInvocationTree)path.getLeaf();
        Element elt = getSite(tree);

        if (elt instanceof VariableElement) {
            ExpressionTree arg = tree.getArguments().get(0);
            return keyForInMap(arg, elt, path)
                || keyForInMap(arg, ((VariableElement)elt).getSimpleName().toString())
                || keyForInMap(arg, String.valueOf(TreeUtils.getReceiverTree(tree)));
        }

        return false;
    }

    /**
     * Returns true if the key is a member of the specified map
     */
    private boolean keyForInMap(ExpressionTree key,
            String mapName) {
        AnnotatedTypeMirror keyForType = keyForFactory.getAnnotatedType(key);

        AnnotationMirror anno = keyForType.getAnnotation(KeyFor.class);
        if (anno == null)
            return false;

        List<String> maps = AnnotationUtils.getElementValueArray(anno, "value", String.class, false);

        return maps.contains(mapName);
    }

    private boolean keyForInMap(ExpressionTree key,
            Element mapElement, TreePath path) {
        AnnotatedTypeMirror keyForType = keyForFactory.getAnnotatedType(key);

        AnnotationMirror anno = keyForType.getAnnotation(KeyFor.class);
        if (anno == null)
            return false;

        List<String> maps = AnnotationUtils.getElementValueArray(anno, "value", String.class, false);
        for (String map: maps) {
            Element elt = resolver.findVariable(map, path);
            if (elt != null &&
                    elt.equals(mapElement) &&
                    !isSiteRequired(TreeUtils.getReceiverTree((ExpressionTree)path.getLeaf()), elt)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Helper function to determine if the passed element is sufficient
     * to resolve a reference at compile time, without needing to
     * represent the call/dereference site.
     */
    private boolean isSiteRequired(ExpressionTree node, Element elt) {
        boolean r = ElementUtils.isStatic(elt) ||
            !elt.getKind().isField() ||
            factory.isMostEnclosingThisDeref(node);
        return !r;
    }

    private Element getSite(MethodInvocationTree tree) {
        // TODO: Check this behavior for implicit receivers/outer receivers
        return TreeUtils.elementFromUse( TreeUtils.getReceiverTree(tree) );
    }

    private boolean isCheckOfGet(Element key, VariableElement map, ExpressionTree tree) {
        tree = TreeUtils.withoutParens(tree);
        if (tree.getKind() != Tree.Kind.NOT_EQUAL_TO
            || ((BinaryTree)tree).getRightOperand().getKind() != Tree.Kind.NULL_LITERAL)
            return false;

        Tree right = TreeUtils.withoutParens(((BinaryTree)tree).getLeftOperand());
        if (right instanceof MethodInvocationTree) {
            MethodInvocationTree invok = (MethodInvocationTree)right;
            if (TreeUtils.isMethodInvocation(invok, mapGet, env)) {
                Element containsArgument = TreeUtils.elementFromTree(invok.getArguments().get(0));
                if (key.equals(containsArgument) && map.equals(getSite(invok)))
                    return true;
            }
        }
        return false;
    }
}

