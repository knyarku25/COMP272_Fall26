/**
 * COMP 272 - Assignment 5, Exercise 1.
 */
public class ProbeHashMap<K, V> {
    // I used this marker for deleted entries.
    private static final Object AVAILABLE = new Object();   // tombstone
    private Object[] keys;
    private Object[] vals;
    private int n = 0;   // number of active entries (tombstones are not counted)

    /**
     * I initialized the key and value arrays with the given capacity.
     */
    public ProbeHashMap(int capacity) { keys = new Object[capacity]; vals = new Object[capacity]; }

    /**
     * I calculated the starting slot from the hash code, clearing its sign bit before taking the remainder.
     */
    private int h(Object key) { return (key.hashCode() & 0x7fffffff) % keys.length; }

    /**
     * I checked consecutive slots from the starting index, wrapping around at the end.
     * I skipped tombstones and stopped at an unused slot or after one full loop.
     * I returned the matching index, or -1 if the key was missing.
     */
    private int find(Object key) {
        int i = h(key);
        for (int p = 0; p < keys.length; p++) {
            if (keys[i] == null) return -1;                     // empty: stop
            if (keys[i] != AVAILABLE && keys[i].equals(key)) return i;
            i = (i + 1) % keys.length;                          // next cell
        }
        return -1;
    }

    /**
     * I used find() to locate the key, then returned its value or null if it was missing.
     */
    @SuppressWarnings("unchecked")
    public V get(K key) { int i = find(key); return i < 0 ? null : (V) vals[i]; }

