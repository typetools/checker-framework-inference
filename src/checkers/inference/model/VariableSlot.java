package checkers.inference.model;

import annotations.io.ASTPath;

/**
 * VariableSlot is a Slot representing an undetermined value (i.e. a variable we are solving for).
 * After the Solver is run, each VariableSlot should have an assigned value which is then written
 * to the output Jaif file for later reinsertion into the original source code.
 *
 * Before the Solver is run, VariableSlots are represented by @VarAnnot( slot id ) annotations
 * on AnnotatedTypeMirrors.  When an AnnotatedTypeMirror is encountered in a position that would
 * generate constraints (e.g. either side of an assignment ), its @VarAnnots are converted into
 * VariableSlots which are then used in the generated constraints.
 *
 * E.g.  @VarAnnot(0) String s;
 * The above example implies that a VariableSlot with id 0 represents the possible annotations
 * on the declaration of s.
 */
public class VariableSlot extends Slot {

    /**
     * Uniquely identifies this Slot.  id's are monotonically increasing in value by the order they
     * are generated
     */
    private int id;

    /**
     * @param astPath Used to locate this variable in code, astPath should point to the tree on which a @VarAnnot would
     *                be placed in order to identify this variable.
     *                E.g.
     *                class MyClass {  @VarAnnot(0) String s = "a";  }
     *
     *                The ASTPath for the VariableSlot with id 0 would be the path from the root of the compilation unit
     *                to the tree "String s"
     *
     * @param id      Unique identifier for this variable
     */
    public VariableSlot(ASTPath astPath, int id) {
        super(astPath);
        this.id = id;
    }
    
    @Override
    public Object serialize(Serializer serializer) {
        return serializer.serialize(this);
    }
    
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public VariableSlot(int id) {
        this.id = id;
    }
}
