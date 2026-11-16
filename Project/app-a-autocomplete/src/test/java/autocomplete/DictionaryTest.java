package autocomplete;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests shared by both solutions. They are written once, against the Dictionary
 * interface; Solution1Test and Solution2Test run them on each solution.
 *
 * <p>The provided tests come first; the tests added for Stage 3 follow the provided
 * dataset test (section "ADDED TESTS"). The dataset tests need data/count_1w.txt and
 * data/pg11.txt (see the README).
 */
abstract class DictionaryTest {

    /** Returns a new, empty dictionary of the solution being tested. */
    protected abstract Dictionary createDictionary();

    private Dictionary dict;

    @BeforeEach
    void setUp() {
        dict = createDictionary();   // a fresh, empty dictionary before every test
    }

    // ------------------------------------------------------------------
    // add, contains, frequency, size
    // ------------------------------------------------------------------

    @Test
    void newDictionaryIsEmpty() {
        assertEquals(0, dict.size());
        assertFalse(dict.contains("the"));
    }

    @Test
    void addedWordIsContained() {
        dict.add("cat", 5);
        assertTrue(dict.contains("cat"));
        assertEquals(1, dict.size());
    }

    @Test
    void missingWordIsNotContainedAndHasFrequencyZero() {
        dict.add("cat", 5);
        assertFalse(dict.contains("dog"));
        assertEquals(0, dict.frequency("dog"));
    }

    @Test
    void frequencyReturnsTheCount() {
        dict.add("cat", 5);
        dict.add("dog", 12);
        assertEquals(5, dict.frequency("cat"));
        assertEquals(12, dict.frequency("dog"));
    }

    @Test
    void addingAnExistingWordReplacesItsCount() {
        dict.add("cat", 5);
        dict.add("cat", 40);
        assertEquals(40, dict.frequency("cat"));
        assertEquals(1, dict.size());
    }

    @Test
    void wordsAreCaseSensitive() {
        dict.add("Cat", 5);
        assertTrue(dict.contains("Cat"));
        assertFalse(dict.contains("cat"));
    }

    // ------------------------------------------------------------------
    // remove
    // ------------------------------------------------------------------

    @Test
    void removingAPresentWordReturnsTrueAndRemovesIt() {
        dict.add("cat", 5);
        dict.add("dog", 12);
        assertTrue(dict.remove("cat"));
        assertFalse(dict.contains("cat"));
        assertEquals(0, dict.frequency("cat"));
        assertEquals(1, dict.size());
        assertTrue(dict.contains("dog"));
    }

    @Test
    void removingAMissingWordReturnsFalse() {
        dict.add("cat", 5);
        assertFalse(dict.remove("dog"));
        assertEquals(1, dict.size());
    }

    @Test
    void removingTheLastWordEmptiesTheDictionary() {
        dict.add("cat", 5);
        dict.remove("cat");
        assertEquals(0, dict.size());
        assertEquals(List.of(), dict.complete("", 10));
    }

    // ------------------------------------------------------------------
    // complete
    // ------------------------------------------------------------------

    @Test
    void completeReturnsTheMostFrequentWordsFirst() {
        dict.add("the", 100);
        dict.add("they", 30);
        dict.add("then", 50);
        dict.add("there", 70);
        dict.add("cat", 1000);    // does not start with "th"
        assertEquals(List.of("the", "there", "then"), dict.complete("th", 3));
    }

    @Test
    void completeBreaksTiesAlphabetically() {
        dict.add("tea", 10);
        dict.add("ten", 10);
        dict.add("tab", 10);
        dict.add("toe", 20);
        assertEquals(List.of("toe", "tab", "tea", "ten"), dict.complete("t", 10));
    }

    @Test
    void completeIncludesThePrefixItselfWhenItIsAWord() {
        dict.add("car", 10);
        dict.add("cart", 5);
        assertEquals(List.of("car", "cart"), dict.complete("car", 5));
    }

