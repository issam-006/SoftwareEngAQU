package fxShield.UI;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public final class DeviceInfoDialog {

    private DeviceInfoDialog() {}

    // ===== iOS-glass / smooth =====
    private static final String DIALOG_ROOT_STYLE =
            "-fx-background-color: rgba(10,14,25,0.88);" +
                    "-fx-background-radius: 22;" +
                    "-fx-border-color: rgba(255,255,255,0.10);" +
                    "-fx-border-width: 1;" +
                    "-fx-border-radius: 22;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.65), 30, 0.22, 0, 12);";

    private static final String HEADER_STYLE =
            "-fx-background-color: linear-gradient(to right, rgba(99,102,241,0.22), rgba(56,189,248,0.12), rgba(34,197,94,0.08));" +
                    "-fx-background-radius: 18;" +
                    "-fx-border-color: rgba(255,255,255,0.10);" +
                    "-fx-border-width: 1;" +
                    "-fx-border-radius: 18;" +
                    "-fx-padding: 12 14 12 14;";

    private static final String CLOSE_NORMAL =
            "-fx-background-color: rgba(255,255,255,0.06);" +
                    "-fx-text-fill: #e5e7eb;" +
                    "-fx-border-color: rgba(255,255,255,0.12);" +
                    "-fx-border-width: 1;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 6 18 6 18;" +
                    "-fx-cursor: hand;";

    private static final String CLOSE_HOVER =
            "-fx-background-color: rgba(255,255,255,0.12);" +
                    "-fx-text-fill: #ffffff;" +
                    "-fx-border-color: rgba(255,255,255,0.18);" +
                    "-fx-border-width: 1;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 6 18 6 18;" +
                    "-fx-cursor: hand;";

    private static final String SECTION_STYLE =
            "-fx-background-color: rgba(255,255,255,0.06);" +
                    "-fx-background-radius: 18;" +
                    "-fx-border-color: rgba(255,255,255,0.08);" +
                    "-fx-border-radius: 18;" +
                    "-fx-border-width: 1;" +
                    "-fx-padding: 14;";

    private static final String MINI_CARD_STYLE =
            "-fx-background-color: rgba(255,255,255,0.05);" +
                    "-fx-background-radius: 14;" +
                    "-fx-border-color: rgba(255,255,255,0.08);" +
                    "-fx-border-radius: 14;" +
                    "-fx-border-width: 1;" +
                    "-fx-padding: 12;";

    // Scroll CSS (يمين فقط)
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

    private static final String SCROLL_CSS_DATA_URL = "data:text/css," + encodeCssStatic(SCROLL_CSS);

    private static final DecimalFormat GB_1D   = new DecimalFormat("0.0");
    private static final DecimalFormat GHZ_2D  = new DecimalFormat("0.00");
    private static final DecimalFormat MBPS_0D = new DecimalFormat("0");
    private static final DecimalFormat PCT_1D  = new DecimalFormat("0.0");
    private static final DecimalFormat DPI_0D  = new DecimalFormat("0");

    // Background executor (daemon) بدل ForkJoinPool
    private static final ExecutorService INFO_EXEC =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "fxShield-device-info");
                t.setDaemon(true);
                return t;
            });

    // Simple cache TTL
    private static volatile Info cached;
    private static volatile long cachedAtMs;

    public static void show(Stage owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Device Information");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16, 20, 18, 20));
        root.setStyle(DIALOG_ROOT_STYLE);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);

        // Header
        Label title = new Label("Device Information");
        title.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 19)); // ✅ no CSS font-weight
        title.setTextFill(Color.web("#f3f4f6"));

        Label sub = new Label("Full device specs and system details.");
        sub.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        sub.setTextFill(Color.web("#a7b0bf"));
        VBox titleBox = new VBox(4, title, sub);

        Button closeBtn = new Button("Close");
        closeBtn.setStyle(CLOSE_NORMAL);

        ScaleTransition hoverIn = new ScaleTransition(Duration.millis(120), closeBtn);
        hoverIn.setToX(1.03);
        hoverIn.setToY(1.03);

        ScaleTransition hoverOut = new ScaleTransition(Duration.millis(120), closeBtn);
        hoverOut.setToX(1.00);
        hoverOut.setToY(1.00);

        closeBtn.setOnMouseEntered(_ -> {
            closeBtn.setStyle(CLOSE_HOVER);
            hoverOut.stop();
            hoverIn.playFromStart();
        });
        closeBtn.setOnMouseExited(_ -> {
            closeBtn.setStyle(CLOSE_NORMAL);
            hoverIn.stop();
            hoverOut.playFromStart();
        });
        closeBtn.setOnAction(_ -> dialog.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, titleBox, spacer, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        StackPane headerWrap = new StackPane(header);
        headerWrap.setStyle(HEADER_STYLE);
        root.setTop(headerWrap);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16, 0, 0, 0));
        content.setFillWidth(true);

        VBox loading = new VBox(10);
        loading.setAlignment(Pos.CENTER);
        ProgressIndicator pi = new ProgressIndicator();
        pi.setPrefSize(44, 44);

        Label loadingLbl = new Label("Collecting system information...");
        loadingLbl.setTextFill(Color.web("#a7b0bf"));
        loadingLbl.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        loading.getChildren().addAll(pi, loadingLbl);
        content.getChildren().add(loading);

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        root.setCenter(sp);

        Scene scene = new Scene(root, 720, 520);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(SCROLL_CSS_DATA_URL);

        scene.setOnKeyPressed(k -> {
            if (k.getCode() == javafx.scene.input.KeyCode.ESCAPE) dialog.close();
        });

        dialog.setScene(scene);
        dialog.centerOnScreen();

        // Pop-in
        root.setOpacity(0);
        root.setScaleX(0.94);
        root.setScaleY(0.94);
        root.setTranslateY(10);
        dialog.show();

        ParallelTransition popIn = new ParallelTransition(
                fade(root, 0, 1, 220),
                scale(root, 0.94, 1, 220),
                moveY(root, 10, 0, 220)
        );
        popIn.setInterpolator(Interpolator.EASE_OUT);
        popIn.play();

        CompletableFuture
                .supplyAsync(DeviceInfoDialog::getInfoCached, INFO_EXEC)
                .whenComplete((info, err) -> Platform.runLater(() -> {
                    content.getChildren().clear();

                    if (err != null || info == null) {
                        content.getChildren().add(collapsibleSimple("Error",
                                new VBox(10, row("Details", "Failed to read system info")),
                                new VBox(10, miniCardText("No additional info"))
                        ));
                        animateSections(content);
                        return;
                    }

                    // نفس ترتيبك + Display
                    content.getChildren().add(osCollapsible(info));
                    content.getChildren().add(displayCollapsible(info.displays));
                    content.getChildren().add(cpuCollapsible(info));
                    content.getChildren().add(ramCollapsible(info));
                    content.getChildren().add(gpuCollapsible(info.gpus));
                    content.getChildren().add(networkCollapsible(info.nics));
                    content.getChildren().add(batteryCollapsible(info.batteries));

                    animateSections(content);
                }));
    }

    private static Info getInfoCached() {
        long now = System.currentTimeMillis();
        Info c = cached;
        if (c != null && (now - cachedAtMs) < 20_000) return c;
        Info fresh = collectInfo();
        cached = fresh;
        cachedAtMs = now;
        return fresh;
    }

    /* =================== Collapsible Builders =================== */

    private static VBox osCollapsible(Info info) {
        VBox summary = new VBox(10,
                row("OS", safe(info.osString)),
                row("Version / Build", safe(info.osVersion)),
                row("Architecture", safe(info.arch))
        );

        VBox details = new VBox(10,
                miniCard("OS Details",
                        row("OS", safe(info.osString)),
                        row("Version / Build", safe(info.osVersion)),
                        row("Architecture", safe(info.arch)),
                        row("Kernel / Family", safe(info.kernel)),
                        row("Hostname", safe(info.hostname)),
                        row("Username", safe(info.username)),
                        row("Boot Time", safe(info.bootTime)),
                        row("Uptime", safe(info.uptime)),
                        row("Timezone", safe(info.timezone)),
                        row("Locale", safe(info.locale))
                )
        );

        return collapsibleSimple("Operating System", summary, details);
    }

    private static VBox displayCollapsible(List<DisplaySpec> displays) {
        int count = (displays == null) ? 0 : displays.size();
        DisplaySpec primary = (count > 0) ? displays.get(0) : null;

        VBox summary = new VBox(10,
                row("Displays", String.valueOf(count)),
                row("Primary", primary == null ? "N/A" : safe(primary.name)),
                row("Resolution", primary == null ? "N/A" : safe(primary.resolution)),
                row("Refresh", primary == null ? "N/A" : safe(primary.refresh))
        );

        VBox details = new VBox(10);
        if (count == 0) {
            details.getChildren().add(miniCardText("No display detected"));
        } else {
            for (int i = 0; i < displays.size(); i++) {
                DisplaySpec d = displays.get(i);
                details.getChildren().add(miniCard(
                        "#" + (i + 1) + "  " + safe(d.name),
                        row("Resolution", safe(d.resolution)),
                        row("Refresh", safe(d.refresh)),
                        row("Scale", safe(d.scale)),
                        row("DPI", safe(d.dpi))
                ));
            }
        }
        return collapsibleSimple("Display", summary, details);
    }

    private static VBox cpuCollapsible(Info info) {
        VBox summary = new VBox(10,
                row("CPU", safe(info.cpuName)),
                row("Cores", info.physCores + " / " + info.logCores),
                row("Max Frequency", hz(info.maxHz))
        );

        VBox details = new VBox(10,
                miniCard("CPU Details",
                        row("Name", safe(info.cpuName)),
                        row("Vendor", safe(info.cpuVendor)),
                        row("Family / Model", safe(info.cpuFamily) + " / " + safe(info.cpuModel)),
                        row("Stepping", safe(info.cpuStepping)),
                        row("L1 Cache", formatCache(info.l1Cache)),
                        row("L2 Cache", formatCache(info.l2Cache)),
                        row("L3 Cache", formatCache(info.l3Cache)),
                        row("Current Load", info.cpuLoad < 0 ? "N/A" : String.format("%.1f %%", info.cpuLoad))
                )
        );

        return collapsibleSimple("CPU", summary, details);
    }

    private static VBox ramCollapsible(Info info) {
        VBox summary = new VBox(10,
                row("Total", GB_1D.format(bytesToGb(info.memTotal)) + " GB"),
                row("Available", GB_1D.format(bytesToGb(info.memAvail)) + " GB")
        );

        VBox details = new VBox(10,
                miniCard("RAM Details",
                        row("Total", GB_1D.format(bytesToGb(info.memTotal)) + " GB"),
                        row("Available (now)", GB_1D.format(bytesToGb(info.memAvail)) + " GB"),
                        row("Used", GB_1D.format(bytesToGb(info.memTotal - info.memAvail)) + " GB"),
                        row("Type", safe(info.ramType)),
                        row("Speed", safe(info.ramSpeed)),
                        row("Swap Total", formatBytesGB(info.swapTotal)),
                        row("Swap Used", formatBytesGB(info.swapUsed)),
                        row("Swap Free", formatBytesGB(info.swapFree))
                )
        );

        return collapsibleSimple("Memory (RAM)", summary, details);
    }

    private static VBox gpuCollapsible(List<GpuSpec> gpus) {
        int count = (gpus == null) ? 0 : gpus.size();
        GpuSpec main = (count > 0) ? gpus.get(0) : null;

        VBox summary = new VBox(10,
                row("GPUs", String.valueOf(count)),
                row("Primary", main == null ? "N/A" : safe(main.name)),
                row("Core Clock", main == null ? "N/A" : safe(main.coreClock)),
                row("Memory Clock", main == null ? "N/A" : safe(main.memClock))
        );

        VBox details = new VBox(10);
        if (count == 0) {
            details.getChildren().add(miniCardText("No GPU detected"));
        } else {
            for (int i = 0; i < gpus.size(); i++) {
                GpuSpec g = gpus.get(i);
                details.getChildren().add(miniCard(
                        "#" + (i + 1) + "  " + safe(g.name),
                        row("Vendor", safe(g.vendor)),
                        row("VRAM", GB_1D.format(bytesToGb(g.vram)) + " GB"),
                        row("Core Clock", safe(g.coreClock)),
                        row("Memory Clock", safe(g.memClock))
                ));
            }
        }

        return collapsibleSimple("GPU", summary, details);
    }

    private static VBox networkCollapsible(List<NicSpec> nics) {
        int count = (nics == null) ? 0 : nics.size();
        NicSpec best = pickBestNic(nics);

        VBox summary = new VBox(10,
                row("Adapters", String.valueOf(count)),
                row("Active", best == null ? "N/A" : safe(best.name)),
                row("IPv4", best == null ? "N/A" : safe(best.ipv4))
        );

        VBox details = new VBox(10);
        if (count == 0) {
            details.getChildren().add(miniCardText("No network adapters detected"));
        } else {
            for (int i = 0; i < nics.size(); i++) {
                NicSpec n = nics.get(i);
                details.getChildren().add(miniCard(
                        "#" + (i + 1) + "  " + safe(n.name),
                        row("MAC", safe(n.mac)),
                        row("IPv4", safe(n.ipv4)),
                        row("IPv6", safe(n.ipv6)),
                        row("Speed", safe(n.speed)),
                        row("MTU", safe(n.mtu))
                ));
            }
        }

        return collapsibleSimple("Network", summary, details);
    }

    private static VBox batteryCollapsible(List<BatterySpec> bats) {
        int count = (bats == null) ? 0 : bats.size();
        BatterySpec b = (count > 0) ? bats.get(0) : null;

        VBox summary = new VBox(10,
                row("Batteries", String.valueOf(count)),
                row("Remaining", b == null ? "N/A" : safe(b.remaining)),
                row("State", b == null ? "N/A" : safe(b.state))
        );

        VBox details = new VBox(10);
        if (count == 0) {
            details.getChildren().add(miniCardText("No battery detected"));
        } else {
            for (int i = 0; i < bats.size(); i++) {
                BatterySpec bb = bats.get(i);
                details.getChildren().add(miniCard(
                        "#" + (i + 1) + "  " + safe(bb.name),
                        row("Remaining", safe(bb.remaining)),
                        row("State", safe(bb.state)),
                        row("Time Remaining", safe(bb.timeRemaining))
                ));
            }
        }

        return collapsibleSimple("Battery", summary, details);
    }

    private static VBox collapsibleSimple(String title, VBox summaryRows, VBox detailsBox) {
        Label t = new Label(title);
        t.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 14)); // ✅ no CSS font-weight
        t.setTextFill(Color.web("#f3f4f6"));

        Label chevron = new Label("⌄");
        chevron.setTextFill(Color.web("#a7b0bf"));
        chevron.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        chevron.setRotate(0);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, t, spacer, chevron);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 6, 0));
        header.setStyle("-fx-cursor: hand;");

        VBox detailsWrap = new VBox(detailsBox);
        detailsWrap.setPadding(new Insets(10, 0, 0, 0));
        detailsWrap.setVisible(false);
        detailsWrap.setManaged(false);
        detailsWrap.setOpacity(0);
        detailsWrap.setMinHeight(0);
        detailsWrap.setPrefHeight(0);
        detailsWrap.setMaxHeight(Region.USE_PREF_SIZE);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(detailsWrap.widthProperty());
        clip.heightProperty().bind(detailsWrap.prefHeightProperty());
        detailsWrap.setClip(clip);

        VBox box = new VBox(10);
        box.setStyle(SECTION_STYLE);
        box.getChildren().addAll(header, summaryRows, detailsWrap);

        box.setOpacity(0);
        box.setTranslateY(10);

        BooleanProperty expanded = new SimpleBooleanProperty(false);
        header.setOnMouseClicked(_ -> toggle(expanded, detailsWrap, detailsBox, chevron));
        header.setOnMouseEntered(_ -> header.setStyle("-fx-cursor: hand; -fx-opacity: 0.96;"));
        header.setOnMouseExited(_ -> header.setStyle("-fx-cursor: hand; -fx-opacity: 1;"));

        return box;
    }

    private static void toggle(BooleanProperty expanded, VBox detailsWrap, VBox detailsBox, Label chevron) {
        boolean open = !expanded.get();
        expanded.set(open);

        if (open) {
            detailsWrap.setManaged(true);
            detailsWrap.setVisible(true);

            detailsBox.applyCss();
            detailsBox.layout();
            double target = detailsBox.prefHeight(-1) + detailsWrap.getPadding().getTop();

            detailsWrap.setPrefHeight(0);
            detailsWrap.setOpacity(0);

            Timeline t = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(detailsWrap.prefHeightProperty(), 0),
                            new KeyValue(detailsWrap.opacityProperty(), 0),
                            new KeyValue(chevron.rotateProperty(), 0)
                    ),
                    new KeyFrame(Duration.millis(220),
                            new KeyValue(detailsWrap.prefHeightProperty(), target, Interpolator.EASE_OUT),
                            new KeyValue(detailsWrap.opacityProperty(), 1, Interpolator.EASE_OUT),
                            new KeyValue(chevron.rotateProperty(), 180, Interpolator.EASE_OUT)
                    )
            );
            t.play();
        } else {
            double from = detailsWrap.getPrefHeight();
            Timeline t = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(detailsWrap.prefHeightProperty(), from),
                            new KeyValue(detailsWrap.opacityProperty(), 1),
                            new KeyValue(chevron.rotateProperty(), 180)
                    ),
                    new KeyFrame(Duration.millis(200),
                            new KeyValue(detailsWrap.prefHeightProperty(), 0, Interpolator.EASE_IN),
                            new KeyValue(detailsWrap.opacityProperty(), 0, Interpolator.EASE_IN),
                            new KeyValue(chevron.rotateProperty(), 0, Interpolator.EASE_IN)
                    )
            );
            t.setOnFinished(_ -> {
                detailsWrap.setVisible(false);
                detailsWrap.setManaged(false);
            });
            t.play();
        }
    }

    /* =================== Data collection =================== */

    private static Info collectInfo() {
        try {
            SystemInfo si = new SystemInfo();
            HardwareAbstractionLayer hal = si.getHardware();
            OperatingSystem os = si.getOperatingSystem();

            CentralProcessor cpu = hal.getProcessor();
            GlobalMemory mem = hal.getMemory();
            List<GraphicsCard> cards = hal.getGraphicsCards();

            Info info = new Info();

            // RAM Type / Speed
            try {
                List<PhysicalMemory> pm = mem.getPhysicalMemory();
                if (pm != null && !pm.isEmpty()) {
                    PhysicalMemory first = pm.get(0);
                    info.ramType = safe(first.getMemoryType());
                    long hz = first.getClockSpeed();
                    info.ramSpeed = (hz > 0) ? ((hz / 1_000_000) + " MHz") : "N/A";
                } else {
                    info.ramType = "N/A";
                    info.ramSpeed = "N/A";
                }
            } catch (Throwable t) {
                info.ramType = "N/A";
                info.ramSpeed = "N/A";
            }

            // OS basics
            info.osString = os.toString();
            info.arch = System.getProperty("os.arch");

            // CPU basics
            var id = cpu.getProcessorIdentifier();
            info.cpuName = safe(id.getName());
            info.physCores = cpu.getPhysicalProcessorCount();
            info.logCores = cpu.getLogicalProcessorCount();
            info.maxHz = cpu.getMaxFreq();

            // CPU extended
            info.cpuVendor = safe(id.getVendor());
            info.cpuFamily = safe(id.getFamily());
            info.cpuModel = safe(id.getModel());
            info.cpuStepping = safe(id.getStepping());

            // ✅ FIX: caches start at -1 so this works
            try {
                List<CentralProcessor.ProcessorCache> caches = cpu.getProcessorCaches();
                for (CentralProcessor.ProcessorCache cache : caches) {
                    long size = cache.getCacheSize();
                    int level = cache.getLevel();
                    if (level == 1 && info.l1Cache < 0) info.l1Cache = size;
                    else if (level == 2 && info.l2Cache < 0) info.l2Cache = size;
                    else if (level == 3 && info.l3Cache < 0) info.l3Cache = size;
                }
            } catch (Throwable ignored) {}

            info.cpuLoad = -1; // Snapshot load N/A (pure OSHI)

            // RAM totals
            info.memTotal = mem.getTotal();
            info.memAvail = mem.getAvailable();

            // Swap
            try {
                VirtualMemory vm = mem.getVirtualMemory();
                info.swapTotal = vm.getSwapTotal();
                info.swapUsed = vm.getSwapUsed();
                info.swapFree = info.swapTotal - info.swapUsed;
            } catch (Throwable t) {
                info.swapTotal = info.swapUsed = info.swapFree = -1;
            }

            // GPU list
            if (cards != null) {
                for (GraphicsCard c : cards) {
                    GpuSpec g = new GpuSpec();
                    g.name = safe(c.getName());
                    g.vendor = safe(c.getVendor());
                    g.vram = c.getVRam();
                    info.gpus.add(g);
                }
            }
            for (GpuSpec g : info.gpus) {
                g.coreClock = "N/A";
                g.memClock = "N/A";
            }

            // OS details
            info.osVersion = System.getProperty("os.version");
            info.kernel = safe(System.getProperty("os.name")) + " / " + safe(System.getProperty("os.version"));

            try { info.hostname = java.net.InetAddress.getLocalHost().getHostName(); }
            catch (Throwable t) { info.hostname = "N/A"; }

            info.username = System.getProperty("user.name");

            try {
                long bootMs = si.getOperatingSystem().getSystemBootTime() * 1000L;
                info.bootTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(bootMs));
                long upSec = si.getOperatingSystem().getSystemUptime();
                info.uptime = formatUptime(upSec);
            } catch (Throwable t) {
                info.bootTime = "N/A";
                info.uptime = "N/A";
            }

            try { info.timezone = java.util.TimeZone.getDefault().getID(); }
            catch (Throwable t) { info.timezone = "N/A"; }

            try { info.locale = java.util.Locale.getDefault().toString(); }
            catch (Throwable t) { info.locale = "N/A"; }

            // Network
            try {
                List<NetworkIF> nifs = hal.getNetworkIFs();
                if (nifs != null) {
                    for (NetworkIF n : nifs) {
                        try { n.updateAttributes(); } catch (Throwable ignored) {}

                        NicSpec ns = new NicSpec();
                        ns.name = safe(n.getDisplayName());
                        ns.mac = safe(n.getMacaddr());
                        ns.ipv4 = join(n.getIPv4addr());
                        ns.ipv6 = join(n.getIPv6addr());

                        long spd = 0;
                        try { spd = n.getSpeed(); } catch (Throwable ignored) {}
                        ns.speed = (spd > 0) ? (MBPS_0D.format(spd / 1_000_000.0) + " Mbps") : "N/A";

                        try { ns.mtu = String.valueOf(n.getMTU()); } catch (Throwable t) { ns.mtu = "N/A"; }

                        info.nics.add(ns);
                    }
                }
            } catch (Throwable ignored) {}

            // Battery
            try {
                List<PowerSource> powerSources = hal.getPowerSources();
                for (PowerSource ps : powerSources) {
                    BatterySpec bs = new BatterySpec();
                    bs.name = safe(ps.getName());

                    double rem = ps.getRemainingCapacityPercent();
                    if (rem >= 0 && rem <= 1.0) bs.remaining = PCT_1D.format(rem * 100.0) + " %";
                    else bs.remaining = "N/A";

                    bs.state = ps.isCharging() ? "Charging" : "Discharging";

                    double timeRemaining = ps.getTimeRemainingEstimated();
                    if (timeRemaining >= 0) bs.timeRemaining = formatUptime((long) timeRemaining);
                    else bs.timeRemaining = ps.isCharging() ? "Charging" : "N/A";

                    info.batteries.add(bs);
                }
            } catch (Throwable ignored) {}

            if (info.batteries.isEmpty()) {
                BatterySpec bs = new BatterySpec();
                bs.name = "Desktop PC";
                bs.remaining = "N/A";
                bs.state = "N/A";
                bs.timeRemaining = "N/A";
                info.batteries.add(bs);
            }

            // Displays (JavaFX Screen)
            try {
                List<Screen> screens = Screen.getScreens();
                for (int i = 0; i < screens.size(); i++) {
                    Screen s = screens.get(i);
                    Rectangle2D b = s.getBounds();
                    DisplaySpec ds = new DisplaySpec();
                    ds.name = "Display " + (i + 1);
                    ds.resolution = ((int) b.getWidth()) + " × " + ((int) b.getHeight());
                    ds.refresh = "N/A";
                    ds.scale = "x" + String.format("%.2f", s.getOutputScaleX());
                    ds.dpi = DPI_0D.format(s.getDpi());
                    info.displays.add(ds);
                }
            } catch (Throwable ignored) {}

            return info;
        } catch (Throwable t) {
            return null;
        }
    }

    /* =================== UI Helpers =================== */

    private static HBox row(String k, String v) {
        Label left = new Label(k);
        left.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        left.setTextFill(Color.web("#a7b0bf"));

        Label right = new Label(v);
        right.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        right.setTextFill(Color.web("#e5e7eb"));
        right.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox r = new HBox(10, left, spacer, right);
        r.setAlignment(Pos.CENTER_LEFT);
        return r;
    }

    private static VBox miniCard(String title, HBox... rows) {
        Label t = new Label(title);
        t.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12.5)); // ✅ no CSS font-weight
        t.setTextFill(Color.web("#e5e7eb"));

        VBox box = new VBox(8);
        box.setStyle(MINI_CARD_STYLE);
        box.getChildren().add(t);
        box.getChildren().addAll(rows);
        return box;
    }

    private static VBox miniCardText(String text) {
        Label t = new Label(text);
        t.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        t.setTextFill(Color.web("#a7b0bf"));
        VBox box = new VBox(t);
        box.setStyle(MINI_CARD_STYLE);
        return box;
    }

    private static void animateSections(VBox content) {
        int idx = 0;
        for (Node n : content.getChildren()) {
            if (!(n instanceof Region r)) continue;
            int delay = 35 + idx * 35;

            PauseTransition p = new PauseTransition(Duration.millis(delay));
            p.setOnFinished(_ -> {
                ParallelTransition pt = new ParallelTransition(
                        fade(r, 0, 1, 220),
                        moveY(r, 10, 0, 220)
                );
                pt.setInterpolator(Interpolator.EASE_OUT);
                pt.play();
            });
            p.play();
            idx++;
        }
    }

    /* =================== Formatting =================== */

    private static double bytesToGb(long b) {
        return b / (1024.0 * 1024 * 1024);
    }

    private static String formatBytesGB(long b) {
        if (b <= 0) return "N/A";
        return GB_1D.format(bytesToGb(b)) + " GB";
    }

    private static String hz(long hz) {
        if (hz <= 0) return "N/A";
        return GHZ_2D.format(hz / 1_000_000_000.0) + " GHz";
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "N/A" : s.trim();
    }

    private static String join(String[] arr) {
        if (arr == null || arr.length == 0) return "N/A";
        return String.join(", ", arr);
    }

    private static String formatUptime(long seconds) {
        if (seconds <= 0) return "N/A";
        long d = seconds / 86400; seconds %= 86400;
        long h = seconds / 3600;  seconds %= 3600;
        long m = seconds / 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

    private static String formatCache(long bytes) {
        if (bytes <= 0) return "N/A";
        if (bytes >= 1024L * 1024L) return (bytes / (1024L * 1024L)) + " MB";
        return (bytes / 1024L) + " KB";
    }

    /* =================== Transitions =================== */

    private static FadeTransition fade(Region node, double from, double to, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(from);
        ft.setToValue(to);
        return ft;
    }

    private static ScaleTransition scale(Region node, double from, double to, int ms) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), node);
        st.setFromX(from); st.setFromY(from);
        st.setToX(to);     st.setToY(to);
        return st;
    }

    private static TranslateTransition moveY(Region node, double from, double to, int ms) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), node);
        tt.setFromY(from);
        tt.setToY(to);
        return tt;
    }

    private static String encodeCssStatic(String css) {
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

    private static NicSpec pickBestNic(List<NicSpec> nics) {
        if (nics == null || nics.isEmpty()) return null;
        for (NicSpec n : nics) {
            if (!isNA(n.ipv4) && !isNA(n.mac)) return n;
        }
        for (NicSpec n : nics) {
            if (!isNA(n.name)) return n;
        }
        return nics.get(0);
    }

    private static boolean isNA(String s) {
        return s == null || s.isBlank() || "N/A".equalsIgnoreCase(s.trim());
    }

    /* =================== DTOs =================== */

    private static final class Info {
        String osString, arch, cpuName, osVersion, kernel, hostname, username, bootTime, uptime, timezone, locale;
        String cpuVendor, cpuFamily, cpuModel, cpuStepping, ramType, ramSpeed;
        double cpuLoad = -1;
        int physCores, logCores;
        long maxHz, memTotal, memAvail;

        // ✅ FIX: start -1 so cache filling works
        long l1Cache = -1, l2Cache = -1, l3Cache = -1;
        long swapTotal = -1, swapUsed = -1, swapFree = -1;

        List<GpuSpec> gpus = new ArrayList<>();
        List<NicSpec> nics = new ArrayList<>();
        List<BatterySpec> batteries = new ArrayList<>();
        List<DisplaySpec> displays = new ArrayList<>();
    }

    private static final class GpuSpec {
        String name, vendor;
        long vram;
        String coreClock = "N/A";
        String memClock  = "N/A";
    }

    private static final class NicSpec {
        String name, mac, ipv4, ipv6, speed, mtu;
    }

    private static final class BatterySpec {
        String name, remaining, state, timeRemaining;
    }

    private static final class DisplaySpec {
        String name, resolution, refresh, scale, dpi;
    }
}
