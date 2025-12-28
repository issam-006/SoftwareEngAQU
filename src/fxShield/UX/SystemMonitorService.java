// FILE: src/fxShield/UX/SystemMonitorService.java
package fxShield.UX;

import fxShield.GPU.GPUStabilizer;
import fxShield.GPU.GpuUsageProvider;
import fxShield.GPU.HybridGpuUsageProvider;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * High-frequency system monitor with low GC and stable readings.
 * Features:
 * - Single daemon scheduler for UI loop
 * - Dedicated GPU sampler thread with stabilizer + median/EMA smoothing
 * - CPU dual-EMA + median filter + deadband to reduce jitter
 * - Bounded PowerShell calls with timeouts and defensive OSHI usage
 * - Clamped outputs 0..100; no blocking in UI loop
 */
public final class SystemMonitorService {

    // =========================================================================
    // Constants and Configuration
    // =========================================================================

    private static final long LOOP_MS = 250;
    private static final long CPU_MS = 500;
    private static final long GPU_MS = 200;
    private static final Duration POWERSHELL_TIMEOUT = Duration.ofSeconds(5);

    // =========================================================================
    // Data Structures
    // =========================================================================

    public interface Listener {
        void onUpdate(double cpuPercent, RamSnapshot ram, PhysicalDiskSnapshot[] disks, int gpuUsage);
    }

    public static class RamSnapshot {
        public double totalGb;
        public double usedGb;
        public double percent;
    }

    public static class PhysicalDiskSnapshot {
        public int index;
        public String model;
        public String typeLabel;
        public double sizeGb;
        public double usedGb;
        public double totalGb;
        public double usedPercent;
        public boolean hasUsage;
        public double activePercent;
    }

    private static final class LogicalUsage {
        double totalGb;
        double usedGb;
    }

    // =========================================================================
    // System Components
    // =========================================================================

    private final SystemInfo si;
    private final HardwareAbstractionLayer hal;
    private final CentralProcessor cpu;
    private final GlobalMemory mem;
    private final OperatingSystem os;
    private final FileSystem fs;
    private final HWDiskStore[] diskStores;
    private final GraphicsCard[] gpus;

    // =========================================================================
    // Monitoring State
    // =========================================================================

    private volatile Listener listener;
    private ScheduledExecutorService exec;

    // CPU sampling state
    private long[] prevCpuTicks;
    private long lastCpuSampleMs = 0L;
    private double lastCpuPercent = 0.0;

    // CPU smoothing (dual EMA + median + deadband)
    private final double cpuAlphaFast = 0.45;
    private final double cpuAlphaSlow = 0.12;
    private double cpuEmaFast = 0;
    private double cpuEmaSlow = 0;
    private boolean cpuEmaInit = false;
    private final double cpuNoiseFloor = 0.3;

    // CPU median window (no streams / no per-tick allocations)
    private final double[] cpuWindow = new double[5];
    private int cpuWinCount = 0;
    private int cpuWinPos = 0;
    private final double[] cpuSortBuf = new double[5];

    // Disk busy sampling
    private final long[] prevTransferTime;
    private final long[] prevDiskTs;
    private final double[] diskBusyEma;
    private volatile boolean disksWarmedUp = false;

    // Disk type cache (must be thread-safe; filled in background thread)
    private final Map<Integer, String> diskTypeByIndex = new ConcurrentHashMap<>();

    // GPU monitoring
    private final boolean isWindows;
    private final GpuUsageProvider gpuProvider;
    private final GPUStabilizer gpuStabilizer = new GPUStabilizer(2000, 0.30, 4, -1);

    private volatile boolean gpuThreadRunning = false;
    private Thread gpuThread;
    private volatile int lastGpuStableForUi = -1;

    // GPU smoothing (median-of-3 + EMA) with fixed buffers
    private final int[] gpuWindow = new int[3];
    private int gpuWinCount = 0;
    private int gpuWinPos = 0;
    private final double gpuAlpha = 0.30;
    private int gpuEma = -1;

    // =========================================================================
    // Constructor and Initialization
    // =========================================================================

