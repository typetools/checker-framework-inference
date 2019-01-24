// Test case for Issue #24:
// https://github.com/typetools/checker-framework-inference/issues/24

// @skip-test
import java.util.Collections;
import java.util.List;

public class Issue24 {

    public void sort(List<Integer> unsorted) {
        Collections.sort(unsorted);
    }
}
