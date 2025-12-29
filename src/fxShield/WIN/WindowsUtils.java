package fxShield.WIN;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Unified Windows and JavaFX Stage utilities.
 * Merged from WindowsUtils, WindowsWindowStyler, and StageUtil.
 */
public final class WindowsUtils {

    private static final Logger logger = LoggerFactory.getLogger(WindowsUtils.class);

    // Registry constants for startup management
    private static final String RUN_KEY = "HKCU:\\\\Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Run";
    private static final String APP_NAME = "FxShield";

    // PowerShell defaults
    private static final Duration DEFAULT_PS_TIMEOUT = Duration.ofSeconds(30);

    // DWM Attributes for window styling
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_CAPTION_COLOR = 35;
    private static final int DWMWA_TEXT_COLOR = 36;
    private static final int DWMWA_BORDER_COLOR = 34;

    private WindowsUtils() {}

    // =========================================================================
    // Native Interfaces
    // =========================================================================

    private interface Dwmapi extends StdCallLibrary {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class);
        int DwmSetWindowAttribute(HWND hwnd, int dwAttribute, Pointer pvAttribute, int cbAttribute);
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);
        HWND FindWindowW(String lpClassName, String lpWindowName);
        boolean IsWindow(HWND hWnd);
    }

    // =========================================================================
    // Public Inner Classes
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

    public static final class BlurGuard implements AutoCloseable {
        private final Stage owner;
        private final Effect previous;
        private boolean closed;

        private BlurGuard(Stage owner, Effect previous) {
            this.owner = owner;
            this.previous = previous;
        }

        static BlurGuard noop() {
            return new BlurGuard(null, null);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;

            if (owner == null || owner.getScene() == null || owner.getScene().getRoot() == null) return;
            runFx(() -> owner.getScene().getRoot().setEffect(previous));
        }
    }

    // =========================================================================
    // JavaFX Stage Utilities (formerly StageUtil)
    // =========================================================================

    public static Optional<Stage> findOwner(Node anyNodeInsideStage) {
        if (anyNodeInsideStage == null || anyNodeInsideStage.getScene() == null) return Optional.empty();
        var w = anyNodeInsideStage.getScene().getWindow();
        return (w instanceof Stage s) ? Optional.of(s) : Optional.empty();
    }

    public static void withOwner(Node anyNodeInsideStage, Consumer<Stage> consumer) {
        if (consumer == null) return;
        findOwner(anyNodeInsideStage).ifPresent(consumer);
    }

    public static <T> T mapOwner(Node node, Function<Stage, T> fn, T fallback) {
        if (fn == null) return fallback;
        return findOwner(node).map(fn).orElse(fallback);
    }

    public static void centerOnOwner(Stage child, Stage owner) {
        if (child == null || owner == null) return;

        // Ensure sizes are computed
        if (child.getWidth() <= 1 || child.getHeight() <= 1) {
            child.sizeToScene();
        }

        double x = owner.getX() + (owner.getWidth() - child.getWidth()) / 2.0;
        double y = owner.getY() + (owner.getHeight() - child.getHeight()) / 2.0;

        child.setX(x);
        child.setY(y);

        clampToScreen(child);
    }

    public static void clampToScreen(Stage stage) {
        if (stage == null) return;

        Rectangle2D vb = Screen.getScreensForRectangle(
                        stage.getX(), stage.getY(),
                        Math.max(1, stage.getWidth()), Math.max(1, stage.getHeight()))
                .stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();

        double x = stage.getX();
        double y = stage.getY();
        double w = stage.getWidth();
        double h = stage.getHeight();

        // If the stage is bigger than the visible area, shrink it (actual stage size)
        if (w > vb.getWidth()) {
            w = vb.getWidth();
            stage.setWidth(w);
        }
        if (h > vb.getHeight()) {
            h = vb.getHeight();
            stage.setHeight(h);
        }

        if (x < vb.getMinX()) x = vb.getMinX();
        if (y < vb.getMinY()) y = vb.getMinY();
        if (x + w > vb.getMaxX()) x = vb.getMaxX() - w;
        if (y + h > vb.getMaxY()) y = vb.getMaxY() - h;

        stage.setX(x);
        stage.setY(y);
    }

    public static void showModalOver(Node anchor, Stage dialog) {
        Objects.requireNonNull(dialog, "dialog");

        withOwner(anchor, owner -> {
            dialog.initOwner(owner);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.sizeToScene();
            centerOnOwner(dialog, owner);
            dialog.show();
        });
    }

    public static BlurGuard applyBlur(Stage owner, double radius) {
        if (owner == null || owner.getScene() == null || owner.getScene().getRoot() == null) {
            return BlurGuard.noop();
        }

        var root = owner.getScene().getRoot();
        Effect prev = root.getEffect();

        runFx(() -> root.setEffect(new GaussianBlur(Math.max(0, radius))));
        return new BlurGuard(owner, prev);
    }

    public static void runFx(Runnable r) {
        if (r == null) return;
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    // =========================================================================
    // Windows Styling (formerly WindowsWindowStyler)
    // =========================================================================

    /**
     * Styles the stage with dark mode and custom caption color.
     */
    public static void styleStage(Stage stage) {
        if (!isWindows()) return;

        // Give it a moment to show and set title if called immediately after show()
        String title = stage.getTitle();
        if (title == null || title.isEmpty()) return;

        HWND hwnd = User32.INSTANCE.FindWindowW(null, title);
        if (hwnd == null) return;

        // 1. Enable Immersive Dark Mode
        setDwmAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, 1);

        // 2. Set Caption Color (BGR format: 0x00BBGGRR)
        // App top color is #020617 -> R=02, G=06, B=17
        // BGR -> 0x170602
        setDwmAttribute(hwnd, DWMWA_CAPTION_COLOR, 0x170602);

        // 3. Set Text Color (White)
        setDwmAttribute(hwnd, DWMWA_TEXT_COLOR, 0xFFFFFF);

        // 4. Set Border Color
        setDwmAttribute(hwnd, DWMWA_BORDER_COLOR, 0x170602);
    }

    private static void setDwmAttribute(HWND hwnd, int attribute, int value) {
        try {
            Memory pValue = new Memory(4);
            pValue.setInt(0, value);
            Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, pValue, (int) pValue.size());
        } catch (Throwable ignored) {
            // DWM attributes might not be supported on older Windows versions
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
    // PowerShell Utilities
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

    private static CommandParts getCurrentProcessCommandParts() {
        try {
            ProcessHandle.Info info = ProcessHandle.current().info();
            String exe = info.command().orElse(null);
            String[] args = info.arguments().orElse(null);

            if ((exe == null || exe.isBlank()) && info.commandLine().isPresent()) {
                return parseCommandLine(info.commandLine().get());
            }

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
        p.arguments = rest.isBlank() ? new String[0] : new String[] { rest };
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

        String base = quoteCmdArg(parts.executable);
        StringBuilder sb = new StringBuilder(base);
        if (parts.arguments != null) {
            for (String argument : parts.arguments) {
                sb.append(" ").append(quoteCmdArg(argument));
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
    // Private Helpers
    // =========================================================================

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
            } catch (Exception ignored) {}
        }
    }

    private static final class CommandParts {
        String executable;
        String[] arguments;
    }
}