    /**
     * I first checked whether the key already existed and updated its value if it did.
     * Otherwise, I inserted it into the first unused slot or tombstone and increased n.
     * I returned the old value for an update and null for a new entry.
     * If the table was full, I threw an exception for a new key.
     */
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        int i = find(key);
        if (i >= 0) { V old = (V) vals[i]; vals[i] = value; return old; }
        if (n == keys.length) throw new IllegalStateException("table full");
        i = h(key);
        while (keys[i] != null && keys[i] != AVAILABLE)          // first free cell
            i = (i + 1) % keys.length;
        keys[i] = key; vals[i] = value; n++;
        return null;
    }

    /**
     * I built a string of the table contents, using _ for unused slots and A for tombstones.
     */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        for (Object k : keys) sb.append(k == null ? "_" : k == AVAILABLE ? "A" : k.toString()).append(' ');
        return sb.toString();
    }

    /**
     * I returned n to check the number of active entries in my tests.
     */
    public int size() { return n; }

    /**
     * I found the key, saved its value, and replaced it with a tombstone.
     * I then cleared its value, decreased n, and returned the saved value.
     * If the key was missing, I returned null.
     */
    @SuppressWarnings("unchecked")
    public V remove(K key) {
        int i = find(key);
        if (i < 0) return null;            // key absent: map unchanged
        V old = (V) vals[i];
        keys[i] = AVAILABLE;               // I kept the probe sequence intact.
        vals[i] = null;                    // drop the reference to the old value
        n--;
        return old;
    }

    /**
     * I removed the entry only if its key and value matched. I used Objects.equals()
     * to account for null values. On a match, I left a tombstone, decreased n,
     * and returned true. Otherwise, I returned false without changing the map.
     */
    public boolean remove(K key, V value) {
        int i = find(key);
        if (i < 0) return false;                               // key absent
        if (!java.util.Objects.equals(vals[i], value)) return false;  // value mismatch (null-safe)
        keys[i] = AVAILABLE;
        vals[i] = null;
        n--;
        return true;
    }

    /**
     * I used find() to check whether the key existed, including keys with null values.
     * I replaced the value and returned true if found, or false if missing.
     * I did not change n.
     */
    public boolean replace(K key, V value) {
        int i = find(key);         // I checked the key rather than its value.
        if (i < 0) return false;
        vals[i] = value;
        return true;
    }

    /**
     * I checked that the key existed and its value matched oldValue using Objects.equals().
     * If both matched, I saved the old value, replaced it, and returned the saved value.
     * Otherwise, I returned null. A successful replacement of null also returns null.
     * I left n unchanged.
     */
    @SuppressWarnings("unchecked")
    public V replace(K key, V oldValue, V newValue) {
        int i = find(key);
        if (i < 0) return null;
        if (!java.util.Objects.equals(vals[i], oldValue)) return null;
        V prev = (V) vals[i];
        vals[i] = newValue;
        return prev;
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    private static int passed = 0, failed = 0;

    /**
     * I used this helper to display expected and actual results and count passes and failures.
     */
    private static void check(String label, Object expected, Object actual) {
        boolean ok = java.util.Objects.equals(expected, actual);
        if (ok) passed++; else failed++;
        System.out.printf("%-4s %-52s expected=%-22s actual=%s%n",
                ok ? "PASS" : "FAIL", label, expected, actual);
    }

    /**
     * I tested collisions, successful and unsuccessful operations, null values, and tombstone reuse.
     */
    public static void main(String[] args) {
        // I chose keys that collide at slot 3 in a table of capacity 7.
        ProbeHashMap<Integer, String> m = new ProbeHashMap<>(7);

        System.out.println("=== put with collisions ===");
        check("put(3,a) new key returns null", null, m.put(3, "a"));
        check("put(10,b) collides, returns null", null, m.put(10, "b"));
        check("put(17,c) collides, returns null", null, m.put(17, "c"));
        check("put(5,e) returns null", null, m.put(5, "e"));
        System.out.println("     table: " + m.dump());
        check("dump after collisions", "_ _ _ 3 10 17 5 ", m.dump());
        check("size = 4", 4, m.size());
        check("put(3,a2) update returns old", "a", m.put(3, "a2"));
        check("size unchanged after update", 4, m.size());

        System.out.println("\n=== get ===");
        check("get(3)", "a2", m.get(3));
        check("get(17) found by probing", "c", m.get(17));
        check("get(99) absent", null, m.get(99));

        System.out.println("\n=== remove(key) ===");
        check("remove(10) returns value", "b", m.remove(10));
        System.out.println("     table: " + m.dump());
        check("slot 4 is a tombstone", "_ _ _ 3 A 17 5 ", m.dump());
        check("size = 3", 3, m.size());
        check("get(10) after removal", null, m.get(10));
        check("get(17) still reachable past tombstone", "c", m.get(17));
        check("remove(10) again (absent) returns null", null, m.remove(10));
        check("remove(42) absent returns null", null, m.remove(42));
        check("size still 3", 3, m.size());

        System.out.println("\n=== tombstone reuse ===");
        check("put(24,d) returns null", null, m.put(24, "d"));
        System.out.println("     table: " + m.dump());
        check("24 reused tombstone at slot 4", "_ _ _ 3 24 17 5 ", m.dump());
        check("get(24)", "d", m.get(24));
        check("size = 4", 4, m.size());
        // I checked that updating a key past a tombstone did not create a duplicate.
        m.remove(24);                             // slot 4 -> A
        check("put(17,c2) updates existing, no duplicate", "c", m.put(17, "c2"));
        check("dump: 17 still at slot 5, slot 4 still A", "_ _ _ 3 A 17 5 ", m.dump());
        check("size = 3", 3, m.size());

        System.out.println("\n=== remove(key, value) ===");
        check("remove(17, wrong) -> false", false, m.remove(17, "zzz"));
        check("17 still present", "c2", m.get(17));
        check("remove(99, x) absent -> false", false, m.remove(99, "x"));
        check("remove(17, c2) -> true", true, m.remove(17, "c2"));
        check("get(17) after conditional remove", null, m.get(17));
        check("size = 2", 2, m.size());
        check("dump", "_ _ _ 3 A A 5 ", m.dump());

        System.out.println("\n=== replace(key, value) ===");
        check("replace(3, z) present -> true", true, m.replace(3, "z"));
        check("get(3) = z", "z", m.get(3));
        check("replace(40, q) absent -> false", false, m.replace(40, "q"));
        check("replace did not insert 40", null, m.get(40));
        check("size unchanged = 2", 2, m.size());

        System.out.println("\n=== replace(key, old, new) ===");
        check("replace(3, z, y) match -> returns z", "z", m.replace(3, "z", "y"));
        check("get(3) = y", "y", m.get(3));
        check("replace(3, wrong, w) mismatch -> null", null, m.replace(3, "nope", "w"));
        check("get(3) still y", "y", m.get(3));
        check("replace(77, a, b) absent -> null", null, m.replace(77, "a", "b"));
        check("size unchanged = 2", 2, m.size());

        System.out.println("\n=== null values ===");
        check("put(1, null) returns null", null, m.put(1, null));
        check("size = 3 (key with null value counts)", 3, m.size());
        check("replace(1, v) on null-valued key -> true", true, m.replace(1, "v"));
        check("get(1) = v", "v", m.get(1));
        check("replace(1, v, null) -> returns v", "v", m.replace(1, "v", null));
        check("replace(1, null, n) -> returns null (old value)", null, m.replace(1, null, "n"));
        check("get(1) = n (replacement happened)", "n", m.get(1));
        m.put(1, null);
        check("remove(1, x) mismatch vs null -> false", false, m.remove(1, "x"));
        check("remove(1, null) match -> true", true, m.remove(1, null));
        check("size = 2", 2, m.size());

        System.out.println("\n=== full table ===");
        ProbeHashMap<Integer, Integer> small = new ProbeHashMap<>(2);
        small.put(0, 0);
        small.put(1, 1);
        String result;
        try { small.put(2, 2); result = "no exception"; }
        catch (IllegalStateException e) { result = "IllegalStateException"; }
        check("put into full table throws", "IllegalStateException", result);
        small.remove(0);
        check("after remove, insert reuses tombstone", null, small.put(2, 2));
        check("get(2) = 2", 2, small.get(2));

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
    }
    /*
     * I used tombstones to keep the probe sequence intact after a deletion.
     * Unlike a never-used null slot, a tombstone tells my search to continue.
     * My insertions can reuse that slot after checking for an existing key.
     *
     * In my capacity-7 example, keys 3, 10, and 17 collide and occupy slots
     * 3, 4, and 5. After I remove 10, slot 4 holds a tombstone, so get(17)
     * continues to slot 5 and finds it. Setting slot 4 to null would make
     * the search stop early and incorrectly report that 17 was missing.
     * When I insert 24, it reuses the tombstone at slot 4.
     */
}