    @Test
    void emptyPrefixMatchesEveryWord() {
        dict.add("b", 1);
        dict.add("a", 2);
        dict.add("c", 3);
        assertEquals(List.of("c", "a", "b"), dict.complete("", 10));
    }

    @Test
    void completeReturnsAllMatchesWhenFewerThanK() {
        dict.add("dog", 3);
        dict.add("door", 7);
        assertEquals(List.of("door", "dog"), dict.complete("do", 100));
    }

    @Test
    void completeWithNoMatchReturnsAnEmptyList() {
        dict.add("dog", 3);
        assertEquals(List.of(), dict.complete("x", 5));
    }

    @Test
    void completeWithKZeroReturnsAnEmptyList() {
        dict.add("dog", 3);
        assertEquals(List.of(), dict.complete("d", 0));
    }

    @Test
    void completeDoesNotReturnRemovedWords() {
        dict.add("sun", 9);
        dict.add("sum", 8);
        dict.remove("sun");
        assertEquals(List.of("sum"), dict.complete("su", 5));
    }

    // ------------------------------------------------------------------
    // Larger inputs
    // ------------------------------------------------------------------

    @Test
    void manyWordsAddedInSortedOrderAreAllFound() {
        // Sorted insertions are the worst case for an unbalanced BST,
        // and force a hash table to resize several times.
        int n = 20_000;
        for (int i = 0; i < n; i++) {
            dict.add(String.format("w%06d", i), i);
        }
        assertEquals(n, dict.size());
        for (int i = 0; i < n; i++) {
            assertEquals(i, dict.frequency(String.format("w%06d", i)));
        }
    }

    @Test
    void randomOperationsGiveTheSameResultsAsTreeMap() {
        // java.util.TreeMap is used here only as a REFERENCE to check your answers.
        TreeMap<String, Long> reference = new TreeMap<>();
        Random random = new Random(42);   // fixed seed: the test is repeatable
        for (int step = 0; step < 50_000; step++) {
            String word = "w" + random.nextInt(2_000);
            int operation = random.nextInt(3);
            if (operation == 0) {
                long count = random.nextInt(1_000);
                dict.add(word, count);
                reference.put(word, count);
            } else if (operation == 1) {
                assertEquals(reference.remove(word) != null, dict.remove(word), "remove " + word);
            } else {
                assertEquals(reference.getOrDefault(word, 0L), dict.frequency(word), "frequency " + word);
            }
            assertEquals(reference.size(), dict.size());
        }
    }

    // ------------------------------------------------------------------
    // Tests from the dataset
    // ------------------------------------------------------------------

    @Test
    void everyWordOfTheDatasetIsFoundWithItsCount() throws IOException {
        List<DatasetReader.WordCount> words =
                DatasetReader.readWordCounts(Path.of("data", "count_1w.txt"), 10_000);
        for (DatasetReader.WordCount wc : words) {
            dict.add(wc.word(), wc.count());
        }
        assertEquals(words.size(), dict.size());
        for (DatasetReader.WordCount wc : words) {
            assertTrue(dict.contains(wc.word()), wc.word());
            assertEquals(wc.count(), dict.frequency(wc.word()), wc.word());
        }
    }

    // ==================================================================
    // ADDED TESTS (Kofi Nyarku)
    // ==================================================================

    // ------------------------------------------------------------------
    // Edge cases of the interface
    // ------------------------------------------------------------------

    @Test
    void emptyDictionaryHasNoCompletionsAndRemovesNothing() {
        assertEquals(List.of(), dict.complete("", 10));
        assertEquals(List.of(), dict.complete("a", 10));
        assertFalse(dict.remove("a"));
        assertEquals(0, dict.frequency("a"));
        assertEquals(0, dict.size());
    }

    @Test
    void singleWordDictionary() {
        dict.add("x", 7);
        assertEquals(List.of("x"), dict.complete("", 5));
        assertEquals(List.of("x"), dict.complete("x", 5));
        assertEquals(List.of(), dict.complete("xx", 5));   // prefix longer than the word
        assertEquals(1, dict.size());
    }

