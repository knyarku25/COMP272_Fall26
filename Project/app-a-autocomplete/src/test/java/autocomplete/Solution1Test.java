package autocomplete;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Runs all the tests of DictionaryTest on Solution1, plus tests specific to its
 * separate-chaining hash table (resizing, chains, instrumentation counters).
 */
class Solution1Test extends DictionaryTest {

    @Override
    protected Dictionary createDictionary() {
        return new Solution1();
    }

    @Test
    void countersAreSwitchedOnForTheseTests() {
        assertTrue(Solution1.COUNTERS, "set COUNTERS to true to run the counter tests");
    }

    @Test
    void tableDoublesWhenTheLoadFactorWouldExceedThreeQuarters() {
        Solution1 h = new Solution1();
        assertEquals(16, h.capacity());
        for (int i = 0; i < 12; i++) {                     // 12 / 16 = 0.75: no resize yet
            h.add("w" + i, i);
        }
        assertEquals(16, h.capacity());
        assertEquals(0, h.resizeCount());
        h.add("w12", 12);                                  // 13 / 16 > 0.75: doubles
        assertEquals(32, h.capacity());
        assertEquals(1, h.resizeCount());
        for (int i = 0; i <= 12; i++) {                    // every word survived the move
            assertEquals(i, h.frequency("w" + i));
        }
    }

    @Test
    void replacingACountDoesNotGrowTheTable() {
        Solution1 h = new Solution1();
        for (int i = 0; i < 1_000; i++) {
            h.add("same", i);
        }
        assertEquals(1, h.size());
        assertEquals(16, h.capacity());
        assertEquals(999, h.frequency("same"));
    }

    @Test
    void loadFactorStaysAtMostThreeQuarters() {
        Solution1 h = new Solution1();
        for (int i = 0; i < 50_000; i++) {
            h.add("k" + i, i);
            assertTrue(h.loadFactor() <= 0.75, "load factor " + h.loadFactor() + " at " + i);
        }
        assertEquals(Integer.bitCount(h.capacity()), 1, "capacity is a power of two");
    }

    @Test
    void wordsWithTheSameHashCodeShareOneChain() {
        Solution1 h = new Solution1();
        List<String> colliding = List.of("AaAaAa", "AaAaBB", "AaBBAa", "AaBBBB",
                                         "BBAaAa", "BBAaBB", "BBBBAa", "BBBBBB");
        for (String w : colliding) {
            h.add(w, 1);
        }
        assertEquals(8, h.longestChain());
        assertEquals(1, h.nonEmptyBuckets());
        h.remove("BBBBBB");                                // head of the chain (inserted last)
        h.remove("AaAaAa");                                // tail of the chain (inserted first)
        h.remove("AaBBBB");                                // middle
        assertEquals(5, h.longestChain());
        assertEquals(5, h.size());
    }

    @Test
    void removeDoesNotShrinkTheTable() {
        Solution1 h = new Solution1();
        for (int i = 0; i < 100; i++) {
            h.add("w" + i, i);
        }
        int capacity = h.capacity();
        for (int i = 0; i < 100; i++) {
            assertTrue(h.remove("w" + i));
        }
        assertEquals(0, h.size());
        assertEquals(capacity, h.capacity());
        assertEquals(0, h.longestChain());
    }

    @Test
    void completeScansEveryWordOfTheTable() {
        Solution1 h = new Solution1();
        for (int i = 0; i < 500; i++) {
            h.add("w" + i, i);
        }
        h.resetCounters();
        h.complete("w49", 3);
        assertEquals(500, h.completeScannedCount());       // no key order: n words examined
        h.resetCounters();
        h.complete("zzz", 3);                              // even with no match at all
        assertEquals(500, h.completeScannedCount());
        h.complete("w", 0);                                // k <= 0 returns at once
        assertEquals(500, h.completeScannedCount());
    }

    @Test
    void probeCountersCountComparedNodes() {
        Solution1 h = new Solution1();
        h.add("AaAa", 1);
        h.add("BBBB", 2);                                  // same hash: same chain, inserted at head
        h.resetCounters();
        assertTrue(h.contains("BBBB"));                    // found at the head: 1 node
        assertEquals(1, h.probeCount());
        assertTrue(h.contains("AaAa"));                    // second in the chain: 2 nodes
        assertEquals(3, h.probeCount());
        assertEquals(2, h.lookupCount());
        assertEquals(1.5, h.averageProbesPerLookup(), 1e-9);
        h.resetCounters();
        assertEquals(0, h.probeCount());
        assertEquals(0, h.lookupCount());
        assertEquals(0.0, h.averageProbesPerLookup());
    }

    @Test
    void fullWordListGivesShortChains() throws IOException {
        Solution1 h = new Solution1();
        for (DatasetReader.WordCount wc : fullList()) {
            h.add(wc.word(), wc.count());
        }
        assertEquals(333_333, h.size());
        assertEquals(1 << 19, h.capacity());               // 333,333 / 0.75 needs 2^19 buckets
        assertEquals(15, h.resizeCount());                 // 16 -> 2^19 is 15 doublings
        assertTrue(h.longestChain() <= 10, "longest chain " + h.longestChain());
        h.resetCounters();
        for (DatasetReader.WordCount wc : fullList()) {
            assertTrue(h.contains(wc.word()));
        }
        // Successful search with chaining: about 1 + alpha/2 = 1.32 nodes for alpha = 0.64.
        assertTrue(h.averageProbesPerLookup() < 1.5, "probes " + h.averageProbesPerLookup());
    }
}