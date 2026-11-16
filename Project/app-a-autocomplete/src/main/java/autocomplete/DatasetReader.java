package autocomplete;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the datasets of Application A. You must write the two methods below.
 *
 * <p>Datasets (plain text files):
 * <ul>
 *   <li>count_1w.txt from https://norvig.com/ngrams/ — one line per word:
 *       the word, a TAB character, and its count. Example line: {@code the<TAB>23135851162}</li>
 *   <li>Books from https://www.gutenberg.org/ — download the "Plain Text UTF-8" version.</li>
 * </ul>
 *
 * <p>Hints:
 * <ul>
 *   <li>{@code java.nio.file.Files.newBufferedReader(file)} gives a reader;
 *       call {@code readLine()} until it returns null.</li>
 *   <li>{@code line.split("\t")} splits a line at the TAB;
 *       {@code Long.parseLong(text)} converts the count.</li>
 * </ul>
 */
public final class DatasetReader {

    /** One line of the word list. (A "record" is a small class that only holds data.) */
    public record WordCount(String word, long count) { }

    private static final String START_MARKER = "*** START OF";
    private static final String END_MARKER = "*** END OF";
    private static final char BOM = '\uFEFF';

    private DatasetReader() { }   // no objects: only static methods

    /**
     * Reads at most maxLines lines of the word-count file, in file order.
     * Use maxLines = Integer.MAX_VALUE to read the whole file.
     */
    public static List<WordCount> readWordCounts(Path file, int maxLines) throws IOException {
                List<WordCount> result = new ArrayList<>();
        if (maxLines <= 0) {
            return result;
        }
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int linesRead = 0;
            while (linesRead < maxLines && (line = in.readLine()) != null) {
                linesRead++;
                WordCount wc = parseWordCountLine(line);
                if (wc != null) {
                    result.add(wc);
                }
            }
        }
        return result;
    }

    /**
     * Parses one line of the word list: {@code word<TAB>count}.
     *
     * @return the parsed record, or null if the line is blank or malformed
     */
    static WordCount parseWordCountLine(String line) {
        if (!line.isEmpty() && line.charAt(0) == BOM) {
            line = line.substring(1);
        }
        int tab = line.indexOf('\t');
        if (tab <= 0) {
            return null;                         // blank line, no TAB, or empty word
        }
        String word = line.substring(0, tab).strip();
        String countText = line.substring(tab + 1).strip();
        if (word.isEmpty() || countText.isEmpty()) {
            return null;
        }
        try {
            long count = Long.parseLong(countText);
            return count < 0 ? null : new WordCount(word, count);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reads a Gutenberg book and returns its words, in order, converted to lowercase,
     * with punctuation removed. Skip the license text before the line starting with
     * "*** START OF" and after the line starting with "*** END OF".
     * Document in your report how you handle apostrophes, hyphens and digits.
     */
    public static List<String> readBookWords(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        // InputStreamReader replaces invalid UTF-8 bytes instead of throwing,
        // so one bad byte in a book does not stop the whole run.
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                lines.add(line);
            }
        }

        int first = 0;
        int last = lines.size();                 // exclusive
        for (int i = 0; i < lines.size(); i++) {
            if (stripBom(lines.get(i)).startsWith(START_MARKER)) {
                first = i + 1;
                break;
            }
        }
        for (int i = first; i < lines.size(); i++) {
            if (lines.get(i).startsWith(END_MARKER)) {
                last = i;
                break;
            }
        }

        List<String> words = new ArrayList<>();
        for (int i = first; i < last; i++) {
            tokenize(stripBom(lines.get(i)), words);
        }
        return words;
    }

    /**
     * Splits one line of text into words with the rules of the class comment and
     * appends them to {@code out}. Package-private so the tests can check the rules.
     */
    static void tokenize(String line, List<String> out) {
        String lower = line.toLowerCase(Locale.ROOT);
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isApostrophe(c)) {
                continue;                        // "don't" -> "dont"
            }
            if (Character.isLetter(c)) {
                current.append(c);
            } else if (current.length() > 0) {   // any other character ends the word
                out.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
    }

    private static boolean isApostrophe(char c) {
        return c == '\'' || c == '\u2019' || c == '\u2018' || c == '\u02BC';
    }

    private static String stripBom(String line) {
        return !line.isEmpty() && line.charAt(0) == BOM ? line.substring(1) : line;
    }

    
}
