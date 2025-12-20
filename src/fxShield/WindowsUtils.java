package fxShield;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Base class for Windows-specific utilities.
 * Provides common functionality for PowerShell execution and Windows detection.
 * Subclasses can extend this to implement specific Windows operations.
 */
public abstract class WindowsUtils {

    protected static final long PS_TIMEOUT_SEC = 4;

    /**
     * Private constructor to prevent direct instantiation.
     * Subclasses should also use private constructors for utility classes.
     */
    protected WindowsUtils() {}

    /**
     * Checks if the current operating system is Windows.
     * @return true if running on Windows, false otherwise
     */
    protected static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    /**
     * Executes a PowerShell script and returns the output.
     * @param script the PowerShell script to execute
     * @return the output of the script, or null if execution failed
     */
    protected static String runPowerShell(String script) {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-Command", script
            );
            pb.redirectErrorStream(true);
            p = pb.start();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = p.getInputStream()) {
                in.transferTo(baos);
            }
            boolean finished = p.waitFor(PS_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return null;
            }
            return baos.toString(StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            if (p != null) try { p.destroyForcibly(); } catch (Exception ignored2) {}
            return null;
        }
    }

    /**
     * Escapes a string for use in PowerShell single-quoted strings.
     * @param s the string to escape
     * @return the escaped string
     */
    protected static String escapeForPowerShell(String s) {
        return s.replace("'", "''");
    }
}
