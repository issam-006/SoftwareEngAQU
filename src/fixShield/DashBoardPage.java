package fixShield;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.FileSystem;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OSFileStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DashBoardPage extends Application {

    private Stage primaryStage;

    // ========== OSHI core ==========
    private SystemInfo systemInfo;
    private HardwareAbstractionLayer hal;
    private CentralProcessor cpu;
    private GlobalMemory memory;
    private OperatingSystem os;
    private FileSystem fileSystem;
    private HWDiskStore[] diskStores;
    private GraphicsCard[] gpus;

    // CPU ticks snapshot
    private long[] prevCpuTicks;

    // Disk transfer timestamps
    private long[] prevDiskTransferTime;
    private long[] prevDiskTimestamp;

    // UI cards
    private MeterCard cpuCard;
    private MeterCard ramCard;
    private MeterCard gpuCard;
    private PhysicalDiskCard[] physicalCards;

    private final DecimalFormat percentFormat = new DecimalFormat("0.0");
    private final DecimalFormat gbFormat = new DecimalFormat("0.0");

    private ScheduledExecutorService executor;

    // GPU caching / state
    private volatile int lastGpuUsage = -1;
    private volatile long lastGpuUpdateTime = 0L;
    private volatile boolean hasNvidiaGpu = false;

    // Smoothing / caching CPU & disks
    private volatile double lastCpuPercent = 0.0;
    private volatile long lastCpuSampleTime = 0L;

    private volatile PhysicalDiskSnapshot[] lastDiskSnaps = null;
    private volatile long lastDiskSampleTime = 0L;

    @Override
    public void start(Stage stage) {

        this.primaryStage = stage;

        // ---------- OSHI init ----------
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

        // ---------- UI root ----------
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #020617, #0f172a);");

        Label header = new Label("FX Shield - System Monitor & Optimizer");
        header.setTextFill(Color.web("#e5e7eb"));
        header.setFont(Font.font("Segoe UI", 26));
        header.setStyle("-fx-font-weight: bold;");

        root.setTop(header);
        BorderPane.setAlignment(header, Pos.TOP_LEFT);
        BorderPane.setMargin(header, new Insets(0, 0, 16, 0));

        // ---------- Top meters (CPU / RAM / GPU) ----------
        cpuCard = new MeterCard("CPU");
        ramCard = new MeterCard("RAM");
        gpuCard = new MeterCard("GPU");

        HBox mainRow = new HBox(18);
        mainRow.setAlignment(Pos.CENTER_LEFT);
        mainRow.getChildren().addAll(cpuCard.getRoot(), ramCard.getRoot(), gpuCard.getRoot());
        HBox.setHgrow(cpuCard.getRoot(), Priority.ALWAYS);
        HBox.setHgrow(ramCard.getRoot(), Priority.ALWAYS);
        HBox.setHgrow(gpuCard.getRoot(), Priority.ALWAYS);

        // ---------- Physical disks row ----------
        HBox disksRow = new HBox(18);
        disksRow.setAlignment(Pos.CENTER_LEFT);

        physicalCards = new PhysicalDiskCard[diskStores.length];
        for (int i = 0; i < diskStores.length; i++) {
            HWDiskStore store = diskStores[i];
            PhysicalDiskCard card = new PhysicalDiskCard(i, store);
            physicalCards[i] = card;
            disksRow.getChildren().add(card.getRoot());
            HBox.setHgrow(card.getRoot(), Priority.ALWAYS);
        }

        // ---------- Actions (tools) grid ----------
        ActionCard freeRamCard = new ActionCard("Free RAM", "Clean memory and free resources", "Run");
        freeRamCard.getButton().setOnAction(e -> runFreeRam());

        ActionCard optimizeDiskCard = new ActionCard("Optimize Disk", "Clean and optimize disk usage", "Run");
        optimizeDiskCard.getButton().setOnAction(e -> runOptimizeDisk());

        ActionCard optimizeNetCard = new ActionCard("Optimize Network", "Flush DNS & reset network tweaks", "Run");
        optimizeNetCard.getButton().setOnAction(e -> runOptimizeNetwork());

        ActionCard optimizeUiCard = new ActionCard("Optimize UI", "Reduce UI lag & visual effects", "Run");
        optimizeUiCard.getButton().setOnAction(e -> runOptimizeUi());

        ActionCard scanFixCard = new ActionCard("Scan & Fix Files", "Scan system and fix corrupted files", "Scan");
        scanFixCard.getButton().setOnAction(e -> runScanAndFix());

        ActionCard modesCard = new ActionCard("Power Modes", "Switch power / balanced / performance", "Open");
        modesCard.getButton().setOnAction(e -> runModes());

        ActionCard allInOneCard = new ActionCard("All in One", "Run full optimization package", "Boost");
        allInOneCard.getButton().setOnAction(e -> runAllInOne());

        GridPane toolsGrid = new GridPane();
        toolsGrid.setHgap(18);
        toolsGrid.setVgap(18);
        toolsGrid.setPadding(new Insets(12, 0, 0, 0));

        toolsGrid.add(freeRamCard.getRoot(),   0, 0);
        toolsGrid.add(optimizeDiskCard.getRoot(), 1, 0);
        toolsGrid.add(optimizeNetCard.getRoot(),  2, 0);

        toolsGrid.add(scanFixCard.getRoot(),   0, 1);
        toolsGrid.add(modesCard.getRoot(),     1, 1);
        toolsGrid.add(allInOneCard.getRoot(),  2, 1);

        for (int i = 0; i < 3; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setHgrow(Priority.ALWAYS);
            col.setPercentWidth(33.33);
            toolsGrid.getColumnConstraints().add(col);
        }

        VBox centerBox = new VBox(22);
        centerBox.setFillWidth(true);
        centerBox.getChildren().addAll(mainRow, disksRow, toolsGrid);

        root.setCenter(centerBox);

        Scene scene = new Scene(root, 1280, 720);
        stage.setTitle("FX Shield - System Monitor & Optimizer");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();

        // ---------- GPU name detection (async) ----------
        Thread gpuNameThread = new Thread(() -> {
            String gpuName = detectGpuNameFromOSHI();
            Platform.runLater(() -> {
                if (gpuName == null || gpuName.isBlank()) {
                    gpuCard.getTitleLabel().setText("GPU - Unknown");
                } else {
                    gpuCard.getTitleLabel().setText("GPU - " + gpuName);
                }
            });
        });
        gpuNameThread.setDaemon(true);
        gpuNameThread.start();

        // ---------- periodic sampler ----------
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::sampleAndUpdate, 0, 200, TimeUnit.MILLISECONDS);

        stage.setOnCloseRequest(e -> {
            if (executor != null) {
                executor.shutdownNow();
            }
        });
    }

    // ==================== Sampling & smoothing ====================

    private void sampleAndUpdate() {
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

    // ==================== CPU / RAM ====================

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

    // ==================== Logical disk usage ====================

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

    // ==================== GPU (OSHI + Counters + nvidia-smi) ====================

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

    // ==================== UI update ====================

    private void updateCpuUI(double percent) {
        if (percent < 0) {
            cpuCard.getValueLabel().setText("N/A");
            cpuCard.getExtraLabel().setText("System CPU usage");
            cpuCard.getBar().setProgress(0);
            styleAsUnavailable(cpuCard);
            return;
        }
        String text = percentFormat.format(percent) + " %";
        cpuCard.getValueLabel().setText(text);
        cpuCard.getExtraLabel().setText("System CPU usage");
        cpuCard.getBar().setProgress(clamp01(percent / 100.0));
        styleByUsage(cpuCard, percent);
    }

    private void updateRamUI(RamSnapshot snap) {
        double percent = snap.percent;
        String percentText = percentFormat.format(percent) + " %";
        String extra = gbFormat.format(snap.usedGb) + " / " +
                gbFormat.format(snap.totalGb) + " GB";

        ramCard.getValueLabel().setText(percentText);
        ramCard.getExtraLabel().setText(extra);
        ramCard.getBar().setProgress(clamp01(percent / 100.0));
        styleByUsage(ramCard, percent);
    }

    private void updateGpuUI(int usage) {
        if (usage < 0) {
            gpuCard.getValueLabel().setText("N/A");
            gpuCard.getExtraLabel().setText("GPU usage not available");
            gpuCard.getBar().setProgress(0);
            styleAsUnavailable(gpuCard);
            return;
        }
        double percent = usage;
        String percentText = percentFormat.format(percent) + " %";
        gpuCard.getValueLabel().setText(percentText);
        gpuCard.getExtraLabel().setText("GPU utilization");
        gpuCard.getBar().setProgress(clamp01(percent / 100.0));
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

                card.getUsedValueLabel().setText(usedText);
                card.getSpaceLabel().setText(spaceText);
                card.getUsedBar().setProgress(clamp01(usedPercent / 100.0));
                styleByUsage(card, usedPercent, true);
            } else {
                card.getUsedValueLabel().setText("Used: N/A");
                card.getSpaceLabel().setText("Size: " + gbFormat.format(snap.sizeGb) + " GB");
                card.getUsedBar().setProgress(0);
                styleAsUnavailable(card, true);
            }

            double activePercent = snap.activePercent;
            String activeText = "Active: " + percentFormat.format(activePercent) + " %";
            card.getActiveValueLabel().setText(activeText);
            card.getActiveBar().setProgress(clamp01(activePercent / 100.0));
            styleByUsage(card, activePercent, false);
        }
    }

    // ==================== Styling helpers ====================

    private void styleByUsage(MeterCard card, double percent) {
        String color = usageColor(percent);
        card.getValueLabel().setTextFill(Color.web(color));
        String barStyle = "-fx-accent: " + color + ";" +
                "-fx-control-inner-background: #020617;";
        card.getBar().setStyle(barStyle);
    }

    private void styleAsUnavailable(MeterCard card) {
        String color = "#9ca3af";
        card.getValueLabel().setTextFill(Color.web(color));
        String barStyle = "-fx-accent: " + color + ";" +
                "-fx-control-inner-background: #020617;";
        card.getBar().setStyle(barStyle);
    }

    private void styleByUsage(PhysicalDiskCard card, double percent, boolean forUsedBar) {
        String color = usageColor(percent);
        if (forUsedBar) {
            card.getUsedValueLabel().setTextFill(Color.web(color));
            String barStyle = "-fx-accent: " + color + ";" +
                    "-fx-control-inner-background: #020617;";
            card.getUsedBar().setStyle(barStyle);
        } else {
            card.getActiveValueLabel().setTextFill(Color.web(color));
            String barStyle = "-fx-accent: " + color + ";" +
                    "-fx-control-inner-background: #020617;";
            card.getActiveBar().setStyle(barStyle);
        }
    }

    private void styleAsUnavailable(PhysicalDiskCard card, boolean forUsedBar) {
        String color = "#9ca3af";
        if (forUsedBar) {
            card.getUsedValueLabel().setTextFill(Color.web(color));
            String barStyle = "-fx-accent: " + color + ";" +
                    "-fx-control-inner-background: #020617;";
            card.getUsedBar().setStyle(barStyle);
        } else {
            card.getActiveValueLabel().setTextFill(Color.web(color));
            String barStyle = "-fx-accent: " + color + ";" +
                    "-fx-control-inner-background: #020617;";
            card.getActiveBar().setStyle(barStyle);
        }
    }

    private String usageColor(double percent) {
        if (percent < 60.0) {
            return "#60a5fa";      // blue
        } else if (percent < 85.0) {
            return "#fb923c";      // orange
        } else {
            return "#f97373";      // red
        }
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    // ==================== Actions ====================

    private void runFreeRam() {
        System.out.println("[ACTION] Free RAM clicked");

        String psScript =
                "$ErrorActionPreference='SilentlyContinue';" +
                        "Write-Host 'Cleaning temp files...';" +
                        "Remove-Item -Path $env:TEMP\\* -Recurse -Force -ErrorAction SilentlyContinue;";

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell",
                    "-Command",
                    psScript
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            new Thread(() -> {
                try (BufferedReader r =
                             new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.out.println("[FreeRAM] " + line);
                    }
                } catch (Exception ignored) {}
            }).start();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void runOptimizeDisk() {
        System.out.println("[ACTION] Optimize Disk clicked");
        // TODO: سكربت defrag / trim
    }

    private void runOptimizeNetwork() {
        System.out.println("[ACTION] Optimize Network clicked");

        String psScript =
                "$ErrorActionPreference='SilentlyContinue';" +
                        "ipconfig /flushdns | Out-Null;";

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell",
                    "-Command",
                    psScript
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            new Thread(() -> {
                try (BufferedReader r =
                             new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.out.println("[NetOpt] " + line);
                    }
                } catch (Exception ignored) {}
            }).start();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void runOptimizeUi() {
        System.out.println("[ACTION] Optimize UI clicked");
        // TODO: سكربت يقلل visual effects
    }

    private void runScanAndFix() {
        System.out.println("[ACTION] Scan & Fix Files clicked");
        // TODO: sfc /scannow أو DISM
    }

    private void runModes() {
        System.out.println("[ACTION] Modes clicked");
        PowerModeDialog.show(primaryStage);
    }

    private void runAllInOne() {
        System.out.println("[ACTION] All in One clicked");
        runFreeRam();
        runOptimizeDisk();
        runOptimizeNetwork();
        runOptimizeUi();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
