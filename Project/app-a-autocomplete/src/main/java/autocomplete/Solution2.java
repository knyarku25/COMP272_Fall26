package autocomplete;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Solution 2 — a Red-Black tree, used through Java's {@code TreeMap}.
 *
 * <p><b>Why I picked it.</b> A Red-Black tree keeps the words in alphabetical order and
 * stays balanced, so every search takes O(log n) steps. That is a little slower than a
 * hash table for "is this word in the dictionary?", but the order is exactly what
 * autocomplete needs: all the words that start with "pro" sit next to each other in the
 * tree. Solution 1 (my hash table) has to scan every word for the same question.
 * Comparing the two is the point of my project.
 *
 * <p><b>Where the code comes from.</b> The tree itself is the library class
 * {@code java.util.TreeMap<String, Long>} (OpenJDK 17+;
 * https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html).
 * TreeMap is a Red-Black tree as described in CLRS, Chapter 13. This class is an
 * <i>adapter</i>: it translates each {@link Dictionary} method into TreeMap calls and adds
 * no tree logic of its own. {@code complete} ranks the matching words with my shared
 * {@link TopK} helper.
 *
 * <p><b>How autocomplete works.</b> Words are ordered with {@link String#compareTo}, so
 * every word that starts with a prefix p comes right after p in the order. I ask TreeMap
 * for the first word that is ≥ p ({@code tailMap(p, true)}), which takes O(log n), then
 * walk forward in order and stop at the first word that does not start with p.
 *
 * <p><b>Expected running time</b> (n = number of words, m = words that match the prefix;
 * a Red-Black tree is never taller than 2 log2(n + 1)):
 * <ul>
 *   <li>add, remove, contains, frequency — O(log n) string comparisons, even in the
 *       worst case.</li>
 *   <li>complete(prefix, k) — O(log n + m log k): only the matching words are visited.</li>
 *   <li>size — O(1).</li>
 * </ul>
 * Memory: one TreeMap entry per word (word, count, left, right, parent and color), and
 * the count is stored as a {@code Long} object rather than a plain {@code long}.
 *
 * <p><b>Measuring it.</b> I cannot add counters inside TreeMap (rotations, height),
 * because it is library code. What I can count is how many words {@code complete} walks
 * through, which is what explains its speed. Set {@link #COUNTERS} to false before timing.
 */
public class Solution2 implements Dictionary {

    /**
     * The counter is on by default, because my tests use it. The benchmark
     * turns it off with -Dautocomplete.counters=false, so counting does not slow
     * down the timings.
     */
    public static final boolean COUNTERS =
            !"false".equals(System.getProperty("autocomplete.counters"));

    /** The Red-Black tree: each word maps to its count, kept in alphabetical order. */
    private final TreeMap<String, Long> tree = new TreeMap<>();

    // What I measure (only while COUNTERS is true)
    private long completeScanned;   // words looked at by complete()

    /** Creates an empty dictionary. (The tree is created with the field above.) */
    public Solution2() {
        // nothing else to set up
    }

    // ==================================================================
    // The Dictionary methods
    // ==================================================================

    /** Adds the word, or replaces its count if it is already there. */
    @Override
    public void add(String word, long count) {
        if (word == null || word.isEmpty()) {
            throw new IllegalArgumentException("word must be non-empty");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be at least 0: " + count);
        }
        tree.put(word, count);            // put() replaces the old count if the word exists
    }

    /** Removes the word. Returns false if it was not there. */
    @Override
    public boolean remove(String word) {
        // TreeMap.remove returns the old count, or null if the word was missing.
        return word != null && tree.remove(word) != null;
    }

    @Override
    public boolean contains(String word) {
        return word != null && tree.containsKey(word);
    }

    /** Returns the word's count, or 0 if the word is not in the dictionary. */
    @Override
    public long frequency(String word) {
        if (word == null) {
            return 0;
        }
        Long count = tree.get(word);
        return count == null ? 0 : count;
    }

    /**
     * Returns up to k words that start with the prefix: highest count first,
     * ties in alphabetical order.
     *
     * <p>This is where the tree shines. Instead of checking all n words, I jump straight
     * to the first word ≥ prefix and only walk through the words that match.
     */
    @Override
    public List<String> complete(String prefix, int k) {
        Objects.requireNonNull(prefix, "prefix");
        TopK best = new TopK(k);
        if (k <= 0) {
            return best.result();
        }
        // tailMap(prefix, true) = every word from the prefix onward, in alphabetical order.
        for (Map.Entry<String, Long> entry : tree.tailMap(prefix, true).entrySet()) {
            if (COUNTERS) {
                completeScanned++;
            }
            if (!entry.getKey().startsWith(prefix)) {
                break;                     // past the last matching word: stop here
            }
            best.offer(entry.getKey(), entry.getValue());
        }
        return best.result();
    }

    @Override
    public int size() {
        return tree.size();
    }

    // ==================================================================
    // Measurements (used by my tests and by the Stage 4 report)
    // ==================================================================

    /** Number of words complete() has looked at since the last reset. */
    public long completeScannedCount() {
        return completeScanned;
    }

    /** Sets the counter back to 0. */
    public void resetCounters() {
        completeScanned = 0;
    }
}