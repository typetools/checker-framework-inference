package checkers.inference;

import java.util.Properties;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;

public class InferenceChecker extends BaseTypeChecker {

    @Override
    public void initChecker() {
        InferenceMain.getInstance().recordInferenceCheckerInstance(this);
        // Needed for error messages and reporting.
        super.initChecker();
        // Overrides visitor created by initChecker
        this.visitor = InferenceMain.getInstance().getVisitor();
    }

    /**
     * Called during super.initChecker(). We want it to do nothing.
     */
    @Override
    protected BaseTypeVisitor<?> createSourceVisitor() {
        return null;
    }

    @Override
    public Properties getMessages() {
        // Add the messages.properties file defined in the same location as
        // InferenceChecker
        Properties messages = super.getMessages();
        messages.putAll(getProperties(this.getClass(), MSGS_FILE));
        return messages;
    }
}
