package autocomplete;

import java.util.List;
import java.util.Objects;

/**
 * Solution 1 — my hash table with separate chaining.
 *
 * <p><b>Why I picked it.</b> A hash table is the classic answer for "is this word in the
 * dictionary?": on average it finds a word in constant time, no matter how big the list is.
 * That makes it a strong fit for spell-checking. The trade-off is that a hash table keeps
 * words in no particular order, so autocomplete has to look at every word. Comparing that
 * trade-off against the Red-Black tree in Solution 2 is the point of my project.
 *
 * <p><b>Where the code comes from.</b> I wrote this class myself, starting from the
 * {@code ChainHashMap} we studied in class ({@code M05/ChainHashMap.java}, COMP 272,
 * Fall 2026). What I changed:
 * <ul>
 *   <li>Each chain is my own singly linked list of {@code Node}s instead of a
 *       {@code java.util.LinkedList}, so there is less overhead per word.</li>
 *   <li>The number of buckets is always a power of two (16, 32, 64, ...), so I can pick a
 *       bucket with {@code hash & (buckets - 1)}, which is faster than {@code % buckets}.</li>
 *   <li>I mix the hash bits with {@code h ^ (h >>> 16)}, the same trick
 *       {@code java.util.HashMap} uses (OpenJDK), so the bucket index depends on all
 *       32 bits of {@code hashCode()} and not just the lowest few.</li>
 *   <li>Each node remembers its hash, so growing the table never recomputes it.</li>
 * </ul>
 * {@code complete} ranks the matching words with my shared {@link TopK} helper.
 *
 * <p><b>How it works.</b> The table is an array of buckets. Each bucket holds a short
 * chain of the words whose hash lands there. When the table is more than 75% full,
 * I double the array and move every word to its new bucket, which keeps chains short.
 * I never shrink the table after removals.
 *
 * <p><b>Expected running time</b> (n = number of words, m = words that match the prefix):
 * <ul>
 *   <li>add — O(1) on average (a resize costs O(n), but it happens rarely, so it
 *       averages out); O(n) in the worst case, if every word landed in one bucket.</li>
 *   <li>remove, contains, frequency — O(1) on average, O(n) worst case.</li>
 *   <li>complete(prefix, k) — O(n + m log k) always: with no order, I must scan every word.</li>
 *   <li>size — O(1).</li>
 * </ul>
 * Memory: one array slot per bucket plus one node per word.
 *
 * <p><b>Measuring it.</b> While {@link #COUNTERS} is true, I count lookups, how many nodes
 * each lookup compares ("probes"), how often the table grows, and how many words
 * {@code complete} scans. I set it to false before timing in Stage 4, so the counting
 * itself does not slow the results down.
 */
public class Solution1 implements Dictionary {

    /** Turn the counters off (false) before measuring time. */
    public static final boolean COUNTERS = true;

    private static final int STARTING_BUCKETS = 16;       // must be a power of two
    private static final double MAX_LOAD_FACTOR = 0.75;   // grow when more than 75% full

    /** One word in a chain: the word, its hash, its count, and the next node. */
    private static final class Node {
        final String word;
        final int hash;     // saved so resize() never has to recompute it
        long count;
        Node next;

        Node(String word, int hash, long count, Node next) {
            this.word = word;
            this.hash = hash;
            this.count = count;
            this.next = next;
        }
    }

    private Node[] buckets;   // buckets[i] is the first node of chain i (or null)
    private int size;         // number of words stored

    // What I measure (only while COUNTERS is true)
    private long lookups;          // calls to add, remove, contains, frequency
    private long probes;           // nodes compared during those calls
    private int resizes;           // times the table doubled
    private long completeScanned;  // words looked at by complete()

    /** Creates an empty dictionary with 16 buckets. */
    public Solution1() {
        buckets = new Node[STARTING_BUCKETS];
    }

    // ==================================================================
    // The Dictionary methods
    // ==================================================================

    /** Adds the word, or replaces its count if it is already there. */
    @Override
    public void add(String word, long count) {
        requireValidWord(word);
        if (count < 0) {
            throw new IllegalArgumentException("count must be at least 0: " + count);
        }

        int h = hash(word);
        Node existing = findNode(word, h);
        if (existing != null) {
            existing.count = count;        // same word: just update the count
            return;
        }

        if (size + 1 > MAX_LOAD_FACTOR * buckets.length) {
            growTable();                   // keep the chains short
        }
        int i = bucketIndex(h, buckets.length);
        buckets[i] = new Node(word, h, count, buckets[i]);   // new node goes first in its chain
        size++;
    }

