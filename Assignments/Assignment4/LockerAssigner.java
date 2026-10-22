import java.util.TreeMap;
import java.util.TreeSet;

/**
 * COMP 272 Assignment 4 — Exercise 3: nearest free locker.
 *
 * The library has n lockers numbered 1..n, all free at the start.
 * A student asks for a preferred number and receives the FREE locker closest
 * to it. If two free lockers are equally close, the SMALLER number wins.
 *
 * Use java.util.TreeSet and/or java.util.TreeMap (your choice).
 * Do not change the method signatures. You may add private fields and helpers.
 */
public class LockerAssigner {

    private final int n;
    private final TreeSet<Integer> free; // free locker numbers, sorted

    /** Create lockers 1..n, all free. Throw IllegalArgumentException if n < 0. */
    public LockerAssigner(int n) {
        if  (n < 0) {
            throw new IllegalArgumentException("n must be >= 0");
        }
        this.n = n;
        this.free = new TreeSet<>();
        for (int locker = 1; locker <= n; locker++) {
            free.add(locker);
        }
    }

    /**
     * Assign the free locker closest to preferred (ties: smaller number),
     * mark it as taken, and return its number. preferred may be any int,
     * even outside 1..n. Return -1 if no locker is free.
     */
    public int assign(int preferred) {
        if (free.isEmpty()) {
            return -1;
        }

        Integer below = free.floor(preferred);
        Integer above = free.ceiling(preferred);

        int chosen;
        if (below == null) {
            chosen = above;
        } else if (above == null) {
            chosen = below;
        } else {
            // long avoids int overflow; <= gives ties to the smaller number.
            long distBelow = (long) preferred - below;
            long distAbove = (long) above - preferred;
            chosen = (distBelow <= distAbove) ? below : above;
        }

        free.remove(chosen);
        return chosen;
    }

    /**
     * Make locker free again and return true. Return false (and change
     * nothing) if it is already free or is not a number in 1..n.
     */
    public boolean release(int locker) {
        if (locker < 1 || locker > n) {
            return false;
        }
        return free.add(locker);    // false if already free
    }

    /*
     * ======================== Exercise 3, Part B — Analysis =======================
     * Let n be the number of lockers.
     *
     * Q1. Which structure did you use (TreeSet, TreeMap, or both), what does it
     *     store, and why does it fit this problem?
     *  A TreeSet<Integer> holding the free locker numbers. The closest free
     * locker is either floor(preferred) or ceiling(preferred), and TreeSet
     * returns both directly. It also supports remove (assign) and add (release)
     * quickly, so no scanning is needed.
     * 
     * Q2. If you had stored the free lockers in an unsorted ArrayList instead,
     *     what would be the worst-case running time of assign and of release?
     * Assign: O(n). With no order, every free locker (up to n) must be checked
     * to find the closest one.
     * Release: O(n). Adding is O(1), but checking that the locker is not already
     * free needs a linear search of up to n elements.
     *
     * Q3. What is the worst-case running time of assign and release with your
     *     solution? Justify it using the fact that TreeSet and TreeMap are
     *     red-black trees.
     * Both are O(log n). The TreeSet is a red-black tree with at most n elements, 
     * so its height is at most 2 log2(n + 1) = O(log n). 
     * - Assign: floor, ceiling and remove each follow one root-to-leaf path, O(log n);
     * the rest is O(1).
     * - Release: the range check is O(1); add is a red-black insertion, O(log n).
     * 
     * ============================================================================
     */
}
