package checkers.inference;

import checkers.inference.model.Constraint;
import com.sun.source.tree.ExpressionTree;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.GeneralAnnotatedTypeFactory;
import org.checkerframework.framework.util.typeinference.DefaultTypeArgumentInference;
import org.checkerframework.framework.util.typeinference.TypeArgInferenceUtil;
import org.checkerframework.framework.util.typeinference.constraint.AFConstraint;
import org.checkerframework.framework.util.typeinference.constraint.F2A;
import org.checkerframework.framework.util.typeinference.constraint.FIsA;
import org.checkerframework.framework.util.typeinference.constraint.TUConstraint;
import org.checkerframework.javacutil.InternalUtils;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InferenceTypeArgumentInference extends DefaultTypeArgumentInference {

    private final ConstraintManager constraintManager;

    public InferenceTypeArgumentInference(ConstraintManager constraintManager) {
        this.constraintManager = constraintManager;
    }

    @Override
    public Map<TypeVariable, AnnotatedTypeMirror> inferTypeArgs(AnnotatedTypeFactory typeFactory,
                                                                ExpressionTree expressionTree,
                                                                ExecutableElement methodElem,
                                                                AnnotatedExecutableType methodType) {



        //Infer as normal but with potentials, recording a copy of the ConstraintMap before continuing
        //Whenever a LUB would occur, leave only the Java type and create a CombineVariable/Constraint that indicates
        //the type is a LUB of the other types, use that Comb as the return type?)

        //NEED TO RETURN SOMETHING
        return null;
    }

    private Map<TypeVariable, AnnotatedTypeMirror> createInferredArgs(Set<TypeVariable> targets, AnnotatedExecutableType methodType) {
        return null;
    }

    private Set<Constraint> tuToCfiConstraints(Set<TUConstraint> tuConstraints, Map<TypeVariable, AnnotatedTypeMirror> inferredArgs,
                                               Set<TypeVariable> target) {
        //return the
        return null;
    }
}
