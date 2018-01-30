package nninf;

import org.checkerframework.checker.nullness.KeyForAnnotatedTypeFactory;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.javacutil.Pair;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;

import nninf.qual.NonNull;
import nninf.qual.Nullable;
import nninf.qual.PolyNull;

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
        addAliasedAnnotation(org.checkerframework.framework.qual.Unqualified.class,     checker.UNKNOWNKEYFOR);

        postInit();

        defaults.addCheckedCodeDefault(checker.NONNULL,  TypeUseLocation.OTHERWISE);
        defaults.addCheckedCodeDefault(checker.NULLABLE, TypeUseLocation.LOCAL_VARIABLE);
    }

    @Override
    protected MultiGraphQualifierHierarchy.MultiGraphFactory createQualifierHierarchyFactory() {
        return new MultiGraphQualifierHierarchy.MultiGraphFactory(this);
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
    public Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> methodFromUse(MethodInvocationTree tree) {
        Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> mfuPair = super.methodFromUse(tree);
        AnnotatedExecutableType method = mfuPair.first;

        TreePath path = this.getPath(tree);
        if (path != null) {
            mapGetHeuristics.handle(path, method);
        }
        return mfuPair;
    }
}
