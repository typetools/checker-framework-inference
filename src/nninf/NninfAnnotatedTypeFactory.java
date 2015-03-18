package nninf;

import java.util.List;

import org.checkerframework.framework.qual.DefaultLocation;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.GeneralAnnotatedTypeFactory;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.javacutil.Pair;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;

public class NninfAnnotatedTypeFactory extends GameAnnotatedTypeFactory {
    NninfChecker checker;
    MapGetHeuristics mapGetHeuristics;

    public NninfAnnotatedTypeFactory(NninfChecker checker) {
        super(checker);

        this.checker = checker;

        // TODO: why is this not a KeyForAnnotatedTypeFactory?
        // What qualifiers does it insert? The qualifier hierarchy is null.
        GeneralAnnotatedTypeFactory mapGetFactory = new GeneralAnnotatedTypeFactory(checker);
        mapGetHeuristics = new MapGetHeuristics(processingEnv, this, mapGetFactory);

        addAliasedAnnotation(org.checkerframework.checker.nullness.qual.NonNull.class,  checker.NONNULL);
        addAliasedAnnotation(org.checkerframework.checker.nullness.qual.Nullable.class, checker.NULLABLE);
        addAliasedAnnotation(org.checkerframework.checker.nullness.qual.KeyFor.class,   checker.KEYFOR);
        addAliasedAnnotation(org.checkerframework.framework.qual.Unqualified.class,       checker.UNKNOWNKEYFOR);

        postInit();

        defaults.addAbsoluteDefault(checker.NONNULL,  DefaultLocation.OTHERWISE);
        defaults.addAbsoluteDefault(checker.NULLABLE, DefaultLocation.LOCAL_VARIABLE);
    }

    @Override
    protected MultiGraphQualifierHierarchy.MultiGraphFactory createQualifierHierarchyFactory() {
        return new MultiGraphQualifierHierarchy.MultiGraphFactory(this);
    }

    /*
     * Handle Map.get heuristics.
     */
    @Override
    public Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> methodFromUse(MethodInvocationTree tree) {
        Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> mfuPair = super.methodFromUse(tree);
        AnnotatedExecutableType method = mfuPair.first;

        TreePath path = this.getPath(tree);
        if (path!=null) {
            mapGetHeuristics.handle(path, method);
        }
        return mfuPair;
    }
}