import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

/**
 * COMP 272 - Assignment 5, Exercise 2.
 */
public class HashingProblems {

    /**
     * I used a HashSet to keep track of the matching keys I already counted.
     * I added their values and divided by the number of unique matches, returning
     * 0.0 if there were none. This takes expected O(n) time and O(n) extra space.
     */
    public static double getAverage(int[] keys, HashMap<Integer, Integer> map) {
        HashSet<Integer> counted = new HashSet<>();
        long sum = 0;
        for (int key : keys) {
            if (map.containsKey(key) && counted.add(key)) {
                sum += map.get(key);
            }
        }
        return counted.isEmpty() ? 0.0 : (double) sum / counted.size();
    }

    /**
     * I checked each key using key % 2 != 0 so negative odd keys are included.
     * I added one value per odd key, keeping equal values from different keys.
     */
    public static ArrayList<Integer> odd(HashMap<Integer, Integer> map) {
        ArrayList<Integer> result = new ArrayList<>();
        for (int key : map.keySet()) {
            if (key % 2 != 0) {
                result.add(map.get(key));
            }
        }
        return result;
    }

    /**
     * I used a HashMap to count the occurrences of each number. For k > 0,
     * I checked whether value + k was in the map, which counts each pair once.
     * For k == 0, I counted only values that appeared at least twice so each
     * pair uses two different positions.
     *
     * My solution takes expected O(n) time to build and check the map, and
     * O(n) auxiliary space for the distinct values. I did not modify the array.
     * I assumed no integer overflow, as allowed in the assignment.
     */
    public static int twoSums(int[] numbers, int k) {
        HashMap<Integer, Integer> frequency = new HashMap<>();
        for (int number : numbers) {
            frequency.put(number, frequency.getOrDefault(number, 0) + 1);
        }

        int count = 0;
        for (int value : frequency.keySet()) {
            if (k == 0) {
                if (frequency.get(value) >= 2) {
                    count++;
                }
            } else if (frequency.containsKey(value + k)) {
                count++;
            }
        }
        return count;
    }

    private static int passed = 0;
    private static int failed = 0;

    /**
     * I used this helper to print the expected and actual results for each test.
     */
    private static void check(String label, Object expected, Object actual) {
        boolean matches = Objects.equals(expected, actual);
        if (matches) passed++; else failed++;
        System.out.printf("%s | %s | expected: %s | actual: %s%n",
                matches ? "PASS" : "FAIL", label, expected, actual);
    }

    /**
     * I allowed a small rounding difference when comparing averages.
     */
    private static void checkDouble(String label, double expected, double actual) {
        boolean matches = Math.abs(expected - actual) < 1e-9;
        if (matches) passed++; else failed++;
        System.out.printf("%s | %s | expected: %s | actual: %s%n",
                matches ? "PASS" : "FAIL", label, expected, actual);
    }

    /**
     * I sorted a copy of the result so I could compare the values regardless of order.
     */
    private static ArrayList<Integer> sorted(ArrayList<Integer> values) {
        ArrayList<Integer> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return copy;
    }

    /**
     * I tested all three methods with regular inputs, duplicates, and edge cases.
     */
    public static void main(String[] args) {
        HashMap<Integer, Integer> map = new HashMap<>();
        map.put(1, 10);
        map.put(2, 20);
        map.put(3, 30);
        map.put(-5, 50);
        map.put(-4, 40);
        map.put(0, 7);
        HashMap<Integer, Integer> originalMap = new HashMap<>(map);

        System.out.println("getAverage tests");
        checkDouble("Three matching keys", 20.0, getAverage(new int[]{1, 2, 3}, map));
        checkDouble("Repeated keys count once", 20.0, getAverage(new int[]{1, 1, 1, 3}, map));
        checkDouble("Some keys missing", 20.0, getAverage(new int[]{2, 99, 100}, map));
        checkDouble("No matches", 0.0, getAverage(new int[]{98, 99}, map));
        checkDouble("Empty array", 0.0, getAverage(new int[]{}, map));
        checkDouble("Empty map", 0.0, getAverage(new int[]{1}, new HashMap<>()));
        checkDouble("Fractional average", 37.0 / 3, getAverage(new int[]{1, 2, 0}, map));
        checkDouble("Negative key", 50.0, getAverage(new int[]{-5, -5}, map));
        int[] keys = {3, 1};
        getAverage(keys, map);
        check("Keys unchanged", "[3, 1]", Arrays.toString(keys));
        check("Map unchanged after averaging", originalMap, map);

        System.out.println("\nodd tests");
        check("Positive and negative odd keys", Arrays.asList(10, 30, 50), sorted(odd(map)));
        HashMap<Integer, Integer> evens = new HashMap<>();
        evens.put(0, 1);
        evens.put(2, 2);
        evens.put(-4, 3);
        check("Only even keys", Collections.emptyList(), odd(evens));
        check("Empty map", Collections.emptyList(), odd(new HashMap<>()));
        HashMap<Integer, Integer> negatives = new HashMap<>();
        negatives.put(-1, 100);
        negatives.put(-3, 100);
        negatives.put(-2, 5);
        check("Equal values from different odd keys", Arrays.asList(100, 100), sorted(odd(negatives)));
        check("Map unchanged after odd", originalMap, map);

        System.out.println("\ntwoSums tests");
        check("Assignment example", 2, twoSums(new int[]{1, 1, 3, 5}, 2));
        check("Unsorted numbers", 3, twoSums(new int[]{1, 5, 3, 4, 2}, 2));
        check("No matching pairs", 0, twoSums(new int[]{1, 2, 3}, 5));
        check("Zero difference with duplicates", 2, twoSums(new int[]{1, 1, 2, 2, 2, 3}, 0));
        check("Zero difference without duplicates", 0, twoSums(new int[]{1, 2, 3}, 0));
        check("Single number", 0, twoSums(new int[]{4}, 0));
        check("Empty array", 0, twoSums(new int[]{}, 3));
        check("Negative numbers", 3, twoSums(new int[]{-3, -1, 1, 3}, 2));
        check("All equal, zero difference", 1, twoSums(new int[]{7, 7, 7, 7}, 0));
        check("All equal, positive difference", 0, twoSums(new int[]{7, 7, 7, 7}, 1));
        check("Repeated pair counts once", 1, twoSums(new int[]{1, 3, 1, 3, 1, 3}, 2));
        int[] numbers = {5, 1, 3};
        twoSums(numbers, 2);
        check("Numbers unchanged", "[5, 1, 3]", Arrays.toString(numbers));

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
    }
}
