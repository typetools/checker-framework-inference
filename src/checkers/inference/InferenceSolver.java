package checkers.inference;

import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;

import joptsimple.OptionSet;
import checkers.types.QualifierHierarchy;

public interface InferenceSolver {

    Map<Integer, AnnotationMirror> solve(List<checkers.inference.model.Slot> slots,
            List<checkers.inference.model.Constraint> constraints,
            OptionSet options,
            QualifierHierarchy qualHierarchy);
}
