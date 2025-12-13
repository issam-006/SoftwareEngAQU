package fxShield;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OSFileStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * SystemMonitorService
 * - Reads CPU/RAM/DISK/GPU periodically using OSHI + Windows PowerShell fallbacks.
 * - Keeps the same API used by your DashBoardPage.
 */
public class SystemMonitorService {

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
        public String typeLabel;     // SSD / HDD / Disk
        public double sizeGb;

        public double usedGb;
        public double totalGb;
        public double usedPercent;
        public boolean hasUsage;

        public double activePercent; // "busy" %
    }

    private final SystemInfo si;
    private final HardwareAbstractionLayer hal;
    private final CentralProcessor cpu;
    private final GlobalMemory mem;
    private final OperatingSystem os;
    private final FileSystem fs;

    private final HWDiskStore[] diskStores;
    private final GraphicsCard[] gpus;

    private final GPUStabilizer gpuStabilizer = new GPUStabilizer(350, 0.35, 2, -1);


    private volatile Listener listener;
    private ScheduledExecutorService exec;

    // CPU sampling
    private long[] prevCpuTicks;
    private long lastCpuSampleMs = 0L;
    private double lastCpuPercent = 0.0;

    // Disk busy sampling
    private final long[] prevTransferTime;
    private final long[] prevDiskTs;
    private volatile boolean disksWarmedUp = false;

    // GPU sampling
    private volatile boolean gpuSupported = true;
    private volatile boolean hasNvidia = false;
    private volatile int lastGpuStable = 0;
    private volatile boolean gpuThreadRunning = false;
    private Thread gpuThread;
    private volatile long lastGpuSampleMs = 0L;
    private volatile int lastGpuStableForUi = 0;

    // Disk type cache by physical index
    private final Map<Integer, String> diskTypeByIndex = new HashMap<>();

    // timings
    private static final long LOOP_MS = 250;
    private static final long CPU_MS  = 500;
    private static final long GPU_MS  = 800;
    private static final int GPU_FAILS_TO_DISABLE = 4;

    private final boolean isWindows;

    public SystemMonitorService() {
        si = new SystemInfo();
        hal = si.getHardware();
        cpu = hal.getProcessor();
        mem = hal.getMemory();
        os = si.getOperatingSystem();
        fs = os.getFileSystem();

        String fam = (os.getFamily() == null ? "" : os.getFamily()).toLowerCase();
        isWindows = fam.contains("windows");

        List<HWDiskStore> disks = hal.getDiskStores();
        diskStores = disks.toArray(new HWDiskStore[0]);

        List<GraphicsCard> gpuList = hal.getGraphicsCards();
        gpus = gpuList.toArray(new GraphicsCard[0]);

        prevCpuTicks = cpu.getSystemCpuLoadTicks();

        prevTransferTime = new long[diskStores.length];
        prevDiskTs = new long[diskStores.length];

        long now = System.currentTimeMillis();
        for (int i = 0; i < diskStores.length; i++) {
            try { diskStores[i].updateAttributes(); } catch (Exception ignored) {}
            prevTransferTime[i] = safeLong(diskStores[i].getTransferTime());
            prevDiskTs[i] = now;
        }

        detectNvidiaFlag();

        if (isWindows) {
            loadDiskMediaTypesWindows();
        }
    }

    // ================== Public API ==================

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void start() {
        if (exec != null) return;

        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fxShield-monitor");
            t.setDaemon(true);
            return t;
        });

        exec.schedule(() -> disksWarmedUp = true, 900, TimeUnit.MILLISECONDS);

        startGpuThread(); // ✅ جديد

        exec.scheduleAtFixedRate(() -> {
            try { sampleAndNotify(); } catch (Exception e) { e.printStackTrace(); }
        }, 0, LOOP_MS, TimeUnit.MILLISECONDS);
    }

    private void startGpuThread() {
        if (gpuThreadRunning) return;
        gpuThreadRunning = true;

        gpuThread = new Thread(() -> {
            while (gpuThreadRunning) {
                long now = System.currentTimeMillis();
                int raw = readGpuUsageHybrid();                 // -1 or 0..100
                int stable = gpuStabilizer.update(raw, now);    // -1 or 0..100
                lastGpuStable = (stable < 0) ? lastGpuStable : stable;

                try { Thread.sleep(GPU_MS); } catch (InterruptedException ignored) {}
            }
        }, "fxShield-gpu");
        gpuThread.setDaemon(true);
        gpuThread.start();
    }

    public void stop() {
        gpuThreadRunning = false;
        if (gpuThread != null) gpuThread.interrupt();

        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
    }

    public boolean isGpuUsageSupported() {
        return gpuSupported;
    }

    public String getGpuName() {
        if (gpus.length == 0) return "Unknown";
        GraphicsCard g = gpus[0];
        String vendor = g.getVendor() == null ? "" : g.getVendor();
        String name = g.getName() == null ? "" : g.getName();
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

    // ================== Main sampler ==================

    private void sampleAndNotify() {

        Listener l = this.listener;
        if (l == null) return;

        long now = System.currentTimeMillis();

        double cpuPct = lastCpuPercent;
        if (lastCpuSampleMs == 0 || now - lastCpuSampleMs >= CPU_MS) {
            double m = readCpuPercent();
            if (m >= 0) {
                lastCpuPercent = smooth(lastCpuPercent, m, 0.35);
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

        int gpuToUi = lastGpuStableForUi;

        if (lastGpuSampleMs == 0 || now - lastGpuSampleMs >= GPU_MS) {
            int raw = readGpuUsageHybrid();                 // -1 or 0..100
            int stable = gpuStabilizer.update(raw, now);    // -1 or 0..100 (مستقر)

            lastGpuSampleMs = now;

            if (stable >= 0) {
                lastGpuStableForUi = stable;
                gpuToUi = stable;
            } else {
                // فشل: لا تنزل 0، خليها آخر قيمة كانت ظاهرة
                gpuToUi = lastGpuStableForUi;
            }
        }

// نداء واحد فقط
        l.onUpdate(cpuPct, ram, disks, gpuToUi);
    }

    // ================== CPU / RAM ==================

    private double readCpuPercent() {
        long[] newTicks = cpu.getSystemCpuLoadTicks();
        double load = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks);
        prevCpuTicks = newTicks;

        if (load < 0) return -1;

        double pct = load * 100.0;
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;
        return pct;
    }

    private RamSnapshot readRamSnapshot() {
        RamSnapshot s = new RamSnapshot();

        long total = mem.getTotal();
        long avail = mem.getAvailable();
        long used = total - avail;

        s.totalGb = total / (1024.0 * 1024 * 1024);
        s.usedGb = used / (1024.0 * 1024 * 1024);
        s.percent = total > 0 ? (used * 100.0 / total) : 0;

        if (s.percent < 0) s.percent = 0;
        if (s.percent > 100) s.percent = 100;

        return s;
    }

    // ================== Logical Usage ==================

    private static class LogicalUsage {
        double totalGb;
        double usedGb;
    }

    private LogicalUsage readLogicalUsage() {
        LogicalUsage u = new LogicalUsage();

        List<OSFileStore> stores = fs.getFileStores();
        long total = 0;
        long used = 0;

        for (OSFileStore st : stores) {
            long t = st.getTotalSpace();
            long us = st.getTotalSpace() - st.getUsableSpace();
            if (t <= 0) continue;
            total += t;
            used += Math.max(0, us);
        }

        u.totalGb = total / (1024.0 * 1024 * 1024);
        u.usedGb = used / (1024.0 * 1024 * 1024);
        return u;
    }

    // ================== Physical Disks ==================

    private PhysicalDiskSnapshot[] readPhysicalSnapshots(LogicalUsage lu, long now) {
        PhysicalDiskSnapshot[] snaps = new PhysicalDiskSnapshot[diskStores.length];

        boolean singlePhysical = diskStores.length == 1 && lu.totalGb > 0;

        for (int i = 0; i < diskStores.length; i++) {
            HWDiskStore d = diskStores[i];
            try { d.updateAttributes(); } catch (Exception ignored) {}

            PhysicalDiskSnapshot s = new PhysicalDiskSnapshot();
            s.index = i;
            s.model = safe(d.getModel(), "Disk");
            s.sizeGb = d.getSize() / (1024.0 * 1024 * 1024);

            String type = diskTypeByIndex.get(i);
            s.typeLabel = (type == null) ? "Disk" : type;

            long transfer = safeLong(d.getTransferTime());
            long prevT = prevTransferTime[i];
            long deltaTransfer = transfer - prevT;

            long prevTs = prevDiskTs[i];
            long deltaMs = now - prevTs;

            double busy = 0;
            if (deltaMs > 0 && deltaTransfer >= 0) {
                busy = (deltaTransfer * 100.0) / deltaMs;
            }

            if (busy < 0) busy = 0;
            if (busy > 100) busy = 100;

            s.activePercent = busy;

            prevTransferTime[i] = transfer;
            prevDiskTs[i] = now;

            if (singlePhysical) {
                s.totalGb = lu.totalGb;
                s.usedGb = lu.usedGb;
                s.usedPercent = s.totalGb > 0 ? (s.usedGb * 100.0 / s.totalGb) : 0;
                s.hasUsage = true;
            } else {
                s.totalGb = s.sizeGb;
                s.usedGb = 0;
                s.usedPercent = 0;
                s.hasUsage = false;
            }

            if (s.usedPercent < 0) s.usedPercent = 0;
            if (s.usedPercent > 100) s.usedPercent = 100;

            snaps[i] = s;
        }

        return snaps;
    }

    // ================== GPU (Windows Counters + nvidia-smi) ==================

    private int readGpuUsageHybrid() {
        int val = -1;

        if (isWindows) {
            val = readGpuFromCounters();
            if (val >= 0) return clampInt(val, 0, 100);
        }

        if (hasNvidia) {
            val = readGpuFromNvidiaSmi();
            if (val >= 0) return clampInt(val, 0, 100);
        }

        return -1;
    }

    private int readGpuFromCounters() {
        try {
            String counterPath = "\\\\GPU Engine(*)\\\\Utilization Percentage";

            String ps =
                    "$c='" + counterPath + "';" +
                            "$maxes=@();" +
                            "for($i=0;$i -lt 2;$i++){" +  // 2 samples بدل 3
                            "  $vals=(Get-Counter -Counter $c).CounterSamples | " +
                            "        Where-Object { $_.InstanceName -notmatch '_Total$' } | " +
                            "        Select-Object -ExpandProperty CookedValue;" +
                            "  if($vals){ $maxes += (($vals | Measure-Object -Maximum).Maximum) }" +
                            "  Start-Sleep -Milliseconds 80;" +  // 80ms بدل 120ms
                            "}" +
                            "if(-not $maxes -or $maxes.Count -eq 0){ '' } else { " +
                            "  [math]::Round((($maxes | Measure-Object -Average).Average), 0)" +
                            "}";

            String out = runPowerShellOneLine(ps);
            if (out == null) return -1;

            out = out.trim();
            if (out.isEmpty()) return -1;

            int v = (int) Math.round(Double.parseDouble(out));
            if (v < 0) v = 0;
            if (v > 100) v = 100;
            return v;

        } catch (Exception e) {
            return -1;
        }
    }


    private int readGpuFromNvidiaSmi() {
        String[][] cmds = new String[][]{
                {"nvidia-smi", "--query-gpu=utilization.gpu", "--format=csv,noheader,nounits"},
                {"C:\\Program Files\\NVIDIA Corporation\\NVSMI\\nvidia-smi.exe", "--query-gpu=utilization.gpu", "--format=csv,noheader,nounits"},
                {"C:\\Windows\\System32\\nvidia-smi.exe", "--query-gpu=utilization.gpu", "--format=csv,noheader,nounits"}
        };

        for (String[] cmd : cmds) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();

                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                String line = br.readLine();
                br.close();
                p.waitFor();

                if (line == null) continue;
                line = line.trim();
                if (line.isEmpty()) continue;

                return Integer.parseInt(line);
            } catch (Exception ignored) {}
        }

        return -1;
    }

    private void detectNvidiaFlag() {
        String name = getGpuName().toLowerCase();
        hasNvidia = name.contains("nvidia");
    }

    // ================== SSD/HDD Detection (Windows) ==================

    private void loadDiskMediaTypesWindows() {
        try {
            Map<String, DiskWinInfo> winByModel = new HashMap<>();
            Map<Long, DiskWinInfo> winBySize = new HashMap<>();

            {
                String ps =
                        "Get-PhysicalDisk | " +
                                "Select-Object FriendlyName, MediaType, Size | " +
                                "ForEach-Object { \"$($_.FriendlyName)|$($_.MediaType)|$($_.Size)\" }";

                String out = runPowerShellAll(ps);
                if (out != null && !out.isBlank()) {
                    for (String line : out.split("\\R")) {
                        String s = line.trim();
                        if (s.isEmpty() || !s.contains("|")) continue;
                        String[] parts = s.split("\\|");
                        if (parts.length < 3) continue;

                        String name = parts[0].trim();
                        String media = parts[1].trim();
                        String sizeStr = parts[2].trim();

                        long size;
                        try { size = Long.parseLong(sizeStr); } catch (Exception ex) { continue; }

                        DiskWinInfo info = new DiskWinInfo();
                        info.model = name;
                        info.sizeBytes = size;
                        info.mediaType = media;

                        winByModel.put(name.toLowerCase(), info);
                        winBySize.put(size, info);
                    }
                }
            }

            {
                String ps =
                        "Get-CimInstance Win32_DiskDrive | " +
                                "Select-Object Model, MediaType, Size, RotationRate | " +
                                "ForEach-Object { \"$($_.Model)|$($_.MediaType)|$($_.Size)|$($_.RotationRate)\" }";

                String out = runPowerShellAll(ps);
                if (out != null && !out.isBlank()) {
                    for (String line : out.split("\\R")) {
                        String s = line.trim();
                        if (s.isEmpty() || !s.contains("|")) continue;
                        String[] parts = s.split("\\|");
                        if (parts.length < 4) continue;

                        String model = parts[0].trim();
                        String mediaType = parts[1].trim();
                        String sizeStr = parts[2].trim();
                        String rotStr = parts[3].trim();

                        long size;
                        try { size = Long.parseLong(sizeStr); } catch (Exception ex) { continue; }

                        Integer rot = null;
                        try { rot = rotStr.isEmpty() ? null : Integer.parseInt(rotStr); } catch (Exception ignored) {}

                        DiskWinInfo info = winByModel.getOrDefault(model.toLowerCase(), new DiskWinInfo());
                        info.model = model;
                        info.sizeBytes = size;
                        if (info.mediaType == null || info.mediaType.isBlank()) info.mediaType = mediaType;
                        info.rotationRate = rot;

                        winByModel.put(model.toLowerCase(), info);
                        winBySize.put(size, info);
                    }
                }
            }

            for (int i = 0; i < diskStores.length; i++) {
                HWDiskStore d = diskStores[i];

                String model = safe(d.getModel(), "");
                long size = d.getSize();

                DiskWinInfo best = null;

                if (!model.isBlank()) {
                    best = findBestByModel(winByModel, model);
                }
                if (best == null) {
                    best = matchByClosestSize(size, winBySize);
                }

                String label = (best != null) ? decideDiskLabel(best) : "Disk";
                diskTypeByIndex.put(i, label);
            }

        } catch (Exception ignored) {}
    }

    private static class DiskWinInfo {
        String model;
        String mediaType;
        Long sizeBytes;
        Integer rotationRate;
    }

    private static DiskWinInfo findBestByModel(Map<String, DiskWinInfo> map, String oshiModel) {
        String key = oshiModel.toLowerCase();
        if (map.containsKey(key)) return map.get(key);

        for (Map.Entry<String, DiskWinInfo> e : map.entrySet()) {
            String m = e.getKey();
            if (m.isEmpty()) continue;
            if (key.contains(m) || m.contains(key)) return e.getValue();
        }
        return null;
    }

    private static DiskWinInfo matchByClosestSize(long size, Map<Long, DiskWinInfo> map) {
        if (map.isEmpty()) return null;

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

        double ratio = size > 0 ? (bestDiff * 1.0 / size) : 1.0;
        return (ratio <= 0.10) ? best : null;
    }

    private static String decideDiskLabel(DiskWinInfo info) {
        String media = info.mediaType == null ? "" : info.mediaType.toLowerCase();

        if (media.contains("ssd")) return "SSD";
        if (media.contains("hdd")) return "HDD";

        if (info.rotationRate != null) {
            if (info.rotationRate == 0) return "SSD";
            if (info.rotationRate > 0) return "HDD";
        }

        return "Disk";
    }

    // ================== Helpers ==================

    private static double smooth(double prev, double next, double alpha) {
        return prev + alpha * (next - prev);
    }

    private static String safe(String s, String fallback) {
        if (s == null) return fallback;
        String t = s.trim();
        return t.isEmpty() ? fallback : t;
    }

    private static long safeLong(long v) {
        return Math.max(0L, v);
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private String runPowerShellOneLine(String psCommand) {
        String out = runPowerShellAll(psCommand);
        if (out == null) return null;

        String[] lines = out.split("\\R");
        for (String l : lines) {
            String t = l.trim();
            if (!t.isEmpty()) return t;
        }
        return null;
    }

    private String runPowerShellAll(String psCommand) {
        if (!isWindows) return null;

        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", psCommand);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));

            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }

            br.close();
            p.waitFor();

            String res = sb.toString();
            return res.isBlank() ? null : res;

        } catch (Exception e) {
            return null;
        }
    }
}