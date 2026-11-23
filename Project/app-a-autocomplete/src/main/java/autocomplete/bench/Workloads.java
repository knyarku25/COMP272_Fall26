package autocomplete.bench;

import autocomplete.DatasetReader.WordCount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Builds the inputs of every workload, once, from the dataset.
 *
 * <p>Everything random uses a fixed seed, so every run of the benchmark, on any
 * computer, gets exactly the same inputs, and both solutions get the same inputs
 * in the same order.
 */
final class Workloads {

    /** Prefix lengths asked by the Application A sheet. */
    static final int[] PREFIX_LENGTHS = {1, 2, 3, 5};
    static final int PREFIX_QUERIES = 100;         // prefixes per length
    static final int MISSPELLINGS = 100_000;
    static final int MAX_TEXT_WORDS = 1_000_000;   // cap on the real-text workload
    static final int MIXED_OPS = 100_000;          // operations in the update workload

    /** The full word list, most frequent first (file order). */
    final List<WordCount> byFrequency;
    /** All words of all the books, in reading order (at most MAX_TEXT_WORDS). */
    final String[] realText;
    /** Real words with one letter changed, none of them in the full word list. */
    final String[] misspellings;
    /** prefixes[i] holds PREFIX_QUERIES prefixes of length PREFIX_LENGTHS[i]. */
    final String[][] prefixes;

    Workloads(List<WordCount> byFrequency, List<String> bookWords, long seed) {
        this.byFrequency = byFrequency;
        this.realText = bookWords.subList(0, Math.min(bookWords.size(), MAX_TEXT_WORDS))
                                 .toArray(new String[0]);
        Set<String> allWords = new HashSet<>();
        for (WordCount wc : byFrequency) {
            allWords.add(wc.word());
        }
        this.misspellings = makeMisspellings(realText, allWords, new Random(seed));
        this.prefixes = new String[PREFIX_LENGTHS.length][];
        for (int i = 0; i < PREFIX_LENGTHS.length; i++) {
            prefixes[i] = makePrefixes(realText, PREFIX_LENGTHS[i], new Random(seed + PREFIX_LENGTHS[i]));
        }
    }

    /**
     * Misspellings: take a random word of the books, change one random letter to a
     * different random letter, and keep the result only if it is NOT in the word list.
     * Common words are picked more often, like real typos.
     */
    private static String[] makeMisspellings(String[] text, Set<String> allWords, Random random) {
        String[] result = new String[MISSPELLINGS];
        int made = 0;
        while (made < MISSPELLINGS) {
            String word = text[random.nextInt(text.length)];
            if (word.length() < 2) {
                continue;
            }
            char[] letters = word.toCharArray();
            int position = random.nextInt(letters.length);
            char replacement = (char) ('a' + random.nextInt(26));
            if (replacement == letters[position]) {
                continue;
            }
            letters[position] = replacement;
            String typo = new String(letters);
            if (!allWords.contains(typo)) {
                result[made++] = typo;
            }
        }
        return result;
    }

    /** Prefixes of a given length, cut from random words of the books that are long enough. */
    private static String[] makePrefixes(String[] text, int length, Random random) {
        String[] result = new String[PREFIX_QUERIES];
        int made = 0;
        while (made < PREFIX_QUERIES) {
            String word = text[random.nextInt(text.length)];
            if (word.length() >= length) {
                result[made++] = word.substring(0, length);
            }
        }
        return result;
    }

    /** The n most frequent words, in alphabetical order (the "sorted insertions" stress test). */
    static List<WordCount> alphabetical(List<WordCount> words) {
        List<WordCount> sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparing(WordCount::word));
        return sorted;
    }

    /**
     * Average number of dictionary words that start with each prefix. Not timed: it
     * explains the complete() results ("does the time depend on the number of matches?").
     */
    static double averageMatches(List<WordCount> dictionary, String[] prefixes) {
        String[] sorted = new String[dictionary.size()];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = dictionary.get(i).word();
        }
        Arrays.sort(sorted);
        long total = 0;
        for (String p : prefixes) {
            int i = Arrays.binarySearch(sorted, p);
            if (i < 0) {
                i = -i - 1;                        // first word >= p
            }
            while (i < sorted.length && sorted[i].startsWith(p)) {
                total++;
                i++;
            }
        }
        return (double) total / prefixes.length;
    }

    // ------------------------------------------------------------------
    // The update workload
    // ------------------------------------------------------------------

    static final byte CONTAINS = 0;
    static final byte ADD = 1;
    static final byte REMOVE = 2;

    /** A fixed sequence of operations: type, word and count of each one. */
    record MixedOps(byte[] type, String[] word, long[] count) {
        int size() {
            return type.length;
        }
    }

    /**
     * 90% contains, 5% add, 5% remove, mixed in blocks of 20 operations.
     *
     * <p>Each block has 18 lookups of real text and one pair of updates that cancel out:
     * either "remove an existing word ... add it back with its count" or
     * "add a new word ... remove it". So after the whole sequence the dictionary is exactly
     * as it was, and the batch can be repeated on the same dictionary for every run.
     */
    MixedOps mixedOps(List<WordCount> dictionary, long seed) {
        Random random = new Random(seed);
        byte[] type = new byte[MIXED_OPS];
        String[] word = new String[MIXED_OPS];
        long[] count = new long[MIXED_OPS];
        int textPosition = random.nextInt(realText.length);
        for (int block = 0; block < MIXED_OPS / 20; block++) {
            int start = block * 20;
            int first = start + random.nextInt(19);              // first update of the pair
            int second = first + 1 + random.nextInt(start + 20 - first - 1);
            for (int i = start; i < start + 20; i++) {
                type[i] = CONTAINS;
                word[i] = realText[textPosition];
                textPosition = (textPosition + 1) % realText.length;
            }
            if (block % 2 == 0) {                                 // remove, then add back
                WordCount existing = dictionary.get(random.nextInt(dictionary.size()));
                type[first] = REMOVE;
                word[first] = existing.word();
                type[second] = ADD;
                word[second] = existing.word();
                count[second] = existing.count();
            } else {                                              // add new, then remove
                String fresh = misspellings[random.nextInt(misspellings.length)];
                type[first] = ADD;
                word[first] = fresh;
                count[first] = 1 + random.nextInt(1_000);
                type[second] = REMOVE;
                word[second] = fresh;
            }
        }
        return new MixedOps(type, word, count);
    }
}