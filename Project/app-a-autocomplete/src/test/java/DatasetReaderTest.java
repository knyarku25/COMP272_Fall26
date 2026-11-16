package autocomplete;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of DatasetReader on small hand-made files, one rule at a time.
 * (The tests on the real dataset files are in DictionaryTest.)
 */
class DatasetReaderTest {

    @TempDir
    Path dir;

    private Path write(String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static List<String> tokens(String line) {
        List<String> out = new ArrayList<>();
        DatasetReader.tokenize(line, out);
        return out;
    }

    // ---- readWordCounts ----

    @Test
    void readsWordsAndCountsInFileOrder() throws IOException {
        Path f = write("w.txt", "the\t100\nof\t50\nand\t7\n");
        assertEquals(List.of(new DatasetReader.WordCount("the", 100),
                             new DatasetReader.WordCount("of", 50),
                             new DatasetReader.WordCount("and", 7)),
                     DatasetReader.readWordCounts(f, Integer.MAX_VALUE));
    }

    @Test
    void readsWindowsLineEndingsAndCountsLargerThanAnInt() throws IOException {
        Path f = write("w.txt", "the\t23135851162\r\nof\t13151942776\r\n");
        List<DatasetReader.WordCount> words = DatasetReader.readWordCounts(f, 10);
        assertEquals("the", words.get(0).word());          // no "\r" left at the end
        assertEquals(23_135_851_162L, words.get(0).count());
        assertEquals(2, words.size());
    }

    @Test
    void stopsAfterMaxLines() throws IOException {
        Path f = write("w.txt", "a\t3\nb\t2\nc\t1\n");
        assertEquals(2, DatasetReader.readWordCounts(f, 2).size());
        assertEquals(0, DatasetReader.readWordCounts(f, 0).size());
        assertEquals(0, DatasetReader.readWordCounts(f, -5).size());
    }

    @Test
    void skipsBlankAndMalformedLines() throws IOException {
        Path f = write("w.txt", "good\t5\n\nnotab 4\nbad\tabc\nneg\t-1\n\t9\nlast\t1");
        List<DatasetReader.WordCount> words = DatasetReader.readWordCounts(f, Integer.MAX_VALUE);
        assertEquals(List.of(new DatasetReader.WordCount("good", 5),
                             new DatasetReader.WordCount("last", 1)), words);   // no final newline needed
    }

    @Test
    void emptyFileGivesAnEmptyList() throws IOException {
        assertEquals(List.of(), DatasetReader.readWordCounts(write("w.txt", ""), 100));
    }

    @Test
    void missingFileThrowsIOException() {
        assertThrows(IOException.class,
                () -> DatasetReader.readWordCounts(dir.resolve("nope.txt"), 10));
    }

    // ---- tokenization rules ----

    @Test
    void lowercasesAndRemovesPunctuation() {
        assertEquals(List.of("hello", "world"), tokens("Hello, WORLD!"));
        assertEquals(List.of("what", "is", "the", "use"), tokens("\u201CWhat is the use?\u201D"));
    }

    @Test
    void apostrophesAreRemovedAndThePiecesJoined() {
        assertEquals(List.of("dont", "alices", "isnt"), tokens("don't Alice\u2019s isn\u2019t"));
        assertEquals(List.of("hello"), tokens("\u2018Hello\u2019"));   // curly quotes around a word
        assertEquals(List.of("hello"), tokens("'Hello'"));
    }

    @Test
    void hyphensAndDashesSeparateWords() {
        assertEquals(List.of("rabbit", "hole"), tokens("Rabbit-Hole"));
        assertEquals(List.of("well", "then"), tokens("well\u2014then"));
        assertEquals(List.of("so", "she"), tokens("so--she"));
    }

    @Test
    void digitsSeparateWordsAndAreDropped() {
        assertEquals(List.of("chapter"), tokens("CHAPTER 12."));
        assertEquals(List.of("in", "it", "was"), tokens("In 1865 it was"));
        assertEquals(List.of("abc", "def"), tokens("abc123def"));
    }

    @Test
    void underscoresAndBracketsSeparateWords() {
        assertEquals(List.of("very", "illustration"), tokens("_very_ [Illustration]"));
    }

    @Test
    void accentedLettersAreKept() {
        assertEquals(List.of("caf\u00E9", "o\u00F9"), tokens("Caf\u00E9, o\u00F9"));
    }

    @Test
    void lineWithNoLettersGivesNoWords() {
        assertEquals(List.of(), tokens(""));
        assertEquals(List.of(), tokens("   *  *  *  "));
        assertEquals(List.of(), tokens("1234 -- 5678"));
    }

    // ---- readBookWords ----

    @Test
    void readsOnlyBetweenTheStartAndEndMarkers() throws IOException {
        Path f = write("book.txt",
                "\uFEFFThe Project Gutenberg eBook of Test\r\n"
                + "License text here\r\n"
                + "*** START OF THE PROJECT GUTENBERG EBOOK TEST ***\r\n"
                + "\r\n"
                + "It\u2019s a Rabbit-Hole!\r\n"
                + "CHAPTER 2.\r\n"
                + "*** END OF THE PROJECT GUTENBERG EBOOK TEST ***\r\n"
                + "More license text, Gutenberg.\r\n");
        assertEquals(List.of("its", "a", "rabbit", "hole", "chapter"), DatasetReader.readBookWords(f));
    }

    @Test
    void oldStyleMarkersAreRecognizedToo() throws IOException {
        Path f = write("book.txt",
                "header\n*** START OF THIS PROJECT GUTENBERG EBOOK X ***\nbody words\n"
                + "*** END OF THIS PROJECT GUTENBERG EBOOK X ***\nfooter\n");
        assertEquals(List.of("body", "words"), DatasetReader.readBookWords(f));
    }

    @Test
    void fileWithoutMarkersIsReadEntirely() throws IOException {
        Path f = write("plain.txt", "\uFEFFJust some text.\nTwo lines.\n");
        assertEquals(List.of("just", "some", "text", "two", "lines"), DatasetReader.readBookWords(f));
    }

    @Test
    void missingEndMarkerReadsToTheEndOfTheFile() throws IOException {
        Path f = write("book.txt", "license\n*** START OF X ***\nall of this\n");
        assertEquals(List.of("all", "of", "this"), DatasetReader.readBookWords(f));
    }

    @Test
    void invalidUtf8BytesDoNotStopTheReader() throws IOException {
        Path f = dir.resolve("bad.txt");
        byte[] bytes = {'o', 'k', ' ', (byte) 0xFF, ' ', 'f', 'i', 'n', 'e', '\n'};
        Files.write(f, bytes);
        assertEquals(List.of("ok", "fine"), DatasetReader.readBookWords(f));
    }
}
