package fxShield;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

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

    private ActionCard[] actionCards;
    private GridPane toolsGrid;
    private int currentCols = -1;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.getProperties().put("appInstance", this);

        try {
            stage.initStyle(StageStyle.TRANSPARENT);
        } catch (Exception ignored) {}

        // Immediate lightweight UI
        showSplashScreen(stage);

        new Thread(() -> {
            RemoteConfigService configService = new RemoteConfigService();
            RemoteConfig config = configService.fetchConfig();

            Platform.runLater(() -> {
                if (config != null && "maintenance".equalsIgnoreCase(config.getAppStatus())) {
                    MaintenanceDialog.show(primaryStage, config, () -> {
                        RemoteConfig retryConfig = configService.fetchConfig();
                        if (retryConfig != null && !"maintenance".equalsIgnoreCase(retryConfig.getAppStatus())) {
                            Platform.runLater(() -> launchNormalUi(primaryStage, retryConfig));
                            return true;
                        }
                        return false;
                    });
                } else {
                    launchNormalUi(primaryStage, config);
                }
            });
        }, "fxShield-startup-config").start();
    }

    private void showSplashScreen(Stage stage) {
        BorderPane splashRoot = new BorderPane();
        splashRoot.setPadding(new Insets(0, 22, 12, 22));

        // Dynamic style for rounded corners
        Runnable updateStyle = () -> {
            if (stage.isFullScreen()) {
                splashRoot.setStyle("-fx-background-color: #020617; -fx-background-radius: 0; -fx-border-radius: 0; -fx-border-width: 0;");
            } else {
                splashRoot.setStyle("-fx-background-color: #020617; -fx-background-radius: 24; -fx-border-radius: 24; -fx-border-color: rgba(147,197,253,0.3); -fx-border-width: 1.5;");
            }
        };
        updateStyle.run();
        stage.fullScreenProperty().addListener((obs, oldVal, newVal) -> updateStyle.run());

        TopBarIcons topIcons = new TopBarIcons();
        WindowsSnapLayouts snapLayouts = new WindowsSnapLayouts();
        snapLayouts.install(stage, topIcons.getMaximizeButton());
        for (Node n : topIcons.getInteractiveNodes()) snapLayouts.addInteractive(n);

        Node topIconsRoot = topIcons.getRoot();
        
        // Use a header for splash too to allow better dragging
        Region splashSpacer = new Region();
        HBox.setHgrow(splashSpacer, Priority.ALWAYS);
        HBox splashHeader = new HBox(splashSpacer, topIconsRoot);
        splashHeader.setPadding(new Insets(32, 0, 0, 0)); // Move top padding here
        splashHeader.setPickOnBounds(true);
        
        // Native drag-and-move
        snapLayouts.setDragArea(splashHeader);

        splashRoot.setTop(splashHeader);

        VBox centerBox = new VBox(25);
        centerBox.setAlignment(Pos.CENTER);

        Label title = new Label("FX SHIELD");
        title.setFont(Font.font("Segoe UI", 52));
        title.setTextFill(Color.web("#93C5FD"));
        title.setStyle("-fx-font-weight: bold; -fx-letter-spacing: 5;");

        ProgressBar progress = new ProgressBar();
        progress.setPrefWidth(320);
        progress.setPrefHeight(6);
        progress.setStyle("-fx-accent: #3b82f6; -fx-control-inner-background: rgba(255,255,255,0.1);");

        Label status = new Label("Connecting to server...");
        status.setFont(Font.font("Segoe UI", 15));
        status.setTextFill(Color.web("#64748b"));

        centerBox.getChildren().addAll(title, progress, status);
        splashRoot.setCenter(centerBox);

        Scene scene = new Scene(splashRoot, 1280, 720, Color.TRANSPARENT);
        stage.setTitle("FX Shield - Loading");
        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.setFullScreenExitHint("");
        stage.show();
    }

    private void launchNormalUi(Stage stage, RemoteConfig config) {
        root = new BorderPane();
        root.setPadding(new Insets(0, 22, 12, 22));

        // Dynamic style for rounded corners & borders
        Runnable updateStyle = () -> {
            if (stage.isFullScreen()) {
                root.setStyle("-fx-background-color: linear-gradient(to bottom, #020617, #0f172a); -fx-background-radius: 0; -fx-border-radius: 0; -fx-border-width: 0;");
            } else {
                root.setStyle("-fx-background-color: linear-gradient(to bottom, #020617, #0f172a); -fx-background-radius: 24; -fx-border-radius: 24; -fx-border-color: rgba(147,197,253,0.3); -fx-border-width: 1.5;");
            }
        };
        updateStyle.run();
        stage.fullScreenProperty().addListener((obs, oldVal, newVal) -> updateStyle.run());

        Label appTitle = new Label("Fx Shield - System Monitor & Optimizer ");
        appTitle.setFont(Font.font("Segoe UI", 22));
        appTitle.setStyle("-fx-font-weight: bold;");
        appTitle.setTextFill(Color.web("#93C5FD"));

        TopBarIcons topIcons = new TopBarIcons();
        WindowsSnapLayouts snapLayouts = new WindowsSnapLayouts();
        snapLayouts.install(stage, topIcons.getMaximizeButton());
        for (Node n : topIcons.getInteractiveNodes()) snapLayouts.addInteractive(n);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox header = new HBox(appTitle, headerSpacer, topIcons.getRoot());
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(32, 0, 20, 0)); // Moved top padding here
        header.setPickOnBounds(true);

        // Native drag-and-move via HTCAPTION support in WindowsSnapLayouts
        snapLayouts.setDragArea(header);

        root.setTop(header);
        BorderPane.setAlignment(header, Pos.TOP_LEFT);

        cpuCard = new MeterCard("CPU");
        ramCard = new MeterCard("RAM");
        gpuCard = new MeterCard("GPU");

        HBox mainRow = new HBox(18);
        mainRow.setAlignment(Pos.CENTER);

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
        disksRow.setAlignment(Pos.CENTER);

        // ✅ Compact switcher (no title) — will be inserted inside card header
        diskSwitcher = new PhysicalDiskSwitcher(0, 0, idx -> Platform.runLater(() -> swapTopDisk(idx)));

        diskSwitcher.getRoot().setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-padding: 0;"
        );


        actionCards = new ActionCard[] {
                new ActionCard("Free RAM", "Clean memory and free resources", "Run", "M10 2 L14 2 L8 14 L4 14 Z"),
                new ActionCard("Optimize Disk", "Clean & minimize disk usage", "Optimize", "M3 5 H13 V11 H3 Z M6 8 A1 1 0 1 0 6 8.01\n"),
                new ActionCard("Optimize Network", "Flush DNS & reset tweaks", "Optimize", "M8 2 L10 6 L8 14 L6 6 Z M7 6 H9\n"),
                new ActionCard("Scan & Fix Files", "Scan system and fix corrupted files", "Scan", "M4 4 H14 V14 H4 Z M7 7 L11 11 M11 7 L7 11"),
                new ActionCard("Power Modes", "Switch power / balanced / performance", "Open", "M7 2 L11 8 H8 L10 14 L5 8 H8 Z\n"),
                new ActionCard("One Click", "Run full optimization package", "Boost", "M3 6 L8 2 L13 6 L13 14 H3 Z")
        };

        actionCards[0].getButton().setOnAction(e -> runFreeRam());
        actionCards[1].getButton().setOnAction(e -> runOptimizeDisk());
        actionCards[2].getButton().setOnAction(e -> runOptimizeNetwork());
        actionCards[3].getButton().setOnAction(e -> runScanAndFix());
        actionCards[4].getButton().setOnAction(e -> runModes());
        actionCards[5].getButton().setOnAction(e -> runAllInOne());

        toolsGrid = new GridPane();
        toolsGrid.setHgap(18);
        toolsGrid.setVgap(18);
        toolsGrid.setPadding(new Insets(12, 0, 0, 0));

        reconfigureToolsGrid(3);

        VBox actionsWrapper = new VBox(28);
        actionsWrapper.setPadding(new Insets(28, 32, 40, 32));
        actionsWrapper.setStyle(
                "-fx-background-color: rgba(147,197,253,0.12);" +
                        "-fx-background-radius: 26;" +
                        "-fx-border-color: rgba(255,255,255,0.15);" +
                        "-fx-border-radius: 26;" +
                        "-fx-border-width: 1;" +
                        "-fx-effect: dropshadow(gaussian, rgba(147,197,253,0.15), 15, 0, 0, 0);"
        );

        actionsWrapper.setBorder(new Border(new BorderStroke(
                Color.rgb(147, 197, 253, 0.2),
                BorderStrokeStyle.SOLID,
                new CornerRadii(26),
                new BorderWidths(1)
        )));

        Label actionsTitle = new Label("Quick Optimization Tools");
        actionsTitle.setFont(Font.font("Segoe UI", 22));
        actionsTitle.setTextFill(Color.web("#f8fafc"));
        actionsTitle.setStyle("-fx-font-weight: bold;");
        actionsTitle.setPadding(new Insets(0, 0, 10, 0));

        actionsWrapper.getChildren().add(actionsTitle);
        actionsWrapper.getChildren().add(toolsGrid);

        VBox centerBox = new VBox(28);
        centerBox.setFillWidth(true);
        centerBox.getChildren().addAll(mainRow, disksRow, actionsWrapper);

        ScrollPane scrollPane = new ScrollPane(centerBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-padding: 0;");
        scrollPane.setFocusTraversable(false);

        root.setCenter(scrollPane);

        Scene scene = new Scene(root, 1280, 720, Color.TRANSPARENT);
        scene.getStylesheets().add("data:text/css," + encodeCss(SCROLL_CSS));
        stage.setTitle("FX Shield - System Monitor & Optimizer");

        // Dynamic Layout: Adjust tools grid & switcher size when window resizes
        scene.widthProperty().addListener((obs, oldW, newW) -> {
            double w = newW.doubleValue();
            int cols = 3;
            if (w < 880) cols = 1;
            else if (w < 1350) cols = 2;
            reconfigureToolsGrid(cols);

            boolean compact = w < 1350;
            if (cpuCard != null) cpuCard.setCompact(compact);
            if (ramCard != null) ramCard.setCompact(compact);
            if (gpuCard != null) gpuCard.setCompact(compact);
            if (physicalCards != null) {
                for (PhysicalDiskCard c : physicalCards) {
                    if (c != null) c.setCompact(compact);
                }
            }

            if (diskSwitcher != null) {
                diskSwitcher.setVeryCompact(w < 1100);
            }

            appTitle.setVisible(w > 650);
            appTitle.setManaged(w > 650);
        });

        // ✅ Enhance: Smooth Fade-in
        root.setOpacity(0);
        FadeTransition ft = new FadeTransition(javafx.util.Duration.millis(650), root);
        ft.setFromValue(0);
        ft.setToValue(1);

        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.setFullScreenExitHint("");
        stage.show();
        ft.play();

        // Resource optimization: Stop monitor when app is minimized
        stage.iconifiedProperty().addListener((obs, minimized, restored) -> {
            if (minimized) {
                if (monitor != null) monitor.stop();
            } else {
                if (monitor != null) monitor.start();
            }
        });

        new Thread(() -> {
            try {
                // Background task: Automation (PowerShell calls inside)
                AutomationService.get().apply(SettingsStore.load());

                // Background task: Monitor
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
                            card.setCompact(stage.getScene().getWidth() < 1350);
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

    private void reconfigureToolsGrid(int cols) {
        if (toolsGrid == null || actionCards == null) return;
        if (cols == currentCols) return;
        currentCols = cols;

        toolsGrid.getChildren().clear();
        toolsGrid.getColumnConstraints().clear();

        for (int i = 0; i < cols; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setHgrow(Priority.ALWAYS);
            toolsGrid.getColumnConstraints().add(cc);
        }

        for (int i = 0; i < actionCards.length; i++) {
            int row = i / cols;
            int col = i % cols;
            toolsGrid.add(actionCards[i].getRoot(), col, row);
            GridPane.setHgrow(actionCards[i].getRoot(), Priority.ALWAYS);
            GridPane.setVgrow(actionCards[i].getRoot(), Priority.ALWAYS);
        }
    }

    private static final String SCROLL_CSS = """
        .scroll-pane { -fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0; }
        .scroll-pane > .viewport { -fx-background-color: transparent; }

        .scroll-bar:vertical { -fx-background-color: transparent; -fx-padding: 2; }
        .scroll-bar:vertical .track {
            -fx-background-color: rgba(255,255,255,0.04);
            -fx-background-radius: 999;
        }
        .scroll-bar:vertical .thumb {
            -fx-background-color: rgba(255,255,255,0.22);
            -fx-background-radius: 999;
        }
        .scroll-bar:vertical .increment-button, .scroll-bar:vertical .decrement-button {
            -fx-padding: 0; -fx-background-color: transparent;
        }
        .scroll-bar .increment-arrow, .scroll-bar .decrement-arrow { -fx-shape: ""; -fx-padding: 0; }
    """;

    private String encodeCss(String css) {
        return css.replace("\n", "%0A")
                .replace(" ", "%20")
                .replace("#", "%23")
                .replace("{", "%7B")
                .replace("}", "%7D")
                .replace(";", "%3B")
                .replace(":", "%3A")
                .replace(",", "%2C")
                .replace("\"", "%22");
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
