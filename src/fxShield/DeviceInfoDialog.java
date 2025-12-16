package fxShield;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class DeviceInfoDialog {

    private static final String DIALOG_ROOT_STYLE =
            "-fx-background-color: #020617;" +
                    "-fx-background-radius: 18;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 24, 0.2, 0, 10);";

    private static final String CLOSE_NORMAL =
            "-fx-background-color: transparent;" +
                    "-fx-text-fill: #9ca3af;" +
                    "-fx-border-color: #4b5563;" +
                    "-fx-border-width: 1.2;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 4 18 4 18;" +
                    "-fx-cursor: hand;";

    private static final String CLOSE_HOVER =
            "-fx-background-color: #1f2937;" +
                    "-fx-text-fill: #e5e7eb;" +
                    "-fx-border-color: #6b7280;" +
                    "-fx-border-width: 1.2;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 4 18 4 18;" +
                    "-fx-cursor: hand;";

    private static final String SECTION_STYLE =
            "-fx-background-color: rgba(255,255,255,0.06);" +
                    "-fx-background-radius: 16;" +
                    "-fx-border-color: rgba(255,255,255,0.08);" +
                    "-fx-border-radius: 16;" +
                    "-fx-border-width: 1;" +
                    "-fx-padding: 14;";

    private static final DecimalFormat GB_1D = new DecimalFormat("0.0");
    private static final DecimalFormat GHZ_2D = new DecimalFormat("0.00");

    public static void show(Stage owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Device Information");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16, 24, 20, 24));
        root.setStyle(DIALOG_ROOT_STYLE);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);

        // Header
        Label title = new Label("Device Information");
        title.setFont(Font.font("Segoe UI", 18));
        title.setTextFill(Color.web("#e5e7eb"));
        title.setStyle("-fx-font-weight: bold;");

        Label sub = new Label("Full device specs and system details.");
        sub.setFont(Font.font("Segoe UI", 12));
        sub.setTextFill(Color.web("#9ca3af"));
        VBox titleBox = new VBox(4, title, sub);

        Button closeBtn = new Button("Close");
        closeBtn.setStyle(CLOSE_NORMAL);
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(CLOSE_HOVER));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(CLOSE_NORMAL));
        closeBtn.setOnAction(e -> dialog.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, titleBox, spacer, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        root.setTop(header);

        // Content placeholder (async fill)
        VBox content = new VBox(12);
        content.setPadding(new Insets(18, 0, 0, 0));
        content.setFillWidth(true);

        VBox loading = new VBox(10);
        loading.setAlignment(Pos.CENTER);
        ProgressIndicator pi = new ProgressIndicator();
        Label loadingLbl = new Label("Collecting system information...");
        loadingLbl.setTextFill(Color.web("#9ca3af"));
        loading.getChildren().addAll(pi, loadingLbl);

        content.getChildren().add(loading);

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        root.setCenter(sp);

        Scene scene = new Scene(root, 720, 520);
        scene.setFill(Color.TRANSPARENT);

        // ESC to close
        scene.setOnKeyPressed(k -> {
            switch (k.getCode()) {
                case ESCAPE -> dialog.close();
            }
        });

        dialog.setScene(scene);
        dialog.centerOnScreen();

        // Pop-in animation
        root.setOpacity(0);
        root.setScaleX(0.95);
        root.setScaleY(0.95);
        dialog.show();

        ParallelTransition popIn = new ParallelTransition(
                fade(root, 0, 1, 220),
                scale(root, 0.95, 1, 220)
        );
        popIn.setInterpolator(Interpolator.EASE_OUT);
        popIn.play();

        // Load OSHI data off the FX thread
        CompletableFuture
                .supplyAsync(DeviceInfoDialog::collectInfo)
                .whenComplete((info, err) -> Platform.runLater(() -> {
                    content.getChildren().clear();
                    if (err != null || info == null) {
                        content.getChildren().add(section("Error", row("Details", "Failed to read system info")));
                        return;
                    }

                    content.getChildren().add(section("Operating System",
                            row("OS", safe(info.osString)),
                            row("Architecture", safe(info.arch)),
                            row("Java", safe(info.java))
                    ));

                    content.getChildren().add(section("CPU",
                            row("Name", safe(info.cpuName)),
                            row("Physical Cores", String.valueOf(info.physCores)),
                            row("Logical Cores", String.valueOf(info.logCores)),
                            row("Max Frequency", hz(info.maxHz))
                    ));

                    content.getChildren().add(section("Memory (RAM)",
                            row("Total", GB_1D.format(bytesToGb(info.memTotal)) + " GB"),
                            row("Available (now)", GB_1D.format(bytesToGb(info.memAvail)) + " GB")
                    ));

                    if (!info.gpus.isEmpty()) {
                        for (int i = 0; i < info.gpus.size(); i++) {
                            GpuSpec g = info.gpus.get(i);
                            content.getChildren().add(section("GPU #" + (i + 1),
                                    row("Name", safe(g.name)),
                                    row("Vendor", safe(g.vendor)),
                                    row("VRAM", GB_1D.format(bytesToGb(g.vram)) + " GB")
                            ));
                        }
                    } else {
                        content.getChildren().add(section("GPU", row("Info", "No GPU detected")));
                    }
                }));
    }

    // Data collection (background)
    private static Info collectInfo() {
        try {
            SystemInfo si = new SystemInfo();
            HardwareAbstractionLayer hal = si.getHardware();
            OperatingSystem os = si.getOperatingSystem();

            CentralProcessor cpu = hal.getProcessor();
            GlobalMemory mem = hal.getMemory();
            List<GraphicsCard> cards = hal.getGraphicsCards();

            Info info = new Info();
            info.osString = os.toString();
            info.arch = System.getProperty("os.arch");
            info.java = System.getProperty("java.version");

            info.cpuName = cpu.getProcessorIdentifier().getName();
            info.physCores = cpu.getPhysicalProcessorCount();
            info.logCores = cpu.getLogicalProcessorCount();
            info.maxHz = cpu.getMaxFreq();

            info.memTotal = mem.getTotal();
            info.memAvail = mem.getAvailable();

            if (cards != null) {
                for (GraphicsCard c : cards) {
                    GpuSpec g = new GpuSpec();
                    g.name = c.getName();
                    g.vendor = c.getVendor();
                    g.vram = c.getVRam();
                    info.gpus.add(g);
                }
            }
            return info;
        } catch (Throwable t) {
            return null;
        }
    }

    // UI helpers
    private static VBox section(String title, HBox... rows) {
        Label t = new Label(title);
        t.setFont(Font.font("Segoe UI", 14));
        t.setTextFill(Color.web("#e5e7eb"));
        t.setStyle("-fx-font-weight: bold;");

        VBox box = new VBox(10);
        box.setStyle(SECTION_STYLE);
        box.getChildren().add(t);
        box.getChildren().addAll(rows);
        return box;
    }

    private static HBox row(String k, String v) {
        Label left = new Label(k);
        left.setFont(Font.font("Segoe UI", 12));
        left.setTextFill(Color.web("#9ca3af"));

        Label right = new Label(v);
        right.setFont(Font.font("Segoe UI", 12));
        right.setTextFill(Color.web("#e5e7eb"));
        right.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox r = new HBox(10, left, spacer, right);
        r.setAlignment(Pos.CENTER_LEFT);
        return r;
    }

    private static double bytesToGb(long b) {
        return b / (1024.0 * 1024 * 1024);
    }

    private static String hz(long hz) {
        if (hz <= 0) return "N/A";
        return GHZ_2D.format(hz / 1_000_000_000.0) + " GHz";
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "N/A" : s.trim();
    }

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

    // DTOs
    private static final class Info {
        String osString, arch, java, cpuName;
        int physCores, logCores;
        long maxHz, memTotal, memAvail;
        java.util.List<GpuSpec> gpus = new java.util.ArrayList<>();
    }

    private static final class GpuSpec {
        String name, vendor;
        long vram;
    }
}