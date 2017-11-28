package nninf;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.flow.CFAbstractAnalysis;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;

public class GameAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    public GameAnnotatedTypeFactory(BaseTypeChecker checker, boolean useFlow) {
        super(checker, useFlow);

        // Subclasses call postInit
        // this.postInit();
    }

    public GameAnnotatedTypeFactory(BaseTypeChecker checker) {
        this(checker, FLOW_BY_DEFAULT);
    }
//
//    @Override
//    protected TypeHierarchy createTypeHierarchy() {
//        return new TypeHierarchy(checker, getQualifierHierarchy()) {
//            @Override
//            public boolean isSubtype(AnnotatedTypeMirror sub, AnnotatedTypeMirror sup) {
//
//                if (sub.getEffectiveAnnotations().isEmpty() ||
//                        sup.getEffectiveAnnotations().isEmpty()) {
//                    // TODO: The super method complains about empty annotations. Prevent this.
//                    // TODO: Can we avoid getting into the state with empty annotations?
//                    assert false;
//                    return true;
//                }
//                return super.isSubtype(sub, sup);
//            }
//        };
//    }

    @Override
    public CFTransfer createFlowTransferFunction(CFAbstractAnalysis<CFValue, CFStore, CFTransfer> analysis) {
        return new CFTransfer(analysis);
    }
}
