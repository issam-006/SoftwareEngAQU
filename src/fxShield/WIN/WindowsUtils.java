package fxShield.WIN;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Windows-specific utilities for PowerShell execution and Windows detection.
 * - Robust PowerShell runner (no deadlocks, supports long scripts)
 * - Reliable elevation (RunAs)
 * - Startup registry management
 */
public final class WindowsUtils {

    private static final Logger logger = LoggerFactory.getLogger(WindowsUtils.class);

    // Registry constants for startup management
    private static final String RUN_KEY = "HKCU:\\\\Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Run";
    private static final String APP_NAME = "FxShield";

    // PowerShell defaults
    private static final Duration DEFAULT_PS_TIMEOUT = Duration.ofSeconds(30);

    private WindowsUtils() {}

    // =========================================================================
    // Result
    // =========================================================================

    public static final class PsResult {
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

    // =========================================================================
    // System Detection
    // =========================================================================

    public static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    /**
     * More reliable than "net session" on some systems.
     */
    public static boolean isAdmin() {
        if (!isWindows()) return true;

        String ps = "([Security.Principal.WindowsPrincipal] " +
                "[Security.Principal.WindowsIdentity]::GetCurrent())" +
                ".IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)";

        String out = runPowerShellCapture(ps, 3);
        if (out == null) return false;
        return out.trim().equalsIgnoreCase("true");
    }

    // =========================================================================
    // PowerShell quoting / encoding
    // =========================================================================

    /**
     * Escapes for PowerShell single-quoted string literal.
     */
    public static String escapeForPowerShell(String s) {
        if (s == null) return "";
        return s.replace("'", "''");
    }

    private static String psSingleQuote(String s) {
        return "'" + escapeForPowerShell(s) + "'";
    }

    private static String getPowerShellExe() {
        String sysRoot = System.getenv("SystemRoot");
        if (sysRoot != null && !sysRoot.isBlank()) {
            File f = new File(sysRoot, "System32\\WindowsPowerShell\\v1.0\\powershell.exe");
            if (f.exists()) return f.getAbsolutePath();
        }
        return "powershell.exe";
    }

    /**
     * Wrap script to force UTF-8 console output (prevents garbled text).
     */
    private static String wrapUtf8(String script) {
        String s = script == null ? "" : script;
        return ""
                + "$ProgressPreference='SilentlyContinue'\n"
                + "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8\n"
                + "$OutputEncoding=[Console]::OutputEncoding\n"
                + s;
    }

    private static String toEncodedCommand(String script) {
        byte[] utf16 = wrapUtf8(script).getBytes(StandardCharsets.UTF_16LE);
        return Base64.getEncoder().encodeToString(utf16);
    }

    private static ProcessBuilder psEncoded(String script) {
        String exe = getPowerShellExe();
        String encoded = toEncodedCommand(script);

        ProcessBuilder pb = new ProcessBuilder(
                exe,
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encoded
        );
        pb.redirectErrorStream(false);
        return pb;
    }

    // =========================================================================
    // Admin Elevation
    // =========================================================================

    public static void requestAdminAndExit() {
        try {
            CommandParts parts = getCurrentProcessCommandParts();
            if (parts == null || parts.executable == null || parts.executable.isBlank()) {
                showPermissionErrorDialog();
                return;
            }

            String elevatePs = buildElevationScript(parts);
            logger.info("Requesting elevation for: {}", parts.executable);

            PsResult r = runPowerShellInternal(elevatePs, Duration.ofSeconds(8), false);
            if (!r.success) {
                logger.error("Elevation failed. exit={} stdout={} stderr={}", r.exitCode, r.stdout, r.stderr);
                showPermissionErrorDialog();
                return;
            }

            logger.info("Elevation requested. Exiting current process.");
            System.exit(0);

        } catch (Exception e) {
            logger.error("Failed to request admin and exit", e);
            showPermissionErrorDialog();
        }
    }

    private static String buildElevationScript(CommandParts parts) {
        // Start-Process -FilePath 'exe' -ArgumentList @('a','b') -Verb RunAs
        StringBuilder sb = new StringBuilder();
        sb.append("Start-Process -FilePath ").append(psSingleQuote(parts.executable)).append(" ");

        if (parts.arguments != null && parts.arguments.length > 0) {
            sb.append("-ArgumentList @(");
            for (int i = 0; i < parts.arguments.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(psSingleQuote(parts.arguments[i]));
            }
            sb.append(") ");
        }

        sb.append("-Verb RunAs -WindowStyle Normal");
        return sb.toString();
    }

