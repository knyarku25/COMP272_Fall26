package autocomplete.bench;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records the machine and JVM the benchmark ran on (CPU, RAM, operating system,
 * Java version, heap size), as the methodology checklist asks. Written automatically,
 * so the numbers in results/ always come with the machine that produced them.
 */
final class MachineInfo {

    private MachineInfo() { }

    static Map<String, String> collect() {
        Map<String, String> info = new LinkedHashMap<>();
        Runtime rt = Runtime.getRuntime();
        info.put("date", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        info.put("cpu", cpuName());
        info.put("logical_cores", String.valueOf(rt.availableProcessors()));
        info.put("ram_total_gb", String.format(java.util.Locale.ROOT, "%.1f", totalRamBytes() / 1e9));
        info.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        info.put("java_version", System.getProperty("java.version"));
        info.put("java_vm", System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        info.put("java_vendor", System.getProperty("java.vendor"));
        info.put("max_heap_mb", String.valueOf(rt.maxMemory() / (1024 * 1024)));
        info.put("jvm_args", String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()));
        info.put("warmup_runs", String.valueOf(Measurement.WARMUP_RUNS));
        info.put("measured_runs", String.valueOf(Measurement.MEASURED_RUNS));
        return info;
    }

    static void write(Path file) throws IOException {
        try (CsvWriter csv = new CsvWriter(file, "key", "value")) {
            for (Map.Entry<String, String> e : collect().entrySet()) {
                // Commas would break the CSV columns.
                csv.row(e.getKey(), e.getValue().replace(',', ';'));
            }
        }
    }

    /** Total physical memory, from the JVM's operating-system bean (works on Windows, Mac, Linux). */
    private static long totalRamBytes() {
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            return sunOs.getTotalMemorySize();
        }
        return -1;
    }

    /** The CPU's marketing name, asked from the operating system. */
    private static String cpuName() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                return firstLine("powershell", "-NoProfile", "-Command",
                        "(Get-CimInstance Win32_Processor).Name");
            }
            if (os.contains("mac")) {
                return firstLine("sysctl", "-n", "machdep.cpu.brand_string");
            }
            for (String line : Files.readAllLines(Path.of("/proc/cpuinfo"))) {
                if (line.startsWith("model name")) {
                    return line.substring(line.indexOf(':') + 1).trim();
                }
            }
        } catch (IOException | RuntimeException e) {
            // fall through to "unknown"
        }
        return "unknown";
    }

    private static String firstLine(String... command) throws IOException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) {
                    return line.trim();
                }
            }
        }
        return "unknown";
    }
}