import java.nio.file.SimpleFileVisitor;

public class AnonymousProblem {

    SimpleFileVisitor s = new SimpleFileVisitor<String>(){};

    OutterI.InnerI<Object> f = new OutterI.InnerI<Object>() {};
}

interface OutterI<T> {
    public interface InnerI<T> {}
}
