package fxShield.WIN;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.io.File;
import javax.swing.JOptionPane;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for Windows-specific utilities.
 * Provides common functionality for PowerShell execution and Windows detection.
 */
public final class WindowsUtils {

    private static final Logger logger = LoggerFactory.getLogger(WindowsUtils.class);


    /**
     * Private constructor to prevent direct instantiation.
     */
    private WindowsUtils() {}

    /**
     * Result of a PowerShell command execution.
     */
    public static class PsResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean timedOut;
        public final boolean success;

        public PsResult(int exitCode, String stdout, String stderr, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout != null ? stdout : "";
            this.stderr = stderr != null ? stderr : "";
            this.timedOut = timedOut;
            this.success = !timedOut && exitCode == 0;
        }
    }

    /**
     * Checks if the current operating system is Windows.
     * @return true if running on Windows, false otherwise
     */
    public static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    /**
     * Escapes a string for use in PowerShell single-quoted strings.
     * @param s the string to escape
     * @return the escaped string
     */
    public static String escapeForPowerShell(String s) {
        return s.replace("'", "''");
    }

    /**
     * Checks if the current process has Administrator privileges.
     * Uses 'net session' command which returns 0 only if running as admin.
     */
    public static boolean isAdmin() {
        if (!isWindows()) return true;

        try {
            return new ProcessBuilder("net", "session")
                    .start()
                    .waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Relaunches the application with Administrator privileges using PowerShell 'RunAs' verb.
     * Then exits the current process.
     */
    public static void requestAdminAndExit() {
        try {
            String fullCommand = getFullCommand();
            if (fullCommand == null) return;

            String executable;
            String arguments;

            if (fullCommand.startsWith("\"")) {
                int secondQuote = fullCommand.indexOf("\"", 1);
                if (secondQuote != -1) {
                    executable = fullCommand.substring(1, secondQuote);
                    arguments = fullCommand.substring(secondQuote + 1).trim();
                } else {
                    executable = fullCommand;
                    arguments = "";
                }
            } else {
                int firstSpace = fullCommand.indexOf(" ");
                if (firstSpace == -1) {
                    executable = fullCommand;
                    arguments = "";
                } else {
                    executable = fullCommand.substring(0, firstSpace);
                    arguments = fullCommand.substring(firstSpace + 1).trim();
                }
            }

            String psCommand;
            if (arguments.isEmpty()) {
                psCommand = String.format(
                    "Start-Process -FilePath '%s' -Verb RunAs -WindowStyle Normal",
                    escapeForPowerShell(executable)
                );
            } else {
                psCommand = String.format(
                    "Start-Process -FilePath '%s' -ArgumentList '%s' -Verb RunAs -WindowStyle Normal",
                    escapeForPowerShell(executable),
                    escapeForPowerShell(arguments)
                );
            }

            logger.info("Elevation command: powershell {}", psCommand);
            
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psCommand)
                    .start();
            
            if (p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                if (p.exitValue() != 0) {
                    logger.error("PowerShell failed with exit code: {}", p.exitValue());
                    JOptionPane.showMessageDialog(null, 
                        "Failed to request Administrator permission.\nPlease try running the app manually as Administrator.", 
                        "Fx Shield - Permission Error", 
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            
            logger.info("Elevation requested. Exiting current process.");
            System.exit(0);
        } catch (Exception e) {
            logger.error("Failed to request admin and exit", e);
        }
    }

    private static String getFullCommand() {
        try {
            var info = ProcessHandle.current().info();
            if (info.commandLine().isPresent()) {
                String cmd = info.commandLine().get();
                System.out.println("[Admin] Detected command line: " + cmd);
                if (cmd.endsWith(".exe") && !cmd.contains(File.separator)) {
                    File exeFile = new File(cmd);
                    String absolute = exeFile.getAbsolutePath();
                    System.out.println("[Admin] Resolving relative EXE to: " + absolute);
                    return absolute;
                }
                return cmd;
            } else if (info.command().isPresent()) {
                String cmd = info.command().get();
                System.out.println("[Admin] Detected command: " + cmd);
                return cmd;
            }
        } catch (Exception ignored) {}

        try {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "javaw.exe";
            if (!new File(javaBin).exists()) {
                javaBin = javaHome + File.separator + "bin" + File.separator + "java.exe";
            }

            File loc = new File(WindowsUtils.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            String path = loc.getAbsolutePath();

            if (path.endsWith(".jar")) {
                return "\"" + javaBin + "\" -jar \"" + path + "\"";
            } else if (path.endsWith(".exe")) {
                return "\"" + path + "\"";
            } else {
                String cp = System.getProperty("java.class.path");
                String mainClass = "fxShield.IMPORTANT.DashBoardPage";
                return "\"" + javaBin + "\" -cp \"" + cp + "\" " + mainClass;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static final String RUN_KEY = "HKCU:\\\\Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Run";
    private static final String APP_NAME = "FxShield";

    /**
     * Applies or removes startup registry entry.
     */
    public static void applyStartup(boolean enable) {
        if (!isWindows()) return;
        String cmd = startupCommand();
        if (cmd == null || cmd.isBlank()) return;

        if (enable) enableStartup(cmd);
        else disableStartup();
    }

    /**
     * Checks if startup is enabled.
     */
    public static boolean isStartupEnabled() {
        if (!isWindows()) return false;
        String ps = "$p='" + RUN_KEY + "';$n='" + APP_NAME + "';" +
                "try{(Get-ItemProperty -Path $p -Name $n -ErrorAction Stop).$n}catch{''}";
        String out = runPowerShellCapture(ps, 5);
        return out != null && !out.trim().isEmpty();
    }

    /**
     * Gets current startup command.
     */
    public static String currentStartupCommand() {
        if (!isWindows()) return null;
        String ps = "$p='" + RUN_KEY + "';$n='" + APP_NAME + "';" +
                "try{(Get-ItemProperty -Path $p -Name $n -ErrorAction Stop).$n}catch{''}";
        String out = runPowerShellCapture(ps, 5);
        return (out == null || out.trim().isEmpty()) ? null : out.trim();
    }

    private static void enableStartup(String cmd) {
        String value = escapeForPowerShell(cmd);
        String ps =
                "$p='" + RUN_KEY + "';$n='" + APP_NAME + "';$v='" + value + "';" +
                "New-Item -Path $p -Force | Out-Null;" +
                "$cur=(Get-ItemProperty -Path $p -Name $n -ErrorAction SilentlyContinue).$n;" +
                "if($cur -ne $v){New-ItemProperty -Path $p -Name $n -Value $v -PropertyType String -Force | Out-Null}";
        runPowerShellSilent(ps, 5);
    }

    private static void disableStartup() {
        String ps = "$p='" + RUN_KEY + "';$n='" + APP_NAME + "';" +
                "Remove-ItemProperty -Path $p -Name $n -ErrorAction SilentlyContinue;";
        runPowerShellSilent(ps, 5);
    }

    private static String startupCommand() {
        try {
            var info = ProcessHandle.current().info();
            if (info.commandLine().isPresent()) {
                String cmd = info.commandLine().get().trim();
                if (!cmd.contains("--minimized")) {
                    cmd += " --minimized";
                }
                System.out.println("[Startup] Using commandLine: " + cmd);
                return cmd;
            }
        } catch (Exception ignored) {
            System.out.println("[Startup] commandLine unavailable, fallback");
        }

        String exePath = getAppExePath();
        if (exePath != null && !exePath.isEmpty()) {
            return "\"" + exePath + "\" --minimized";
        }
        return null;
    }

    private static String getAppExePath() {
        if (!isWindows()) return null;
        String ps = "(Get-Process -Id $PID).Path";
        String out = runPowerShellCapture(ps, 3);
        return out != null && !out.trim().isEmpty() ? out.trim() : null;
    }

    /**
     * Runs PowerShell synchronously with live logging of stdout/stderr lines,
     * 30s timeout. Robust concurrent stream reading prevents blocking.
     *
     * @param script PowerShell script
     * @param logTag log prefix (e.g. "[SFC]")
     * @return PsResult with exitCode, outputs, timedOut flag
     */
    public static PsResult runPowerShellLogged(String script, String logTag) {
        if (!isWindows()) {
            return new PsResult(-1, "", "", false);
        }
        if (Platform.isFxApplicationThread()) {
            System.err.println("[CRITICAL] runPowerShellLogged called on FX thread: " + Thread.currentThread().getName());
            return new PsResult(-1, "", "FX_THREAD_VIOLATION", false);
        }
        Process p = null;
        StringBuilder outBuilder = new StringBuilder();
        StringBuilder errBuilder = new StringBuilder();
        ExecutorService es = Executors.newFixedThreadPool(2);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script
            );
            p = pb.start();
            es.submit(new StreamGobbler(p.getInputStream(), logTag + " ", outBuilder));
            es.submit(new StreamGobbler(p.getErrorStream(), logTag + " [ERR] ", errBuilder));
            es.shutdownNow();
            boolean finished = p.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                try {
                    if (p.waitFor(3, TimeUnit.SECONDS)) {
                        return new PsResult(p.exitValue(), outBuilder.toString(), errBuilder.toString(), true);
                    } else {
                        return new PsResult(-1, outBuilder.toString(), errBuilder.toString(), true);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new PsResult(-1, outBuilder.toString(), errBuilder.toString(), true);
                }
            }
            int exitCode = p.exitValue();
            return new PsResult(exitCode, outBuilder.toString(), errBuilder.toString(), false);
        } catch (Exception ex) {
            logger.error("Error in runPowerShellLogged", ex);
            if (p != null) {
                p.destroyForcibly();
            }
            es.shutdownNow();
            return new PsResult(-1, outBuilder.toString(), errBuilder.toString(), false);
        }
    }

    /**
     * Runs PowerShell silently (no console output), discards streams concurrently to prevent blocking, custom timeout.
     * Underlying logic shared with runPowerShellLogged.
     *
     * @param script the PowerShell script
     * @param timeoutSec timeout in seconds
     */
    public static void runPowerShellSilent(String script, long timeoutSec) {
        if (!isWindows()) {
            return;
        }
        Process p = null;
        ExecutorService es = Executors.newFixedThreadPool(2);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script
            );
            p = pb.start();
            es.submit(new NoPrintGobbler(p.getInputStream(), null));
            es.submit(new NoPrintGobbler(p.getErrorStream(), null));
            boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            es.shutdownNow();
            if (!finished) {
                p.destroyForcibly();
                try {
                    p.waitFor(3, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception ex) {
            logger.error("Error in runPowerShellSilent", ex);
            if (p != null) {
                p.destroyForcibly();
            }
        } finally {
            es.shutdownNow();
        }
    }

    /**
     * Runs PowerShell and captures merged stdout+stderr (no console print), custom timeout.
     * Returns full output; empty on error/timeout.
     * Underlying logic shared with runPowerShellLogged.
     *
     * @param script the PowerShell script
     * @param timeoutSec timeout in seconds
     * @return captured output (stdout + stderr)
     */
    public static String runPowerShellCapture(String script, long timeoutSec) {
        if (!isWindows()) {
            return "";
        }
        Process p = null;
        StringBuilder outBuilder = new StringBuilder();
        StringBuilder errBuilder = new StringBuilder();
        ExecutorService es = Executors.newFixedThreadPool(2);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script
            );
            p = pb.start();
            es.submit(new NoPrintGobbler(p.getInputStream(), outBuilder));
            es.submit(new NoPrintGobbler(p.getErrorStream(), errBuilder));
            boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            es.shutdownNow();
            if (!finished) {
                p.destroyForcibly();
                try {
                    p.waitFor(3, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            return outBuilder.toString() + errBuilder.toString();
        } catch (Exception ex) {
            logger.error("Error in runPowerShellCapture", ex);
            if (p != null) {
                p.destroyForcibly();
            }
            return "";
        } finally {
            es.shutdownNow();
        }
    }

    private static class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final String prefix;
        private final StringBuilder builder;

        StreamGobbler(InputStream is, String prefix, StringBuilder builder) {
            this.inputStream = is;
            this.prefix = prefix;
            this.builder = builder;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(prefix + line);
                    synchronized (builder) {
                        builder.append(line).append("\n");
                    }
                }
            } catch (IOException ignored) {
                // Normal on process destroy
            }
        }
    }

    private static class NoPrintGobbler implements Runnable {
        private final InputStream inputStream;
        private final StringBuilder builder;

        NoPrintGobbler(InputStream is, StringBuilder builder) {
            this.inputStream = is;
            this.builder = builder;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (builder != null) {
                        synchronized (builder) {
                            builder.append(line).append("\n");
                        }
                    }
                }
            } catch (IOException ignored) {
                // Normal on process destroy
            }
        }
    }
}