    private static void showPermissionErrorDialog() {
        try {
            JOptionPane.showMessageDialog(
                    null,
                    "Failed to request Administrator permission.\nPlease try running the app manually as Administrator.",
                    "Fx Shield - Permission Error",
                    JOptionPane.WARNING_MESSAGE
            );
        } catch (Exception ignored) {}
    }

    /**
     * Best-effort: get executable + arguments from ProcessHandle.
     */
    private static CommandParts getCurrentProcessCommandParts() {
        try {
            ProcessHandle.Info info = ProcessHandle.current().info();

            String exe = null;
            if (info.command().isPresent()) exe = info.command().get();

            String[] args = null;
            if (info.arguments().isPresent()) args = info.arguments().get();

            // If we only have commandLine (rare), fallback to simple parse
            if ((exe == null || exe.isBlank()) && info.commandLine().isPresent()) {
                return parseCommandLine(info.commandLine().get());
            }

            // Jar case: if we're running java/javaw, we still want to re-run same args
            if (exe == null || exe.isBlank()) {
                return null;
            }

            CommandParts parts = new CommandParts();
            parts.executable = exe;
            parts.arguments = (args != null) ? args : new String[0];
            return parts;

        } catch (Exception e) {
            logger.warn("Failed to read current process command parts", e);
            return parseCommandLine(fallbackCommandLine());
        }
    }

    private static String fallbackCommandLine() {
        try {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "javaw.exe";
            if (!new File(javaBin).exists()) javaBin = javaHome + File.separator + "bin" + File.separator + "java.exe";

            File loc = new File(WindowsUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String path = loc.getAbsolutePath();

            if (path.endsWith(".jar")) {
                return "\"" + javaBin + "\" -jar \"" + path + "\"";
            }
            if (path.endsWith(".exe")) {
                return "\"" + path + "\"";
            }

            String cp = System.getProperty("java.class.path");
            String mainClass = "fxShield.UX.DashBoardPage";
            return "\"" + javaBin + "\" -cp \"" + cp + "\" " + mainClass;
        } catch (Exception e) {
            return null;
        }
    }

    private static CommandParts parseCommandLine(String cmdLine) {
        if (cmdLine == null || cmdLine.isBlank()) return null;

        // Minimal parser: extract first token as executable, keep rest as one arg-string array
        String s = cmdLine.trim();
        String exe;
        String rest;

        if (s.startsWith("\"")) {
            int q = s.indexOf("\"", 1);
            if (q < 0) return null;
            exe = s.substring(1, q);
            rest = s.substring(q + 1).trim();
        } else {
            int sp = s.indexOf(' ');
            if (sp < 0) {
                exe = s;
                rest = "";
            } else {
                exe = s.substring(0, sp);
                rest = s.substring(sp + 1).trim();
            }
        }

        CommandParts p = new CommandParts();
        p.executable = exe;

        if (rest.isBlank()) {
            p.arguments = new String[0];
        } else {
            // Not perfect, but good fallback: treat remaining as one arg chunk
            p.arguments = new String[] { rest };
        }
        return p;
    }

    // =========================================================================
    // Startup Management
    // =========================================================================

    public static void applyStartup(boolean enable) {
        if (!isWindows()) return;

        String cmd = startupCommand();
        if (cmd == null || cmd.isBlank()) return;

        if (enable) enableStartup(cmd);
        else disableStartup();
    }

    public static boolean isStartupEnabled() {
        if (!isWindows()) return false;
        String ps = "$p='" + RUN_KEY + "';$n='" + APP_NAME + "';" +
                "try{(Get-ItemProperty -Path $p -Name $n -ErrorAction Stop).$n}catch{''}";
        String out = runPowerShellCapture(ps, 5);
        return out != null && !out.trim().isEmpty();
    }

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
        runPowerShellSilent(ps, 6);
    }

    private static void disableStartup() {
        String ps = "$p='" + RUN_KEY + "';$n='" + APP_NAME + "';" +
                "Remove-ItemProperty -Path $p -Name $n -ErrorAction SilentlyContinue;";
        runPowerShellSilent(ps, 6);
    }

