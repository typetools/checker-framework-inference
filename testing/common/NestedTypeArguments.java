import java.util.Map;
import java.util.List;

class NestedTypeArguments {
    Generic<? extends String, List<Object>> g;
}

class Generic<T, S> {}