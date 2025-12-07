package javafxx;

import com.sun.management.OperatingSystemMXBean;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
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
import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DashBoardPage extends Application {

    // OSHI objects
    private SystemInfo systemInfo;
    private HardwareAbstractionLayer hal;
    private CentralProcessor cpu;
    private GlobalMemory memory;
    private OperatingSystem os;
    private FileSystem fileSystem;
    private HWDiskStore[] diskStores;
    private GraphicsCard[] gpus;

    // آخر قراءة CPU + وقتها
    private volatile double lastCpuPercent = 0.0;
    private volatile long lastCpuSampleTime = 0L;

    // آخر سنابشوت للأقراص + وقته
    private volatile PhysicalDiskSnapshot[] lastDiskSnaps = null;
    private volatile long lastDiskSampleTime = 0L;


    // CPU ticks snapshot
    private long[] prevCpuTicks;

    // Disk transfer-timestamps
    private long[] prevDiskTransferTime;
    private long[] prevDiskTimestamp;

    private OperatingSystemMXBean osBean;

    private MeterCard cpuCard;
    private MeterCard ramCard;
    private MeterCard gpuCard;

    private PhysicalDiskCard[] physicalCards;

    private final DecimalFormat percentFormat = new DecimalFormat("0.0");
    private final DecimalFormat gbFormat = new DecimalFormat("0.0");

    private ScheduledExecutorService executor;

    // GPU caching
    private volatile int lastGpuUsage = -1;
    private volatile long lastGpuUpdateTime = 0L;
    private volatile boolean hasNvidiaGpu = false;

    @Override
    public void start(Stage stage) {

        // OSHI init
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

        osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #020617, #0f172a);");

        Label header = new Label("FX Shield - Physical Disk & System Monitor");
        header.setTextFill(Color.web("#e5e7eb"));
        header.setFont(Font.font("Segoe UI", 24));
        header.setStyle("-fx-font-weight: bold;");

        root.setTop(header);
        BorderPane.setAlignment(header, Pos.TOP_LEFT);
        BorderPane.setMargin(header, new Insets(0, 0, 20, 5));

        cpuCard = new MeterCard("CPU");
        ramCard = new MeterCard("RAM");
        gpuCard = new MeterCard("GPU");

        HBox mainRow = new HBox();
        mainRow.setSpacing(20);
        mainRow.setAlignment(Pos.CENTER_LEFT);
        mainRow.getChildren().add(cpuCard.root);
        mainRow.getChildren().add(ramCard.root);
        mainRow.getChildren().add(gpuCard.root);

        HBox disksRow = new HBox();
        disksRow.setSpacing(20);
        disksRow.setAlignment(Pos.CENTER_LEFT);

        physicalCards = new PhysicalDiskCard[diskStores.length];
        for (int i = 0; i < diskStores.length; i++) {
            HWDiskStore store = diskStores[i];
            PhysicalDiskCard card = new PhysicalDiskCard(i, store);
            physicalCards[i] = card;
            disksRow.getChildren().add(card.root);
        }

        VBox centerBox = new VBox();
        centerBox.setSpacing(20);
        centerBox.getChildren().add(mainRow);
        centerBox.getChildren().add(disksRow);

        root.setCenter(centerBox);

        Scene scene = new Scene(root, 1200, 520);
        stage.setTitle("FX Shield - Physical Disk & System Monitor");
        stage.setScene(scene);
        stage.show();

        // GPU name + نوع الكرت (هل هو NVIDIA؟)
        Thread gpuNameThread = new Thread(() -> {
            String gpuName = detectGpuNameFromOSHI();
            Platform.runLater(() -> {
                if (gpuName == null || gpuName.isBlank()) {
                    gpuCard.titleLabel.setText("GPU - Unknown");
                } else {
                    gpuCard.titleLabel.setText("GPU - " + gpuName);
                }
            });
        });
        gpuNameThread.setDaemon(true);
        gpuNameThread.start();

        // تحديث سريع: كل 200ms لــ CPU/RAM وDisk busy (مع كاش للـ GPU كل 1 ثانية)
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::sampleAndUpdate, 0, 200, TimeUnit.MILLISECONDS);

        stage.setOnCloseRequest(e -> {
            if (executor != null) {
                executor.shutdownNow();
            }
        });
    }

    private void sampleAndUpdate() {
        try {
            long now = System.currentTimeMillis();

            // ---------- 1) CPU: تحديث كل 500ms فقط ----------
            double cpuPercentLocal = lastCpuPercent;
            if (lastCpuSampleTime == 0L || now - lastCpuSampleTime >= 500) {
                double measured = readCpuPercentFromOSHI();
                if (measured >= 0) {          // لو القراءة مش -1
                    lastCpuPercent = measured;
                    lastCpuSampleTime = now;
                }
                cpuPercentLocal = lastCpuPercent;
            }

            // ---------- 2) RAM: نقرأها في كل دورة (خفيفة) ----------
            RamSnapshot ramSnap = readRamSnapshotFromOSHI();

            // ---------- 3) Disk: تحديث كل 1000ms ----------
            PhysicalDiskSnapshot[] diskSnapsLocal = lastDiskSnaps;
            if (diskSnapsLocal == null || now - lastDiskSampleTime >= 1000) {
                LogicalUsage logicalUsage = readLogicalUsageFromOSHI();
                diskSnapsLocal = readPhysicalDiskSnapshots(logicalUsage, now);
                lastDiskSnaps = diskSnapsLocal;
                lastDiskSampleTime = now;
            }

            // ---------- 4) GPU: كما هو (كل 1000ms) ----------
            // ---------- 4) GPU: قراءة شاملة لكل الأنواع (كل 1000ms) ----------
            // ---------- 4) GPU: هجين (Counters + nvidia-smi) كل 1000ms ----------
            int gpuUsageLocal = lastGpuUsage;
            if (now - lastGpuUpdateTime >= 1000) {
                gpuUsageLocal = readGpuUsageHybrid();
                lastGpuUsage = gpuUsageLocal;
                lastGpuUpdateTime = now;
            }

            final double cpuPercentFinal = cpuPercentLocal;
            final RamSnapshot ramSnapFinal = ramSnap;
            final PhysicalDiskSnapshot[] finalDiskSnaps = diskSnapsLocal;
            final int finalGpuUsage = gpuUsageLocal;

            Platform.runLater(() -> {
                updateCpuUI(cpuPercentFinal);
                updateRamUI(ramSnapFinal);
                updateGpuUI(finalGpuUsage);
                if (finalDiskSnaps != null) {
                    updatePhysicalDisksUI(finalDiskSnaps);
                }
            });

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    // ==================== CPU / RAM من OSHI ====================

    private double readCpuPercentFromOSHI() {
        long[] newTicks = cpu.getSystemCpuLoadTicks();
        double load = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks);
        prevCpuTicks = newTicks;
        if (load < 0) {
            return -1;
        }
        return load * 100.0;
    }

    private static class RamSnapshot {
        double totalGb;
        double usedGb;
        double percent;
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

    // ==================== Logical usage (file systems) ====================

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



    // ==================== Physical disks snapshots ====================

    private static class PhysicalDiskSnapshot {
        int index;
        String model;
        double sizeGb;
        double usedGb;
        double totalGb;
        double usedPercent;
        double activePercent;
        boolean hasUsage;
    }

    private PhysicalDiskSnapshot[] readPhysicalDiskSnapshots(LogicalUsage logicalUsage, long now) {
        PhysicalDiskSnapshot[] snaps = new PhysicalDiskSnapshot[diskStores.length];

        boolean singlePhysical = diskStores.length == 1 && logicalUsage.totalGb > 0;

        for (int i = 0; i < diskStores.length; i++) {
            HWDiskStore disk = diskStores[i];
            disk.updateAttributes();  // تحديث إحصائيات OSHI

            PhysicalDiskSnapshot snap = new PhysicalDiskSnapshot();
            snap.index = i;
            snap.model = disk.getModel();
            snap.sizeGb = disk.getSize() / (1024.0 * 1024 * 1024);

            // Active time (busy %) = deltaTransferTime / deltaWallTime
            long transferTime = disk.getTransferTime(); // ms of I/O since boot
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

            // Usage:
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
                snap.hasUsage = false; // ما ربطنا بارتيشنات بهذا الديسك
            }

            snaps[i] = snap;
        }

        return snaps;
    }

    // ==================== GPU: اسم من OSHI + استعمال من nvidia-smi (إن وجد) ====================

    private String detectGpuNameFromOSHI() {
        if (gpus.length == 0) {
            hasNvidiaGpu = false;
            return null;
        }
        GraphicsCard primary = gpus[0]; // أغلب الأجهزة عندها كرت واحد مهم
        String name = primary.getName();
        String vendor = primary.getVendor();
        String combined = (vendor == null ? "" : vendor + " ") + (name == null ? "" : name);

        String lower = combined.toLowerCase();
        hasNvidiaGpu = lower.contains("nvidia");

        System.out.println("GPU detected by OSHI: " + combined + " | NVIDIA? " + hasNvidiaGpu);
        return combined.trim();
    }

    // قراءة GPU لجميع الأنواع (Intel / AMD / NVIDIA) عبر Performance Counters
    private int readGpuUsageUniversal() {
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
                    // Ignore non-numeric lines
                }
            }

            reader.close();
            p.waitFor();

            // Debug لمعرفة إذا كان ويندوز لا يعطي أي قيمة
            if (!anyNumberRead) {
                System.out.println("[GPU] No values returned by GPU counters.");
                return -1;
            }

            if (max < 0) {
                return -1;
            }

            int usage = (int) Math.round(max);
            return Math.max(0, Math.min(usage, 100));

        } catch (Exception ex) {
            System.out.println("[GPU] Exception: " + ex.getMessage());
            return -1;
        }
    }

    // 1) نحاول نقرأ GPU من Performance Counters (Intel / AMD / NVIDIA)
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
                    // تجاهل أي سطر مش رقم
                }
            }

            reader.close();
            p.waitFor();

            if (!anyNumberRead || max < 0) {
                return -1;  // ما في قراءات صالحة
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


    // 2) قراءة من nvidia-smi (NVIDIA فقط)
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


    // 3) دالة هجين: جرّب Counters أولاً، لو فشلت و في NVIDIA جرّب nvidia-smi
    private int readGpuUsageHybrid() {
        // جرّب Windows GPU Counters أولاً
        int fromCounters = readGpuUsageFromCounters();
        if (fromCounters >= 0) {
            return fromCounters;
        }

        // لو ما فيه أرقام، وجهازك عليه NVIDIA، جرّب nvidia-smi
        if (hasNvidiaGpu) {
            int fromNvidia = readGpuUsageFromNvidiaSmi();
            if (fromNvidia >= 0) {
                return fromNvidia;
            }
        }

        // لو كله فشل
        return -1;
    }



    // ==================== تحديث الـ UI ====================

    private void updateCpuUI(double percent) {
        if (percent < 0) {
            cpuCard.valueLabel.setText("N/A");
            cpuCard.extraLabel.setText("System CPU usage");
            cpuCard.bar.setProgress(0);
            styleAsUnavailable(cpuCard);
            return;
        }
        String text = percentFormat.format(percent) + " %";
        cpuCard.valueLabel.setText(text);
        cpuCard.extraLabel.setText("System CPU usage");
        cpuCard.bar.setProgress(clamp01(percent / 100.0));
        styleByUsage(cpuCard, percent);
    }

    private void updateRamUI(RamSnapshot snap) {
        double percent = snap.percent;
        String percentText = percentFormat.format(percent) + " %";
        String extra = gbFormat.format(snap.usedGb) + " / " +
                gbFormat.format(snap.totalGb) + " GB";

        ramCard.valueLabel.setText(percentText);
        ramCard.extraLabel.setText(extra);
        ramCard.bar.setProgress(clamp01(percent / 100.0));
        styleByUsage(ramCard, percent);
    }

    private void updateGpuUI(int usage) {
        if (usage < 0) {
            gpuCard.valueLabel.setText("N/A");
            gpuCard.extraLabel.setText("GPU usage not available");
            gpuCard.bar.setProgress(0);
            styleAsUnavailable(gpuCard);
            return;
        }
        double percent = usage;
        String percentText = percentFormat.format(percent) + " %";
        gpuCard.valueLabel.setText(percentText);
        gpuCard.extraLabel.setText("GPU utilization");
        gpuCard.bar.setProgress(clamp01(percent / 100.0));
        styleByUsage(gpuCard, percent);
    }

    private void updatePhysicalDisksUI(PhysicalDiskSnapshot[] snaps) {
        for (int i = 0; i < snaps.length; i++) {
            PhysicalDiskSnapshot snap = snaps[i];
            PhysicalDiskCard card = physicalCards[i];
            if (card == null) continue;

            if (snap.hasUsage) {
                double usedPercent = snap.usedPercent;
                String usedText = "Used: " + percentFormat.format(usedPercent) + " %";
                String spaceText = gbFormat.format(snap.usedGb) + " / " +
                        gbFormat.format(snap.totalGb) + " GB";

                card.usedValueLabel.setText(usedText);
                card.spaceLabel.setText(spaceText);
                card.usedBar.setProgress(clamp01(usedPercent / 100.0));
                styleByUsage(card, usedPercent, true);
            } else {
                card.usedValueLabel.setText("Used: N/A");
                card.spaceLabel.setText("Size: " + gbFormat.format(snap.sizeGb) + " GB");
                card.usedBar.setProgress(0);
                styleAsUnavailable(card, true);
            }

            double activePercent = snap.activePercent;
            String activeText = "Active: " + percentFormat.format(activePercent) + " %";
            card.activeValueLabel.setText(activeText);
            card.activeBar.setProgress(clamp01(activePercent / 100.0));
            styleByUsage(card, activePercent, false);
        }
    }

    // ==================== Styling ====================

    private void styleByUsage(MeterCard card, double percent) {
        String color = usageColor(percent);
        card.valueLabel.setTextFill(Color.web(color));
        String barStyle = "-fx-accent: " + color + ";" +
                "-fx-control-inner-background: #020617;";
        card.bar.setStyle(barStyle);
    }

    private void styleAsUnavailable(MeterCard card) {
        String color = "#9ca3af";
        card.valueLabel.setTextFill(Color.web(color));
        String barStyle = "-fx-accent: " + color + ";" +
                "-fx-control-inner-background: #020617;";
        card.bar.setStyle(barStyle);
    }

    private void styleByUsage(PhysicalDiskCard card, double percent, boolean forUsedBar) {
        String color = usageColor(percent);
        if (forUsedBar) {
            card.usedValueLabel.setTextFill(Color.web(color));
            String barStyle = "-fx-accent: " + color + ";" +
                    "-fx-control-inner-background: #020617;";
            card.usedBar.setStyle(barStyle);
        } else {
            card.activeValueLabel.setTextFill(Color.web(color));
            String barStyle = "-fx-accent: " + color + ";" +
                    "-fx-control-inner-background: #020617;";
            card.activeBar.setStyle(barStyle);
        }
    }

    private void styleAsUnavailable(PhysicalDiskCard card, boolean forUsedBar) {
        String color = "#9ca3af";
        if (forUsedBar) {
            card.usedValueLabel.setTextFill(Color.web(color));
            String barStyle = "-fx-accent: " + color + ";" +
                    "-fx-control-inner-background: #020617;";
            card.usedBar.setStyle(barStyle);
        } else {
            card.activeValueLabel.setTextFill(Color.web(color));
            String barStyle = "-fx-accent: " + color + ";" +
                    "-fx-control-inner-background: #020617;";
            card.activeBar.setStyle(barStyle);
        }
    }

    private String usageColor(double percent) {
        if (percent < 60.0) {
            return "#60a5fa";      // أزرق
        } else if (percent < 85.0) {
            return "#fb923c";      // برتقالي
        } else {
            return "#f97373";      // أحمر
        }
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    // ==================== UI components ====================

    private static class MeterCard {
        VBox root;
        Label titleLabel;
        Label valueLabel;
        Label extraLabel;
        ProgressBar bar;

        MeterCard(String titleText) {
            titleLabel = new Label(titleText);
            titleLabel.setTextFill(Color.web("#93c5fd"));
            titleLabel.setFont(Font.font("Segoe UI", 18));
            titleLabel.setStyle("-fx-font-weight: bold;");

            valueLabel = new Label("0 %");
            valueLabel.setTextFill(Color.web("#e5e7eb"));
            valueLabel.setFont(Font.font("Segoe UI", 16));

            bar = new ProgressBar(0);
            bar.setPrefWidth(260);
            bar.setStyle(
                    "-fx-accent: #22c55e;" +
                            "-fx-control-inner-background: #020617;"
            );

            extraLabel = new Label("");
            extraLabel.setTextFill(Color.web("#9ca3af"));
            extraLabel.setFont(Font.font("Segoe UI", 12));

            root = new VBox();
            root.setSpacing(10);
            root.setAlignment(Pos.CENTER_LEFT);
            root.setPadding(new Insets(15));
            root.setStyle(
                    "-fx-background-color: linear-gradient(to bottom right, #020617, #111827);" +
                            "-fx-background-radius: 18;" +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 18, 0.1, 0, 8);"
            );
            root.getChildren().add(titleLabel);
            root.getChildren().add(valueLabel);
            root.getChildren().add(bar);
            root.getChildren().add(extraLabel);
        }
    }

    private static class PhysicalDiskCard {
        VBox root;
        Label titleLabel;
        Label usedValueLabel;
        Label spaceLabel;
        Label activeValueLabel;
        ProgressBar usedBar;
        ProgressBar activeBar;

        PhysicalDiskCard(int index, HWDiskStore store) {
            String titleText = "Disk " + index + " - " + store.getModel();
            titleLabel = new Label(titleText);
            titleLabel.setTextFill(Color.web("#93c5fd"));
            titleLabel.setFont(Font.font("Segoe UI", 15));
            titleLabel.setStyle("-fx-font-weight: bold;");

            usedValueLabel = new Label("Used: N/A");
            usedValueLabel.setTextFill(Color.web("#e5e7eb"));
            usedValueLabel.setFont(Font.font("Segoe UI", 13));

            usedBar = new ProgressBar(0);
            usedBar.setPrefWidth(240);
            usedBar.setStyle(
                    "-fx-accent: #22c55e;" +
                            "-fx-control-inner-background: #020617;"
            );

            double sizeGb = store.getSize() / (1024.0 * 1024 * 1024);
            spaceLabel = new Label("Size: " + new DecimalFormat("0.0").format(sizeGb) + " GB");
            spaceLabel.setTextFill(Color.web("#9ca3af"));
            spaceLabel.setFont(Font.font("Segoe UI", 11));

            activeValueLabel = new Label("Active: 0 %");
            activeValueLabel.setTextFill(Color.web("#e5e7eb"));
            activeValueLabel.setFont(Font.font("Segoe UI", 13));

            activeBar = new ProgressBar(0);
            activeBar.setPrefWidth(240);
            activeBar.setStyle(
                    "-fx-accent: #22c55e;" +
                            "-fx-control-inner-background: #020617;"
            );

            root = new VBox();
            root.setSpacing(8);
            root.setAlignment(Pos.CENTER_LEFT);
            root.setPadding(new Insets(12));
            root.setStyle(
                    "-fx-background-color: linear-gradient(to bottom right, #020617, #111827);" +
                            "-fx-background-radius: 18;" +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 18, 0.1, 0, 8);"
            );
            root.getChildren().add(titleLabel);
            root.getChildren().add(usedValueLabel);
            root.getChildren().add(usedBar);
            root.getChildren().add(spaceLabel);
            root.getChildren().add(activeValueLabel);
            root.getChildren().add(activeBar);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
