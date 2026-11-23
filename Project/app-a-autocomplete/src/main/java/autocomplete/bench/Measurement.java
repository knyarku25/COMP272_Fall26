package autocomplete.bench;

import java.util.Arrays;

/**
 * How I time one data point (one solution, one size, one workload).
 *
 * <p>Java microbenchmarks are easy to get wrong, because the JVM changes the program
 * while it runs. This class handles the four classic pitfalls from the handout:
 * <ol>
 *   <li><b>JIT warm-up.</b> The first runs are slow while the JVM compiles the code, so I
 *       do {@link #WARMUP_RUNS} runs first and throw their times away.</li>
 *   <li><b>Timer resolution.</b> One operation can take a few nanoseconds, too short to time
 *       on its own. I time a <i>batch</i> of many operations, and if a batch is still
 *       shorter than {@link #MIN_BATCH_NANOS}, I repeat it until it is long enough.</li>
 *   <li><b>Garbage collection.</b> A GC pause during one run would inflate it. I ask for a
 *       GC before each measured run, then report the <b>median</b> of
 *       {@link #MEASURED_RUNS} runs, which one slow run cannot move, plus the min and max.</li>
 *   <li><b>Dead-code elimination.</b> If a result is never used, the JIT compiler may delete
 *       the work. Every batch returns a number built from its results, and I add it to
 *       {@link #checksum}, which the benchmark prints at the end.</li>
 * </ol>
 */
final class Measurement {

    static int WARMUP_RUNS = 5;
    static int MEASURED_RUNS = 7;

    /** A batch shorter than this is repeated, so the timer has enough time to be accurate. */
    static final long MIN_BATCH_NANOS = 20_000_000L;   // 20 ms
    private static final long MAX_REPEATS = 1_000_000L;

    /** Sum of every batch's result. Printed at the end so no work can be optimized away. */
    static long checksum;

    /** One batch of work. Returns a number that depends on the results. */
    @FunctionalInterface
    interface Batch {
        long run();
    }

    /** The times of the measured runs for one data point. */
    record Result(long[] runNanos, long repeats, long opsPerBatch) {

        /** Operations timed in one run (the batch, repeated). */
        long opsPerRun() {
            return repeats * opsPerBatch;
        }

        double nsPerOp(long runNanos) {
            return (double) runNanos / opsPerRun();
        }

        double medianNsPerOp() {
            long[] sorted = runNanos.clone();
            Arrays.sort(sorted);
            return nsPerOp(sorted[sorted.length / 2]);
        }

        double minNsPerOp() {
            return nsPerOp(Arrays.stream(runNanos).min().orElseThrow());
        }

        double maxNsPerOp() {
            return nsPerOp(Arrays.stream(runNanos).max().orElseThrow());
        }
    }

    private Measurement() { }

    /**
     * Times a batch: warm-up runs first (also used to choose how many times to repeat
     * the batch), then the measured runs.
     *
     * @param opsPerBatch how many operations one call of {@code batch} performs
     */
    static Result measure(long opsPerBatch, Batch batch) {
        long repeats = 1;
        for (int run = 0; run < WARMUP_RUNS; run++) {
            long nanos = time(batch, repeats);
            // Choose the number of repeats from the latest (warmer) run.
            long perBatch = Math.max(1, nanos / repeats);
            repeats = Math.min(MAX_REPEATS, Math.max(1, (MIN_BATCH_NANOS + perBatch - 1) / perBatch));
        }

        long[] runNanos = new long[MEASURED_RUNS];
        for (int run = 0; run < MEASURED_RUNS; run++) {
            System.gc();                       // start every measured run from a clean heap
            runNanos[run] = time(batch, repeats);
        }
        return new Result(runNanos, repeats, opsPerBatch);
    }

    /** Runs the batch {@code repeats} times and returns the elapsed time in nanoseconds. */
    private static long time(Batch batch, long repeats) {
        long sum = 0;
        long start = System.nanoTime();
        for (long r = 0; r < repeats; r++) {
            sum += batch.run();
        }
        long elapsed = System.nanoTime() - start;
        checksum += sum;
        return elapsed;
    }
}
