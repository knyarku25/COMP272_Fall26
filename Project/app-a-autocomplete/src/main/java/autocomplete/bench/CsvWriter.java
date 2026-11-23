package autocomplete.bench;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Writes one CSV file: a header line, then one line per call to {@link #row}.
 * Numbers always use a dot as the decimal separator, whatever the computer's language,
 * so Excel, Python and my plot script all read the files the same way.
 */
final class CsvWriter implements AutoCloseable {

    private final PrintWriter out;

    CsvWriter(Path file, String... header) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
        out.println(String.join(",", header));
    }

    /** Writes one line. Doubles get 3 decimals; everything else is written as is. */
    void row(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            Object v = values[i];
            if (v instanceof Double d) {
                line.append(String.format(Locale.ROOT, "%.3f", d));
            } else {
                line.append(v == null ? "" : v.toString());
            }
        }
        out.println(line);
        out.flush();                           // keep partial results if a run is stopped
    }

    @Override
    public void close() {
        out.close();
    }
}
