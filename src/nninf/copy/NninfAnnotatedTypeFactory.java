package nninf.copy;

import java.util.List;

import checkers.quals.DefaultLocation;
import checkers.types.AnnotatedTypeMirror;
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType;
import checkers.types.GeneralAnnotatedTypeFactory;
import checkers.util.MultiGraphQualifierHierarchy;
import javacutils.Pair;

import com.sun.source.tree.CompilationUnitTree;
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

        addAliasedAnnotation(checkers.nullness.quals.NonNull.class,  checker.NONNULL);
        addAliasedAnnotation(checkers.nullness.quals.Nullable.class, checker.NULLABLE);
        addAliasedAnnotation(checkers.nullness.quals.KeyFor.class,   checker.KEYFOR);
        addAliasedAnnotation(checkers.quals.Unqualified.class,       checker.UNKNOWNKEYFOR);

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