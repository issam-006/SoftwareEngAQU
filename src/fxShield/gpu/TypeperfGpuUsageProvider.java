package fxShield.gpu;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class TypeperfGpuUsageProvider implements GpuUsageProvider {

    private static final String COUNTER = "\\\\GPU Engine(*)\\\\Utilization Percentage";
    private static final int SAMPLES = 2;
    private static final long TIMEOUT_SECONDS = 3;

    @Override
    public int readGpuUsagePercent() {
        try {
            ProcessBuilder pb = new ProcessBuilder("typeperf", COUNTER, "-sc", String.valueOf(SAMPLES));
            pb.redirectErrorStream(true);
            Process p = pb.start();

            String lastDataLine = null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.contains("\"") || !trimmed.contains(",")) continue;
                    // Skip header lines like: "Time","\\GPU Engine(...)","..."
                    String firstToken = firstQuotedToken(trimmed);
                    if ("Time".equalsIgnoreCase(firstToken)) continue;
                    lastDataLine = trimmed;
                }
            }

            boolean finished = p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return -1;
            }
            if (p.exitValue() != 0) return -1;
            if (lastDataLine == null) return -1;

            List<String> tokens = quotedTokens(lastDataLine);
            if (tokens.size() <= 1) return -1; // index 0 is timestamp

            double max = -1.0;
            for (int i = 1; i < tokens.size(); i++) {
                String s = tokens.get(i).trim();
                if (s.isEmpty()) continue;
                // Handle locales where decimal separator is comma
                String normalized = s.replace(',', '.');
                try {
                    double d = Double.parseDouble(normalized);
                    if (d > max) max = d;
                } catch (NumberFormatException ignored) { }
            }
            if (max < 0) return -1;

            int v = (int) Math.round(max);
            if (v < 0) v = 0;
            if (v > 100) v = 100;
            return v;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("typeperf", "-qx");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(2, TimeUnit.SECONDS);
            if (!finished) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String firstQuotedToken(String line) {
        int start = line.indexOf('"');
        if (start < 0) return "";
        int end = line.indexOf('"', start + 1);
        if (end < 0) return "";
        return line.substring(start + 1, end);
    }

    private static List<String> quotedTokens(String line) {
        List<String> out = new ArrayList<>();
        int i = 0, n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            if (c == '"') {
                int j = i + 1;
                StringBuilder sb = new StringBuilder();
                while (j < n) {
                    char cj = line.charAt(j);
                    if (cj == '"') { j++; break; }
                    sb.append(cj);
                    j++;
                }
                out.add(sb.toString());
                // advance to next comma (if present)
                while (j < n && line.charAt(j) != ',') j++;
                if (j < n && line.charAt(j) == ',') j++;
                i = j;
            } else {
                i++;
            }
        }
        return out;
    }
}