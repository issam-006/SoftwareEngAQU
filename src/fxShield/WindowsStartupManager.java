package fxShield;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Manages Windows startup (HKCU\...\Run).
 * - Idempotent apply/remove
 * - Checks current state
 * - Waits with timeout, captures output
 * - Safe quoting for PowerShell single-quoted strings
 */
public final class WindowsStartupManager {

    private static final String RUN_KEY = "HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String APP_NAME = "FxShield";
    private static final long PS_TIMEOUT_SEC = 4;

    private WindowsStartupManager() {}

    public static void apply(boolean enable) {
        if (!isWindows()) return;
        String cmd = startupCommand();
        if (cmd == null || cmd.isBlank()) return;

        if (enable) enable(cmd);
        else disable();
    }

    public static boolean isEnabled() {
        if (!isWindows()) return false;
        String ps = "$p='" + RUN_KEY + "';$n='" + APP_NAME + "';" +
                "try{(Get-ItemProperty -Path $p -Name $n -ErrorAction Stop).$n}catch{''}";
        String out = runPs(ps);
        return out != null && !out.trim().isEmpty();
    }

    public static String currentCommand() {
        if (!isWindows()) return null;
        String ps = "$p='" + RUN_KEY + "';$n='" + APP_NAME + "';" +
                "try{(Get-ItemProperty -Path $p -Name $n -ErrorAction Stop).$n}catch{''}";
        String out = runPs(ps);
        return (out == null || out.trim().isEmpty()) ? null : out.trim();
    }

    private static void enable(String cmd) {
        String value = escapeForPs(cmd);
        String ps =
                "$p='" + RUN_KEY + "';$n='" + APP_NAME + "';$v='" + value + "';" +
                        "New-Item -Path $p -Force | Out-Null;" +
                        "$cur=(Get-ItemProperty -Path $p -Name $n -ErrorAction SilentlyContinue).$n;" +
                        "if($cur -ne $v){New-ItemProperty -Path $p -Name $n -Value $v -PropertyType String -Force | Out-Null}";
        runPs(ps);
    }

    private static void disable() {
        String ps = "$p='" + RUN_KEY + "';$n='" + APP_NAME + "';" +
                "Remove-ItemProperty -Path $p -Name $n -ErrorAction SilentlyContinue;";
        runPs(ps);
    }

    // If running from jar: use javaw.exe -jar "<path>"; else quote executable path.
    private static String startupCommand() {
        try {
            // 1. Try ProcessHandle (most accurate for jpackaged EXE)
            var info = ProcessHandle.current().info();
            if (info.command().isPresent()) {
                String cmd = info.command().get();
                if (cmd.endsWith(".exe")) {
                    return "\"" + cmd + "\" --minimized";
                }
            }

            // 2. Fallback to code source
            File loc = new File(WindowsStartupManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            String path = loc.getAbsolutePath();
            if (path.toLowerCase().endsWith(".jar")) {
                String javaw = Paths.get(System.getProperty("java.home", ""),
                        "bin", "javaw.exe").toFile().exists()
                        ? Paths.get(System.getProperty("java.home", ""), "bin", "javaw.exe").toString()
                        : "javaw";
                return "\"" + javaw + "\" -jar \"" + path + "\"";
            }
            return "\"" + path + "\"";
        } catch (Exception e) {
            return null;
        }
    }

    private static String runPs(String script) {
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

    private static String escapeForPs(String s) {
        return s.replace("'", "''");
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }
}