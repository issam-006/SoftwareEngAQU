package fxShield.WIN;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * Schedules background automations (free RAM / optimize disk).
 * - Daemon single-thread scheduler
 * - Idempotent apply (no restart if unchanged)
 * - PowerShell execution with timeout and safe drain
 * - Graceful stop and shutdown hook
 */
public final class AutomationService implements AutoCloseable {

    // Singleton
    private static final AutomationService INSTANCE = new AutomationService();
    public static AutomationService get() { return INSTANCE; }

    // Scheduling
    private static final long FREE_RAM_INITIAL_DELAY_SEC = 30;
    private static final long FREE_RAM_PERIOD_SEC = 10 * 60;
    private static final long DISK_INITIAL_DELAY_SEC = 60;
    private static final long DISK_PERIOD_SEC = 30 * 60;

    // PowerShell
    private static final Duration POWERSHELL_TIMEOUT = Duration.ofSeconds(30);

    private volatile ScheduledExecutorService exec;
    private volatile FxSettings lastApplied;

    private AutomationService() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "fxShield-automation-shutdown"));
    }

    // Apply settings idempotently
    public synchronized void apply(FxSettings settings) {
        FxSettings s = (settings != null) ? settings : FxSettings.defaults();
        if (equalsLast(s)) return;

        stop();
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fxShield-automation");
            t.setDaemon(true);
            return t;
        });

        if (s.autoFreeRam) {
            exec.scheduleAtFixedRate(this::runFreeRam, FREE_RAM_INITIAL_DELAY_SEC, FREE_RAM_PERIOD_SEC, TimeUnit.SECONDS);
        }
        if (s.autoOptimizeHardDisk) {
            exec.scheduleAtFixedRate(this::runOptimizeDisk, DISK_INITIAL_DELAY_SEC, DISK_PERIOD_SEC, TimeUnit.SECONDS);
        }
        // Optional OS integration
        try { WindowsUtils.applyStartup(s.autoStartWithWindows); } catch (Throwable ignored) {}

        lastApplied = new FxSettings(s);
    }

    public synchronized void stop() {
        if (exec != null) {
            exec.shutdownNow();
            try { exec.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exec = null;
        }
    }

    @Override
    public void close() { stop(); }

    // ===== Tasks =====
    private void runFreeRam() {
        String ps =
                "$ErrorActionPreference='SilentlyContinue';" +
                        "Remove-Item -Path \"$env:TEMP\\*\" -Recurse -Force -ErrorAction SilentlyContinue;" +
                        "Remove-Item -Path \"$env:WINDIR\\Prefetch\\*\" -Recurse -Force -ErrorAction SilentlyContinue;" +
                        "Remove-Item -Path \"$env:APPDATA\\Microsoft\\Windows\\Recent\\*\" -Recurse -Force -ErrorAction SilentlyContinue;";
        runPowerShell(ps);
    }

    private void runOptimizeDisk() {
        String ps =
                "$ErrorActionPreference='SilentlyContinue';" +
                        "cleanmgr /verylowdisk | Out-Null;";
        runPowerShell(ps);
    }

    // ===== PowerShell helper =====
    private void runPowerShell(String script) {
        WindowsUtils.runPowerShellSilent(script, 30);
    }

    private boolean equalsLast(FxSettings s) {
        FxSettings last = lastApplied;
        return last != null
                && last.autoFreeRam == s.autoFreeRam
                && last.autoOptimizeHardDisk == s.autoOptimizeHardDisk
                && last.autoStartWithWindows == s.autoStartWithWindows;
    }
}