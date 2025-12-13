package fxShield;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class PhysicalDiskCard {

    private static final Map<String, String> diskTypeCache = new ConcurrentHashMap<>();
    private static final Pattern NVME_PATTERN = Pattern.compile("\\b(nvme|nvm|pci-?e|pci express|pcie)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern M2_PATTERN = Pattern.compile("\\b(m\\.2|m2)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSD_PATTERN = Pattern.compile("\\b(ssd|solid state|flash)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HDD_PATTERN = Pattern.compile("\\b(hdd|rotational|rpm)\\b", Pattern.CASE_INSENSITIVE);

    // Toggle for debug prints
    private static final boolean DEBUG = false;

    // Reuse formatters (avoid new DecimalFormat in multiple places)
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.0");

    private final VBox root;
    private final Label titleLabel;
    private final Label usedValueLabel;
    private final Label spaceLabel;
    private final Label activeValueLabel;
    private final ProgressBar usedBar;
    private final ProgressBar activeBar;

    // Optional: let service override type (SSD/HDD/NVMe) without changing UI
    private String typeOverride = null;

    public PhysicalDiskCard(int index, String model, double sizeGb) {

        String diskType = resolveDiskType(model);

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

        spaceLabel = new Label("Size: " + SIZE_FORMAT.format(sizeGb) + " GB");
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

    // =========================
    // Type resolution
    // =========================

    private String resolveDiskType(String model) {
        // If override set, use it
        if (typeOverride != null && !typeOverride.isBlank()) {
            return normalizeType(typeOverride);
        }
        // Otherwise keep your heuristics (same as before)
        return detectDiskType(model);
    }

    private String normalizeType(String t) {
        String v = t.trim();
        if (v.equalsIgnoreCase("solid state drive") || v.equalsIgnoreCase("solidstate")) return "SSD";
        if (v.equalsIgnoreCase("hard disk drive") || v.equalsIgnoreCase("harddisk")) return "HDD";
        return v.toUpperCase();
    }

    public void setDiskTypeOverride(String type) {
        // Optional: if you want the service to set SSD/HDD/NVMe 정확
        this.typeOverride = type;
    }

    private String detectDiskType(String model) {
        if (model == null || model.trim().isEmpty()) return "Disk";

        String key = model.trim().toLowerCase(Locale.ROOT);
        String cached = diskTypeCache.get(key);
        if (cached != null) return cached;

        String result = computeDiskType(key);
        diskTypeCache.put(key, result);
        return result;
    }

    private String computeDiskType(String m) {
        if (DEBUG) System.out.println("Detecting disk type for model: " + m);

        if (NVME_PATTERN.matcher(m).find() || (M2_PATTERN.matcher(m).find() && m.contains("nvme"))) {
            return "NVMe";
        }

        if (SSD_PATTERN.matcher(m).find() || M2_PATTERN.matcher(m).find()) {
            return "SSD";
        }

        if (m.contains("samsung") && (m.contains("evo") || m.contains("pro") || m.contains("pm") || m.contains("qvo"))) return "SSD";
        if (m.contains("kingston") || m.contains("crucial") || m.contains("sandisk") || m.contains("intel") || m.contains("micron")) return "SSD";

        if (HDD_PATTERN.matcher(m).find()) return "HDD";

        if (m.contains("seagate") || m.contains("western digital") || m.contains("wd")) {
            if (SSD_PATTERN.matcher(m).find() || m.contains("ssd") || m.contains("nvme") || m.contains("m.2") || m.contains("m2")) {
                return "SSD";
            }
            return "HDD";
        }

        if (m.contains("sata")) {
            if (m.contains("ssd") || m.contains("nvme") || M2_PATTERN.matcher(m).find()
                    || m.contains("evo") || m.contains("pro") || m.contains("mx") || m.contains("qvo") || m.contains("ext") || m.contains("plus")) {
                return "SSD";
            }
            return "Disk";
        }

        return "Disk";
    }

    // =========================
    // Public API
    // =========================

    public void setDiskInfo(int index, String model, double sizeGb) {
        String diskType = resolveDiskType(model);
        titleLabel.setText("Disk " + index + " • " + diskType);
        spaceLabel.setText("Size: " + SIZE_FORMAT.format(sizeGb) + " GB");
    }

    public VBox getRoot() {
        return root;
    }

    public Label getUsedValueLabel() {
        return usedValueLabel;
    }

    public Label getSpaceLabel() {
        return spaceLabel;
    }

    public Label getActiveValueLabel() {
        return activeValueLabel;
    }

    public ProgressBar getUsedBar() {
        return usedBar;
    }

    public ProgressBar getActiveBar() {
        return activeBar;
    }

    public void updateDisk(SystemMonitorService.PhysicalDiskSnapshot snap,
                           DecimalFormat percentFormat,
                           DecimalFormat gbFormat) {

        if (snap == null) {
            // Safe fallback (no crash)
            usedValueLabel.setText("Used: N/A");
            usedBar.setProgress(0);
            activeValueLabel.setText("Active: N/A");
            activeBar.setProgress(0);
            return;
        }

        if (DEBUG) System.out.println("PhysicalDisk[" + snap.index + "] model: " + snap.model);

        // IMPORTANT: keep your title style exactly, but allow optional override via service
        if (snap.typeLabel != null && !snap.typeLabel.isBlank()) {
            // If you want: service drives SSD/HDD/NVMe
            // Comment next line if you want ONLY heuristics.
            this.typeOverride = snap.typeLabel;
        }

        String diskType = resolveDiskType(snap.model);
        titleLabel.setText("Disk " + snap.index + " • " + diskType);

        if (snap.hasUsage) {
            usedValueLabel.setText("Used: " + percentFormat.format(snap.usedPercent) + " %");
            usedBar.setProgress(clamp01(snap.usedPercent / 100.0));

            spaceLabel.setText(
                    gbFormat.format(snap.usedGb) + " / " + gbFormat.format(snap.totalGb) + " GB"
            );
        } else {
            usedValueLabel.setText("Used: N/A");
            usedBar.setProgress(0);
            spaceLabel.setText("Size: " + gbFormat.format(snap.sizeGb) + " GB");
        }

        activeValueLabel.setText("Active: " + percentFormat.format(snap.activePercent) + " %");
        activeBar.setProgress(clamp01(snap.activePercent / 100.0));
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
