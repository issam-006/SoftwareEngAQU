package fxShield.UX;

import fxShield.DB.*;
import fxShield.WIN.*;
import fxShield.UI.*;
import fxShield.GPU.*;
import fxShield.DISK.*;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCombination;
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
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class DashBoardPage extends Application {

    private static final Pattern EAP_BAD =
            Pattern.compile("(?im)^\\s*\\$ErrorActionPreference\\s*=\\s*(SilentlyContinue|Continue|Stop|Inquire)\\s*;?\\s*$");

    private static final Duration PS_TIMEOUT = Duration.ofSeconds(25);

    private final RemoteConfigService configService = new RemoteConfigService();

    private Stage primaryStage;
    private BorderPane root;

    private Label appTitle;

    private MeterCard cpuCard;
    private MeterCard ramCard;
    private MeterCard gpuCard;

    private PhysicalDiskCard[] physicalCards;

    private final DecimalFormat percentFormat = new DecimalFormat("0.0");
    private final DecimalFormat gbFormat = new DecimalFormat("0.0");

    private SystemMonitorService monitor;
    private HBox disksRow;

    private boolean isTraySupported = false;

    private StackPane topDiskContainer;
    private PhysicalDiskSwitcher diskSwitcher;

    private ActionCard[] actionCards;
    private GridPane toolsGrid;
    private int currentCols = -1;

    // ---------- Responsive (FINAL FIX) ----------
    private final PauseTransition responsiveDebounce = new PauseTransition(javafx.util.Duration.millis(140));
    private double pendingWidth = -1;

    private boolean compactState = false;
    private boolean diskVeryCompactState = false;
    private boolean responsiveInstalled = false;

    // Hysteresis (تمنع toggling على micro-jitter)
    private static final double COMPACT_ON  = 1150;
    private static final double COMPACT_OFF = 1250;

    private static final double DISK_VC_ON  = 1100;
    private static final double DISK_VC_OFF = 1160;

    private double lastAppliedWidth = -1;

    private ScrollPane mainScroll;

    // تفعيل اللوج
    private static final boolean DEBUG_RESP = true;

    private static String fmtW(double v) {
        if (v <= 0) return "-";
        return String.valueOf(Math.rint(v));
    }

    private void logRespTransition(String what, double responsiveWidth, int cols) {
        double stageW = (primaryStage != null) ? primaryStage.getWidth() : -1;
        double sceneW = (primaryStage != null && primaryStage.getScene() != null) ? primaryStage.getScene().getWidth() : -1;

        FxShieldDebugLog.log(
                "[RESP] " + what +
                        " width=" + fmtW(responsiveWidth) +
                        " cols=" + cols +
                        " compact=" + compactState +
                        " diskVeryCompact=" + diskVeryCompactState +
                        " stageW=" + fmtW(stageW) +
                        " sceneW=" + fmtW(sceneW),
                new RuntimeException("responsive transition")
        );
    }

    @Override
    public void start(Stage stage) {
        System.err.println("[DBG] DashBoardPage.start() RUNNING");
        System.err.flush();

        FxShieldDebugLog.log("[START] DashBoardPage.start(); log=" + FxShieldDebugLog.path());

        this.primaryStage = stage;
        stage.getProperties().put("appInstance", this);

        boolean startMinimized = getParameters().getRaw().contains("--minimized");

        try { stage.initStyle(StageStyle.TRANSPARENT); } catch (Exception ignored) {}

        setupTrayIcon(stage);
        showSplashScreen(stage);

        if (startMinimized && isTraySupported) {
            Platform.runLater(() -> stage.setIconified(true));
        }

        AtomicBoolean launched = new AtomicBoolean(false);

        Thread t = new Thread(() -> {
            RemoteConfig cfg = null;
            try { cfg = configService.fetchConfig(); } catch (Exception ignored) {}
            RemoteConfig finalCfg = cfg;

            Platform.runLater(() -> {
                if (!launched.compareAndSet(false, true)) return;

                if (finalCfg != null && finalCfg.isMaintenance()) {
                    MaintenanceDialog.show(
                            primaryStage,
                            finalCfg,
                            configService::fetchConfig,
                            okCfg -> {
                                if (okCfg != null && !okCfg.isMaintenance()) {
                                    launchNormalUi(primaryStage, okCfg);
                                }
                            }
                    );
                    return;
                }

                launchNormalUi(primaryStage, finalCfg);
            });
        }, "fxShield-startup-config");

        t.setDaemon(true);
        t.start();
    }

    // =============== RESPONSIVE (FINAL FIX) ===============

    private void installResponsive(Stage stage, Scene scene) {
        if (responsiveInstalled) return;
        responsiveInstalled = true;

        responsiveDebounce.setOnFinished(e -> applyResponsiveLayoutNow(pendingWidth));

        // ✅ Single source of truth: Scene width فقط (مش viewport)
        if (scene != null) {
            scene.widthProperty().addListener((obs, o, n) -> {
                if (n == null) return;
                scheduleResponsive(n.doubleValue());
            });
        } else {
            // fallback
            stage.widthProperty().addListener((obs, o, n) -> {
                if (n == null) return;
                scheduleResponsive(n.doubleValue());
            });
        }

        // initial after layout
        Platform.runLater(() -> scheduleResponsive(currentResponsiveWidth(stage)));
    }


    private double currentResponsiveWidth(Stage stage) {
        if (stage != null && stage.getScene() != null) {
            double sw = stage.getScene().getWidth();
            if (sw > 0) return sw;
        }
        return stage != null ? stage.getWidth() : -1;
    }

    private void scheduleResponsive(double w) {
        if (w <= 0) return;

        if (DEBUG_RESP) {
            double stageW = (primaryStage != null) ? primaryStage.getWidth() : -1;
            double sceneW = (primaryStage != null && primaryStage.getScene() != null) ? primaryStage.getScene().getWidth() : -1;
            double viewportW = (mainScroll != null && mainScroll.getViewportBounds() != null) ? mainScroll.getViewportBounds().getWidth() : -1;
            System.out.println("[RESP-SCHED] w=" + Math.rint(w)
                    + " stageW=" + Math.rint(stageW)
                    + " sceneW=" + Math.rint(sceneW)
                    + " viewportW=" + Math.rint(viewportW));
        }

        pendingWidth = w;
        responsiveDebounce.playFromStart();
    }


    private void applyResponsiveLayoutNow(double w) {
        if (w <= 0) return;

        double width = Math.rint(w);

        // ✅ تجاهل تكرار نفس العرض (يقتل jitter من أي سبب)
        if (lastAppliedWidth == width) return;
        lastAppliedWidth = width;

        int cols = 3;
        if (width < 880) cols = 1;
        else if (width < 1350) cols = 2;

        reconfigureToolsGrid(cols);

        boolean nextCompact = compactState
                ? (width < COMPACT_OFF)
                : (width < COMPACT_ON);

        if (nextCompact != compactState) {
            compactState = nextCompact;

            logRespTransition("compact=" + compactState, width, cols);

            if (DEBUG_RESP) {
                System.out.println("[RESP] width=" + width + " compact=" + compactState + " cols=" + cols);
            }

            if (cpuCard != null) cpuCard.setCompact(compactState);
            if (ramCard != null) ramCard.setCompact(compactState);
            if (gpuCard != null) gpuCard.setCompact(compactState);

            if (physicalCards != null) {
                for (PhysicalDiskCard c : physicalCards) {
                    if (c != null) c.setCompact(compactState);
                }
            }

            if (topDiskContainer != null) {
                topDiskContainer.setMinWidth(compactState ? 200 : 280);
                topDiskContainer.setPrefWidth(compactState ? 240 : 320);
                topDiskContainer.setMinHeight(compactState ? 180 : 240);
            }
        }

        boolean nextDiskVeryCompact = diskVeryCompactState
                ? (width < DISK_VC_OFF)
                : (width < DISK_VC_ON);

        if (diskSwitcher != null && nextDiskVeryCompact != diskVeryCompactState) {
            diskVeryCompactState = nextDiskVeryCompact;

            logRespTransition("diskVeryCompact=" + diskVeryCompactState, width, cols);
            diskSwitcher.setVeryCompact(diskVeryCompactState);
        }

        if (appTitle != null) {
            boolean show = width > 650;
            appTitle.setVisible(show);
            appTitle.setManaged(show);
        }
    }

    // =============== UI BUILD ===============

    private void launchNormalUi(Stage stage, RemoteConfig config) {
        root = new BorderPane();
        root.setPadding(new Insets(0, 22, 12, 22));

        Runnable updateStyle = () -> {
            if (stage.isFullScreen()) {
                root.setStyle("-fx-background-color: linear-gradient(to bottom, #020617, #0f172a); -fx-background-radius: 0; -fx-border-radius: 0; -fx-border-width: 0;");
            } else {
                root.setStyle("-fx-background-color: linear-gradient(to bottom, #020617, #0f172a); -fx-background-radius: 24; -fx-border-radius: 24; -fx-border-color: rgba(147,197,253,0.3); -fx-border-width: 1.5;");
            }
        };
        updateStyle.run();
        stage.fullScreenProperty().addListener((obs, o, n) -> updateStyle.run());

        appTitle = new Label("Fx Shield - System Monitor & Optimizer ");
        appTitle.setFont(Font.font("Segoe UI", 22));
        appTitle.setStyle("-fx-font-weight: bold;");
        appTitle.setTextFill(Color.web("#93C5FD"));

        TopBarIcons topIcons = new TopBarIcons();

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox header = new HBox(appTitle, headerSpacer, topIcons.getRoot());
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(32, 0, 20, 0));
        header.setPickOnBounds(true);

        root.setTop(header);
        BorderPane.setAlignment(header, Pos.TOP_LEFT);

        cpuCard = new MeterCard("CPU");
        ramCard = new MeterCard("RAM");
        gpuCard = new MeterCard("GPU");

        HBox mainRow = new HBox(18);
        mainRow.setAlignment(Pos.CENTER);

        topDiskContainer = new StackPane();
        topDiskContainer.setMinWidth(280);
        topDiskContainer.setPrefWidth(320);
        topDiskContainer.setMaxWidth(520);
        topDiskContainer.setMinHeight(240);
        topDiskContainer.setAlignment(Pos.CENTER);

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

        diskSwitcher = new PhysicalDiskSwitcher(0, 0, idx -> Platform.runLater(() -> swapTopDisk(idx)));
        diskSwitcher.getRoot().setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        actionCards = new ActionCard[] {
                new ActionCard("Free RAM", "Clean memory and free resources", "Run", "M10 2 L14 2 L8 14 L4 14 Z"),
                new ActionCard("Optimize Disk", "Clean & minimize disk usage", "Optimize", "M3 5 H13 V11 H3 Z M6 8 A1 1 0 1 0 6 8.01\n"),
                new ActionCard("Optimize Network", "Flush DNS & reset tweaks", "Optimize", "M8 2 L10 6 L8 14 L6 6 Z M7 6 H9\n"),
                new ActionCard("Scan & Fix Files", "Scan system and fix corrupted files", "Scan", "M4 4 H14 V14 H4 Z M7 7 L11 11 M11 7 L7 11"),
                new ActionCard("Power Modes", "Switch power / balanced / performance", "Open", "M7 2 L11 8 H8 L10 14 L5 8 H8 Z\n"),
                new ActionCard("One Click", "Run full optimization package", "Boost", "M3 6 L8 2 L13 6 L13 14 H3 Z")
        };

        actionCards[0].getButton().setOnAction(e -> runFreeRamFromDb());
        actionCards[1].getButton().setOnAction(e -> runDbScript(ScriptKey.OPTIMIZE_DISK, "Optimizing Disk", "Fetching latest script from server...", "[DiskOpt]", false));
        actionCards[2].getButton().setOnAction(e -> runDbScript(ScriptKey.OPTIMIZE_NETWORK, "Optimizing Network", "Fetching latest script from server...", "[NetOpt]", true));
        actionCards[3].getButton().setOnAction(e -> runDbScript(ScriptKey.SCAN_AND_FIX, "System File Scan", "Fetching latest script from server... (may take a few minutes)", "[SFC]", false));
        actionCards[4].getButton().setOnAction(e -> PowerModeDialog.show(primaryStage));
        actionCards[5].getButton().setOnAction(e -> runAllInOneFromDb());

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

        Label actionsTitle = new Label("Quick Optimization Tools");
        actionsTitle.setFont(Font.font("Segoe UI", 22));
        actionsTitle.setTextFill(Color.web("#f8fafc"));
        actionsTitle.setStyle("-fx-font-weight: bold;");
        actionsTitle.setPadding(new Insets(0, 0, 10, 0));

        actionsWrapper.getChildren().addAll(actionsTitle, toolsGrid);

        VBox centerBox = new VBox(28);
        centerBox.setFillWidth(true);
        centerBox.getChildren().addAll(mainRow, disksRow, actionsWrapper);

        mainScroll = new ScrollPane(centerBox);
        mainScroll.setFitToWidth(true);
        mainScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        mainScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        mainScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-padding: 0;");
        mainScroll.setFocusTraversable(false);

        root.setCenter(mainScroll);

        Scene scene = new Scene(root, 1280, 720, Color.TRANSPARENT);
        scene.getStylesheets().add("data:text/css," + encodeCss(SCROLL_CSS));

        stage.setTitle("FX Shield - System Monitor & Optimizer");

        root.setOpacity(0);
        FadeTransition ft = new FadeTransition(javafx.util.Duration.millis(650), root);
        ft.setFromValue(0);
        ft.setToValue(1);

        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.setFullScreenExitHint("");
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        stage.show();
        ft.play();

        // ✅ Responsive: stage width only
        installResponsive(stage, scene);

        stage.iconifiedProperty().addListener((obs, minimized, restored) -> {
            if (minimized) {
                if (monitor != null) monitor.stop();
            } else {
                if (monitor != null) monitor.start();
            }
        });

        new Thread(() -> {
            try {
                AutomationService.get().apply(SettingsStore.load());

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

                    // ✅ re-apply once after disks inserted (stage width only)
                    scheduleResponsive(stage.getWidth());

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

        stage.setOnCloseRequest(e -> hardExit());
    }

    private void showSplashScreen(Stage stage) {
        BorderPane splashRoot = new BorderPane();
        splashRoot.setPadding(new Insets(0, 22, 12, 22));

        Runnable updateStyle = () -> {
            if (stage.isFullScreen()) {
                splashRoot.setStyle("-fx-background-color: #020617; -fx-background-radius: 0; -fx-border-radius: 0; -fx-border-width: 0;");
            } else {
                splashRoot.setStyle("-fx-background-color: #020617; -fx-background-radius: 24; -fx-border-radius: 24; -fx-border-color: rgba(147,197,253,0.3); -fx-border-width: 1.5;");
            }
        };
        updateStyle.run();
        stage.fullScreenProperty().addListener((obs, o, n) -> updateStyle.run());

        TopBarIcons topIcons = new TopBarIcons();
        Node topIconsRoot = topIcons.getRoot();

        Region splashSpacer = new Region();
        HBox.setHgrow(splashSpacer, Priority.ALWAYS);
        HBox splashHeader = new HBox(splashSpacer, topIconsRoot);
        splashHeader.setPadding(new Insets(32, 0, 0, 0));
        splashHeader.setPickOnBounds(true);

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

    private void setupTrayIcon(Stage stage) {
        if (!java.awt.SystemTray.isSupported()) return;
        this.isTraySupported = true;

        java.awt.SystemTray tray = java.awt.SystemTray.getSystemTray();
        java.awt.Image image = java.awt.Toolkit.getDefaultToolkit().createImage("");

        java.awt.PopupMenu popup = new java.awt.PopupMenu();

        java.awt.MenuItem showItem = new java.awt.MenuItem("Show FxShield");
        showItem.addActionListener(e -> Platform.runLater(() -> {
            stage.setIconified(false);
            stage.show();
            stage.toFront();
        }));

        java.awt.MenuItem exitItem = new java.awt.MenuItem("Exit");
        exitItem.addActionListener(e -> Platform.runLater(this::hardExit));

        popup.add(showItem);
        popup.addSeparator();
        popup.add(exitItem);

        java.awt.TrayIcon trayIcon = new java.awt.TrayIcon(image, "FxShield", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> Platform.runLater(() -> {
            stage.setIconified(false);
            stage.show();
            stage.toFront();
        }));

        try { tray.add(trayIcon); } catch (Exception e) { e.printStackTrace(); }

        Platform.setImplicitExit(false);
    }

    private void swapTopDisk(int index) {
        if (physicalCards == null || physicalCards.length == 0) return;
        if (index < 0 || index >= physicalCards.length) return;

        PhysicalDiskCard card = physicalCards[index];
        card.setSwitcherNode(diskSwitcher.getRoot());
        topDiskContainer.getChildren().setAll(card.getRoot());
        HBox.setHgrow(topDiskContainer, Priority.ALWAYS);
    }

    // ---------------- Monitor UI updates ----------------

    private void updateCpuUI(double percent) {
        if (percent < 0) {
            cpuCard.setUnavailable("System CPU usage");
            return;
        }
        cpuCard.setValuePercent(percent, "System CPU usage");
    }

    private void updateRamUI(SystemMonitorService.RamSnapshot snap) {
        if (snap == null) return;
        ramCard.setValuePercent(snap.percent, gbFormat.format(snap.usedGb) + " / " + gbFormat.format(snap.totalGb) + " GB");
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

    // ---------------- DB scripts ----------------

    private enum ScriptKey {
        FREE_RAM("FreeRam_Script", "getFreeRam_Script"),
        OPTIMIZE_DISK("OptimizeDisk_Script", "getOptimizeDisk_Script"),
        OPTIMIZE_NETWORK("OptimizeNetwork_Script", "getOptimizeNetwork_Script"),
        PERFORMANCE_MODE("PerformanceMode_Script", "getPerformanceMode_Script"),
        BALANCED_MODE("BalancedMode_Script", "getBalancedMode_Script"),
        QUIT_MODE("QuitMode_Script", "getQuitMode_Script"),
        SCAN_AND_FIX("ScanAndFix_Script", "getScanAndFix_Script");

        final String fieldName;
        final String getterName;

        ScriptKey(String fieldName, String getterName) {
            this.fieldName = fieldName;
            this.getterName = getterName;
        }
    }

    private RemoteConfig fetchLatestConfigSafe() {
        try { return configService.fetchConfig(); } catch (Exception ignored) {}
        return null;
    }

    private String getScriptFromConfig(RemoteConfig cfg, ScriptKey key) {
        if (cfg == null || key == null) return null;

        try {
            var m = cfg.getClass().getMethod(key.getterName);
            Object v = m.invoke(cfg);
            if (v instanceof String s) return normalizeScript(s);
        } catch (Exception ignored) {}

        try {
            var f = cfg.getClass().getDeclaredField(key.fieldName);
            f.setAccessible(true);
            Object v = f.get(cfg);
            if (v instanceof String s) return normalizeScript(s);
        } catch (Exception ignored) {}

        return null;
    }

    private String normalizeScript(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        t = EAP_BAD.matcher(t).replaceAll("$ErrorActionPreference = '$1'");

        String low = t.toLowerCase(Locale.ROOT);
        if (!low.contains("$erroractionpreference")) {
            t = "$ErrorActionPreference = 'SilentlyContinue'\n" + t;
        }
        return t;
    }

    private void runDbScript(ScriptKey key, String title, String loadingMsg, String logTag, boolean supportsReboot) {
        LoadingDialog dialog = LoadingDialog.show(primaryStage, title, loadingMsg, supportsReboot);

        new Thread(() -> {
            RemoteConfig cfg = fetchLatestConfigSafe();

            if (cfg != null && cfg.isMaintenance()) {
                Platform.runLater(() -> {
                    dialog.setFailed("Service is under maintenance.");
                    MaintenanceDialog.show(primaryStage, cfg, configService::fetchConfig, okCfg -> {});
                });
                return;
            }

            String script = getScriptFromConfig(cfg, key);
            if (script == null) {
                Platform.runLater(() -> dialog.setFailed("No script found in database."));
                return;
            }

            boolean ok = runPowerShellSync(script, logTag);

            Platform.runLater(() -> {
                if (ok) {
                    if (supportsReboot) dialog.setDoneRequiresReboot("Completed successfully.");
                    else dialog.setDone("Completed successfully.");
                } else {
                    dialog.setFailed("Command failed.");
                }
            });

        }, "fxShield-db-" + key.name()).start();
    }

    private void runFreeRamFromDb() {
        SystemMonitorService.RamSnapshot before = (monitor != null ? monitor.readRamOnce() : null);
        LoadingDialog dialog = LoadingDialog.show(primaryStage, "Cleaning RAM", "Fetching latest script from server...", false);

        new Thread(() -> {
            RemoteConfig cfg = fetchLatestConfigSafe();

            if (cfg != null && cfg.isMaintenance()) {
                Platform.runLater(() -> {
                    dialog.setFailed("Service is under maintenance.");
                    MaintenanceDialog.show(primaryStage, cfg, configService::fetchConfig, okCfg -> {});
                });
                return;
            }

            String script = getScriptFromConfig(cfg, ScriptKey.FREE_RAM);
            if (script == null) {
                Platform.runLater(() -> dialog.setFailed("No Free RAM script found in database."));
                return;
            }

            boolean ok = runPowerShellSync(script, "[FreeRAM]");

            try { Thread.sleep(900); } catch (Exception ignored) {}

            SystemMonitorService.RamSnapshot after = (monitor != null ? monitor.readRamOnce() : null);

            Platform.runLater(() -> {
                if (!ok) {
                    dialog.setFailed("Cleanup failed.");
                    return;
                }
                if (before == null || after == null) {
                    dialog.setDone("Cleanup completed.\n\n(Unable to detect RAM difference)");
                    return;
                }

                double diffGb = before.usedGb - after.usedGb;
                String done =
                        "Before: " + gbFormat.format(before.usedGb) + " / " + gbFormat.format(before.totalGb) + " GB\n" +
                                "After:  " + gbFormat.format(after.usedGb) + " / " + gbFormat.format(after.totalGb) + " GB\n\n" +
                                "Freed:  " + gbFormat.format(diffGb) + " GB";

                dialog.setDone(done);
            });

        }, "fxShield-db-FreeRAM").start();
    }

    private void runAllInOneFromDb() {
        LoadingDialog dialog = LoadingDialog.show(primaryStage, "Full Optimization", "Fetching latest scripts from server...", false);

        new Thread(() -> {
            RemoteConfig cfg = fetchLatestConfigSafe();

            if (cfg != null && cfg.isMaintenance()) {
                Platform.runLater(() -> {
                    dialog.setFailed("Service is under maintenance.");
                    MaintenanceDialog.show(primaryStage, cfg, configService::fetchConfig, okCfg -> {});
                });
                return;
            }

            String s1 = getScriptFromConfig(cfg, ScriptKey.FREE_RAM);
            String s2 = getScriptFromConfig(cfg, ScriptKey.OPTIMIZE_DISK);
            String s3 = getScriptFromConfig(cfg, ScriptKey.OPTIMIZE_NETWORK);

            if (s1 == null || s2 == null || s3 == null) {
                Platform.runLater(() -> dialog.setFailed("Missing one or more scripts in database."));
                return;
            }

            try {
                Platform.runLater(() -> dialog.setMessageText("Step 1/3: Cleaning memory and junk files..."));
                runPowerShellSync(s1, "[All-RAM]");

                Platform.runLater(() -> dialog.setMessageText("Step 2/3: Optimizing disk drives..."));
                runPowerShellSync(s2, "[All-Disk]");

                Platform.runLater(() -> dialog.setMessageText("Step 3/3: Optimizing network stack..."));
                runPowerShellSync(s3, "[All-Net]");

                Platform.runLater(() -> dialog.setDone("All optimizations completed successfully."));
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> dialog.setFailed("Optimization package failed."));
            }

        }, "fxShield-db-allInOne").start();
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

        .scroll-bar:vertical {
            -fx-background-color: transparent;
            -fx-padding: 2;
            -fx-pref-width: 12;
            -fx-min-width: 12;
            -fx-max-width: 12;
            -fx-opacity: 0.80;
        }
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

    private String shortenGpuName(String full) {
        if (full == null || full.isBlank()) return "Unknown";
        String f = full.toLowerCase(Locale.ROOT);

        if (f.contains("iris")) return "Iris Xe";
        if (f.contains("uhd")) return "UHD Graphics";
        if (f.contains("xe")) return "Intel Xe";
        if (f.contains("vega")) return "Radeon Vega";
        if (f.contains("rtx")) return "RTX";
        if (f.contains("gtx")) return "GTX";
        if (f.contains("radeon")) return "Radeon";

        return full.length() > 18 ? full.substring(0, 18) + "..." : full;
    }

    private void hardExit() {
        try { if (monitor != null) monitor.stop(); } catch (Exception ignored) {}
        try { AutomationService.get().stop(); } catch (Exception ignored) {}
        try { Platform.exit(); } catch (Exception ignored) {}
        System.exit(0);
    }

    public static void main(String[] args) {
        System.out.println("Fx Shield starting...");
        try {
            if (!WindowsUtils.isAdmin()) {
                WindowsUtils.requestAdminAndExit();
                return;
            }
            launch(args);
        } catch (Throwable t) {
            t.printStackTrace();
            try {
                javax.swing.JOptionPane.showMessageDialog(
                        null,
                        "Startup Error: " + t.getMessage() + "\nCheck the console for details.",
                        "Fx Shield Error",
                        javax.swing.JOptionPane.ERROR_MESSAGE
                );
            } catch (Exception ignored) {}
        }
    }
}
