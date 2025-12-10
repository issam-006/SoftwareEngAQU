package fxShield;

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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DecimalFormat;

public class DashBoardPage extends Application {

    private Stage primaryStage;

    private BorderPane root;

    private MeterCard cpuCard;
    private MeterCard ramCard;
    private MeterCard gpuCard;
    private PhysicalDiskCard[] physicalCards;

    private final DecimalFormat percentFormat = new DecimalFormat("0.0");
    private final DecimalFormat gbFormat = new DecimalFormat("0.0");

    private SystemMonitorService monitor;
    private HBox disksRow;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // عشان MaintenanceDialog يقدر يستخدم HostServices
        primaryStage.getProperties().put("appInstance", this);

        RemoteConfigService configService = new RemoteConfigService();
        RemoteConfig config = configService.fetchConfig();

        // 🔹 وضع الصيانة
        if (config != null && "maintenance".equalsIgnoreCase(config.getAppStatus())) {
            MaintenanceDialog.show(primaryStage, config, () -> {
                // Retry: إعادة المحاولة
                RemoteConfig retryConfig = configService.fetchConfig();
                if (retryConfig != null &&
                        !"maintenance".equalsIgnoreCase(retryConfig.getAppStatus())) {
                    // خرجنا من الصيانة → نزّل الواجهة العادية
                    launchNormalUi(primaryStage, retryConfig);
                } else {
                    // ما زال في صيانة
                    MaintenanceDialog.show(primaryStage, retryConfig, null);
                }
            });
            return;
        }

        // 🔹 ممكن فيما بعد تضيفي تحقق من ForceUpdate هنا
        // String currentVersion = "1.0.0";
        // if (config != null && config.isForceUpdate()
        //         && !currentVersion.equals(config.getLatestVersion())) {
        //     // تعرضي Dialog تحديث إجباري
        //     return;
        // }

