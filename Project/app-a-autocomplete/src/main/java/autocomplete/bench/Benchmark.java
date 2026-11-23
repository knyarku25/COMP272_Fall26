package autocomplete.bench;

import autocomplete.DatasetReader;
import autocomplete.DatasetReader.WordCount;
import autocomplete.Dictionary;
import autocomplete.Solution1;
import autocomplete.Solution2;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Reference;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * My Stage 4 benchmark harness.
 *
 * <p>One run of this program measures ONE solution (so each solution gets its own JVM and
 * neither one's JIT warm-up affects the other). The script run_benchmarks.ps1 (Windows) or
 * run_benchmarks.sh (Mac/Linux) runs it once per solution, then once more with the counters on.
 *
 * <pre>
 *   java -Xmx2g -Dautocomplete.counters=false -cp target/classes autocomplete.bench.Benchmark --solution hash
 *   java -Xmx2g -Dautocomplete.counters=false -cp target/classes autocomplete.bench.Benchmark --solution tree
 *   java -Xmx2g -cp target/classes autocomplete.bench.Benchmark --counters
 * </pre>
 *
 * Options: {@code --data DIR} (default data), {@code --out DIR} (default results),
 * {@code --quick} (2 sizes and fewer runs, to check that everything works in a minute;
 * writes to results_quick so it never mixes with the real results).
 *
 * <p><b>What I vary:</b> the solution, the dictionary size n (the n most frequent words), and
 * the workload. <b>What I measure:</b> time per operation (median, min, max of 7 runs),
 * build time, memory per word, and my instrumentation counters.
 * <b>What I keep fixed:</b> the machine, the JVM settings, and the inputs (fixed seeds).
 */
public final class Benchmark {

    /** Dictionary sizes: 3.5 orders of magnitude. The last one means "the full list". */
    static final int[] SIZES = {100, 1_000, 10_000, 100_000, Integer.MAX_VALUE};
    static final int[] QUICK_SIZES = {1_000, 10_000};
    static final long SEED = 272;
    static final int K = 10;                         // complete(prefix, 10), as in A-N2

    private Benchmark() { }

    public static void main(String[] args) throws IOException {
        String solution = null;
        boolean counters = false;
        boolean quick = false;
        Path data = Path.of("data");
        Path out = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--solution" -> solution = args[++i];
                case "--counters" -> counters = true;
                case "--quick" -> quick = true;
                case "--data" -> data = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("unknown option: " + args[i]);
            }
        }
        if (solution == null && !counters) {
            System.err.println("Use --solution hash, --solution tree, or --counters");
            System.exit(1);
        }
        if (quick) {
            Measurement.WARMUP_RUNS = 2;
            Measurement.MEASURED_RUNS = 3;
        }
        if (out == null) {
            out = Path.of(quick ? "results_quick" : "results");   // a quick test never mixes with real results
        }
        int[] sizes = quick ? QUICK_SIZES : SIZES;
        long startTime = System.nanoTime();

        // ---- Load the data (not timed) ----
        List<WordCount> words = DatasetReader.readWordCounts(data.resolve("count_1w.txt"), Integer.MAX_VALUE);
        List<String> bookWords = readBooks(data);
        Workloads w = new Workloads(words, bookWords, SEED);
        System.out.printf(Locale.ROOT, "Loaded %,d dictionary words and %,d words of real text.%n",
                words.size(), w.realText.length);

        if (counters) {
            runCounters(w, sizes, out.resolve("counters.csv"));
        } else {
            Supplier<Dictionary> factory = switch (solution) {
                case "hash" -> Solution1::new;
                case "tree" -> Solution2::new;
                default -> throw new IllegalArgumentException("unknown solution: " + solution);
            };
            if (Solution1.COUNTERS || Solution2.COUNTERS) {
                System.out.println("WARNING: counters are ON. Add -Dautocomplete.counters=false for timing runs.");
            }
            MachineInfo.write(out.resolve(solution + "_machine.csv"), Map.of(   // the JVM that made these timings
                    "books", String.valueOf(booksRead),
                    "real_text_words", String.valueOf(w.realText.length)));
            runTimings(solution, factory, w, sizes, data, out);
            runMemory(solution, factory, w, sizes, out.resolve(solution + "_memory.csv"));
        }
        System.out.printf(Locale.ROOT, "Done in %.1f s. checksum=%d%n",
                (System.nanoTime() - startTime) / 1e9, Measurement.checksum);
    }

    /** How many books readBooks() found (recorded in the machine CSV and the PDF). */
    static int booksRead;

    /** All words of every data/pg*.txt book, in file-name order. */
    static List<String> readBooks(Path data) throws IOException {
        List<Path> books = new ArrayList<>();
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(data, "pg*.txt")) {
            dir.forEach(books::add);
        }
        books.sort(null);
        if (books.isEmpty()) {
            throw new IOException("No books found: put Gutenberg files named pg*.txt in " + data);
        }
        booksRead = books.size();
        List<String> words = new ArrayList<>();
        for (Path book : books) {
            List<String> bookWords = DatasetReader.readBookWords(book);
            System.out.printf(Locale.ROOT, "  %-12s %,9d words%n", book.getFileName(), bookWords.size());
            words.addAll(bookWords);
        }
        return words;
    }

    static Dictionary build(Supplier<Dictionary> factory, List<WordCount> words) {
        Dictionary d = factory.get();
        for (WordCount wc : words) {
            d.add(wc.word(), wc.count());
        }
        return d;
    }

    // ==================================================================
    // Timing experiments
    // ==================================================================

    static void runTimings(String name, Supplier<Dictionary> factory, Workloads w, int[] sizes,
                           Path data, Path out) throws IOException {
        try (CsvWriter runs = new CsvWriter(out.resolve(name + "_runs.csv"),
                     "solution", "workload", "n", "param", "run", "ops", "elapsed_ns", "ns_per_op");
             CsvWriter summary = new CsvWriter(out.resolve(name + "_summary.csv"),
                     "solution", "workload", "n", "param", "ops", "median_ns_per_op", "min_ns_per_op",
                     "max_ns_per_op", "median_ms_per_batch", "ops_per_sec", "avg_matches")) {
            Recorder rec = new Recorder(name, runs, summary);

            for (int size : sizes) {
                int n = Math.min(size, w.byFrequency.size());
                List<WordCount> dict = w.byFrequency.subList(0, n);
                List<WordCount> sorted = Workloads.alphabetical(dict);

                // 1. Build time: a fresh dictionary filled with the n most frequent words.
                rec.record("build", n, "frequency_order", Measurement.measure(n,
                        () -> build(factory, dict).size()), null);

                // 2. Stress: the same words inserted in alphabetical order.
                rec.record("build", n, "sorted_order", Measurement.measure(n,
                        () -> build(factory, sorted).size()), null);

                Dictionary d = build(factory, dict);

                // 3. Spell-checking real text: a realistic mix of very common and rare words.
                rec.record("contains", n, "real_text", Measurement.measure(w.realText.length, () -> {
                    long hits = 0;
                    for (String word : w.realText) {
                        if (d.contains(word)) {
                            hits++;
                        }
                    }
                    return hits;
                }), null);

                // 4. Stress: every word is a misspelling, so every lookup misses.
                rec.record("contains", n, "misspelled", Measurement.measure(w.misspellings.length, () -> {
                    long hits = 0;
                    for (String word : w.misspellings) {
                        if (d.contains(word)) {
                            hits++;
                        }
                    }
                    return hits;
                }), null);

                // 5. Autocomplete with prefixes of length 1, 2, 3 and 5.
                for (int i = 0; i < Workloads.PREFIX_LENGTHS.length; i++) {
                    String[] prefixes = w.prefixes[i];
                    rec.record("complete", n, "len" + Workloads.PREFIX_LENGTHS[i],
                            Measurement.measure(prefixes.length, () -> {
                                long found = 0;
                                for (String p : prefixes) {
                                    found += d.complete(p, K).size();
                                }
                                return found;
                            }), Workloads.averageMatches(dict, prefixes));
                }

                // 6. Updates: 90% contains, 5% add, 5% remove (leaves d unchanged at the end).
                Workloads.MixedOps ops = w.mixedOps(dict, SEED + n);
                rec.record("mixed", n, "10pct_updates", Measurement.measure(ops.size(), () -> {
                    long result = 0;
                    for (int i = 0; i < ops.size(); i++) {
                        switch (ops.type()[i]) {
                            case Workloads.CONTAINS -> result += d.contains(ops.word()[i]) ? 1 : 0;
                            case Workloads.ADD -> d.add(ops.word()[i], ops.count()[i]);
                            default -> result += d.remove(ops.word()[i]) ? 1 : 0;
                        }
                    }
                    return result + d.size();
                }), null);

                if (n == w.byFrequency.size()) {
                    runFullSizeOnly(rec, factory, d, dict, data);
                }
            }
        }
    }

    /** Targets that are defined on the full dictionary: A-N4 (build from file) and A-N3. */
    static void runFullSizeOnly(Recorder rec, Supplier<Dictionary> factory, Dictionary d,
                                List<WordCount> dict, Path data) {
        int n = dict.size();
        // A-N4: reading the file + adding every word.
        rec.record("build_from_file", n, "count_1w.txt", Measurement.measure(n, () -> {
            try {
                return build(factory, DatasetReader.readWordCounts(
                        data.resolve("count_1w.txt"), Integer.MAX_VALUE)).size();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }), null);

        // A-N3: every 1-letter prefix, timed one by one, to find the worst one.
        for (char c = 'a'; c <= 'z'; c++) {
            String letter = String.valueOf(c);
            rec.record("complete_letter", n, letter,
                    Measurement.measure(1, () -> d.complete(letter, K).size()),
                    Workloads.averageMatches(dict, new String[] {letter}));
        }
    }

    /** Writes the measured runs and the summary line of one data point, and prints progress. */
    record Recorder(String solution, CsvWriter runs, CsvWriter summary) {
        void record(String workload, int n, String param, Measurement.Result r, Double avgMatches) {
            for (int i = 0; i < r.runNanos().length; i++) {
                runs.row(solution, workload, n, param, i + 1, r.opsPerRun(), r.runNanos()[i],
                        r.nsPerOp(r.runNanos()[i]));
            }
            double median = r.medianNsPerOp();
            summary.row(solution, workload, n, param, r.opsPerBatch(), median, r.minNsPerOp(),
                    r.maxNsPerOp(), median * r.opsPerBatch() / 1e6, 1e9 / median, avgMatches);
            System.out.printf(Locale.ROOT, "%-5s n=%-7d %-16s %-16s %12.1f ns/op  (min %.1f, max %.1f)%n",
                    solution, n, workload, param, median, r.minNsPerOp(), r.maxNsPerOp());
        }
    }

    // ==================================================================
    // Memory
    // ==================================================================

    /** Heap in use after asking for garbage collection a few times (approximate). */
    static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return rt.totalMemory() - rt.freeMemory();
    }

    /**
     * Memory of the structure: used heap before and after building it, 3 trials per size.
     * The word Strings already exist in the input list, so this measures what the structure
     * itself adds (nodes, arrays, boxed counts), not the characters of the words.
     */
    static void runMemory(String name, Supplier<Dictionary> factory, Workloads w, int[] sizes, Path file)
            throws IOException {
        try (CsvWriter csv = new CsvWriter(file, "solution", "n", "trial", "bytes_total", "bytes_per_word")) {
            for (int size : sizes) {
                int n = Math.min(size, w.byFrequency.size());
                List<WordCount> dict = w.byFrequency.subList(0, n);
                long[] perWord = new long[3];
                for (int trial = 1; trial <= 3; trial++) {
                    long before = usedMemory();
                    Dictionary d = build(factory, dict);
                    long after = usedMemory();
                    Reference.reachabilityFence(d);   // keep d alive until after the measurement
                    long bytes = after - before;
                    csv.row(name, n, trial, bytes, (double) bytes / n);
                    perWord[trial - 1] = bytes / n;
                }
                Arrays.sort(perWord);
                System.out.printf(Locale.ROOT, "%-5s n=%-7d memory           %,d bytes/word (median of 3)%n",
                        name, n, perWord[1]);
            }
        }
    }

    // ==================================================================
    // Counters (a separate run, with the counters ON)
    // ==================================================================

    static void runCounters(Workloads w, int[] sizes, Path file) throws IOException {
        if (!Solution1.COUNTERS || !Solution2.COUNTERS) {
            throw new IllegalStateException("Run --counters WITHOUT -Dautocomplete.counters=false");
        }
        try (CsvWriter csv = new CsvWriter(file, "solution", "n", "metric", "value")) {
            for (int size : sizes) {
                int n = Math.min(size, w.byFrequency.size());
                List<WordCount> dict = w.byFrequency.subList(0, n);

                // Hash table: how full it is, how long the chains are, how many nodes a lookup reads.
                Solution1 h = (Solution1) build(Solution1::new, dict);
                csv.row("hash", n, "capacity", h.capacity());
                csv.row("hash", n, "load_factor", h.loadFactor());
                csv.row("hash", n, "longest_chain", h.longestChain());
                csv.row("hash", n, "nonempty_buckets", h.nonEmptyBuckets());
                csv.row("hash", n, "resizes", h.resizeCount());
                h.resetCounters();
                for (String word : w.realText) {
                    h.contains(word);
                }
                csv.row("hash", n, "probes_per_lookup_real_text", h.averageProbesPerLookup());
                h.resetCounters();
                for (String word : w.misspellings) {
                    h.contains(word);
                }
                csv.row("hash", n, "probes_per_lookup_misspelled", h.averageProbesPerLookup());

                // Both: how many words complete() looks at per query.
                Solution2 t = (Solution2) build(Solution2::new, dict);
                for (int i = 0; i < Workloads.PREFIX_LENGTHS.length; i++) {
                    String metric = "words_scanned_per_complete_len" + Workloads.PREFIX_LENGTHS[i];
                    h.resetCounters();
                    t.resetCounters();
                    for (String p : w.prefixes[i]) {
                        h.complete(p, K);
                        t.complete(p, K);
                    }
                    csv.row("hash", n, metric, (double) h.completeScannedCount() / w.prefixes[i].length);
                    csv.row("tree", n, metric, (double) t.completeScannedCount() / w.prefixes[i].length);
                }
                System.out.printf(Locale.ROOT, "counters n=%-7d capacity=%d longest_chain=%d%n",
                        n, h.capacity(), h.longestChain());
            }
        }
    }
}