    /** Removes the word. Returns false if it was not there. */
    @Override
    public boolean remove(String word) {
        if (word == null) {
            return false;
        }
        int h = hash(word);
        int i = bucketIndex(h, buckets.length);
        if (COUNTERS) {
            lookups++;
        }

        // Walk the chain and remember the node before the current one,
        // so I can link around the node I remove.
        Node previous = null;
        for (Node current = buckets[i]; current != null; previous = current, current = current.next) {
            if (COUNTERS) {
                probes++;
            }
            if (current.hash == h && current.word.equals(word)) {
                if (previous == null) {
                    buckets[i] = current.next;     // it was the first node of the chain
                } else {
                    previous.next = current.next;  // it was in the middle or at the end
                }
                size--;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(String word) {
        return word != null && findNode(word, hash(word)) != null;
    }

    /** Returns the word's count, or 0 if the word is not in the dictionary. */
    @Override
    public long frequency(String word) {
        if (word == null) {
            return 0;
        }
        Node node = findNode(word, hash(word));
        return node == null ? 0 : node.count;
    }

    /**
     * Returns up to k words that start with the prefix: highest count first,
     * ties in alphabetical order.
     *
     * <p>This is the weak spot of a hash table. Words are spread across the buckets in no
     * order, so I cannot jump to the words that start with "pro"; I have to check all of them.
     */
    @Override
    public List<String> complete(String prefix, int k) {
        Objects.requireNonNull(prefix, "prefix");
        TopK best = new TopK(k);
        if (k <= 0) {
            return best.result();
        }
        for (Node first : buckets) {
            for (Node node = first; node != null; node = node.next) {
                if (COUNTERS) {
                    completeScanned++;
                }
                if (node.word.startsWith(prefix)) {
                    best.offer(node.word, node.count);
                }
            }
        }
        return best.result();
    }

    @Override
    public int size() {
        return size;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** Turns a word into a hash, mixing the high bits into the low bits. */
    private static int hash(String word) {
        int h = word.hashCode();
        return h ^ (h >>> 16);
    }

    /** Picks a bucket. Same result as hash % bucketCount, because bucketCount is a power of two. */
    private static int bucketIndex(int hash, int bucketCount) {
        return hash & (bucketCount - 1);
    }

    /** Walks one chain and returns the node holding the word, or null. */
    private Node findNode(String word, int h) {
        if (COUNTERS) {
            lookups++;
        }
        for (Node node = buckets[bucketIndex(h, buckets.length)]; node != null; node = node.next) {
            if (COUNTERS) {
                probes++;
            }
            // Comparing the saved hash first skips most String comparisons.
            if (node.hash == h && node.word.equals(word)) {
                return node;
            }
        }
        return null;
    }

    /** Doubles the number of buckets and moves every node to its new bucket. */
    private void growTable() {
        Node[] bigger = new Node[buckets.length * 2];
        for (Node first : buckets) {
            Node node = first;
            while (node != null) {
                Node next = node.next;                       // save before relinking
                int i = bucketIndex(node.hash, bigger.length);
                node.next = bigger[i];
                bigger[i] = node;
                node = next;
            }
        }
        buckets = bigger;
        if (COUNTERS) {
            resizes++;
        }
    }

    private static void requireValidWord(String word) {
        if (word == null || word.isEmpty()) {
            throw new IllegalArgumentException("word must be non-empty");
        }
    }

    // ==================================================================
    // Measurements (used by my tests and by the Stage 4 report)
    // ==================================================================

    /** Number of buckets in the table right now. */
    public int capacity() {
        return buckets.length;
    }

    /** How full the table is: words / buckets. */
    public double loadFactor() {
        return (double) size / buckets.length;
    }

    /** Length of the longest chain. Short chains mean the hash spreads words well. */
    public int longestChain() {
        int longest = 0;
        for (Node first : buckets) {
            int length = 0;
            for (Node node = first; node != null; node = node.next) {
                length++;
            }
            longest = Math.max(longest, length);
        }
        return longest;
    }

    /** Number of buckets holding at least one word. */
    public int nonEmptyBuckets() {
        int used = 0;
        for (Node first : buckets) {
            if (first != null) {
                used++;
            }
        }
        return used;
    }

    public int resizeCount() {
        return resizes;
    }

    public long lookupCount() {
        return lookups;
    }

    public long probeCount() {
        return probes;
    }

    /** Average number of nodes compared per lookup since the last reset. */
    public double averageProbesPerLookup() {
        return lookups == 0 ? 0.0 : (double) probes / lookups;
    }

    public long completeScannedCount() {
        return completeScanned;
    }

    /** Sets all the counters back to 0. */
    public void resetCounters() {
        lookups = 0;
        probes = 0;
        resizes = 0;
        completeScanned = 0;
    }
}