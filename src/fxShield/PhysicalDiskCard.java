package fxShield;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class PhysicalDiskCard {

    private static final Map<String, String> diskTypeCache = new ConcurrentHashMap<>();
    private static final Pattern NVME_PATTERN = Pattern.compile("\\b(nvme|nvm|pci-?e|pci express|pcie)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern M2_PATTERN   = Pattern.compile("\\b(m\\.2|m2)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSD_PATTERN  = Pattern.compile("\\b(ssd|solid state|flash)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HDD_PATTERN  = Pattern.compile("\\b(hdd|rotational|rpm)\\b", Pattern.CASE_INSENSITIVE);

    private static final boolean DEBUG = false;

    private static final String CARD_STYLE =
            "-fx-background-color: rgba(17,13,34,0.55);" +
                    "-fx-background-radius: 28;" +
                    "-fx-border-radius: 28;" +
                    "-fx-border-color: rgba(255,255,255,0.10);" +
                    "-fx-border-width: 1;" +
                    "-fx-effect: dropshadow(gaussian, rgba(157,110,255,0.28), 25, 0.25, 0, 0);";

    private static final String BAR_BG = "-fx-control-inner-background: rgba(255,255,255,0.08);";
    private static final String ACCENT_USED   = "#a78bfa";
    private static final String ACCENT_ACTIVE = "#7dd3fc";

    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.0");

    private final StackPane root;
    private final VBox content;

    // Header: title centered, switcher on the left
    private Pane headerRow;

    private final Label titleLabel;
    private final Label usedValueLabel;
    private final Label spaceLabel;
    private final Label activeValueLabel;

    private final ProgressBar usedBar;
    private final ProgressBar activeBar;

    private final ColumnConstraints col0;
    private final ColumnConstraints col1;
    private final ColumnConstraints col2;

    private Node switcherNode = null;
    private boolean isCompact = false;

    private String typeOverride = null;

    public PhysicalDiskCard(int index, String model, double sizeGb) {

        String diskType = resolveDiskType(model);

        // ===== Title (centered) =====
        titleLabel = new Label("Disk " + index + " • " + diskType);
        titleLabel.setTextFill(Color.web("#e9d8ff"));
        titleLabel.setFont(Font.font("Segoe UI", 20));
        titleLabel.setStyle("-fx-font-weight: bold;");
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        // ===== Header (GridPane) =====
        GridPane headerGrid = new GridPane();
        headerGrid.setMinHeight(36);
        headerGrid.setPadding(new Insets(0, 4, 0, 4));

        this.col0 = new ColumnConstraints();
        col0.setPrefWidth(0); // Start at 0
        col0.setMinWidth(0);
        col0.setHalignment(HPos.LEFT);

        this.col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        col1.setHalignment(HPos.CENTER);
        col1.setMinWidth(0);

        this.col2 = new ColumnConstraints();
        col2.setPrefWidth(0); // Start at 0
        col2.setMinWidth(0);

        headerGrid.getColumnConstraints().addAll(col0, col1, col2);
        headerGrid.add(titleLabel, 1, 0);

        headerRow = headerGrid;

        // ===== Used =====
        usedValueLabel = new Label("Used: Loading...");
        usedValueLabel.setAlignment(Pos.CENTER);
        usedValueLabel.setMaxWidth(Double.MAX_VALUE);
        usedValueLabel.setTextFill(Color.web("#f5e8ff"));
        usedValueLabel.setFont(Font.font("Segoe UI", 17));

        usedBar = new ProgressBar(0);
        makeBarFullWidth(usedBar);
        setBarAccent(usedBar, ACCENT_USED);

        // ===== Space =====
        spaceLabel = new Label("Size: " + SIZE_FORMAT.format(sizeGb) + " GB");
        spaceLabel.setAlignment(Pos.CENTER);
        spaceLabel.setMaxWidth(Double.MAX_VALUE);
        spaceLabel.setTextFill(Color.web("#c5b3ff"));
        spaceLabel.setFont(Font.font("Segoe UI", 13));

        // ===== Active =====
        activeValueLabel = new Label("Active: 0 %");
        activeValueLabel.setAlignment(Pos.CENTER);
        activeValueLabel.setMaxWidth(Double.MAX_VALUE);
        activeValueLabel.setTextFill(Color.web("#f5e8ff"));
        activeValueLabel.setFont(Font.font("Segoe UI", 17));

        activeBar = new ProgressBar(0);
        makeBarFullWidth(activeBar);
        setBarAccent(activeBar, ACCENT_ACTIVE);

        // ===== Content =====
        content = new VBox(14);
        content.setPadding(new Insets(22));
        content.setAlignment(Pos.TOP_CENTER);
        content.setStyle(CARD_STYLE);

        content.setMinHeight(240);
        content.setMinWidth(280);
        content.setPrefWidth(320);
        content.setMaxWidth(520);

        content.getChildren().addAll(
                headerRow,
                usedValueLabel,
                usedBar,
                spaceLabel,
                activeValueLabel,
                activeBar
        );

        root = new StackPane(content);
        root.setMaxWidth(520);
        root.setMinWidth(280);
        root.setPrefWidth(320);
    }

    /**
     * ضع هنا diskSwitcher.getRoot()
     * سيظهر على يسار الهيدر، بينما العنوان يبقى بالمنتصف.
     */
    public void setSwitcherNode(Node node) {
        if (headerRow == null || !(headerRow instanceof GridPane grid)) return;

        if (switcherNode != null) {
            grid.getChildren().remove(switcherNode);
        }

        switcherNode = node;

        double w = (switcherNode != null) ? (isCompact ? 62 : 85) : 0;
        col0.setPrefWidth(w);
        col0.setMinWidth(w > 0 ? Region.USE_PREF_SIZE : 0);
        col2.setPrefWidth(w);
        col2.setMinWidth(w > 0 ? Region.USE_PREF_SIZE : 0);

        if (switcherNode != null) {
            if (switcherNode instanceof Region r) {
                r.setMaxWidth(Region.USE_PREF_SIZE);
                r.setMaxHeight(Region.USE_PREF_SIZE);
            }
            grid.add(switcherNode, 0, 0);
        }
    }

    // ===== Type resolution =====
    private String resolveDiskType(String model) {
        if (typeOverride != null && !typeOverride.isBlank()) return normalizeType(typeOverride);
        return detectDiskType(model);
    }

    private String normalizeType(String t) {
        String v = t.trim();
        if (v.equalsIgnoreCase("solid state drive") || v.equalsIgnoreCase("solidstate")) return "SSD";
        if (v.equalsIgnoreCase("hard disk drive") || v.equalsIgnoreCase("harddisk")) return "HDD";
        return v.toUpperCase(Locale.ROOT);
    }

    public void setDiskTypeOverride(String type) {
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

        if (NVME_PATTERN.matcher(m).find() || (M2_PATTERN.matcher(m).find() && m.contains("nvme"))) return "NVMe";
        if (SSD_PATTERN.matcher(m).find() || M2_PATTERN.matcher(m).find()) return "SSD";

        if (m.contains("samsung") && (m.contains("evo") || m.contains("pro") || m.contains("pm") || m.contains("qvo"))) return "SSD";
        if (m.contains("kingston") || m.contains("crucial") || m.contains("sandisk") || m.contains("intel") || m.contains("micron")) return "SSD";

        if (HDD_PATTERN.matcher(m).find()) return "HDD";

        if (m.contains("seagate") || m.contains("western digital") || m.contains("wd")) {
            if (SSD_PATTERN.matcher(m).find() || m.contains("ssd") || m.contains("nvme") || m.contains("m.2") || m.contains("m2")) return "SSD";
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

    // ===== Public API =====
    public void setDiskInfo(int index, String model, double sizeGb) {
        String diskType = resolveDiskType(model);
        titleLabel.setText("Disk " + index + " • " + diskType);
        spaceLabel.setText("Size: " + SIZE_FORMAT.format(sizeGb) + " GB");
    }

    public void updateDisk(SystemMonitorService.PhysicalDiskSnapshot snap,
                           DecimalFormat percentFormat,
                           DecimalFormat gbFormat) {
        if (snap == null) {
            usedValueLabel.setText("Used: N/A");
            usedBar.setProgress(0);
            activeValueLabel.setText("Active: N/A");
            activeBar.setProgress(0);
            return;
        }

        if (snap.typeLabel != null && !snap.typeLabel.isBlank()) {
            this.typeOverride = snap.typeLabel;
        }

        String diskType = resolveDiskType(snap.model);
        titleLabel.setText("Disk " + snap.index + " • " + diskType);

        if (snap.hasUsage) {
            usedValueLabel.setText("Used: " + percentFormat.format(snap.usedPercent) + " %");
            usedBar.setProgress(clamp01(snap.usedPercent / 100.0));
            spaceLabel.setText(gbFormat.format(snap.usedGb) + " / " + gbFormat.format(snap.totalGb) + " GB");
        } else {
            usedValueLabel.setText("Used: N/A");
            usedBar.setProgress(0);
            spaceLabel.setText("Size: " + gbFormat.format(snap.sizeGb) + " GB");
        }

        activeValueLabel.setText("Active: " + percentFormat.format(snap.activePercent) + " %");
        activeBar.setProgress(clamp01(snap.activePercent / 100.0));
    }

    private static void setBarAccent(ProgressBar bar, String accentHex) {
        bar.setStyle(BAR_BG + "-fx-accent: " + accentHex + ";");
    }

    private static void makeBarFullWidth(ProgressBar bar) {
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefWidth(Double.MAX_VALUE);
        VBox.setVgrow(bar, Priority.NEVER);
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    public StackPane getRoot() { return root; }

    public void setCompact(boolean compact) {
        this.isCompact = compact;
        if (compact) {
            titleLabel.setFont(Font.font("Segoe UI", 15));
            usedValueLabel.setFont(Font.font("Segoe UI", 14));
            activeValueLabel.setFont(Font.font("Segoe UI", 14));
            spaceLabel.setFont(Font.font("Segoe UI", 11));

            double w = (switcherNode != null) ? 62 : 0;
            col0.setPrefWidth(w);
            col0.setMinWidth(w > 0 ? Region.USE_PREF_SIZE : 0);
            col2.setPrefWidth(w);
            col2.setMinWidth(w > 0 ? Region.USE_PREF_SIZE : 0);

            content.setPadding(new Insets(12));
            content.setSpacing(8);
            content.setMinWidth(200);
            content.setPrefWidth(240);
            content.setMinHeight(180);

            root.setMinWidth(200);
            root.setPrefWidth(240);
        } else {
            titleLabel.setFont(Font.font("Segoe UI", 20));
            usedValueLabel.setFont(Font.font("Segoe UI", 17));
            activeValueLabel.setFont(Font.font("Segoe UI", 17));
            spaceLabel.setFont(Font.font("Segoe UI", 13));

            double w = (switcherNode != null) ? 85 : 0;
            col0.setPrefWidth(w);
            col0.setMinWidth(w > 0 ? Region.USE_PREF_SIZE : 0);
            col2.setPrefWidth(w);
            col2.setMinWidth(w > 0 ? Region.USE_PREF_SIZE : 0);

            content.setPadding(new Insets(22));
            content.setSpacing(14);
            content.setMinWidth(280);
            content.setPrefWidth(320);
            content.setMinHeight(240);

            root.setMinWidth(280);
            root.setPrefWidth(320);
        }
    }

    public Label getUsedValueLabel() { return usedValueLabel; }
    public Label getSpaceLabel() { return spaceLabel; }
    public Label getActiveValueLabel() { return activeValueLabel; }
    public ProgressBar getUsedBar() { return usedBar; }
    public ProgressBar getActiveBar() { return activeBar; }
}