package javafxx;

import com.sun.management.OperatingSystemMXBean;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;

public class DashBoardPage extends Application {

    private OperatingSystemMXBean osBean;

    private MeterCard cpuCard;
    private MeterCard ramCard;
    private MeterCard diskCard;
    private MeterCard gpuCard;

    private final DecimalFormat percentFormat = new DecimalFormat("0.0");
    private final DecimalFormat gbFormat = new DecimalFormat("0.0");

    @Override
    public void start(Stage stage) {

        osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #020617, #0f172a);");

        Label header = new Label("FX Shield - Live System Monitor");
        header.setTextFill(Color.web("#e5e7eb"));
        header.setFont(Font.font("Segoe UI", 24));
        header.setStyle("-fx-font-weight: bold;");

        root.setTop(header);
        BorderPane.setAlignment(header, Pos.TOP_LEFT);
        BorderPane.setMargin(header, new Insets(0, 0, 20, 5));

        cpuCard = new MeterCard("CPU");
        ramCard = new MeterCard("RAM");
        diskCard = new MeterCard("Disk C:");
        gpuCard = new MeterCard("GPU");

        HBox row = new HBox();
        row.setSpacing(20);
        row.setAlignment(Pos.CENTER);
        row.getChildren().add(cpuCard.root);
        row.getChildren().add(ramCard.root);
        row.getChildren().add(diskCard.root);
        row.getChildren().add(gpuCard.root);

        root.setCenter(row);

        Scene scene = new Scene(root, 1100, 420);
        stage.setTitle("FX Shield - Dashboard");
        stage.setScene(scene);
        stage.show();

        // قراءة اسم كرت الشاشة في ثريد آخر
        Thread gpuNameThread = new Thread(() -> {
            String gpuName = detectGpuName();
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

        // تحديث القيم كل ثانية
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, e -> updateAllMetrics()),
                new KeyFrame(Duration.seconds(1))
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void updateAllMetrics() {
        Platform.runLater(() -> {
            updateCpu();
            updateRam();
            updateDisk();
            updateGpu();
        });
    }

    private void updateCpu() {
        double load = osBean.getSystemCpuLoad();  // 0..1 أو -1
        if (load < 0) {
            cpuCard.valueLabel.setText("N/A");
            cpuCard.bar.setProgress(0);
            styleAsUnavailable(cpuCard);
            return;
        }
        double percent = load * 100.0;
        String text = percentFormat.format(percent) + " %";
        cpuCard.valueLabel.setText(text);
        cpuCard.extraLabel.setText("System CPU usage");
        double p = clamp01(percent / 100.0);
        cpuCard.bar.setProgress(p);
        styleByUsage(cpuCard, percent);
    }

    private void updateRam() {
        long total = osBean.getTotalPhysicalMemorySize();
        long free = osBean.getFreePhysicalMemorySize();
        long used = total - free;

        double totalGb = total / (1024.0 * 1024 * 1024);
        double usedGb = used / (1024.0 * 1024 * 1024);
        double percent = (total > 0) ? (used * 100.0 / total) : 0.0;

        String percentText = percentFormat.format(percent) + " %";
        String extra = gbFormat.format(usedGb) + " / " + gbFormat.format(totalGb) + " GB";

        ramCard.valueLabel.setText(percentText);
        ramCard.extraLabel.setText(extra);

        double p = clamp01(percent / 100.0);
        ramCard.bar.setProgress(p);
        styleByUsage(ramCard, percent);
    }

    private void updateDisk() {
        File c = new File("C:\\");
        long total = c.getTotalSpace();
        long free = c.getFreeSpace();
        long used = total - free;

        double totalGb = total / (1024.0 * 1024 * 1024);
        double usedGb = used / (1024.0 * 1024 * 1024);
        double percent = (total > 0) ? (used * 100.0 / total) : 0.0;

        String percentText = percentFormat.format(percent) + " %";
        String extra = gbFormat.format(usedGb) + " / " + gbFormat.format(totalGb) + " GB";

        diskCard.valueLabel.setText(percentText);
        diskCard.extraLabel.setText(extra);

        double p = clamp01(percent / 100.0);
        diskCard.bar.setProgress(p);
        styleByUsage(diskCard, percent);
    }

    private void updateGpu() {
        int usage = readNvidiaGpuUsagePercent();
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
        gpuCard.extraLabel.setText("NVIDIA GPU utilization");

        double p = clamp01(percent / 100.0);
        gpuCard.bar.setProgress(p);
        styleByUsage(gpuCard, percent);
    }

    private void styleByUsage(MeterCard card, double percent) {
        String color;
        if (percent < 60.0) {
            color = "#60a5fa";      // أزرق - استخدام طبيعي
        } else if (percent < 85.0) {
            color = "#fb923c";      // برتقالي - استخدام عالي
        } else {
            color = "#f97373";      // أحمر - استخدام عالي جداً
        }

        card.valueLabel.setTextFill(Color.web(color));
        String barStyle = "-fx-accent: " + color + ";" +
                "-fx-control-inner-background: #020617;";
        card.bar.setStyle(barStyle);
    }

    private void styleAsUnavailable(MeterCard card) {
        String color = "#9ca3af"; // رمادي
        card.valueLabel.setTextFill(Color.web(color));
        String barStyle = "-fx-accent: " + color + ";" +
                "-fx-control-inner-background: #020617;";
        card.bar.setStyle(barStyle);
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    // قراءة اسم كرت الشاشة من WMI (يعمل على ويندوز مهما كان النوع)
    private String detectGpuName() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "wmic",
                    "path",
                    "win32_videocontroller",
                    "get",
                    "name"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            String name = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.equalsIgnoreCase("Name") || line.isEmpty()) {
                    continue;
                }
                name = line;
                break;  // أول اسم GPU
            }
            reader.close();
            p.waitFor();
            return name;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    // قراءة استعمال GPU لو NVIDIA موجودة (nvidia-smi)
    private int readNvidiaGpuUsagePercent() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "nvidia-smi",
                    "--query-gpu=utilization.gpu",
                    "--format=csv,noheader,nounits"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            reader.close();
            p.waitFor();

            if (line == null) {
                return -1;
            }
            line = line.trim();
            if (line.isEmpty()) {
                return -1;
            }
            int value = Integer.parseInt(line);
            if (value < 0) value = 0;
            if (value > 100) value = 100;
            return value;
        } catch (Exception ex) {
            // لو ما فيه nvidia-smi أو كرت NVIDIA → نرجع -1
            return -1;
        }
    }

    // كلاس بسيط يمثل "كارد" واحد
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
            bar.setPrefWidth(220);
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

    public static void main(String[] args) {
        launch(args);
    }
}
