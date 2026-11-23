package autocomplete;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * My live demo: it loads the full word list into BOTH solutions and lets me type
 * prefixes and sentences, so I can show on screen how each one behaves.
 *
 * <p>Commands (type {@code help} to see them again):
 * <ul>
 *   <li>any prefix, e.g. {@code th} : the 10 most common completions, and how long each solution took</li>
 *   <li>{@code check <sentence>} : spell-check a sentence and flag unknown words</li>
 *   <li>{@code book <file>} : spell-check a whole Gutenberg book from the data folder</li>
 *   <li>{@code quit} : leave</li>
 * </ul>
 *
 * <p>Timings here are for one query (best of 5 tries), so they are rough. The real
 * measurements are in the benchmark (autocomplete.bench.Benchmark).
 *
 * <p>Run it with run_demo.ps1 (Windows) or run_demo.sh (Mac/Linux).
 */
public final class Demo {

    private static final int K = 10;          // completions shown per prefix
    private static final int TRIES = 5;       // I time each query 5 times and keep the fastest

    private final Dictionary hash = new Solution1();
    private final Dictionary tree = new Solution2();
    private final Path dataDir;

    private Demo(Path dataDir) {
        this.dataDir = dataDir;
    }

    public static void main(String[] args) throws IOException {
        Path dataDir = Path.of(args.length > 0 ? args[0] : "data");
        Demo demo = new Demo(dataDir);
        demo.load();
        demo.warmUp();
        demo.loop();
    }

    // ----- start-up ---------------------------------------------------------------

    /** Reads count_1w.txt once and builds both dictionaries from it. */
    private void load() throws IOException {
        Path file = dataDir.resolve("count_1w.txt");
        if (!Files.exists(file)) {
            System.out.println("I cannot find " + file + ". See the README for how to download it.");
            System.exit(1);
        }
        List<DatasetReader.WordCount> words = DatasetReader.readWordCounts(file, Integer.MAX_VALUE);
        System.out.printf("Read %,d words from %s%n", words.size(), file);

        long start = System.nanoTime();
        for (DatasetReader.WordCount wc : words) {
            hash.add(wc.word(), wc.count());
        }
        long hashNanos = System.nanoTime() - start;

        start = System.nanoTime();
        for (DatasetReader.WordCount wc : words) {
            tree.add(wc.word(), wc.count());
        }
        long treeNanos = System.nanoTime() - start;

        System.out.printf("Built Solution 1 (hash table) in %s and Solution 2 (Red-Black tree) in %s%n",
                show(hashNanos), show(treeNanos));
    }

    /**
     * Java compiles code to fast machine code only after it has run a while (the JIT),
     * so I run a few thousand queries first. Otherwise my first prefix on screen would
     * look much slower than it really is.
     */
    private void warmUp() {
        String[] prefixes = {"a", "th", "pro", "inter", "zz", "q"};
        long sink = 0;
        for (int round = 0; round < 30; round++) {
            for (String p : prefixes) {
                sink += hash.complete(p, K).size() + tree.complete(p, K).size();
                sink += hash.contains(p) ? 1 : 0;
                sink += tree.contains(p) ? 1 : 0;
            }
        }
        if (sink == 42) {
            System.out.println();          // never true; it just keeps the JIT from skipping the work
        }
        System.out.println("Ready. Type a prefix, or 'help'.");
    }

    // ----- the command loop ---------------------------------------------------------