    @Test
    void wordWithCountZeroIsStillAWord() {
        dict.add("rare", 0);
        assertTrue(dict.contains("rare"));
        assertEquals(0, dict.frequency("rare"));
        assertEquals(List.of("rare"), dict.complete("ra", 3));
        assertEquals(1, dict.size());
    }

    @Test
    void completeWithNegativeKReturnsAnEmptyList() {
        dict.add("dog", 3);
        assertEquals(List.of(), dict.complete("d", -1));
        assertEquals(List.of(), dict.complete("", Integer.MIN_VALUE));
    }

    @Test
    void completeWithHugeKReturnsAllMatches() {
        dict.add("a", 1);
        dict.add("ab", 2);
        dict.add("b", 3);
        assertEquals(List.of("ab", "a"), dict.complete("a", Integer.MAX_VALUE));
    }

    @Test
    void completeReturnsANewListEachTime() {
        dict.add("cat", 5);
        List<String> first = dict.complete("c", 5);
        assertNotNull(first);
        first.clear();                                     // changing the result ...
        assertEquals(List.of("cat"), dict.complete("c", 5)); // ... does not change the dictionary
        assertEquals(1, dict.size());
    }

    @Test
    void completeIsCaseSensitive() {
        dict.add("Apple", 50);
        dict.add("apple", 10);
        assertEquals(List.of("apple"), dict.complete("a", 5));
        assertEquals(List.of("Apple"), dict.complete("A", 5));
    }

    @Test
    void completeOnlyReturnsWordsInsideThePrefixRange() {
        // Neighbours just before and just after the "ab" range in alphabetical order.
        dict.add("a", 100);
        dict.add("aa", 90);
        dict.add("ab", 1);
        dict.add("aba", 2);
        dict.add("abz", 3);
        dict.add("ac", 80);
        dict.add("b", 70);
        assertEquals(List.of("abz", "aba", "ab"), dict.complete("ab", 10));
    }

    @Test
    void completeWhenThePrefixRangeIsAtTheEndOfTheOrder() {
        dict.add("apple", 1);
        dict.add("zz", 2);
        dict.add("zzz", 3);
        assertEquals(List.of("zzz", "zz"), dict.complete("zz", 10));
        assertEquals(List.of(), dict.complete("zzzz", 10));
    }

    @Test
    void updatingACountChangesTheRanking() {
        dict.add("cab", 1);
        dict.add("car", 2);
        assertEquals(List.of("car", "cab"), dict.complete("ca", 2));
        dict.add("cab", 10);
        assertEquals(List.of("cab", "car"), dict.complete("ca", 2));
        assertEquals(2, dict.size());
    }

    @Test
    void kSmallerThanTheMatchesKeepsOnlyTheBest() {
        for (int i = 0; i < 100; i++) {
            dict.add("w" + i, i);
        }
        assertEquals(List.of("w99", "w98", "w97"), dict.complete("w", 3));
        assertEquals(List.of("w99"), dict.complete("w9", 1));
    }

    @Test
    void tiesAtTheCutOffAreBrokenAlphabetically() {
        dict.add("bd", 5);
        dict.add("bc", 5);
        dict.add("bb", 5);
        dict.add("ba", 9);
        assertEquals(List.of("ba", "bb", "bc"), dict.complete("b", 3));
    }

