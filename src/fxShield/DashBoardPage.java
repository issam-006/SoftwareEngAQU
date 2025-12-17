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
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class DashBoardPage extends Application {

    private static final Duration PS_TIMEOUT = Duration.ofSeconds(25);

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

    private StackPane topDiskContainer;
    private PhysicalDiskSwitcher diskSwitcher;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.getProperties().put("appInstance", this);

        RemoteConfigService configService = new RemoteConfigService();
        RemoteConfig config = configService.fetchConfig();

        if (config != null && "maintenance".equalsIgnoreCase(config.getAppStatus())) {
            MaintenanceDialog.show(primaryStage, config, () -> {
                RemoteConfig retryConfig = configService.fetchConfig();
                if (retryConfig != null && !"maintenance".equalsIgnoreCase(retryConfig.getAppStatus())) {
                    Platform.runLater(() -> launchNormalUi(primaryStage, retryConfig));
                    return true;
                }
                return false;
            });
            return;
        }

        launchNormalUi(primaryStage, config);
    }

    private void launchNormalUi(Stage stage, RemoteConfig config) {
        root = new BorderPane();
        root.setPadding(new Insets(32, 22, 12, 22));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #020617, #0f172a);");

        Label appTitle = new Label("Fx Shield - System Monitor & Optimizer ");
        appTitle.setFont(Font.font("Segoe UI", 22));
        appTitle.setStyle("-fx-font-weight: bold;");
        appTitle.setTextFill(Color.web("#93C5FD"));
        appTitle.setPadding(new Insets(0, 0, 20, 0));

        root.setTop(appTitle);
        BorderPane.setAlignment(appTitle, Pos.TOP_LEFT);

        cpuCard = new MeterCard("CPU");
        ramCard = new MeterCard("RAM");
        gpuCard = new MeterCard("GPU");

        HBox mainRow = new HBox(18);
        mainRow.setAlignment(Pos.CENTER_LEFT);

        topDiskContainer = new StackPane();
        PhysicalDiskCard placeholder = new PhysicalDiskCard(0, "Loading Disk...", 0);
        topDiskContainer.getChildren().add(placeholder.getRoot());

        mainRow.getChildren().addAll(
                cpuCard.getRoot(),
                ramCard.getRoot(),
                gpuCard.getRoot(),
                topDiskContainer
        );

        HBox.setHgrow(topDiskContainer, Priority.ALWAYS);
        HBox.setHgrow(cpuCard.getRoot(), Priority.ALWAYS);
        HBox.setHgrow(ramCard.getRoot(), Priority.ALWAYS);
        HBox.setHgrow(gpuCard.getRoot(), Priority.ALWAYS);

        disksRow = new HBox(18);
        disksRow.setAlignment(Pos.CENTER_LEFT);

        // ✅ Compact switcher (no title) — will be inserted inside card header
        diskSwitcher = new PhysicalDiskSwitcher(0, 0, idx -> Platform.runLater(() -> swapTopDisk(idx)));

        diskSwitcher.getRoot().setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-padding: 0;"
        );


        ActionCard freeRamCard = new ActionCard(
                "Free RAM",
                "Clean memory and free resources",
                "Run",
                "M10 2 L14 2 L8 14 L4 14 Z"
        );

        ActionCard optimizeDiskCard = new ActionCard(
                "Optimize Disk",
                "Clean & minimize disk usage",
                "Optimize",
                "M3 5 H13 V11 H3 Z M6 8 A1 1 0 1 0 6 8.01\n"
        );

        ActionCard optimizeNetCard = new ActionCard(
                "Optimize Network",
                "Flush DNS & reset tweaks",
                "Optimize",
                "M8 2 L10 6 L8 14 L6 6 Z M7 6 H9\n"
        );

        ActionCard scanFixCard = new ActionCard(
                "Scan & Fix Files",
                "Scan system and fix corrupted files",
                "Scan",
                "M4 4 H14 V14 H4 Z M7 7 L11 11 M11 7 L7 11"
        );

        ActionCard modesCard = new ActionCard(
                "Power Modes",
                "Switch power / balanced / performance",
                "Open",
                "M7 2 L11 8 H8 L10 14 L5 8 H8 Z\n"
        );

        ActionCard allInOneCard = new ActionCard(
                "One Click",
                "Run full optimization package",
                "Boost",
                "M3 6 L8 2 L13 6 L13 14 H3 Z"
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

        toolsGrid.add(freeRamCard.getRoot(), 0, 0);
        toolsGrid.add(optimizeDiskCard.getRoot(), 1, 0);
        toolsGrid.add(optimizeNetCard.getRoot(), 2, 0);
        toolsGrid.add(scanFixCard.getRoot(), 0, 1);
        toolsGrid.add(modesCard.getRoot(), 1, 1);
        toolsGrid.add(allInOneCard.getRoot(), 2, 1);

        GridPane.setHgrow(freeRamCard.getRoot(), Priority.ALWAYS);
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

        VBox actionsWrapper = new VBox(28);
        actionsWrapper.setPadding(new Insets(28, 32, 40, 32));
        actionsWrapper.setStyle(
                "-fx-background-color: rgba(147,197,253,0.18);" +
                        "-fx-background-radius: 26;" +
                        "-fx-border-color: rgba(255,255,255,0.25);" +
                        "-fx-border-radius: 26;" +
                        "-fx-border-width: 1;" +
                        "-fx-effect: dropshadow(gaussian, rgba(147,197,253,0.80), 1, 0.7, 0, 0)," +
                        " dropshadow(gaussian, rgba(147,197,253,0.35), 20, 0.4, 0, 0)," +
                        " dropshadow(gaussian, rgba(147,197,253,0.55), 35, 0.5, 0, 0);"
        );

        actionsWrapper.setBorder(new Border(new BorderStroke(
                Color.rgb(147, 197, 253, 0.25),
                BorderStrokeStyle.SOLID,
                new CornerRadii(22),
                new BorderWidths(1.4)
        )));

        Label actionsTitle = new Label("Quick Optimization Tools");
        actionsTitle.setFont(Font.font("Segoe UI", 22));
        actionsTitle.setTextFill(Color.web("#000000"));
        actionsTitle.setStyle("-fx-font-weight: bold;");

        TopBarIcons topIcons = new TopBarIcons();

        HBox titleRow = new HBox();
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setPadding(new Insets(0, 0, 10, 0));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleRow.getChildren().addAll(actionsTitle, spacer, topIcons.getRoot());
        actionsWrapper.getChildren().add(titleRow);
        actionsWrapper.getChildren().add(toolsGrid);

        VBox centerBox = new VBox(28);
        centerBox.setFillWidth(true);
        centerBox.getChildren().addAll(mainRow, disksRow, actionsWrapper);

        root.setCenter(centerBox);

        Scene scene = new Scene(root, 1280, 720);
        stage.setTitle("FX Shield - System Monitor & Optimizer");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();

        AutomationService.get().apply(SettingsStore.load());

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
                        if (diskSnaps != null && physicalCards != null && physicalCards.length > 0) {
                            updatePhysicalDisksUI(diskSnaps);
                        }
                    });
                });

                Platform.runLater(() -> {
                    this.monitor = m;

                    gpuCard.getTitleLabel().setText("GPU - " + shortenGpuName(gpuName));

                    if (initialDisks != null && initialDisks.length > 0) {
                        physicalCards = new PhysicalDiskCard[initialDisks.length];
                        disksRow.getChildren().clear();

                        for (int i = 0; i < initialDisks.length; i++) {
                            SystemMonitorService.PhysicalDiskSnapshot snap = initialDisks[i];
                            PhysicalDiskCard card = new PhysicalDiskCard(i, snap.model, snap.sizeGb);
                            physicalCards[i] = card;

                            if (i == 0) {
                                // ✅ insert switcher into the card header (no overlay)
                                card.setSwitcherNode(diskSwitcher.getRoot());

                                topDiskContainer.getChildren().setAll(card.getRoot());
                                HBox.setHgrow(topDiskContainer, Priority.ALWAYS);

                            } else {
                                disksRow.getChildren().add(card.getRoot());
                                HBox.setHgrow(card.getRoot(), Priority.ALWAYS);
                            }
                        }

                        diskSwitcher.setCount(initialDisks.length);
                        diskSwitcher.setSelectedIndex(0);
                        swapTopDisk(0);

                    } else {
                        physicalCards = new PhysicalDiskCard[0];
                        diskSwitcher.setCount(0);
                        Label noDisk = new Label("No physical disks detected.");
                        noDisk.setTextFill(Color.web("#9ca3af"));
                        disksRow.getChildren().add(noDisk);
                    }

                    m.start();
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    disksRow.getChildren().setAll();
                    Label err = new Label("Error initializing system monitor.");
                    err.setTextFill(Color.web("#f97373"));
                    disksRow.getChildren().add(err);
                });
            }
        }, "fxShield-ui-init").start();

        stage.setOnCloseRequest(e -> {
            if (monitor != null) monitor.stop();
            AutomationService.get().stop();
        });
    }

    private void swapTopDisk(int index) {
        if (physicalCards == null || physicalCards.length == 0) return;
        if (index < 0 || index >= physicalCards.length) return;

        PhysicalDiskCard card = physicalCards[index];

        // ✅ keep switcher in header for the new card
        card.setSwitcherNode(diskSwitcher.getRoot());

        topDiskContainer.getChildren().setAll(card.getRoot());
        HBox.setHgrow(topDiskContainer, Priority.ALWAYS);
    }

    private void updateCpuUI(double percent) {
        if (percent < 0) {
            cpuCard.setUnavailable("System CPU usage");
            return;
        }
        cpuCard.setValuePercent(percent, "System CPU usage");
    }

    private void updateRamUI(SystemMonitorService.RamSnapshot snap) {
        if (snap == null) return;
        cpuCard.getExtraLabel().setText("System CPU usage");
        double percent = snap.percent;
        ramCard.setValuePercent(percent, gbFormat.format(snap.usedGb) + " / " + gbFormat.format(snap.totalGb) + " GB");
    }

    private void updateGpuUI(int usage) {
        double percent = Math.max(0, usage);
        String extra = (monitor == null || !monitor.isGpuUsageSupported())
                ? "GPU usage not supported on this system"
                : "GPU utilization";
        gpuCard.setValuePercent(percent, extra);
    }

    private void updatePhysicalDisksUI(SystemMonitorService.PhysicalDiskSnapshot[] snaps) {
        if (snaps == null || physicalCards == null) return;
        int len = Math.min(snaps.length, physicalCards.length);
        for (int i = 0; i < len; i++) {
            SystemMonitorService.PhysicalDiskSnapshot snap = snaps[i];
            PhysicalDiskCard card = physicalCards[i];
            if (card == null || snap == null) continue;

            if (snap.hasUsage) {
                card.getUsedValueLabel().setText("Used: " + percentFormat.format(snap.usedPercent) + " %");
                card.getSpaceLabel().setText(gbFormat.format(snap.usedGb) + " / " + gbFormat.format(snap.totalGb) + " GB");
                card.getUsedBar().setProgress(clamp01(snap.usedPercent / 100.0));
            } else {
                card.getUsedValueLabel().setText("Used: N/A");
                card.getSpaceLabel().setText("Size: " + gbFormat.format(snap.sizeGb) + " GB");
                card.getUsedBar().setProgress(0);
            }
            card.getActiveValueLabel().setText("Active: " + percentFormat.format(snap.activePercent) + " %");
            card.getActiveBar().setProgress(clamp01(snap.activePercent / 100.0));
        }
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private void runFreeRam() {
        System.out.println("[ACTION] Free RAM clicked");
        SystemMonitorService.RamSnapshot before = (monitor != null ? monitor.readRamOnce() : null);

        LoadingDialog dialog = LoadingDialog.show(primaryStage, "Cleaning RAM", "Cleaning junk files & freeing memory...");

        String psScript =
                "$ErrorActionPreference='SilentlyContinue';" +
                        "Remove-Item -Path \"$env:TEMP\\*\" -Recurse -Force -ErrorAction SilentlyContinue;" +
                        "Remove-Item -Path \"$env:WINDIR\\Prefetch\\*\" -Recurse -Force -ErrorAction SilentlyContinue;" +
                        "Remove-Item -Path \"$env:APPDATA\\Microsoft\\Windows\\Recent\\*\" -Recurse -Force -ErrorAction SilentlyContinue;";

        new Thread(() -> {
            runPowerShellSync(psScript, "[FreeRAM]");
            try { Thread.sleep(900); } catch (Exception ignored) {}
            SystemMonitorService.RamSnapshot after = (monitor != null ? monitor.readRamOnce() : null);
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
        }, "fxShield-action-freeRam").start();
    }

    private void runOptimizeDisk() {
        System.out.println("[ACTION] Optimize Disk clicked");
    }

    private void runOptimizeNetwork() {
        System.out.println("[ACTION] Optimize Network clicked");

        String psScript =
                "$ErrorActionPreference='SilentlyContinue';" +
                        "ipconfig /flushdns | Out-Null;" +
                        "netsh int ip reset | Out-Null;" +
                        "netsh winsock reset | Out-Null;" +
                        "Write-Host 'Network optimization commands sent. Restart may be required.';";

        runPowerShellWithRebootDialog(
                "Optimizing Network",
                "Flushing DNS and resetting network stack...",
                psScript,
                "[NetOpt]"
        );
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
    }

    private void runPowerShellWithRebootDialog(String dialogTitle, String dialogMessage, String psScript, String logTag) {
        int minLoadingSeconds = 8;
        LoadingDialogReboot dialog = LoadingDialogReboot.show(primaryStage, dialogTitle, dialogMessage);

        new Thread(() -> {
            long start = System.currentTimeMillis();
            boolean ok = runPowerShellSync(psScript, logTag);
            long elapsed = System.currentTimeMillis() - start;
            long remain = Math.max(0, minLoadingSeconds * 1000L - elapsed);
            try { Thread.sleep(remain); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> {
                if (ok) dialog.setDoneRequiresReboot("Network optimization completed successfully.");
                else dialog.setFailed("An error occurred while running the command.");
            });
        }, "fxShield-action-ps-reboot").start();
    }

    private boolean runPowerShellSync(String psScript, String tag) {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psScript
            );
            pb.redirectErrorStream(true);
            p = pb.start();

            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.out.println(tag + " " + line);
                }
            }

            boolean finished = p.waitFor(PS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception ex) {
            if (p != null) try { p.destroyForcibly(); } catch (Exception ignored) {}
            ex.printStackTrace();
            return false;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private String shortenGpuName(String full) {
        if (full == null || full.isBlank()) return "Unknown";
        String f = full.toLowerCase();

        if (f.contains("iris")) return "Iris Xe";
        if (f.contains("uhd")) return "UHD Graphics";
        if (f.contains("xe")) return "Intel Xe";
        if (f.contains("vega")) return "Radeon Vega";
        if (f.contains("rtx")) return "RTX";
        if (f.contains("gtx")) return "GTX";
        if (f.contains("radeon")) return "Radeon";

        return full.length() > 18 ? full.substring(0, 18) + "..." : full;
    }
}
