package checkers.inference.model;

import annotations.io.ASTPath;

public abstract class Slot {

    private ASTPath astPath;

    public Slot() { }
    
    public abstract Object serialize(Serializer serializer);
    
    public Slot(ASTPath astPath) {
        this.astPath = astPath;
    }

    public ASTPath getAstPath() {
        return astPath;
    }

    public void setAstPath(ASTPath astPath) {
        this.astPath = astPath;
    }
}