    @Test
    void removedWordCanBeAddedAgain() {
        dict.add("cat", 5);
        assertTrue(dict.remove("cat"));
        assertFalse(dict.remove("cat"));                   // second remove: already gone
        dict.add("cat", 8);
        assertTrue(dict.contains("cat"));
        assertEquals(8, dict.frequency("cat"));
        assertEquals(1, dict.size());
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> dict.add("", 1));
        assertThrows(IllegalArgumentException.class, () -> dict.add(null, 1));
        assertThrows(IllegalArgumentException.class, () -> dict.add("cat", -1));
        assertThrows(NullPointerException.class, () -> dict.complete(null, 3));
        assertEquals(0, dict.size());
    }

    @Test
    void nullWordIsNeverContained() {
        dict.add("cat", 5);
        assertFalse(dict.contains(null));
        assertEquals(0, dict.frequency(null));
        assertFalse(dict.remove(null));
        assertEquals(1, dict.size());
    }

    // ------------------------------------------------------------------
    // Special cases of the implementations
    // ------------------------------------------------------------------

    @Test
    void wordsWithTheSameHashCodeAreKeptApart() {
        // "Aa" and "BB" have the same String.hashCode(), so these 8 words all collide
        // and end up in ONE chain of the hash table (Solution1).
        List<String> colliding = List.of("AaAaAa", "AaAaBB", "AaBBAa", "AaBBBB",
                                         "BBAaAa", "BBAaBB", "BBBBAa", "BBBBBB");
        for (String w : colliding) {
            assertEquals(colliding.get(0).hashCode(), w.hashCode());
        }
        for (int i = 0; i < colliding.size(); i++) {
            dict.add(colliding.get(i), i);
        }
        for (int i = 0; i < colliding.size(); i++) {
            assertEquals(i, dict.frequency(colliding.get(i)));
        }
        // Remove the first-inserted, a middle one and the last-inserted word of the chain.
        assertTrue(dict.remove("AaAaAa"));
        assertTrue(dict.remove("AaBBBB"));
        assertTrue(dict.remove("BBBBBB"));
        assertFalse(dict.remove("AaAaAa"));
        assertEquals(5, dict.size());
        assertEquals(1, dict.frequency("AaAaBB"));
        assertEquals(6, dict.frequency("BBBBAa"));
        assertEquals(List.of("BBBBAa", "BBAaBB", "BBAaAa"), dict.complete("BB", 3));
    }

    @Test
    void sortedInsertionsThenRemovingEverything() {
        // Forces resizes (hash table) and many rebalancings after delete (Red-Black tree).
        int n = 5_000;
        for (int i = 0; i < n; i++) {
            dict.add(String.format("k%05d", i), i);
        }
        for (int i = 0; i < n; i += 2) {
            assertTrue(dict.remove(String.format("k%05d", i)));
        }
        assertEquals(n / 2, dict.size());
        for (int i = 0; i < n; i++) {
            assertEquals(i % 2 == 1, dict.contains(String.format("k%05d", i)));
        }
        for (int i = 1; i < n; i += 2) {
            assertTrue(dict.remove(String.format("k%05d", i)));
        }
        assertEquals(0, dict.size());
        assertEquals(List.of(), dict.complete("", 10));
        dict.add("again", 1);                              // still usable after being emptied
        assertEquals(List.of("again"), dict.complete("", 10));
    }

    @Test
    void reverseSortedInsertionsAreAllFound() {
        int n = 10_000;
        for (int i = n - 1; i >= 0; i--) {
            dict.add(String.format("r%05d", i), i);
        }
        assertEquals(n, dict.size());
        assertEquals(List.of("r09999", "r09998"), dict.complete("r", 2));
        assertEquals(List.of("r00099", "r00098"), dict.complete("r000", 2));
    }

    // ------------------------------------------------------------------
    // Random comparison with a reference, including complete()
    // ------------------------------------------------------------------

    /** Brute-force reference for complete(): filter, sort by (count desc, word asc), cut at k. */
    private static List<String> referenceComplete(TreeMap<String, Long> ref, String prefix, int k) {
        List<Map.Entry<String, Long>> matches = new ArrayList<>();
        for (Map.Entry<String, Long> e : ref.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                matches.add(e);
            }
        }
        matches.sort((a, b) -> {
            int byCount = Long.compare(b.getValue(), a.getValue());
            return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
        });
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(Math.max(k, 0), matches.size()); i++) {
            out.add(matches.get(i).getKey());
        }
        return out;
    }

    private static String randomWord(Random random, int maxLength) {
        int length = 1 + random.nextInt(maxLength);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + random.nextInt(4)));   // small alphabet: many shared prefixes
        }
        return sb.toString();
    }

    @Test
    void randomOperationsIncludingCompleteGiveTheSameResultsAsTheReference() {
        TreeMap<String, Long> reference = new TreeMap<>();
        Random random = new Random(272);                   // fixed seed: repeatable
        for (int step = 0; step < 30_000; step++) {
            int operation = random.nextInt(10);
            String word = randomWord(random, 5);
            if (operation < 4) {                           // 40% add
                long count = random.nextInt(50);           // small range: many ties
                dict.add(word, count);
                reference.put(word, count);
            } else if (operation < 6) {                    // 20% remove
                assertEquals(reference.remove(word) != null, dict.remove(word), "remove " + word);
            } else if (operation < 8) {                    // 20% contains / frequency
                assertEquals(reference.containsKey(word), dict.contains(word), "contains " + word);
                assertEquals(reference.getOrDefault(word, 0L), dict.frequency(word), "frequency " + word);
            } else {                                       // 20% complete
                String prefix = word.substring(0, random.nextInt(Math.min(word.length(), 3) + 1));
                int k = random.nextInt(12) - 1;            // includes k = -1 and k = 0
                assertEquals(referenceComplete(reference, prefix, k), dict.complete(prefix, k),
                        "complete(\"" + prefix + "\", " + k + ") at step " + step);
            }
            assertEquals(reference.size(), dict.size(), "size at step " + step);
        }
    }

    // ------------------------------------------------------------------
    // Tests from the dataset. Expected values were read in data/count_1w.txt
    // (Norvig, 333,333 lines) and data/pg11.txt (Alice's Adventures in Wonderland,
    // Project Gutenberg eBook #11, edition updated February 4, 2024).
    // ------------------------------------------------------------------

    private static final Path WORD_LIST = Path.of("data", "count_1w.txt");
    private static final Path ALICE = Path.of("data", "pg11.txt");

    private static List<DatasetReader.WordCount> fullList;   // read once, shared by the tests

    static List<DatasetReader.WordCount> fullList() throws IOException {
        if (fullList == null) {
            fullList = DatasetReader.readWordCounts(WORD_LIST, Integer.MAX_VALUE);
        }
        return fullList;
    }

    private void loadFullList() throws IOException {
        for (DatasetReader.WordCount wc : fullList()) {
            dict.add(wc.word(), wc.count());
        }
    }

    @Test
    void theFullWordListIsLoaded() throws IOException {
        loadFullList();
        assertEquals(333_333, dict.size());                // every line of the file is a word
        assertEquals(23_135_851_162L, dict.frequency("the"));   // line 1
        assertEquals(13_151_942_776L, dict.frequency("of"));    // line 2
        assertEquals(12_711L, dict.frequency("golgw"));          // last line
        assertTrue(dict.contains("wonderland"));
        assertFalse(dict.contains("The"));                 // the list is all lowercase
    }

    @Test
    void readWordCountsKeepsFileOrderAndStopsAtMaxLines() throws IOException {
        List<DatasetReader.WordCount> first3 = DatasetReader.readWordCounts(WORD_LIST, 3);
        assertEquals(List.of(new DatasetReader.WordCount("the", 23_135_851_162L),
                             new DatasetReader.WordCount("of", 13_151_942_776L),
                             new DatasetReader.WordCount("and", 12_997_637_966L)), first3);
        assertEquals(List.of(), DatasetReader.readWordCounts(WORD_LIST, 0));
    }

    @Test
    void topFiveCompletionsOfProFromTheDataset() throws IOException {
        loadFullList();
        assertEquals(List.of("products", "product", "program", "project", "profile"),
                     dict.complete("pro", 5));
    }

    @Test
    void oneLetterPrefixesOnTheFullList() throws IOException {
        loadFullList();
        assertEquals(List.of("z", "zip", "zone", "zealand", "zoom"), dict.complete("z", 5));
        assertEquals(List.of("quality", "questions", "q", "quote", "question"), dict.complete("q", 5));
    }

    @Test
    void twoLetterPrefixesOnTheFullList() throws IOException {
        loadFullList();
        assertEquals(List.of("the", "that", "this", "they", "their"), dict.complete("th", 5));
        assertEquals(List.of("xy", "xyz", "xylene", "xylophone", "xythos"), dict.complete("xy", 5));
        assertEquals(40, dict.complete("xy", 1_000).size());   // exactly 40 words start with "xy"
    }

    @Test
    void fiveLetterPrefixesOnTheFullList() throws IOException {
        loadFullList();
        assertEquals(List.of("international", "internet", "interest", "interface", "internal"),
                     dict.complete("inter", 5));
        assertEquals(List.of("computer", "computers", "computing", "computational", "computed"),
                     dict.complete("compu", 5));
        assertEquals(144, dict.complete("compu", 1_000).size());
        assertEquals(List.of(), dict.complete("qwxyz", 5));    // no word starts with it
    }

    @Test
    void tiesInTheDatasetAreBrokenAlphabetically() throws IOException {
        // 152 words of the list have the lowest count, 12711.
        for (DatasetReader.WordCount wc : fullList()) {
            if (wc.count() == 12_711L) {
                dict.add(wc.word(), wc.count());
            }
        }
        assertEquals(152, dict.size());
        assertEquals(List.of("golgw", "gollgo", "gooblle", "gooddg", "gooek"), dict.complete("", 5));
    }

    @Test
    void removingDatasetWordsUpdatesCompletions() throws IOException {
        loadFullList();
        assertTrue(dict.remove("products"));
        assertTrue(dict.remove("program"));
        assertEquals(List.of("product", "project", "profile"), dict.complete("pro", 3));
        assertEquals(333_331, dict.size());
    }

    @Test
    void readBookWordsSkipsTheLicenseAndNormalizesTheText() throws IOException {
        List<String> words = DatasetReader.readBookWords(ALICE);
        // First words after "*** START OF": "[Illustration] / Alice’s Adventures in Wonderland
        // / by Lewis Carroll". The curly apostrophe of "Alice’s" is removed.
        assertEquals(List.of("illustration", "alices", "adventures", "in", "wonderland",
                             "by", "lewis", "carroll"), words.subList(0, 8));
        // Last words before "*** END OF": "... the happy summer days. / THE END"
        assertEquals(List.of("happy", "summer", "days", "the", "end"),
                     words.subList(words.size() - 5, words.size()));
        for (String w : words) {
            assertFalse(w.isEmpty());
            assertEquals(w.toLowerCase(), w, w);
            assertTrue(w.chars().allMatch(Character::isLetter), w);   // no punctuation, digits, hyphens
        }
        assertFalse(words.contains("gutenberg"));          // header and footer are skipped
        assertTrue(words.size() > 25_000);                 // 26,776 words in this edition
    }

    @Test
    void wordsOfAliceThatAreInTheWordList() throws IOException {
        loadFullList();
        for (String w : List.of("alice", "rabbit", "hatter", "dont", "alices", "wonderland")) {
            assertTrue(dict.contains(w), w);
        }
    }

    @Test
    void wordsOfAliceThatAreNotInTheWordList() throws IOException {
        loadFullList();
        List<String> book = DatasetReader.readBookWords(ALICE);
        // Each of these appears in the book but not in count_1w.txt.
        for (String w : List.of("jurymen", "uglification", "muchness", "ootiful", "neednt")) {
            assertTrue(book.contains(w), w + " should be in the book");
            assertFalse(dict.contains(w), w + " should be flagged as misspelled");
            assertEquals(0, dict.frequency(w));
        }
    }

    @Test
    void spellCheckingAliceFlagsOnlyAFewWords() throws IOException {
        loadFullList();
        List<String> book = DatasetReader.readBookWords(ALICE);
        Set<String> flagged = new HashSet<>();
        int misses = 0;
        for (String w : book) {
            if (!dict.contains(w)) {
                misses++;
                flagged.add(w);
            }
        }
        // 52 misses (35 distinct words) out of 26,776 in this edition: under 1%.
        assertTrue(misses < book.size() / 100, "misses: " + misses);
        assertTrue(flagged.contains("jurymen"));
        assertFalse(flagged.contains("alice"));
    }
}