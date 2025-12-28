package fxShield.GPU;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class TypeperfGpuUsageProvider implements GpuUsageProvider {

    private static final String COUNTER = "\\\\GPU Engine(*)\\\\Utilization Percentage";

    // typeperf rate-based counters need at least 2 samples
    private static final int SAMPLES = 2;

    // typeperf default interval is 1s; leave enough headroom (wildcards may output many columns)
    private static final long TIMEOUT_SECONDS = 5;

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    @Override
    public int readGpuUsagePercent() {
        if (!isWindows()) return -1;

        Process p = null;
        Thread drain = null;

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "typeperf",
                    COUNTER,
                    "-sc", String.valueOf(SAMPLES)
            );
            pb.redirectErrorStream(true);

            p = pb.start();

            AtomicReference<String> lastDataLine = new AtomicReference<>(null);

            Process finalP = p;
            drain = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(finalP.getInputStream(), Charset.defaultCharset())
                )) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!looksLikeCsv(trimmed)) continue;

                        String first = firstQuotedToken(trimmed);
                        if (first == null) continue;

                        // Skip typeperf header lines
                        if ("Time".equalsIgnoreCase(first)) continue;
                        if (first.startsWith("(PDH-CSV")) continue;

                        // Data line (timestamp + values)
                        lastDataLine.set(trimmed);
                    }
                } catch (Throwable ignored) {
                }
            }, "fxShield-typeperf-drain");

            drain.setDaemon(true);
            drain.start();

            boolean finished = p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return -1;
            }

            // ensure drain finishes quickly
            try { drain.join(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

            if (p.exitValue() != 0) return -1;

            String line = lastDataLine.get();
            if (line == null) return -1;

            double max = parseMaxValueFromQuotedCsvLine(line);
            if (!(max >= 0.0)) return -1;

            int v = (int) Math.round(max);
            if (v < 0) v = 0;
            if (v > 100) v = 100;
            return v;

        } catch (Throwable t) {
            if (p != null) {
                try { p.destroyForcibly(); } catch (Throwable ignored) {}
            }
            return -1;
        }
    }

    @Override
    public boolean isAvailable() {
        if (!isWindows()) return false;

        // Fast + safe: don't use "-qx" (it prints huge output and can hang if not drained)
        try {
            ProcessBuilder pb = new ProcessBuilder("typeperf", "/?");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            Process p = pb.start();
            boolean finished = p.waitFor(2, TimeUnit.SECONDS);
            if (!finished) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------- parsing helpers ----------

    private static boolean looksLikeCsv(String line) {
        return line != null && line.indexOf('"') >= 0 && line.indexOf(',') >= 0;
    }

    private static String firstQuotedToken(String line) {
        if (line == null) return null;
        int start = line.indexOf('"');
        if (start < 0) return null;
        int end = line.indexOf('"', start + 1);
        if (end < 0) return null;
        return line.substring(start + 1, end);
    }

    /**
     * Parses a typeperf CSV line:
     * "timestamp","v1","v2",...
     * Returns MAX(v1..vn), ignoring non-numeric values like "N/A".
     */
    private static double parseMaxValueFromQuotedCsvLine(String line) {
        if (line == null) return -1;

        int n = line.length();
        int i = 0;

        int tokenIndex = 0;   // 0 = timestamp, 1.. = values
        double max = -1.0;

        while (i < n) {
            char c = line.charAt(i);
            if (c != '"') { i++; continue; }

            // read quoted token
            int j = i + 1;
            StringBuilder sb = new StringBuilder(32);
            while (j < n) {
                char cj = line.charAt(j);
                if (cj == '"') { j++; break; }
                sb.append(cj);
                j++;
            }

            String token = sb.toString();
            if (tokenIndex >= 1) {
                double d = parseDoubleSafe(token);
                if (d >= 0.0 && d > max) max = d;
            }

            tokenIndex++;

            // move to next token (skip until comma then one char)
            while (j < n && line.charAt(j) != ',') j++;
            if (j < n && line.charAt(j) == ',') j++;
            i = j;
        }

        return max;
    }

    private static double parseDoubleSafe(String s) {
        if (s == null) return -1;
        String t = s.trim();
        if (t.isEmpty()) return -1;

        // common non-numeric
        if ("N/A".equalsIgnoreCase(t)) return -1;

        // locale fix: "12,34" => "12.34" only when '.' not present
        if (t.indexOf(',') >= 0 && t.indexOf('.') < 0) {
            t = t.replace(',', '.');
        }

        // strip spaces
        t = t.replace(" ", "");

        try {
            double d = Double.parseDouble(t);
            if (Double.isNaN(d) || Double.isInfinite(d)) return -1;
            return d;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
