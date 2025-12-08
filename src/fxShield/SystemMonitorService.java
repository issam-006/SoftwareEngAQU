package fxShield;

import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.FileSystem;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OSFileStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SystemMonitorService {

    // Public helper: قراءة Snapshot RAM فورية
    public RamSnapshot readRamOnce() {
        return readRamSnapshotFromOSHI();
    }


    // === Public method to get an initial disk snapshot ===
    public PhysicalDiskSnapshot[] sampleDisksOnce() {
        LogicalUsage logical = readLogicalUsageFromOSHI();
        long now = System.currentTimeMillis();
        PhysicalDiskSnapshot[] snaps = readPhysicalDiskSnapshots(logical, now);
        lastDiskSnaps = snaps;
        lastDiskSampleTime = now;
        return snaps;
    }

    // ===== Listener =====
    public interface MonitorListener {
        void onUpdate(double cpuPercent,
                      RamSnapshot ram,
                      PhysicalDiskSnapshot[] disks,
                      int gpuUsage);
    }

    private MonitorListener listener;

    public void setListener(MonitorListener listener) {
        this.listener = listener;
    }

    // ===== OSHI =====
    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hal;
    private final CentralProcessor cpu;
    private final GlobalMemory memory;
    private final OperatingSystem os;
    private final FileSystem fileSystem;
    private final HWDiskStore[] diskStores;
    private final GraphicsCard[] gpus;

    private long[] prevCpuTicks;

    private long[] prevDiskTransferTime;
    private long[] prevDiskTimestamp;

    private ScheduledExecutorService executor;

    // GPU state
    private volatile int lastGpuUsage = -1;
    private volatile long lastGpuUpdateTime = 0L;
    private volatile boolean hasNvidiaGpu = false;
    private final String gpuName;

    // CPU / Disk caching
    private volatile double lastCpuPercent = 0.0;
    private volatile long lastCpuSampleTime = 0L;

    private volatile PhysicalDiskSnapshot[] lastDiskSnaps = null;
    private volatile long lastDiskSampleTime = 0L;

    public SystemMonitorService() {
        systemInfo = new SystemInfo();
        hal = systemInfo.getHardware();
        cpu = hal.getProcessor();
        memory = hal.getMemory();
        os = systemInfo.getOperatingSystem();
        fileSystem = os.getFileSystem();

        List<HWDiskStore> diskList = hal.getDiskStores();
        diskStores = diskList.toArray(new HWDiskStore[0]);

        List<GraphicsCard> gpuList = hal.getGraphicsCards();
        gpus = gpuList.toArray(new GraphicsCard[0]);

        prevCpuTicks = cpu.getSystemCpuLoadTicks();

        prevDiskTransferTime = new long[diskStores.length];
        prevDiskTimestamp = new long[diskStores.length];
        long now = System.currentTimeMillis();
        for (int i = 0; i < diskStores.length; i++) {
            diskStores[i].updateAttributes();
            prevDiskTransferTime[i] = diskStores[i].getTransferTime();
            prevDiskTimestamp[i] = now;
        }

        gpuName = detectGpuNameFromOSHI();
    }

    public String getGpuName() {
        return gpuName;
    }

    // ===== control =====

    public void start() {
        if (executor != null && !executor.isShutdown()) return;
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::sampleAndNotify, 0, 200, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void sampleAndNotify() {
        try {
            long now = System.currentTimeMillis();

            double cpuPercentLocal = lastCpuPercent;
            if (lastCpuSampleTime == 0L || now - lastCpuSampleTime >= 500) {
                double measured = readCpuPercentFromOSHI();
                if (measured >= 0) {
                    lastCpuPercent = measured;
                    lastCpuSampleTime = now;
                }
                cpuPercentLocal = lastCpuPercent;
            }

            RamSnapshot ramSnap = readRamSnapshotFromOSHI();

            PhysicalDiskSnapshot[] diskSnapsLocal = lastDiskSnaps;
            if (diskSnapsLocal == null || now - lastDiskSampleTime >= 1000) {
                LogicalUsage logicalUsage = readLogicalUsageFromOSHI();
                diskSnapsLocal = readPhysicalDiskSnapshots(logicalUsage, now);
                lastDiskSnaps = diskSnapsLocal;
                lastDiskSampleTime = now;
            }

            int gpuUsageLocal = lastGpuUsage;
            if (now - lastGpuUpdateTime >= 1000) {
                gpuUsageLocal = readGpuUsageHybrid();
                lastGpuUsage = gpuUsageLocal;
                lastGpuUpdateTime = now;
            }

            MonitorListener l = this.listener;
            if (l != null) {
                l.onUpdate(cpuPercentLocal, ramSnap, diskSnapsLocal, gpuUsageLocal);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ===== CPU / RAM =====

    private double readCpuPercentFromOSHI() {
        long[] newTicks = cpu.getSystemCpuLoadTicks();
        double load = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks);
        prevCpuTicks = newTicks;
        if (load < 0) return -1;
        return load * 100.0;
    }

    public static class RamSnapshot {
        public double totalGb;
        public double usedGb;
        public double percent;
    }

    private RamSnapshot readRamSnapshotFromOSHI() {
        RamSnapshot snap = new RamSnapshot();
        long total = memory.getTotal();
        long available = memory.getAvailable();
        long used = total - available;

        snap.totalGb = total / (1024.0 * 1024 * 1024);
        snap.usedGb = used / (1024.0 * 1024 * 1024);
        snap.percent = (total > 0) ? (used * 100.0 / total) : 0.0;
        return snap;
    }

    // ===== logical usage =====

    private static class LogicalUsage {
        double totalGb;
        double usedGb;
    }

    private LogicalUsage readLogicalUsageFromOSHI() {
        LogicalUsage usage = new LogicalUsage();

        List<OSFileStore> stores = fileSystem.getFileStores();
        long total = 0;
        long used = 0;

        for (OSFileStore store : stores) {
            long t = store.getTotalSpace();
            long u = store.getTotalSpace() - store.getUsableSpace();
            total += t;
            used += u;
        }

        usage.totalGb = total / (1024.0 * 1024 * 1024);
        usage.usedGb = used / (1024.0 * 1024 * 1024);
        return usage;
    }

    // ===== physical disks =====

    public static class PhysicalDiskSnapshot {
        public int index;
        public String model;
        public double sizeGb;
        public double usedGb;
        public double totalGb;
        public double usedPercent;
        public double activePercent;
        public boolean hasUsage;
    }

    private PhysicalDiskSnapshot[] readPhysicalDiskSnapshots(LogicalUsage logicalUsage, long now) {
        PhysicalDiskSnapshot[] snaps = new PhysicalDiskSnapshot[diskStores.length];

        boolean singlePhysical = diskStores.length == 1 && logicalUsage.totalGb > 0;

        for (int i = 0; i < diskStores.length; i++) {
            HWDiskStore disk = diskStores[i];
            disk.updateAttributes();

            PhysicalDiskSnapshot snap = new PhysicalDiskSnapshot();
            snap.index = i;
            snap.model = disk.getModel();
            snap.sizeGb = disk.getSize() / (1024.0 * 1024 * 1024);

            long transferTime = disk.getTransferTime();
            long prevTransfer = prevDiskTransferTime[i];
            long deltaTransfer = transferTime - prevTransfer;

            long prevTs = prevDiskTimestamp[i];
            long deltaTime = now - prevTs;

            double busy = 0.0;
            if (deltaTime > 0) {
                busy = (deltaTransfer * 100.0) / deltaTime;
            }
            if (busy < 0) busy = 0;
            if (busy > 100) busy = 100;

            snap.activePercent = busy;

            prevDiskTransferTime[i] = transferTime;
            prevDiskTimestamp[i] = now;

            if (singlePhysical) {
                snap.totalGb = logicalUsage.totalGb;
                snap.usedGb = logicalUsage.usedGb;
                snap.usedPercent = (snap.totalGb > 0)
                        ? (snap.usedGb * 100.0 / snap.totalGb)
                        : 0.0;
                snap.hasUsage = true;
            } else {
                snap.totalGb = snap.sizeGb;
                snap.usedGb = 0;
                snap.usedPercent = 0;
                snap.hasUsage = false;
            }

            snaps[i] = snap;
        }

        return snaps;
    }

    // ===== GPU =====

    private String detectGpuNameFromOSHI() {
        if (gpus.length == 0) {
            hasNvidiaGpu = false;
            return null;
        }
        GraphicsCard primary = gpus[0];
        String name = primary.getName();
        String vendor = primary.getVendor();
        String combined = (vendor == null ? "" : vendor + " ") + (name == null ? "" : name);

        String lower = combined.toLowerCase();
        hasNvidiaGpu = lower.contains("nvidia");

        System.out.println("GPU detected: " + combined + " | NVIDIA? " + hasNvidiaGpu);
        return combined.trim();
    }

    private int readGpuUsageFromCounters() {
        try {
            String counterPath = "\\\\GPU Engine(*)\\\\Utilization Percentage";

            String psCmd =
                    "(Get-Counter '" + counterPath + "').CounterSamples " +
                            "| Select-Object -ExpandProperty CookedValue";

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell",
                    "-Command",
                    psCmd
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            double max = -1.0;
            boolean anyNumberRead = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    double value = Double.parseDouble(line);
                    anyNumberRead = true;
                    if (value > max) max = value;
                } catch (NumberFormatException ignore) {
                }
            }

            reader.close();
            p.waitFor();

            if (!anyNumberRead || max < 0) {
                System.out.println("[GPU] No counter values.");
                return -1;
            }

            int usage = (int) Math.round(max);
            if (usage < 0) usage = 0;
            if (usage > 100) usage = 100;
            return usage;
        } catch (Exception ex) {
            System.out.println("[GPU] Counter exception: " + ex.getMessage());
            return -1;
        }
    }

    private int readGpuUsageFromNvidiaSmi() {
        String[][] candidates = new String[][]{
                {"nvidia-smi", "--query-gpu=utilization.gpu", "--format=csv,noheader,nounits"},
                {"C:\\Program Files\\NVIDIA Corporation\\NVSMI\\nvidia-smi.exe", "--query-gpu=utilization.gpu", "--format=csv,noheader,nounits"},
                {"C:\\Windows\\System32\\nvidia-smi.exe", "--query-gpu=utilization.gpu", "--format=csv,noheader,nounits"}
        };

        for (String[] cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();

                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = reader.readLine();
                reader.close();
                p.waitFor();

                if (line == null) continue;
                line = line.trim();
                if (line.isEmpty()) continue;

                int v = Integer.parseInt(line);
                if (v < 0) v = 0;
                if (v > 100) v = 100;
                return v;
            } catch (Exception ex) {
                System.out.println("[GPU] nvidia-smi failed: " + String.join(" ", cmd));
            }
        }
        return -1;
    }

    private int readGpuUsageHybrid() {
        int fromCounters = readGpuUsageFromCounters();
        if (fromCounters >= 0) {
            return fromCounters;
        }

        if (hasNvidiaGpu) {
            int fromNvidia = readGpuUsageFromNvidiaSmi();
            if (fromNvidia >= 0) {
                return fromNvidia;
            }
        }

        return -1;
    }
}
