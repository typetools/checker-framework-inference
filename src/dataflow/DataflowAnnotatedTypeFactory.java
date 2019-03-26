package dataflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.util.GraphQualifierHierarchy;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy.MultiGraphFactory;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;

import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree.Kind;

import dataflow.qual.DataFlow;
import dataflow.qual.DataFlowTop;
import dataflow.util.DataflowUtils;

/**
 * DataflowAnnotatedTypeFactory is the type factory for Dataflow type system. It
 * defines the subtype relationship of Dataflow type system, annotate the base
 * cases, and implements simplification algorithm.
 * 
 * @author jianchu
 *
 */
public class DataflowAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    protected final AnnotationMirror DATAFLOW, DATAFLOWBOTTOM, DATAFLOWTOP;
    /**
     * For each Java type is present in the target program, typeNamesMap maps
     * String of the type to the TypeMirror.
     */
    private final Map<String, TypeMirror> typeNamesMap = new HashMap<String, TypeMirror>();

    public DataflowAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        DATAFLOW = AnnotationBuilder.fromClass(elements, DataFlow.class);
        DATAFLOWBOTTOM = DataflowUtils.createDataflowAnnotation(DataflowUtils.convert(""), processingEnv);
        DATAFLOWTOP = AnnotationBuilder.fromClass(elements, DataFlowTop.class);
        postInit();
    }

    @Override
    public TreeAnnotator createTreeAnnotator() {
        return new ListTreeAnnotator(super.createTreeAnnotator(), new DataflowTreeAnnotator());
    }

    @Override
    public QualifierHierarchy createQualifierHierarchy(MultiGraphFactory factory) {
        return new DataFlowQualifierHierarchy(factory, DATAFLOWBOTTOM);
    }

    /**
     * This method handles autoboxing for primitive type.
     * For statements, Integer i = 3;
     * The annotation for i should be @DataFlow(typeNames = {"Integer"}).
     */
    @Override
    public AnnotatedDeclaredType getBoxedType(AnnotatedPrimitiveType type) {
        TypeElement typeElt = types.boxedClass(type.getUnderlyingType());
        AnnotationMirror am = DataflowUtils.createDataflowAnnotation(typeElt.asType().toString(),
                this.processingEnv);
        AnnotatedDeclaredType dt = fromElement(typeElt);
        dt.addAnnotation(am);
        return dt;
    }

    /**
     * This method handles unboxing for reference type.
     * For statements, int i = new Integer(3);
     * The annotation for i should be @DataFlow(typeNames = {"int"}).
     */
    @Override
    public AnnotatedPrimitiveType getUnboxedType(AnnotatedDeclaredType type)
            throws IllegalArgumentException {
        PrimitiveType primitiveType = types.unboxedType(type.getUnderlyingType());
        AnnotationMirror am = DataflowUtils.createDataflowAnnotation(primitiveType.toString(),
                this.processingEnv);
        AnnotatedPrimitiveType pt = (AnnotatedPrimitiveType) AnnotatedTypeMirror.createType(
                primitiveType, this, false);
        pt.addAnnotation(am);
        return pt;
    }

    private final class DataFlowQualifierHierarchy extends GraphQualifierHierarchy {

        public DataFlowQualifierHierarchy(MultiGraphFactory f, AnnotationMirror bottom) {
            super(f, bottom);
        }

        /**
         * This method checks whether rhs is subtype of lhs. rhs and lhs are
         * both Dataflow types with typeNameRoots argument.
         * 
         * @param rhs
         * @param lhs
         * @return true is rhs is subtype of lhs, otherwise return false.
         */
        private boolean isSubtypeWithRoots(AnnotationMirror rhs, AnnotationMirror lhs) {

            Set<String> rTypeNamesSet = new HashSet<String>(Arrays.asList(DataflowUtils
                    .getTypeNames(rhs)));
            Set<String> lTypeNamesSet = new HashSet<String>(Arrays.asList(DataflowUtils
                    .getTypeNames(lhs)));
            Set<String> rRootsSet = new HashSet<String>(Arrays.asList(DataflowUtils
                    .getTypeNameRoots(rhs)));
            Set<String> lRootsSet = new HashSet<String>(Arrays.asList(DataflowUtils
                    .getTypeNameRoots(lhs)));
            Set<String> combinedTypeNames = new HashSet<String>();
            combinedTypeNames.addAll(rTypeNamesSet);
            combinedTypeNames.addAll(lTypeNamesSet);
            Set<String> combinedRoots = new HashSet<String>();
            combinedRoots.addAll(rRootsSet);
            combinedRoots.addAll(lRootsSet);

            AnnotationMirror combinedAnno = DataflowUtils.createDataflowAnnotationWithRoots(
                    combinedTypeNames, combinedRoots, processingEnv);
            AnnotationMirror refinedCombinedAnno = refineDataflow(combinedAnno);
            AnnotationMirror refinedLhs = refineDataflow(lhs);

            if (AnnotationUtils.areSame(refinedCombinedAnno, refinedLhs)) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * This method checks whether rhs is subtype of lhs. rhs and lhs are
         * both Dataflow types without typeNameRoots argument. Currently this
         * method is not used, but we can use it for a lightweight dataflow type
         * system. (One without typeNameRoots argument).
         * 
         * @param rhs
         * @param lhs
         * @return true is rhs is subtype of lhs, otherwise return false.
         */
        private boolean isSubtypeWithoutRoots(AnnotationMirror rhs, AnnotationMirror lhs) {
            Set<String> rTypeNamesSet = new HashSet<String>(Arrays.asList(DataflowUtils
                    .getTypeNames(rhs)));
            Set<String> lTypeNamesSet = new HashSet<String>(Arrays.asList(DataflowUtils
                    .getTypeNames(lhs)));
            if (lTypeNamesSet.containsAll(rTypeNamesSet)) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean isSubtype(AnnotationMirror rhs, AnnotationMirror lhs) {
            if (AnnotationUtils.areSameByName(rhs, DATAFLOW)
                    && AnnotationUtils.areSameByName(lhs, DATAFLOW)) {
                return isSubtypeWithRoots(rhs, lhs);
                // return isSubtypeWithoutRoots(rhs, lhs);
            } else {
                // if (rhs != null && lhs != null)
                if (AnnotationUtils.areSameByName(rhs, DATAFLOW)) {
                    rhs = DATAFLOW;
                } else if (AnnotationUtils.areSameByName(lhs, DATAFLOW)) {
                    lhs = DATAFLOW;
                }
                return super.isSubtype(rhs, lhs);
            }
        }
    }

    public class DataflowTreeAnnotator extends TreeAnnotator {
        public DataflowTreeAnnotator() {
            super(DataflowAnnotatedTypeFactory.this);
        }

        @Override
        public Void visitNewArray(final NewArrayTree node, final AnnotatedTypeMirror type) {
            AnnotationMirror dataFlowType = DataflowUtils.genereateDataflowAnnoFromNewClass(type,
                    processingEnv);
            TypeMirror tm = type.getUnderlyingType();
            typeNamesMap.put(tm.toString(), tm);
            type.replaceAnnotation(dataFlowType);
            return super.visitNewArray(node, type);
        }

        @Override
        public Void visitNewClass(NewClassTree node, AnnotatedTypeMirror type) {
            AnnotationMirror dataFlowType = DataflowUtils.genereateDataflowAnnoFromNewClass(type,
                    processingEnv);
            TypeMirror tm = type.getUnderlyingType();
            typeNamesMap.put(tm.toString(), tm);
            type.replaceAnnotation(dataFlowType);
            return super.visitNewClass(node, type);
        }

        @Override
        public Void visitLiteral(LiteralTree node, AnnotatedTypeMirror type) {
            if (!node.getKind().equals(Kind.NULL_LITERAL)) {
                AnnotatedTypeMirror annoType = type;
                AnnotationMirror dataFlowType = DataflowUtils.generateDataflowAnnoFromLiteral(annoType,
                        processingEnv);
                type.replaceAnnotation(dataFlowType);
            }
            return super.visitLiteral(node, type);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, AnnotatedTypeMirror type) {
            ExecutableElement methodElement = TreeUtils.elementFromUse(node);
            boolean isBytecode = ElementUtils.isElementFromByteCode(methodElement);
            if (isBytecode) {
                AnnotationMirror dataFlowType = DataflowUtils.genereateDataflowAnnoFromByteCode(type,
                        processingEnv);
                TypeMirror tm = type.getUnderlyingType();
                if (tm.getKind() == TypeKind.ARRAY) {
                    replaceArrayComponentATM((AnnotatedArrayType) type);
                }
                typeNamesMap.put(tm.toString(), tm);
                type.replaceAnnotation(dataFlowType);
            }
            return super.visitMethodInvocation(node, type);
        }
    }
    
    /**
     * Simplefication algoirthm.
     * 
     * @param type
     * @return A simplified annotation.
     */
    public AnnotationMirror refineDataflow(AnnotationMirror type) {
        String[] typeNameRoots = DataflowUtils.getTypeNameRoots(type);
        Set<String> refinedRoots = new HashSet<String>();

        if (typeNameRoots.length == 0) {

        } else if (typeNameRoots.length == 1) {
            refinedRoots.add(typeNameRoots[0]);
        } else {
            List<String> rootsList = new ArrayList<String>(Arrays.asList(typeNameRoots));
            while (rootsList.size() != 0) {
                TypeMirror decType = getTypeMirror(rootsList.get(0));
                if (!isComparable(decType, rootsList)) {
                    refinedRoots.add(rootsList.get(0));
                    rootsList.remove(0);
                }
            }
        }

        String[] typeNames = DataflowUtils.getTypeNames(type);
        Arrays.sort(typeNames);
        Set<String> refinedtypeNames = new HashSet<String>();

        if (refinedRoots.size() == 0) {
            refinedtypeNames = new HashSet<String>(Arrays.asList(typeNames));
            return DataflowUtils.createDataflowAnnotation(refinedtypeNames, processingEnv);
        } else {
            for (String typeName : typeNames) {
                if (typeName == "") {
                    continue;
                }
                TypeMirror decType = getTypeMirror(typeName);
                if (shouldPresent(decType, refinedRoots)) {
                    refinedtypeNames.add(typeName);
                }
            }
        }

        return DataflowUtils.createDataflowAnnotationWithRoots(refinedtypeNames, refinedRoots,
                processingEnv);
    }

    /**
     * Add the bytecode default Dataflow annotation for component type of the given {@link AnnotatedArrayType}.
     *
     *<p> For multi-dimensional array, this method will recursively add bytecode default Dataflow annotation to array's component type.
     *
     * @param arrayAtm the given {@link AnnotatedArrayType}, whose component type will be added the bytecode default.
     */
    private void replaceArrayComponentATM(AnnotatedArrayType arrayAtm) {
        AnnotatedTypeMirror componentAtm = arrayAtm.getComponentType();
        AnnotationMirror componentAnno = DataflowUtils.genereateDataflowAnnoFromByteCode(componentAtm,
                processingEnv);
        componentAtm.replaceAnnotation(componentAnno);
        if (componentAtm.getKind() == TypeKind.ARRAY) {
            replaceArrayComponentATM((AnnotatedArrayType) componentAtm);
        }
    }

    private boolean isComparable(TypeMirror decType, List<String> rootsList) {
        for (int i = 1; i < rootsList.size(); i++) {
            if (rootsList.get(i) == "") {
                continue;
            }
            TypeMirror comparedDecType = getTypeMirror(rootsList.get(i));
            if (this.types.isSubtype(comparedDecType, decType)) {
                rootsList.remove(i);
                return true;
            } else if (this.types.isSubtype(decType, comparedDecType)) {
                rootsList.remove(0);
                return true;
            }
        }

        return false;
    }

    private boolean shouldPresent(TypeMirror decType, Set<String> refinedRoots) {
        for (String refinedRoot : refinedRoots) {
            if (refinedRoot == "") {
                continue;
            }
            TypeMirror comparedDecType = getTypeMirror(refinedRoot);
            if (this.types.isSubtype(decType, comparedDecType)) {
                return false;
            } else if (this.types.isSubtype(comparedDecType, decType)) {
                return true;
            }
        }
        return true;
    }

    private TypeMirror getTypeMirror(String typeName) {
        if (this.typeNamesMap.keySet().contains(typeName)) {
            return this.typeNamesMap.get(typeName);
        } else {
            return elements.getTypeElement(convertToReferenceType(typeName)).asType();
        }
    }

    private String convertToReferenceType(String typeName) {
        switch (typeName) {
        case "int":
            return Integer.class.getName();
        case "short":
            return Short.class.getName();
        case "byte":
            return Byte.class.getName();
        case "long":
            return Long.class.getName();
        case "char":
            return Character.class.getName();
        case "float":
            return Float.class.getName();
        case "double":
            return Double.class.getName();
        case "boolean":
            return Boolean.class.getName();
        default:
            return typeName;
        }
    }

    public Map<String, TypeMirror> getTypeNameMap() {
        return this.typeNamesMap;
    }
}
