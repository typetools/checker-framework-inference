package checkers.inference;

import java.lang.annotation.Annotation;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import checkers.types.AnnotatedTypeFactory;
import checkers.types.AnnotatedTypeMirror;

import com.sun.source.tree.CompilationUnitTree;

public interface InferenceTypeChecker {
    // from BaseTypeChecker
    void init(ProcessingEnvironment processingEnv);

    // from BaseTypeChecker
    AnnotatedTypeFactory createFactory(CompilationUnitTree root);

    // from BaseTypeChecker
    Set<Class<? extends Annotation>> getSupportedTypeQualifiers();

    boolean needsAnnotation(AnnotatedTypeMirror ty);

    AnnotationMirror defaultQualifier();

    AnnotationMirror selfQualifier();

    boolean withCombineConstraints();
}