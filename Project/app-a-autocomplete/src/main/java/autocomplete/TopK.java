package autocomplete;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Keeps the k best (word, count) pairs seen so far, for {@link Dictionary#complete}.
 *
 * <p>"Best" means: highest count first; for equal counts, alphabetical order
 * ({@link String#compareTo}). Both solutions use this same class, so a difference in
 * their {@code complete} times comes from how they <i>find</i> the matching words,
 * not from how they rank them.
 *
 * <p>Implementation: a bounded binary min-heap ({@link java.util.PriorityQueue}, JDK 17+)
 * whose head is the <i>worst</i> pair kept. A new pair is inserted only if the heap is not
 * full or the pair beats the head. Cost: O(m log k) for m offered pairs, O(k) memory.
 * An object is created only when a pair actually enters the heap.
 */
final class TopK {

    private record Entry(String word, long count) { }

    /** The ranking of the interface: count descending, then word ascending. */
    private static final Comparator<Entry> RANK = (a, b) -> {
        int byCount = Long.compare(b.count, a.count);
        return byCount != 0 ? byCount : a.word.compareTo(b.word);
    };

    private final int k;
    private final PriorityQueue<Entry> heap;   // head = worst pair kept

    TopK(int k) {
        this.k = k;
        this.heap = new PriorityQueue<>(RANK.reversed());
    }

    /** Offers one candidate word. */
    void offer(String word, long count) {
        if (k <= 0) {
            return;
        }
        if (heap.size() < k) {
            heap.add(new Entry(word, count));
        } else if (beats(word, count, heap.peek())) {
            heap.poll();
            heap.add(new Entry(word, count));
        }
    }

    /** True if (word, count) ranks before e. */
    private static boolean beats(String word, long count, Entry e) {
        if (count != e.count) {
            return count > e.count;
        }
        return word.compareTo(e.word) < 0;
    }

    /** Returns the kept words, best first, in a new list. */
    List<String> result() {
        List<Entry> entries = new ArrayList<>(heap);
        entries.sort(RANK);
        List<String> words = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            words.add(e.word);
        }
        return words;
    }
}