    public SystemMonitorService() {
        si = new SystemInfo();
        hal = si.getHardware();
        cpu = hal.getProcessor();
        mem = hal.getMemory();
        os = si.getOperatingSystem();
        fs = os.getFileSystem();

        String fam = Optional.ofNullable(os.getFamily()).orElse("").toLowerCase();
        isWindows = fam.contains("windows");

        List<HWDiskStore> disks = safeList(hal.getDiskStores());
        diskStores = disks.toArray(new HWDiskStore[0]);

        List<GraphicsCard> gpuList = safeList(hal.getGraphicsCards());
        gpus = gpuList.toArray(new GraphicsCard[0]);

        prevCpuTicks = cpu.getSystemCpuLoadTicks();

        prevTransferTime = new long[diskStores.length];
        prevDiskTs = new long[diskStores.length];
        diskBusyEma = new double[diskStores.length];

        long now = System.currentTimeMillis();
        for (int i = 0; i < diskStores.length; i++) {
            try {
                diskStores[i].updateAttributes();
            } catch (Exception ignored) {}
            prevTransferTime[i] = safeLong(diskStores[i].getTransferTime());
            prevDiskTs[i] = now;
            diskBusyEma[i] = 0.0;
        }

        gpuProvider = new HybridGpuUsageProvider(isWindows);
    }

