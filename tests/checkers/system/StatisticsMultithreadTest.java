package checkers.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import checkers.inference.model.Constraint;
import checkers.inference.model.Serializer;
import checkers.inference.model.Slot;
import checkers.inference.solver.util.Statistics;
import junit.framework.TestCase;

public class StatisticsMultithreadTest extends TestCase {

    public static final int maxThreads = 100;
    public static final int numOfThreads = 100;

    private ExecutorService executor;

    @Override
    protected void setUp() throws Exception {
        executor = Executors.newFixedThreadPool(maxThreads);
    }

    @Override
    protected void tearDown() throws Exception {
        System.out.println("Statistics collected:");
        for (Entry<String, Long> entry : Statistics.getStatistics().entrySet()) {
            System.out.println(entry.getKey() + " --> " + entry.getValue());
        }
        Statistics.clearStatistics();
        executor = null;
    }

    /**
     * Functional interface for creating threads.
     *
     * Each thread is given a unique threadID and the return object must implement {@link Runnable}.
     */
    @FunctionalInterface
    private interface ThreadMaker {
        Runnable make(int threadID);
    }

    /**
     * Helper which runs a number of threads, using the given lambda to create threads, and waits
     * until all threads have completed.
     *
     * @param threadMaker
     *            lambda parameter which should return newly created threads
     */
    private void runThreads(ThreadMaker threadMaker) {
        // create and execute 100 threads, each trying to add or update an entry to the statistics
        for (int threadID = 0; threadID < numOfThreads; threadID++) {
            executor.execute(threadMaker.make(threadID));
        }
        // initiate clean shutdown of executor
        executor.shutdown();
        // wait for all threads to finish, up to 1 min
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method which checks the long value for the given key in the statistics map matches the
     * expected value.
     *
     * @param statistics
     * @param key
     * @param expectedValue
     */
    private void checkEqual(Map<String, Long> statistics, String key, long expectedValue) {
        assertEquals(statistics.get(key).longValue(), expectedValue);
    }

    // =======================================

    // must be lower case for retrieval
    public static final String IncrementEntryKey = "incremententrykey";
    public static final long IncrementEntryVal = 100L;

    @Test
    public void testAddOrIncrementEntry() {
        runThreads(threadID -> new AddOrIncrementEntryTestThread());

        // check that the entry in the statistics match the expected value
        Map<String, Long> finalStatistics = Statistics.getStatistics();
        checkEqual(finalStatistics, IncrementEntryKey, IncrementEntryVal * numOfThreads);
    }

    private class AddOrIncrementEntryTestThread implements Runnable {
        @Override
        public void run() {
            Statistics.addOrIncrementEntry(IncrementEntryKey, IncrementEntryVal);
        }
    }

    // =======================================

    /**
     * Helper method which returns the simple class name in lower case of the given class to be used
     * as a statistics key
     *
     * @param clazz
     * @return the simple class name in lower case
     */
    private String classStatsKeyName(Class<?> clazz) {
        return clazz.getSimpleName().toLowerCase();
    }

    // =======================================

    private List<Slot> slots;

    @Test
    public void testRecordSlotsStatistics() {
        slots = new ArrayList<>();

        slots.add(new DummyOneSlot());
        slots.add(new DummyTwoSlot());
        slots.add(new DummyTwoSlot());

        runThreads(threadID -> new RecordSlotsTestThread());

        Map<String, Long> finalStatistics = Statistics.getStatistics();
        checkEqual(finalStatistics, classStatsKeyName(DummyOneSlot.class), numOfThreads);
        checkEqual(finalStatistics, classStatsKeyName(DummyTwoSlot.class), 2 * numOfThreads);
    }

    private class RecordSlotsTestThread implements Runnable {
        @Override
        public void run() {
            Statistics.recordSlotsStatistics(slots);
        }
    }

    // dummy slots used in this test
    private class DummyOneSlot extends Slot {
        @Override
        public <S, T> S serialize(Serializer<S, T> serializer) {
            return null;
        }

        @Override
        public Kind getKind() {
            return null;
        }
    }

    private class DummyTwoSlot extends Slot {
        @Override
        public <S, T> S serialize(Serializer<S, T> serializer) {
            return null;
        }

        @Override
        public Kind getKind() {
            return null;
        }
    }

    // =======================================

    private List<Constraint> constraints;

    @Test
    public void testRecordConstraintsStatistics() {
        constraints = new ArrayList<>();

        constraints.add(new DummyOneTestConstraint(null));
        constraints.add(new DummyTwoTestConstraint(null));
        constraints.add(new DummyTwoTestConstraint(null));

        runThreads(threadID -> new RecordConstraintsTestThread());

        Map<String, Long> finalStatistics = Statistics.getStatistics();
        checkEqual(finalStatistics, classStatsKeyName(DummyOneTestConstraint.class), numOfThreads);
        checkEqual(finalStatistics, classStatsKeyName(DummyTwoTestConstraint.class), 2 * numOfThreads);
    }

    private class RecordConstraintsTestThread implements Runnable {
        @Override
        public void run() {
            Statistics.recordConstraintsStatistics(constraints);
        }
    }

    // dummy constraints used in this test
    private class DummyOneTestConstraint extends Constraint {
        public DummyOneTestConstraint(List<Slot> slots) {
            super(slots);
        }

        @Override
        public <S, T> T serialize(Serializer<S, T> serializer) {
            return null;
        }
    }

    private class DummyTwoTestConstraint extends Constraint {
        public DummyTwoTestConstraint(List<Slot> slots) {
            super(slots);
        }

        @Override
        public <S, T> T serialize(Serializer<S, T> serializer) {
            return null;
        }
    }
}

