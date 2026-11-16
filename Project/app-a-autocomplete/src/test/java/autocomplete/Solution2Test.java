package autocomplete;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Runs all the tests of DictionaryTest on Solution2, plus tests specific to its
 * Red-Black tree (TreeMap) adapter: complete() walks only the prefix range.
 */
class Solution2Test extends DictionaryTest {

    @Override
    protected Dictionary createDictionary() {
        return new Solution2();
    }

    @Test
    void countersAreSwitchedOnForTheseTests() {
        assertTrue(Solution2.COUNTERS, "set COUNTERS to true to run the counter tests");
    }

    @Test
    void completeWalksOnlyThePrefixRangePlusOneKey() {
        Solution2 t = new Solution2();
        for (String w : List.of("a", "aa", "ab", "aba", "abz", "ac", "b", "c")) {
            t.add(w, 1);
        }
        t.resetCounters();
        t.complete("ab", 10);
        assertEquals(4, t.completeScannedCount());         // ab, aba, abz, then "ac" ends the walk
        t.resetCounters();
        t.complete("c", 10);
        assertEquals(1, t.completeScannedCount());         // range at the end: no key after it
        t.resetCounters();
        t.complete("d", 10);
        assertEquals(0, t.completeScannedCount());         // no key >= "d"
    }

    @Test
    void completeOnTheFullListScansOnlyTheMatches() throws IOException {
        Solution2 t = new Solution2();
        for (DatasetReader.WordCount wc : fullList()) {
            t.add(wc.word(), wc.count());
        }
        t.resetCounters();
        t.complete("xy", 5);                               // 40 words start with "xy"
        assertEquals(41, t.completeScannedCount());
        t.resetCounters();
        t.complete("compu", 5);                            // 144 words start with "compu"
        assertEquals(145, t.completeScannedCount());
        t.resetCounters();
        t.complete("qwxyz", 5);                            // no match: a single key is checked
        assertEquals(1, t.completeScannedCount());
    }

    @Test
    void emptyPrefixScansTheWholeTree() {
        Solution2 t = new Solution2();
        for (int i = 0; i < 300; i++) {
            t.add("w" + i, i);
        }
        t.resetCounters();
        assertEquals(List.of("w299"), t.complete("", 1));
        assertEquals(300, t.completeScannedCount());
    }
}