    private static String startupCommand() {
        CommandParts parts = getCurrentProcessCommandParts();
        if (parts == null || parts.executable == null || parts.executable.isBlank()) return null;

        // Build a normal command line string for registry
        String base = quoteCmdArg(parts.executable);
        StringBuilder sb = new StringBuilder(base);

        if (parts.arguments != null) {
            for (int i = 0; i < parts.arguments.length; i++) {
                sb.append(" ").append(quoteCmdArg(parts.arguments[i]));
            }
        }

        String cmd = sb.toString();
        if (!cmd.contains("--minimized")) cmd += " --minimized";
        return cmd;
    }

    private static String quoteCmdArg(String a) {
        if (a == null) return "\"\"";
        String s = a;
        boolean needs = s.contains(" ") || s.contains("\t") || s.contains("\"");
        if (!needs) return s;
        s = s.replace("\"", "\\\"");
        return "\"" + s + "\"";
    }

    // =========================================================================
    // PowerShell Execution (Robust)
    // =========================================================================

    /**
     * Logged runner: reads stdout/stderr concurrently (no deadlock).
     * NOTE: Avoid calling from FX thread.
     */
    public static PsResult runPowerShellLogged(String script, String logTag) {
        if (!isWindows()) return new PsResult(-1, "", "", false);
        if (Platform.isFxApplicationThread()) return new PsResult(-1, "", "FX_THREAD_VIOLATION", false);

        String tag = (logTag == null) ? "" : logTag;
        return runPowerShellInternal(script, DEFAULT_PS_TIMEOUT, true, tag);
    }

    public static void runPowerShellSilent(String script, long timeoutSec) {
        if (!isWindows()) return;
        Duration t = Duration.ofSeconds(Math.max(1, timeoutSec));
        runPowerShellInternal(script, t, false);
    }

    public static String runPowerShellCapture(String script, long timeoutSec) {
        if (!isWindows()) return "";
        Duration t = Duration.ofSeconds(Math.max(1, timeoutSec));
        PsResult r = runPowerShellInternal(script, t, false);
        if (r == null) return "";
        return (r.stdout + r.stderr);
    }

    private static PsResult runPowerShellInternal(String script, Duration timeout, boolean logLines) {
        return runPowerShellInternal(script, timeout, logLines, "");
    }

    private static PsResult runPowerShellInternal(String script, Duration timeout, boolean logLines, String tag) {
        Process p = null;

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();

        Thread tOut = null;
        Thread tErr = null;

        try {
            ProcessBuilder pb = psEncoded(script);
            p = pb.start();

            final Process proc = p;

            tOut = new Thread(new Gobbler(proc.getInputStream(), out, logLines ? (tag + " ") : null), "fxShield-ps-out");
            tErr = new Thread(new Gobbler(proc.getErrorStream(), err, logLines ? (tag + " [ERR] ") : null), "fxShield-ps-err");

            tOut.setDaemon(true);
            tErr.setDaemon(true);

            tOut.start();
            tErr.start();

            boolean finished = proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                safeDestroy(proc);
                joinQuiet(tOut, 1200);
                joinQuiet(tErr, 1200);
                return new PsResult(-1, out.toString(), err.toString(), true);
            }

            joinQuiet(tOut, 1200);
            joinQuiet(tErr, 1200);

            int code = proc.exitValue();
            return new PsResult(code, out.toString(), err.toString(), false);

        } catch (Exception ex) {
            logger.error("PowerShell execution failed", ex);
            if (p != null) safeDestroy(p);
            joinQuiet(tOut, 800);
            joinQuiet(tErr, 800);
            return new PsResult(-1, out.toString(), err.toString(), false);
        }
    }

    private static void safeDestroy(Process p) {
        try { p.destroy(); } catch (Exception ignored) {}
        try {
            if (!p.waitFor(400, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                p.waitFor(400, TimeUnit.MILLISECONDS);
            }
        } catch (Exception ignored) {
            try { p.destroyForcibly(); } catch (Exception ignored2) {}
        }
    }

    private static void joinQuiet(Thread t, long ms) {
        if (t == null) return;
        try { t.join(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private static final class Gobbler implements Runnable {
        private final InputStream is;
        private final StringBuilder sink;
        private final String prefix;

        Gobbler(InputStream is, StringBuilder sink, String prefix) {
            this.is = is;
            this.sink = sink;
            this.prefix = prefix;
        }

        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    synchronized (sink) {
                        sink.append(line).append('\n');
                    }
                    if (prefix != null) {
                        logger.info("{}{}", prefix, line);
                    }
                }
            } catch (Exception ignored) {
                // Normal during process kill
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static final class CommandParts {
        String executable;
        String[] arguments;
    }
}
