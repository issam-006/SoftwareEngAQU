package fxShield;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.text.DecimalFormat;

public class PhysicalDiskCard {

    private VBox root;
    private Label titleLabel;
    private Label usedValueLabel;
    private Label spaceLabel;
    private Label activeValueLabel;
    private ProgressBar usedBar;
    private ProgressBar activeBar;

    public PhysicalDiskCard(int index, String model, double sizeGb) {

        // ===== نوع الديسك (SSD / NVMe / HDD) =====
        String diskType = detectDiskType(model);

        titleLabel = new Label("Disk " + index + " • " + diskType);
        titleLabel.setTextFill(Color.web("#e9d8ff"));
        titleLabel.setFont(Font.font("Segoe UI", 20));
        titleLabel.setStyle("-fx-font-weight: bold;");

        usedValueLabel = new Label("Used: Loading...");
        usedValueLabel.setTextFill(Color.web("#f5e8ff"));
        usedValueLabel.setFont(Font.font("Segoe UI", 17));

        usedBar = new ProgressBar(0);
        usedBar.setPrefWidth(260);
        usedBar.setStyle(
                "-fx-accent: #a78bfa;" +
                        "-fx-control-inner-background: rgba(255,255,255,0.08);"
        );

        spaceLabel = new Label("Size: " + new DecimalFormat("0.0").format(sizeGb) + " GB");
        spaceLabel.setTextFill(Color.web("#c5b3ff"));
        spaceLabel.setFont(Font.font("Segoe UI", 13));

        activeValueLabel = new Label("Active: 0 %");
        activeValueLabel.setTextFill(Color.web("#f5e8ff"));
        activeValueLabel.setFont(Font.font("Segoe UI", 17));

        activeBar = new ProgressBar(0);
        activeBar.setPrefWidth(260);
        activeBar.setStyle(
                "-fx-accent: #7dd3fc;" +
                        "-fx-control-inner-background: rgba(255,255,255,0.08);"
        );

        // ===== ستايل الكرت =====
        root = new VBox(14);
        root.setPadding(new Insets(22));
        root.setAlignment(Pos.CENTER);

        root.setStyle(
                "-fx-background-color: rgba(17,13,34,0.55);" +
                        "-fx-background-radius: 28;" +
                        "-fx-border-radius: 28;" +
                        "-fx-border-color: rgba(255,255,255,0.10);" +
                        "-fx-border-width: 1;" +
                        "-fx-effect: dropshadow(gaussian, rgba(157,110,255,0.28), 25, 0.2, 0, 0);"
        );

        root.setMinHeight(240);
        root.setMinWidth(260);
        root.setPrefWidth(0);
        root.setMaxWidth(Double.MAX_VALUE);

        root.getChildren().addAll(
                titleLabel,
                usedValueLabel,
                usedBar,
                spaceLabel,
                activeValueLabel,
                activeBar
        );
    }

    private String detectDiskType(String model) {
        if (model == null) return "Disk";

        String m = model.toLowerCase();

        if (m.contains("nvme") || m.contains("nvm")) return "NVMe";
        if (m.contains("ssd")) return "SSD";
        if (m.contains("hdd") || m.contains("sata") || m.contains("st")) return "HDD";

        return "Disk";
    }

    public VBox getRoot() { return root; }
    public Label getUsedValueLabel() { return usedValueLabel; }
    public Label getSpaceLabel() { return spaceLabel; }
    public Label getActiveValueLabel() { return activeValueLabel; }
    public ProgressBar getUsedBar() { return usedBar; }
    public ProgressBar getActiveBar() { return activeBar; }

    public void updateDisk(SystemMonitorService.PhysicalDiskSnapshot snap,
                           DecimalFormat percentFormat,
                           DecimalFormat gbFormat) {

        String diskType = detectDiskType(snap.model);
        titleLabel.setText("Disk " + snap.index + " • " + diskType);

        if (snap.hasUsage) {
            usedValueLabel.setText("Used: " + percentFormat.format(snap.usedPercent) + " %");
            usedBar.setProgress(snap.usedPercent / 100.0);

            spaceLabel.setText(
                    gbFormat.format(snap.usedGb) + " / " + gbFormat.format(snap.totalGb) + " GB"
            );
        } else {
            usedValueLabel.setText("Used: N/A");
            usedBar.setProgress(0);
            spaceLabel.setText("Size: " + gbFormat.format(snap.sizeGb) + " GB");
        }

        activeValueLabel.setText("Active: " + percentFormat.format(snap.activePercent) + " %");
        activeBar.setProgress(snap.activePercent / 100.0);
    }
}
