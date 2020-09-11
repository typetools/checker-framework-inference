package nninf;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.Tree;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import org.checkerframework.checker.nullness.KeyForAnnotatedTypeFactory;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;

import nninf.qual.NonNull;
import nninf.qual.Nullable;
import nninf.qual.PolyNull;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;

public class NninfAnnotatedTypeFactory extends GameAnnotatedTypeFactory {
    NninfChecker checker;
    MapGetHeuristics mapGetHeuristics;

    public NninfAnnotatedTypeFactory(NninfChecker checker) {
        super(checker);

        this.checker = checker;

        KeyForAnnotatedTypeFactory mapGetFactory = new KeyForAnnotatedTypeFactory(checker);
        mapGetHeuristics = new MapGetHeuristics(processingEnv, this, mapGetFactory);

        addAliasedAnnotation(org.checkerframework.checker.nullness.qual.NonNull.class,  checker.NONNULL);
        addAliasedAnnotation(org.checkerframework.checker.nullness.qual.Nullable.class, checker.NULLABLE);
        addAliasedAnnotation(org.checkerframework.checker.nullness.qual.KeyFor.class,   checker.KEYFOR);
        addAliasedAnnotation(org.checkerframework.common.subtyping.qual.Unqualified.class,     checker.UNKNOWNKEYFOR);

        postInit();

        defaults.addCheckedCodeDefault(checker.NONNULL,  TypeUseLocation.OTHERWISE);
        defaults.addCheckedCodeDefault(checker.NULLABLE, TypeUseLocation.LOCAL_VARIABLE);
    }

    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        Set<Class<? extends Annotation>> res = new HashSet<>();
        res.add(NonNull.class);
        res.add(Nullable.class);
        res.add(PolyNull.class);
        /*UnknownKeyFor.class, KeyFor.class */
        return res;
    }

    /*
     * Handle Map.get heuristics.
     */
    @Override
    public ParameterizedExecutableType methodFromUse(MethodInvocationTree tree) {
        ParameterizedExecutableType mType = super.methodFromUse(tree);
        AnnotatedExecutableType method = mType.executableType;

        TreePath path = this.getPath(tree);
        if (path != null) {
            mapGetHeuristics.handle(path, method);
        }
        return mType;
    }
    /**
     * Determine whether the tree dereferences the most enclosing "this" object. That is, we have an
     * expression like "f.g" and want to know whether it is an access "this.f.g". Returns false if f
     * is a field of an outer class or f is a local variable.
     *
     * @param tree the tree to check
     * @return true, iff the tree is an explicit or implicit reference to the most enclosing "this"
     */
    public final boolean isMostEnclosingThisDeref(ExpressionTree tree) {
        if (!isAnyEnclosingThisDeref(tree)) {
            return false;
        }

        Element element = TreeUtils.elementFromUse(tree);
        TypeElement typeElt = ElementUtils.enclosingClass(element);

        ClassTree enclosingClass = getCurrentClassTree(tree);
        if (enclosingClass != null
                && isSubtype(TreeUtils.elementFromDeclaration(enclosingClass), typeElt)) {
            return true;
        }

        // ran out of options
        return false;
    }

    private boolean isSubtype(TypeElement a1, TypeElement a2) {
        return (a1.equals(a2)
                || types.isSubtype(types.erasure(a1.asType()), types.erasure(a2.asType())));
    }

    /**
     * Does this expression have (the innermost or an outer) "this" as receiver? Note that the
     * receiver can be either explicit or implicit.
     *
     * @param tree the tree to test
     * @return true, iff the expression uses (the innermost or an outer) "this" as receiver
     */
    public final boolean isAnyEnclosingThisDeref(ExpressionTree tree) {
        if (!TreeUtils.isUseOfElement(tree)) {
            return false;
        }
        ExpressionTree recv = TreeUtils.getReceiverTree(tree);

        if (recv == null) {
            Element element = TreeUtils.elementFromUse(tree);

            if (!ElementUtils.hasReceiver(element)) {
                return false;
            }

            tree = TreeUtils.withoutParens(tree);

            if (tree.getKind() == Tree.Kind.IDENTIFIER) {
                Name n = ((IdentifierTree) tree).getName();
                if ("this".contentEquals(n) || "super".contentEquals(n)) {
                    // An explicit reference to "this"/"super" has no receiver.
                    return false;
                }
            }
            // Must be some access through this.
            return true;
        } else if (!TreeUtils.isUseOfElement(recv)) {
            // The receiver is e.g. a String literal.
            return false;
            // TODO: I think this:
            //  (i==9 ? this : this).toString();
            // is not a use of an element, as the receiver is an
            // expression. How should this be handled?
        }

        Element element = TreeUtils.elementFromUse(recv);

        if (!ElementUtils.hasReceiver(element)) {
            return false;
        }

        return TreeUtils.isExplicitThisDereference(recv);
    }
}