        // 🔹 تشغيل الواجهة الطبيعية
        launchNormalUi(primaryStage, config);
    }

    /**
     * هذه الدالة تحتوي كل كود واجهة fxShield العادي
     */
    private void launchNormalUi(Stage stage, RemoteConfig config) {
        // ===== root / background =====
        root = new BorderPane();
        root.setPadding(new Insets(32, 22, 12, 22));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #020617, #0f172a);");

        // ===== Header + Badge =====
        Label appTitle = new Label("Fx Shield - System Monitor & Optimizer ");
        appTitle.setFont(Font.font("Segoe UI", 22));
        appTitle.setStyle("-fx-font-weight: bold;");
        appTitle.setTextFill(Color.web("#93C5FD"));
        appTitle.setPadding(new Insets(0, 0, 20, 0)); // مسافة بسيطة فقط

        root.setTop(appTitle);
        BorderPane.setAlignment(appTitle, Pos.TOP_LEFT);






        // ===== Top meters =====
        cpuCard = new MeterCard("CPU");
        ramCard = new MeterCard("RAM");
        gpuCard = new MeterCard("GPU");


        HBox mainRow = new HBox(18);
        mainRow.setAlignment(Pos.CENTER_LEFT);
        PhysicalDiskCard topDiskCard = new PhysicalDiskCard(0, "Loading Disk...", 0);
        mainRow.getChildren().addAll(
                cpuCard.getRoot(),
                ramCard.getRoot(),
                gpuCard.getRoot(),
                topDiskCard.getRoot()
        );

        HBox.setHgrow(topDiskCard.getRoot(), Priority.ALWAYS);
        HBox.setHgrow(cpuCard.getRoot(), Priority.ALWAYS);
        HBox.setHgrow(ramCard.getRoot(), Priority.ALWAYS);
        HBox.setHgrow(gpuCard.getRoot(), Priority.ALWAYS);

        // ===== Disks row =====
        disksRow = new HBox(18);
        disksRow.setAlignment(Pos.CENTER_LEFT);



        // ===== Action cards =====
        ActionCard freeRamCard = new ActionCard(
                "Free RAM",
                "Clean memory and free resources",
                "Run",
                "M10 2 L14 2 L8 14 L4 14 Z" // broom icon
        );

        ActionCard optimizeDiskCard = new ActionCard(
                "Optimize Disk",
                "Clean & minimize disk usage",
                "Optimize",
                "M3 5 H13 V11 H3 Z M6 8 A1 1 0 1 0 6 8.01\n" // disk icon
        );

        ActionCard optimizeNetCard = new ActionCard(
                "Optimize Network",
                "Flush DNS & reset tweaks",
                "Optimize",
                "M8 2 L10 6 L8 14 L6 6 Z M7 6 H9\n" // rocket
        );

        ActionCard scanFixCard = new ActionCard(
                "Scan & Fix Files",
                "Scan system and fix corrupted files",
                "Scan",
                "M4 4 H14 V14 H4 Z M7 7 L11 11 M11 7 L7 11" // 🔍 أيقونة بحث + X
        );



        ActionCard modesCard = new ActionCard(
                "Power Modes",
                "Switch power / balanced / performance",
                "Open",
                "M7 2 L11 8 H8 L10 14 L5 8 H8 Z\n" // ⚡ أيقونة برق
        );



        ActionCard allInOneCard = new ActionCard(
                "One Click",
                "Run full optimization package",
                "Boost",
                "M3 6 L8 2 L13 6 L13 14 H3 Z" // 🛡 أيقونة درع
        );

        freeRamCard.getButton().setOnAction(e -> runFreeRam());
        optimizeNetCard.getButton().setOnAction(e -> runOptimizeNetwork());
        optimizeDiskCard.getButton().setOnAction(e -> runOptimizeDisk());
        scanFixCard.getButton().setOnAction(e -> runScanAndFix());
        modesCard.getButton().setOnAction(e -> runModes());
        allInOneCard.getButton().setOnAction(e -> runAllInOne());



        GridPane toolsGrid = new GridPane();
        toolsGrid.setHgap(18);
        toolsGrid.setVgap(18);
        toolsGrid.setPadding(new Insets(12, 0, 0, 0));

        toolsGrid.add(freeRamCard.getRoot(),      0, 0);
        toolsGrid.add(optimizeDiskCard.getRoot(), 1, 0);
        toolsGrid.add(optimizeNetCard.getRoot(),  2, 0);

        toolsGrid.add(scanFixCard.getRoot(),      0, 1);
        toolsGrid.add(modesCard.getRoot(),        1, 1);
        toolsGrid.add(allInOneCard.getRoot(),     2, 1);

        GridPane.setHgrow(freeRamCard.getRoot(), Priority.ALWAYS);
        GridPane.setVgrow(freeRamCard.getRoot(), Priority.ALWAYS);
        GridPane.setHgrow(optimizeDiskCard.getRoot(), Priority.ALWAYS);
        GridPane.setHgrow(optimizeNetCard.getRoot(), Priority.ALWAYS);

        GridPane.setHgrow(scanFixCard.getRoot(), Priority.ALWAYS);
        GridPane.setHgrow(modesCard.getRoot(), Priority.ALWAYS);
        GridPane.setHgrow(allInOneCard.getRoot(), Priority.ALWAYS);

        GridPane.setVgrow(freeRamCard.getRoot(), Priority.ALWAYS);
        GridPane.setVgrow(optimizeDiskCard.getRoot(), Priority.ALWAYS);
        GridPane.setVgrow(optimizeNetCard.getRoot(), Priority.ALWAYS);
        GridPane.setVgrow(scanFixCard.getRoot(), Priority.ALWAYS);
        GridPane.setVgrow(modesCard.getRoot(), Priority.ALWAYS);
        GridPane.setVgrow(allInOneCard.getRoot(), Priority.ALWAYS);


        for (int i = 0; i < 3; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setHgrow(Priority.ALWAYS);
            col.setPercentWidth(33.33);
            toolsGrid.getColumnConstraints().add(col);
        }

        // --- Purple background only for toolsGrid ---
        VBox actionsWrapper = new VBox(28);      // مسافة أكبر بين العناصر
        actionsWrapper.setPadding(new Insets(28, 32, 40, 32));
// ↑  أكبر من فوق — أكبر من الجوانب — أكبر من تحت

// 10 top - 26 right - 40 bottom - 26 left

        actionsWrapper.setStyle(
                "-fx-background-color: rgba(147,197,253,0.18);" +      /* Glass light blue */
                        "-fx-background-radius: 26;" +
                        "-fx-border-color: rgba(255,255,255,0.25);" +
                        "-fx-border-radius: 26;" +
                        "-fx-border-width: 1;" +

                        /* Outer Glow قوي */
                        "-fx-effect: dropshadow(gaussian, rgba(147,197,253,0.80), 1, 0.7, 0, 0)," +

                        /* Inner Glow */
                        " dropshadow(gaussian, rgba(147,197,253,0.35), 20, 0.4, 0, 0)," +

                        /* Soft blur glow */
                        " dropshadow(gaussian, rgba(147,197,253,0.55), 35, 0.5, 0, 0);"
        );


        actionsWrapper.setBorder(new Border(new BorderStroke(
                Color.rgb(147, 197, 253, 0.25),   // Light blue border
                BorderStrokeStyle.SOLID,
                new CornerRadii(22),
                new BorderWidths(1.4)
        )));

        // Title
        Label actionsTitle = new Label("Quick Optimization Tools");
        actionsTitle.setFont(Font.font("Segoe UI", 22));
        actionsTitle.setTextFill(Color.web("#000000"));
        actionsTitle.setStyle("-fx-font-weight: bold;");

// Icons bar
        TopBarIcons topIcons = new TopBarIcons();

// Title + Icons row
        HBox titleRow = new HBox();
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setPadding(new Insets(0, 0, 10, 0));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleRow.getChildren().addAll(actionsTitle, spacer, topIcons.getRoot());

// Add to wrapper
        actionsWrapper.getChildren().add(titleRow);

        // === ADD TOP BAR ICONS ===
        TopBarIcons topBarIcons = new TopBarIcons();


// Add tools grid
        actionsWrapper.getChildren().add(toolsGrid);


// Center layout (no background here)
        VBox centerBox = new VBox(28);
        centerBox.setFillWidth(true);

        centerBox.getChildren().addAll(
                mainRow,
                disksRow,
                actionsWrapper
        );

        root.setCenter(centerBox);





        Scene scene = new Scene(root, 1280, 720);
        stage.setTitle("FX Shield - System Monitor & Optimizer");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();

        // ======================
        //   SystemMonitorService في Thread منفصل
        // ======================
        new Thread(() -> {
            try {
                SystemMonitorService m = new SystemMonitorService();

                SystemMonitorService.PhysicalDiskSnapshot[] initialDisks = m.sampleDisksOnce();
                String gpuName = m.getGpuName();

                m.setListener((cpuPercent, ramSnap, diskSnaps, gpuUsage) -> {
                    Platform.runLater(() -> {
                        updateCpuUI(cpuPercent);
                        updateRamUI(ramSnap);
                        updateGpuUI(gpuUsage);
                        if (diskSnaps != null && physicalCards != null) {
                            updatePhysicalDisksUI(diskSnaps);
                        }
                    });
                });

                Platform.runLater(() -> {
                    this.monitor = m;

                    // GPU title
                    if (gpuName == null || gpuName.isBlank()) {
                        gpuCard.getTitleLabel().setText("GPU - Unknown");
                    } else {
                        gpuCard.getTitleLabel().setText("GPU - " + shortenGpuName(gpuName));
                    }

                    // 🔹 نستبدل الكرت الوهمي بالكروت الحقيقية
                    if (initialDisks != null && initialDisks.length > 0) {
                        physicalCards = new PhysicalDiskCard[initialDisks.length];

                        disksRow.getChildren().clear();

                        for (int i = 0; i < initialDisks.length; i++) {
                            var snap = initialDisks[i];

                            PhysicalDiskCard card = new PhysicalDiskCard(i, snap.model, snap.sizeGb);
                            physicalCards[i] = card;

                            if (i == 0) {
                                // الديسك الأول → في الصف العلوي
                                mainRow.getChildren().set(3, card.getRoot());
                                HBox.setHgrow(card.getRoot(), Priority.ALWAYS);
                            } else {
                                // باقي الأقراص → تبقى في الأسفل
                                disksRow.getChildren().add(card.getRoot());
                                HBox.setHgrow(card.getRoot(), Priority.ALWAYS);
                            }
                        }

                } else {
                        disksRow.getChildren().clear();
                        Label noDisk = new Label("No physical disks detected.");
                        noDisk.setTextFill(Color.web("#9ca3af"));
                        disksRow.getChildren().add(noDisk);
                    }

                    m.start();
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    disksRow.getChildren().clear();
                    Label err = new Label("Error initializing system monitor.");
                    err.setTextFill(Color.web("#f97373"));
                    disksRow.getChildren().add(err);
                });
            }
        }).start();

        stage.setOnCloseRequest(e -> {
            if (monitor != null) {
                monitor.stop();
            }
        });
    }

    // ===== UI update =====

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

    private void updateRamUI(SystemMonitorService.RamSnapshot snap) {
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
        double percent = usage;
        String percentText = percentFormat.format(percent) + " %";
        gpuCard.getValueLabel().setText(percentText);

        if (!monitor.isGpuUsageSupported()) {
            gpuCard.getExtraLabel().setText("GPU usage not supported on this system");
        } else {
            gpuCard.getExtraLabel().setText("GPU utilization");
        }

        gpuCard.getBar().setProgress(clamp01(percent / 100.0));
        styleByUsage(gpuCard, percent);
    }


    private void updatePhysicalDisksUI(SystemMonitorService.PhysicalDiskSnapshot[] snaps) {
        for (int i = 0; i < snaps.length; i++) {
            SystemMonitorService.PhysicalDiskSnapshot snap = snaps[i];
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

    // ===== Styling helpers =====

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
            return "#60a5fa";
        } else if (percent < 85.0) {
            return "#fb923c";
        } else {
            return "#f97373";
        }
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    // ===== Actions =====

    private void runFreeRam() {
        System.out.println("[ACTION] Free RAM clicked");

        // قبل التنظيف: snapshot RAM
        SystemMonitorService.RamSnapshot before =
                (monitor != null ? monitor.readRamOnce() : null);

        // نعرض لودنغ نفس Optimize Network
        LoadingDialog dialog = LoadingDialog.show(
                primaryStage,
                "Cleaning RAM",
                "Cleaning junk files & freeing memory..."
        );

        // سكربت PowerShell الحقيقي
        String psScript =
                "$ErrorActionPreference='SilentlyContinue';" +
                        "Remove-Item -Path $env:TEMP\\* -Recurse -Force -ErrorAction SilentlyContinue;" +
                        "Remove-Item -Path $env:WINDIR\\Prefetch\\* -Recurse -Force -ErrorAction SilentlyContinue;" +
                        "Remove-Item -Path $env:APPDATA\\Microsoft\\Windows\\Recent\\* -Recurse -Force -ErrorAction SilentlyContinue;";

        // ننفّذ على Thread منفصل
        new Thread(() -> {
            runPowerShellSync(psScript, "[FreeRAM]");

            try { Thread.sleep(1200); } catch (Exception ignored) {}

            SystemMonitorService.RamSnapshot after =
                    (monitor != null ? monitor.readRamOnce() : null);

            Platform.runLater(() -> {
                if (before == null || after == null) {
                    dialog.setDone("Cleanup completed.\n\n(Unable to detect RAM difference)");
                    return;
                }

                double diffGb = before.usedGb - after.usedGb;

                String msg =
                        "Before: " + gbFormat.format(before.usedGb) + " / " + gbFormat.format(before.totalGb) + " GB\n" +
                                "After:  " + gbFormat.format(after.usedGb) + " / " + gbFormat.format(after.totalGb) + " GB\n\n" +
                                "Freed:  " + gbFormat.format(diffGb) + " GB";

                dialog.setDone(msg);
            });

        }).start();
    }


    private void runOptimizeDisk() {
        System.out.println("[ACTION] Optimize Disk clicked");
    }

    private void runOptimizeNetwork() {
        System.out.println("[ACTION] Optimize Network clicked");

        String psScript =
                "$ErrorActionPreference='SilentlyContinue';" +
                        "Write-Host 'Flushing DNS cache...';" +
                        "ipconfig /flushdns | Out-Null;" +
                        "Write-Host 'Resetting IP stack...';" +
                        "netsh int ip reset | Out-Null;" +
                        "Write-Host 'Resetting Winsock...';" +
                        "netsh winsock reset | Out-Null;" +
                        "Write-Host 'Network optimization commands sent. Restart may be required.';";

        runPowerShellWithDialog(
                "Optimizing Network",
                "Flushing DNS and resetting network stack...",
                psScript,
                "[NetOpt]"
        );
    }

    private void runOptimizeUi() {
        System.out.println("[ACTION] Optimize UI clicked");
    }

    private void runScanAndFix() {
        System.out.println("[ACTION] Scan & Fix Files clicked");
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

    private void runPowerShellWithDialog(
            String dialogTitle,
            String dialogMessage,
            String psScript,
            String logTag
    ) {
        int minLoadingSeconds = 10;

        LoadingDialog dialog = LoadingDialog.show(primaryStage, dialogTitle, dialogMessage);

        new Thread(() -> {
            long startTime = System.currentTimeMillis();

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "powershell",
                        "-Command",
                        psScript
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();

                try (BufferedReader r =
                             new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.out.println(logTag + " " + line);
                    }
                }

                p.waitFor();

                long endTime = System.currentTimeMillis();
                long elapsed = endTime - startTime;
                long minMillis = minLoadingSeconds * 1000L;

                if (elapsed < minMillis) {
                    try {
                        Thread.sleep(minMillis - elapsed);
                    } catch (InterruptedException ignored) {}
                }

                Platform.runLater(() ->
                        dialog.setDone("Completed successfully")
                );

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() ->
                        dialog.setFailed("An error occurred while running the command.")
                );
            }
        }).start();
    }

    private void runPowerShellSync(String psScript, String tag) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell",
                    "-Command",
                    psScript
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader r =
                         new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.out.println(tag + " " + line);
                }
            }
            p.waitFor();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void showActionResult(String title, String message) {
        javafx.scene.control.Alert alert =
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        javafx.stage.Stage s = (javafx.stage.Stage) alert.getDialogPane().getScene().getWindow();
        alert.getDialogPane().setStyle(
                "-fx-background-color: #020617;" +
                        "-fx-text-fill: #e5e7eb;"
        );
        alert.getDialogPane().lookup(".content.label")
                .setStyle("-fx-text-fill: #e5e7eb; -fx-font-family: 'Segoe UI';");

        s.getScene().setFill(Color.TRANSPARENT);
        alert.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
    private String shortenGpuName(String full) {
        if (full == null || full.isBlank()) return "Unknown";

        String f = full.toLowerCase();

        // Intel internal GPUs
        if (f.contains("iris")) return "Iris Xe";
        if (f.contains("uhd")) return "UHD Graphics";
        if (f.contains("xe")) return "Intel Xe";

        // AMD internal GPUs
        if (f.contains("vega")) return "Radeon Vega";

        // NVIDIA
        if (f.contains("rtx")) return "RTX";
        if (f.contains("gtx")) return "GTX";

        // AMD desktop cards
        if (f.contains("radeon")) return "Radeon";

        // Default
        return full.length() > 18 ? full.substring(0, 18) + "..." : full;
    }

    private StackPane createTopIcon(String svgContent) {
        javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
        icon.setContent(svgContent);
        icon.setFill(Color.web("#ffffff"));

        StackPane circle = new StackPane(icon);
        circle.setPrefSize(38, 38);
        circle.setStyle(
                "-fx-background-color: rgba(255,255,255,0.18);" +
                        "-fx-background-radius: 50%;" +
                        "-fx-cursor: hand;"
        );
        circle.setAlignment(Pos.CENTER);

        circle.setOnMouseEntered(e ->
                circle.setStyle(
                        "-fx-background-color: rgba(255,255,255,0.32);" +
                                "-fx-background-radius: 50%;" +
                                "-fx-cursor: hand;"
                )
        );
        circle.setOnMouseExited(e ->
                circle.setStyle(
                        "-fx-background-color: rgba(255,255,255,0.18);" +
                                "-fx-background-radius: 50%;" +
                                "-fx-cursor: hand;"
                )
        );

        return circle;
    }



}
