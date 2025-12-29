package fxShield.WIN;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Schedules background automations (free RAM / optimize disk).
 * - Daemon single-thread scheduler
 * - Idempotent apply (no restart if unchanged)
 * - PowerShell execution with timeout (delegated to WindowsUtils)
 * - Tasks are exception-safe (won't stop silently)
 */
public final class AutomationService implements AutoCloseable {

    // Singleton
    private static final AutomationService INSTANCE = new AutomationService();
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

    public static AutomationService get() {
        return INSTANCE;
    }

    public synchronized void apply(FxSettings settings) {
        FxSettings s = (settings != null) ? settings : FxSettings.defaults();
        if (equalsLast(s)) return;

        stop();

        if (!WindowsUtils.isWindows()) {
            // Still persist lastApplied so we don't loop on apply()
            lastApplied = new FxSettings(s);
            return;
        }

        boolean needScheduler = s.autoFreeRam || s.autoOptimizeHardDisk;
        if (needScheduler) {
            exec = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "fxShield-automation");
                t.setDaemon(true);
                return t;
            });

            if (s.autoFreeRam) {
                exec.scheduleWithFixedDelay(
                        this::safeRunFreeRam,
                        FREE_RAM_INITIAL_DELAY_SEC,
                        FREE_RAM_PERIOD_SEC,
                        TimeUnit.SECONDS
                );
            }

            if (s.autoOptimizeHardDisk) {
                exec.scheduleWithFixedDelay(
                        this::safeRunOptimizeDisk,
                        DISK_INITIAL_DELAY_SEC,
                        DISK_PERIOD_SEC,
                        TimeUnit.SECONDS
                );
            }
        }

        // Optional OS integration
        try {
            WindowsUtils.applyStartup(s.autoStartWithWindows);
        } catch (Throwable ignored) {
        }

        lastApplied = new FxSettings(s);
    }

    public synchronized void stop() {
        ScheduledExecutorService e = exec;
        exec = null;

        if (e != null) {
            e.shutdownNow();
            try {
                e.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    // ===== Exception-safe wrappers =====

    private void safeRunFreeRam() {
        try {
            runFreeRam();
        } catch (Throwable ignored) {
        }
    }

    private void safeRunOptimizeDisk() {
        try {
            runOptimizeDisk();
        } catch (Throwable ignored) {
        }
    }

    // ===== Tasks =====

    private void runFreeRam() {
        String ps =
                "$ErrorActionPreference='SilentlyContinue'\n" +
                        "Remove-Item -Path \"$env:TEMP\\*\" -Recurse -Force -ErrorAction SilentlyContinue\n" +
                        "Remove-Item -Path \"$env:WINDIR\\Temp\\*\" -Recurse -Force -ErrorAction SilentlyContinue\n" +
                        "Remove-Item -Path \"$env:WINDIR\\Prefetch\\*\" -Recurse -Force -ErrorAction SilentlyContinue\n" +
                        "Remove-Item -Path \"$env:APPDATA\\Microsoft\\Windows\\Recent\\*\" -Recurse -Force -ErrorAction SilentlyContinue\n";
        runPowerShell(ps);
    }

    private void runOptimizeDisk() {
        // cleanmgr is legacy لكنه آمن وبسيط. تشغيله كـ Start-Process يقلل التعليق.
        String ps =
                "$ErrorActionPreference='SilentlyContinue'\n" +
                        "Start-Process -FilePath 'cleanmgr.exe' -ArgumentList '/VERYLOWDISK' -WindowStyle Hidden -Wait | Out-Null\n";
        runPowerShell(ps);
    }

    // ===== PowerShell helper =====

    private void runPowerShell(String script) {
        long sec = Math.max(1, POWERSHELL_TIMEOUT.toSeconds());
        WindowsUtils.runPowerShellSilent(script, sec);
    }

    private boolean equalsLast(FxSettings s) {
        FxSettings last = lastApplied;
        return last != null
                && last.autoFreeRam == s.autoFreeRam
                && last.autoOptimizeHardDisk == s.autoOptimizeHardDisk
                && last.autoStartWithWindows == s.autoStartWithWindows;
    }
}