    private void loop() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        boolean echo = System.console() == null;   // commands piped from a file: print them so the output reads well
        while (true) {
            System.out.print("\n> ");
            String line = in.readLine();
            if (line == null) {
                return;                            // end of input
            }
            line = line.strip();
            if (echo) {
                System.out.println(line);
            }
            if (line.isEmpty()) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.equals("quit") || lower.equals("exit")) {
                return;
            } else if (lower.equals("help")) {
                help();
            } else if (lower.startsWith("check ")) {
                checkSentence(line.substring(6));
            } else if (lower.startsWith("book ")) {
                checkBook(line.substring(5).strip());
            } else {
                completePrefix(lower);
            }
        }
    }

    private static void help() {
        System.out.println("  <prefix>          show the 10 most common words starting with it");
        System.out.println("  check <sentence>  spell-check a sentence");
        System.out.println("  book <file>       spell-check a whole book, e.g. book pg1342.txt");
        System.out.println("  quit              leave the demo");
    }

    // ----- autocomplete -----------------------------------------------------------------

    /** Shows the completions of one prefix, with the time each solution needed. */
    private void completePrefix(String prefix) {
        List<String> fromHash = null;
        List<String> fromTree = null;
        long bestHash = Long.MAX_VALUE;
        long bestTree = Long.MAX_VALUE;
        for (int t = 0; t < TRIES; t++) {
            long start = System.nanoTime();
            fromHash = hash.complete(prefix, K);
            bestHash = Math.min(bestHash, System.nanoTime() - start);

            start = System.nanoTime();
            fromTree = tree.complete(prefix, K);
            bestTree = Math.min(bestTree, System.nanoTime() - start);
        }

        System.out.printf("  hash table: %-10s  Red-Black tree: %-10s  same answer: %s%n",
                show(bestHash), show(bestTree), fromHash.equals(fromTree) ? "yes" : "NO");
        if (fromTree.isEmpty()) {
            System.out.println("  (no word starts with \"" + prefix + "\")");
        }
        for (int i = 0; i < fromTree.size(); i++) {
            String word = fromTree.get(i);
            System.out.printf("  %2d  %-20s %,18d%n", i + 1, word, tree.frequency(word));
        }
        if (!fromHash.equals(fromTree)) {
            System.out.println("  hash table said: " + fromHash);   // should never happen; my tests check this
        }
    }

    // ----- spell-checking --------------------------------------------------------------

    /** Splits a sentence into words (same rules as the books) and flags the unknown ones. */
    private void checkSentence(String sentence) {
        List<String> words = new ArrayList<>();
        DatasetReader.tokenize(sentence, words);
        if (words.isEmpty()) {
            System.out.println("  (no words found)");
            return;
        }

        long start = System.nanoTime();
        List<String> unknownHash = unknownWords(hash, words);
        long hashNanos = System.nanoTime() - start;
        start = System.nanoTime();
        List<String> unknownTree = unknownWords(tree, words);
        long treeNanos = System.nanoTime() - start;

        StringBuilder marked = new StringBuilder("  ");
        for (String w : words) {
            marked.append(tree.contains(w) ? w : "[" + w + "]").append(' ');
        }
        System.out.println(marked.toString().stripTrailing());
        System.out.printf("  %d words, %d unknown.  hash table: %s  Red-Black tree: %s  same answer: %s%n",
                words.size(), unknownTree.size(), show(hashNanos), show(treeNanos),
                unknownHash.equals(unknownTree) ? "yes" : "NO");

        for (String w : unknownTree) {
            System.out.println("  [" + w + "]  did you mean: " + String.join(", ", ideasFor(w)));
        }
    }

    /**
     * A simple suggestion idea: keep the longest start of the unknown word that still
     * begins some real words, and offer the 3 most common of them. It uses the tree,
     * because completing a prefix is where the tree is fast.
     */
    private List<String> ideasFor(String unknown) {
        for (int len = unknown.length() - 1; len >= 1; len--) {
            List<String> ideas = tree.complete(unknown.substring(0, len), 3);
            if (!ideas.isEmpty()) {
                return ideas;
            }
        }
        return List.of("(nothing close)");
    }

    /** Spell-checks a whole book with both solutions and compares the time per word. */
    private void checkBook(String fileName) throws IOException {
        Path file = dataDir.resolve(fileName);
        if (!Files.exists(file)) {
            System.out.println("  I cannot find " + file);
            return;
        }
        List<String> words = DatasetReader.readBookWords(file);
        System.out.printf("  %s: %,d words%n", fileName, words.size());

        long start = System.nanoTime();
        List<String> unknownHash = unknownWords(hash, words);
        long hashNanos = System.nanoTime() - start;
        start = System.nanoTime();
        List<String> unknownTree = unknownWords(tree, words);
        long treeNanos = System.nanoTime() - start;

        System.out.printf("  hash table:     %-10s (%.0f ns per word)%n", show(hashNanos), (double) hashNanos / words.size());
        System.out.printf("  Red-Black tree: %-10s (%.0f ns per word)%n", show(treeNanos), (double) treeNanos / words.size());
        System.out.printf("  %,d words not in the dictionary (same answer: %s)%n",
                unknownTree.size(), unknownHash.equals(unknownTree) ? "yes" : "NO");

        // The most common unknown words: usually names and old spellings, not real typos.
        Map<String, Integer> counts = new HashMap<>();
        for (String w : unknownTree) {
            counts.merge(w, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> top = new ArrayList<>(counts.entrySet());
        top.sort(Map.Entry.<String, Integer>comparingByValue().reversed()      // most common first,
                .thenComparing(Map.Entry.comparingByKey()));                  // ties alphabetically
        System.out.print("  most common:");
        for (int i = 0; i < Math.min(8, top.size()); i++) {
            System.out.print(" " + top.get(i).getKey() + " (" + top.get(i).getValue() + ")");
        }
        System.out.println();
    }

    private static List<String> unknownWords(Dictionary dict, List<String> words) {
        List<String> unknown = new ArrayList<>();
        for (String w : words) {
            if (!dict.contains(w)) {
                unknown.add(w);
            }
        }
        return unknown;
    }

    // ----- formatting ----------------------------------------------------------------

    /** Turns nanoseconds into something readable: 850 ns, 12.3 us, 4.56 ms, 1.23 s. */
    static String show(long nanos) {
        if (nanos < 1_000) {
            return nanos + " ns";
        } else if (nanos < 1_000_000) {
            return String.format(Locale.ROOT, "%.1f us", nanos / 1e3);
        } else if (nanos < 1_000_000_000) {
            return String.format(Locale.ROOT, "%.2f ms", nanos / 1e6);
        }
        return String.format(Locale.ROOT, "%.2f s", nanos / 1e9);
    }
}