    // =========================================================================
    // Public API Methods
    // =========================================================================

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void start() {
        if (exec != null) return;

        if (isWindows) {
            new Thread(this::loadDiskMediaTypesWindows, "fxShield-disk-detect").start();
        }

        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fxShield-monitor");
            t.setDaemon(true);
            return t;
        });

        exec.schedule(() -> disksWarmedUp = true, 900, TimeUnit.MILLISECONDS);
        startGpuThread();

        exec.scheduleAtFixedRate(() -> {
            try {
                sampleAndNotify();
            } catch (Throwable ignored) {}
        }, 0, LOOP_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        // stop GPU thread first
        gpuThreadRunning = false;
        if (gpuThread != null) gpuThread.interrupt();

        try {
            gpuProvider.close();
        } catch (Exception ignored) {}

        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
    }

    public boolean isGpuUsageSupported() {
        return lastGpuStableForUi >= 0;
    }

    public String getGpuName() {
        if (gpus.length == 0) return "Unknown";
        GraphicsCard g = gpus[0];
        String vendor = Optional.ofNullable(g.getVendor()).orElse("");
        String name = Optional.ofNullable(g.getName()).orElse("");
        String combined = (vendor + " " + name).trim();
        return combined.isBlank() ? "Unknown" : combined;
    }

    public RamSnapshot readRamOnce() {
        return readRamSnapshot();
    }

    public PhysicalDiskSnapshot[] sampleDisksOnce() {
        LogicalUsage lu = readLogicalUsage();
        return readPhysicalSnapshots(lu, System.currentTimeMillis());
    }

    // =========================================================================
    // GPU Monitoring Thread
    // =========================================================================

    private void startGpuThread() {
        if (gpuThreadRunning) return;
        gpuThreadRunning = true;

        gpuThread = new Thread(() -> {
            while (gpuThreadRunning) {
                long now = System.currentTimeMillis();

                int raw = -1;
                try {
                    raw = gpuProvider.readGpuUsagePercent();
                } catch (Throwable ignored) {}

                int stable = gpuStabilizer.update(raw, now);

                // stable may still be >=0 during grace window even when raw fails
                if (stable >= 0) {
                    pushGpuSample(stable);
                    int median = computeGpuMedian();
                    gpuEma = (gpuEma < 0) ? median : clampInt((int) Math.round(gpuEma + gpuAlpha * (median - gpuEma)), 0, 100);
                    lastGpuStableForUi = gpuEma;
                }

                try {
                    Thread.sleep(GPU_MS);
                } catch (InterruptedException ie) {
                    break; // ✅ do not keep interrupt-flag + busy-loop
                }
            }
        }, "fxShield-gpu");

        gpuThread.setDaemon(true);
        gpuThread.start();
    }

    private void pushGpuSample(int v) {
        gpuWindow[gpuWinPos] = clampInt(v, 0, 100);
        gpuWinPos++;
        if (gpuWinPos == gpuWindow.length) gpuWinPos = 0;
        if (gpuWinCount < gpuWindow.length) gpuWinCount++;
    }

    private int computeGpuMedian() {
        if (gpuWinCount <= 0) return -1;
        if (gpuWinCount == 1) return gpuWindow[0];

        if (gpuWinCount == 2) {
            int a = gpuWindow[0];
            int b = gpuWindow[1];
            return (a + b) / 2;
        }

        // median of 3
        int a = gpuWindow[0];
        int b = gpuWindow[1];
        int c = gpuWindow[2];
        if (a > b) { int t = a; a = b; b = t; }
        if (b > c) { int t = b; b = c; c = t; }
        if (a > b) { int t = a; a = b; b = t; }
        return b;
    }

    // =========================================================================
    // Main Monitoring Loop
    // =========================================================================

    private void sampleAndNotify() {
        Listener l = this.listener;
        if (l == null) return;

        long now = System.currentTimeMillis();

        double cpuPct = lastCpuPercent;
        if (lastCpuSampleMs == 0 || now - lastCpuSampleMs >= CPU_MS) {
            double m = readCpuPercent();
            if (m >= 0) {
                lastCpuPercent = m;
                lastCpuSampleMs = now;
            }
            cpuPct = lastCpuPercent;
        }

        RamSnapshot ram = readRamSnapshot();

        PhysicalDiskSnapshot[] disks;
        if (disksWarmedUp) {
            LogicalUsage lu = readLogicalUsage();
            disks = readPhysicalSnapshots(lu, now);
        } else {
            disks = sampleDisksOnce();
            for (PhysicalDiskSnapshot d : disks) d.activePercent = 0;
        }

        int gpuToUi = (lastGpuStableForUi < 0) ? 0 : lastGpuStableForUi;
        l.onUpdate(cpuPct, ram, disks, gpuToUi);
    }

    // =========================================================================
    // CPU Monitoring
    // =========================================================================

    private double readCpuPercent() {
        double load = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks);
        // ✅ update prev ticks AFTER betweenTicks call (avoid pre-call mismatch)
        prevCpuTicks = cpu.getSystemCpuLoadTicks();

        if (load < 0) return -1;

        double pct = clamp01_100(load * 100.0);

        // push to median window (size 5)
        cpuWindow[cpuWinPos] = pct;
        cpuWinPos++;
        if (cpuWinPos == cpuWindow.length) cpuWinPos = 0;
        if (cpuWinCount < cpuWindow.length) cpuWinCount++;

        // median (insertion sort small buffer)
        double median = medianOfCpuWindow();

        // dual EMA
        if (!cpuEmaInit) {
            cpuEmaFast = median;
            cpuEmaSlow = median;
            cpuEmaInit = true;
        } else {
            cpuEmaFast = cpuEmaFast + cpuAlphaFast * (median - cpuEmaFast);
            cpuEmaSlow = cpuEmaSlow + cpuAlphaSlow * (median - cpuEmaSlow);
        }

        double fused = 0.65 * cpuEmaFast + 0.35 * cpuEmaSlow;

        // deadband
        if (Math.abs(fused - lastCpuPercent) < cpuNoiseFloor) return lastCpuPercent;

        return clamp01_100(fused);
    }

    private double medianOfCpuWindow() {
        int n = cpuWinCount;
        if (n <= 0) return 0;

        // when not full yet, values are only in [0..n-1]
        System.arraycopy(cpuWindow, 0, cpuSortBuf, 0, n);

        // insertion sort
        for (int i = 1; i < n; i++) {
            double x = cpuSortBuf[i];
            int j = i - 1;
            while (j >= 0 && cpuSortBuf[j] > x) {
                cpuSortBuf[j + 1] = cpuSortBuf[j];
                j--;
            }
            cpuSortBuf[j + 1] = x;
        }

        return cpuSortBuf[n / 2];
    }

    // =========================================================================
    // RAM Monitoring
    // =========================================================================

    private RamSnapshot readRamSnapshot() {
        RamSnapshot s = new RamSnapshot();

        long total = mem.getTotal();
        long avail = mem.getAvailable();
        long used = total - avail;

        s.totalGb = toGb(total);
        s.usedGb = toGb(used);
        s.percent = total > 0 ? clamp01_100(used * 100.0 / total) : 0;

        return s;
    }

    // =========================================================================
    // Disk Monitoring
    // =========================================================================

    private LogicalUsage readLogicalUsage() {
        LogicalUsage u = new LogicalUsage();

        List<OSFileStore> stores = safeList(fs.getFileStores());
        long total = 0;
        long used = 0;

        for (OSFileStore st : stores) {
            long t = st.getTotalSpace();
            long us = t - st.getUsableSpace();
            if (t <= 0) continue;
            total += t;
            used += Math.max(0, us);
        }

        u.totalGb = toGb(total);
        u.usedGb = toGb(used);
        return u;
    }

    private PhysicalDiskSnapshot[] readPhysicalSnapshots(LogicalUsage lu, long now) {
        PhysicalDiskSnapshot[] snaps = new PhysicalDiskSnapshot[diskStores.length];

        boolean singlePhysical = diskStores.length == 1 && lu.totalGb > 0;

        for (int i = 0; i < diskStores.length; i++) {
            HWDiskStore d = diskStores[i];
            try {
                d.updateAttributes();
            } catch (Exception ignored) {}

            PhysicalDiskSnapshot s = new PhysicalDiskSnapshot();
            s.index = i;
            s.model = safe(d.getModel(), "Disk");
            s.sizeGb = toGb(d.getSize());

            String type = diskTypeByIndex.get(i);
            s.typeLabel = (type == null) ? "Disk" : type;

            long transfer = safeLong(d.getTransferTime());
            long prevT = prevTransferTime[i];
            long deltaTransfer = transfer - prevT;

            long prevTs = prevDiskTs[i];
            long deltaMs = now - prevTs;

            double busy = 0;
            if (deltaMs > 0 && deltaTransfer >= 0) {
                busy = clamp01_100((deltaTransfer * 100.0) / deltaMs);
            }

            // Smooth active% with EMA
            final double alphaDisk = 0.35;
            diskBusyEma[i] = (prevTs == 0) ? busy : (diskBusyEma[i] + alphaDisk * (busy - diskBusyEma[i]));
            s.activePercent = clamp01_100(diskBusyEma[i]);

            prevTransferTime[i] = transfer;
            prevDiskTs[i] = now;

            if (singlePhysical) {
                s.totalGb = lu.totalGb;
                s.usedGb = lu.usedGb;
                s.usedPercent = s.totalGb > 0 ? clamp01_100(s.usedGb * 100.0 / s.totalGb) : 0;
                s.hasUsage = true;
            } else {
                s.totalGb = s.sizeGb;
                s.usedGb = 0;
                s.usedPercent = 0;
                s.hasUsage = false;
            }

            snaps[i] = s;
        }

        return snaps;
    }

    // =========================================================================
    // Disk Type Detection (Windows)
    // =========================================================================

    private void loadDiskMediaTypesWindows() {
        try {
            Map<String, DiskWinInfo> winByModel = new HashMap<>();
            Map<Long, DiskWinInfo> winBySize = new HashMap<>();

            String ps1 = "Get-PhysicalDisk | " +
                    "Select-Object FriendlyName, MediaType, Size | " +
                    "ForEach-Object { \"$($_.FriendlyName)|$($_.MediaType)|$($_.Size)\" }";
            parsePsDiskLines(runPowerShellAll(ps1), winByModel, winBySize, true);

            String ps2 = "Get-CimInstance Win32_DiskDrive | " +
                    "Select-Object Model, MediaType, Size, RotationRate | " +
                    "ForEach-Object { \"$($_.Model)|$($_.MediaType)|$($_.Size)|$($_.RotationRate)\" }";
            parsePsDiskLines(runPowerShellAll(ps2), winByModel, winBySize, false);

            for (int i = 0; i < diskStores.length; i++) {
                HWDiskStore d = diskStores[i];
                String model = safe(d.getModel(), "");
                long size = d.getSize();

                DiskWinInfo best = null;
                if (!model.isBlank()) best = findBestByModel(winByModel, model);
                if (best == null) best = matchByClosestSize(size, winBySize);

                String label = (best != null) ? decideDiskLabel(best) : "Disk";
                diskTypeByIndex.put(i, label);
            }
        } catch (Exception ignored) {}
    }

    private static final class DiskWinInfo {
        String model;
        String mediaType;
        Long sizeBytes;
        Integer rotationRate;
    }

    private void parsePsDiskLines(String out,
                                  Map<String, DiskWinInfo> winByModel,
                                  Map<Long, DiskWinInfo> winBySize,
                                  boolean pmStyle) {
        if (out == null || out.isBlank()) return;

        for (String line : out.split("\\R")) {
            String s = line.trim();
            if (s.isEmpty() || !s.contains("|")) continue;

            String[] parts = s.split("\\|", -1);
            try {
                if (pmStyle) {
                    if (parts.length < 3) continue;
                    String name = parts[0].trim();
                    String media = parts[1].trim();
                    String sizeStr = parts[2].trim();
                    if (name.isEmpty() || sizeStr.isEmpty()) continue;

                    long size = Long.parseLong(sizeStr);

                    DiskWinInfo info = new DiskWinInfo();
                    info.model = name;
                    info.mediaType = media;
                    info.sizeBytes = size;

                    winByModel.put(name.toLowerCase(), info);
                    winBySize.put(size, info);
                } else {
                    if (parts.length < 4) continue;
                    String model = parts[0].trim();
                    String mediaType = parts[1].trim();
                    String sizeStr = parts[2].trim();
                    String rotStr = parts[3].trim();

                    if (model.isEmpty() || sizeStr.isEmpty()) continue;

                    long size = Long.parseLong(sizeStr);
                    Integer rot = rotStr.isEmpty() ? null : Integer.parseInt(rotStr);

                    DiskWinInfo info = winByModel.getOrDefault(model.toLowerCase(), new DiskWinInfo());
                    info.model = model;
                    info.sizeBytes = size;
                    if (info.mediaType == null || info.mediaType.isBlank()) info.mediaType = mediaType;
                    info.rotationRate = rot;

                    winByModel.put(model.toLowerCase(), info);
                    winBySize.put(size, info);
                }
            } catch (Exception ignored) {}
        }
    }

    private static DiskWinInfo findBestByModel(Map<String, DiskWinInfo> map, String oshiModel) {
        String key = oshiModel.toLowerCase();
        DiskWinInfo exact = map.get(key);
        if (exact != null) return exact;

        for (Map.Entry<String, DiskWinInfo> e : map.entrySet()) {
            String m = e.getKey();
            if (m.isEmpty()) continue;
            if (key.contains(m) || m.contains(key)) return e.getValue();
        }
        return null;
    }

    private static DiskWinInfo matchByClosestSize(long size, Map<Long, DiskWinInfo> map) {
        if (map.isEmpty() || size <= 0) return null;

        long bestDiff = Long.MAX_VALUE;
        DiskWinInfo best = null;

        for (Map.Entry<Long, DiskWinInfo> e : map.entrySet()) {
            long s = e.getKey();
            long diff = Math.abs(s - size);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = e.getValue();
            }
        }

        double ratio = (bestDiff * 1.0) / size;
        return (ratio <= 0.10) ? best : null;
    }

    private static String decideDiskLabel(DiskWinInfo info) {
        String media = Optional.ofNullable(info.mediaType).orElse("").toLowerCase();
        if (media.contains("ssd")) return "SSD";
        if (media.contains("hdd")) return "HDD";
        if (info.rotationRate != null) {
            if (info.rotationRate == 0) return "SSD";
            if (info.rotationRate > 0) return "HDD";
        }
        return "Disk";
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    private static String safe(String s, String fallback) {
        if (s == null) return fallback;
        String t = s.trim();
        return t.isEmpty() ? fallback : t;
    }

    private static long safeLong(long v) {
        return Math.max(0L, v);
    }

    private static double toGb(long bytes) {
        return bytes / (1024.0 * 1024 * 1024);
    }

    private static double clamp01_100(double v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static <T> List<T> safeList(List<T> x) {
        return (x == null) ? Collections.emptyList() : x;
    }

    private String runPowerShellAll(String psCommand) {
        if (!isWindows) return null;

        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", psCommand
            );
            pb.redirectErrorStream(true);
            p = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
            }

            boolean finished = p.waitFor(POWERSHELL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return null;
            }

            String res = sb.toString();
            return res.isBlank() ? null : res;

        } catch (Exception e) {
            if (p != null) {
                try { p.destroyForcibly(); } catch (Exception ignored) {}
            }
            return null;
        }
    }
}
