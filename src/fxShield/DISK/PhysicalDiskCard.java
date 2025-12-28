package fxShield.DISK;

import fxShield.UX.SystemMonitorService;
import fxShield.UI.BaseCard;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class PhysicalDiskCard extends BaseCard {

    private static final Map<String, String> DISK_TYPE_CACHE = new ConcurrentHashMap<>();

    private static final Pattern NVME_PATTERN = Pattern.compile("\\b(nvme|nvm|pci-?e|pci express|pcie)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern M2_PATTERN   = Pattern.compile("\\b(m\\.2|m2)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSD_PATTERN  = Pattern.compile("\\b(ssd|solid state|flash)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HDD_PATTERN  = Pattern.compile("\\b(hdd|rotational|rpm)\\b", Pattern.CASE_INSENSITIVE);

    private static final String CARD_STYLE =
            "-fx-background-color: rgba(17,13,34,0.55);" +
                    "-fx-background-radius: 28;" +
                    "-fx-border-radius: 28;" +
                    "-fx-border-color: rgba(255,255,255,0.10);" +
                    "-fx-border-width: 1;" +
                    "-fx-effect: dropshadow(gaussian, rgba(157,110,255,0.28), 25, 0.25, 0, 0);";

    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.0");
    private static final String FONT_UI = "Segoe UI";

    // cached fonts (no CSS font-weight)
    private static final Font TITLE_REG   = Font.font(FONT_UI, FontWeight.BOLD, 20);
    private static final Font USED_REG    = Font.font(FONT_UI, FontWeight.NORMAL, 17);
    private static final Font ACTIVE_REG  = Font.font(FONT_UI, FontWeight.NORMAL, 17);
    private static final Font SPACE_REG   = Font.font(FONT_UI, FontWeight.NORMAL, 13);

    private static final Font TITLE_COMP  = Font.font(FONT_UI, FontWeight.BOLD, 15);
    private static final Font USED_COMP   = Font.font(FONT_UI, FontWeight.NORMAL, 14);
    private static final Font ACTIVE_COMP = Font.font(FONT_UI, FontWeight.NORMAL, 14);
    private static final Font SPACE_COMP  = Font.font(FONT_UI, FontWeight.NORMAL, 11);

    private final StackPane root;
    private final VBox content;

    private final Label titleLabel;
    private final Label usedValueLabel;
    private final Label spaceLabel;
    private final Label activeValueLabel;

    private final ProgressBar usedBar;
    private final ProgressBar activeBar;

    private final GridPane headerGrid;
    private final ColumnConstraints col0;
    private final ColumnConstraints col1;
    private final ColumnConstraints col2;

    private Node switcherNode;
    private boolean compact;

    // overrides (separated to avoid “sticky” wrong type)
    private String manualTypeOverride; // set by UI / user
    private String snapTypeOverride;   // set from snapshot (if provided)

    public PhysicalDiskCard(int indexZeroBased, String model, double sizeGb) {

        titleLabel = new Label();
        titleLabel.setTextFill(colorFromHex(COLOR_TEXT_MEDIUM));
        titleLabel.setFont(TITLE_REG);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        headerGrid = new GridPane();
        headerGrid.setMinHeight(36);
        headerGrid.setPadding(new Insets(0, 4, 0, 4));

        col0 = new ColumnConstraints();
        col0.setMinWidth(0);
        col0.setHalignment(HPos.LEFT);

        col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        col1.setHalignment(HPos.CENTER);
        col1.setMinWidth(0);

        col2 = new ColumnConstraints();
        col2.setMinWidth(0);

        headerGrid.getColumnConstraints().addAll(col0, col1, col2);
        headerGrid.add(titleLabel, 1, 0);

        usedValueLabel = new Label("Used: Loading...");
        usedValueLabel.setAlignment(Pos.CENTER);
        usedValueLabel.setMaxWidth(Double.MAX_VALUE);
        usedValueLabel.setTextFill(colorFromHex(COLOR_TEXT_LIGHT));
        usedValueLabel.setFont(USED_REG);

        usedBar = new ProgressBar(0);
        makeBarFullWidth(usedBar);
        setBarAccentColor(usedBar, COLOR_PRIMARY);

        spaceLabel = new Label();
        spaceLabel.setAlignment(Pos.CENTER);
        spaceLabel.setMaxWidth(Double.MAX_VALUE);
        spaceLabel.setTextFill(colorFromHex(COLOR_TEXT_DIM));
        spaceLabel.setFont(SPACE_REG);

        activeValueLabel = new Label("Active: 0 %");
        activeValueLabel.setAlignment(Pos.CENTER);
        activeValueLabel.setMaxWidth(Double.MAX_VALUE);
        activeValueLabel.setTextFill(colorFromHex(COLOR_TEXT_LIGHT));
        activeValueLabel.setFont(ACTIVE_REG);

        activeBar = new ProgressBar(0);
        makeBarFullWidth(activeBar);
        setBarAccentColor(activeBar, COLOR_INFO);

        content = new VBox(14);
        content.setPadding(new Insets(22));
        content.setAlignment(Pos.TOP_CENTER);
        content.setStyle(CARD_STYLE);
        content.setMaxWidth(520);

        content.getChildren().addAll(
                headerGrid,
                usedValueLabel,
                usedBar,
                spaceLabel,
                activeValueLabel,
                activeBar
        );

        root = new StackPane(content);
        root.setMaxWidth(520);

        setCompact(false);
        setDiskInfo(indexZeroBased, model, sizeGb);
    }

    public void setSwitcherNode(Node node) {
        if (switcherNode != null) headerGrid.getChildren().remove(switcherNode);
        switcherNode = node;

        if (switcherNode != null) {
            if (switcherNode instanceof Region r) {
                r.setMaxWidth(Region.USE_PREF_SIZE);
                r.setMaxHeight(Region.USE_PREF_SIZE);
            }
            headerGrid.add(switcherNode, 0, 0);
        }

        updateSwitcherColumns();
    }

    public void setDiskInfo(int indexZeroBased, String model, double sizeGb) {
        snapTypeOverride = null; // reset snapshot override when manually setting base info
        String diskType = resolveDiskType(model);
        titleLabel.setText(diskTitle(indexZeroBased) + " • " + diskType);
        spaceLabel.setText("Size: " + SIZE_FORMAT.format(sizeGb) + " GB");
    }

    public void updateDisk(SystemMonitorService.PhysicalDiskSnapshot snap,
                           DecimalFormat percentFormat,
                           DecimalFormat gbFormat) {

        if (snap == null) {
            snapTypeOverride = null;
            usedValueLabel.setText("Used: N/A");
            usedBar.setProgress(0);
            activeValueLabel.setText("Active: N/A");
            activeBar.setProgress(0);
            return;
        }

        // keep snapshot override ONLY for this snapshot (no sticky old type)
        if (snap.typeLabel != null && !snap.typeLabel.isBlank()) snapTypeOverride = snap.typeLabel;
        else snapTypeOverride = null;

        String diskType = resolveDiskType(snap.model);
        titleLabel.setText(diskTitle(snap.index) + " • " + diskType);

        DecimalFormat pf = (percentFormat != null) ? percentFormat : new DecimalFormat("0");
        DecimalFormat gf = (gbFormat != null) ? gbFormat : new DecimalFormat("0.0");

        if (snap.hasUsage) {
            usedValueLabel.setText("Used: " + pf.format(snap.usedPercent) + " %");
            usedBar.setProgress(clamp01(snap.usedPercent / 100.0));
            spaceLabel.setText(gf.format(snap.usedGb) + " / " + gf.format(snap.totalGb) + " GB");
        } else {
            usedValueLabel.setText("Used: N/A");
            usedBar.setProgress(0);
            spaceLabel.setText("Size: " + gf.format(snap.sizeGb) + " GB");
        }

        activeValueLabel.setText("Active: " + pf.format(snap.activePercent) + " %");
        activeBar.setProgress(clamp01(snap.activePercent / 100.0));
    }

    @Override
    public StackPane getRoot() { return root; }

    @Override
    public void setCompact(boolean compact) {
        this.compact = compact;
        if (compact) applyCompact();
        else applyRegular();
        updateSwitcherColumns();
    }

    public Label getUsedValueLabel() { return usedValueLabel; }
    public Label getSpaceLabel() { return spaceLabel; }
    public Label getActiveValueLabel() { return activeValueLabel; }
    public ProgressBar getUsedBar() { return usedBar; }
    public ProgressBar getActiveBar() { return activeBar; }

    private void applyCompact() {
        titleLabel.setFont(TITLE_COMP);
        usedValueLabel.setFont(USED_COMP);
        activeValueLabel.setFont(ACTIVE_COMP);
        spaceLabel.setFont(SPACE_COMP);

        content.setPadding(new Insets(12));
        content.setSpacing(8);
        content.setMinWidth(200);
        content.setPrefWidth(240);
        content.setMinHeight(180);

        root.setMinWidth(200);
        root.setPrefWidth(240);
        root.setMinHeight(180);
    }

    private void applyRegular() {
        titleLabel.setFont(TITLE_REG);
        usedValueLabel.setFont(USED_REG);
        activeValueLabel.setFont(ACTIVE_REG);
        spaceLabel.setFont(SPACE_REG);

        content.setPadding(new Insets(22));
        content.setSpacing(14);
        content.setMinWidth(280);
        content.setPrefWidth(320);
        content.setMinHeight(240);

        root.setMinWidth(280);
        root.setPrefWidth(320);
        root.setMinHeight(240);
    }

    private void updateSwitcherColumns() {
        double w = (switcherNode == null) ? 0 : (compact ? 62 : 85);

        col0.setPrefWidth(w);
        col2.setPrefWidth(w);

        if (w <= 0) {
            col0.setMinWidth(0);
            col2.setMinWidth(0);
        } else {
            col0.setMinWidth(Region.USE_PREF_SIZE);
            col2.setMinWidth(Region.USE_PREF_SIZE);
        }
    }

    private static void makeBarFullWidth(ProgressBar bar) {
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefWidth(Double.MAX_VALUE);
        VBox.setVgrow(bar, Priority.NEVER);
    }

    private String resolveDiskType(String model) {
        if (manualTypeOverride != null && !manualTypeOverride.isBlank()) return normalizeType(manualTypeOverride);
        if (snapTypeOverride != null && !snapTypeOverride.isBlank()) return normalizeType(snapTypeOverride);
        return detectDiskType(model);
    }

    public void setDiskTypeOverride(String type) {
        this.manualTypeOverride = type;
    }

    private static String normalizeType(String t) {
        if (t == null) return "Disk";
        String v = t.trim();
        if (v.isEmpty()) return "Disk";

        if (v.equalsIgnoreCase("nvme")) return "NVMe";
        if (v.equalsIgnoreCase("solid state drive") || v.equalsIgnoreCase("solidstate")) return "SSD";
        if (v.equalsIgnoreCase("hard disk drive") || v.equalsIgnoreCase("harddisk")) return "HDD";
        if (v.equalsIgnoreCase("hdd")) return "HDD";
        if (v.equalsIgnoreCase("ssd")) return "SSD";

        // keep a nice casing for NVMe-like strings
        if (v.equalsIgnoreCase("nvme ssd") || v.equalsIgnoreCase("m.2 nvme")) return "NVMe";

        return v.toUpperCase(Locale.ROOT);
    }

    private static String detectDiskType(String model) {
        if (model == null) return "Disk";
        String key = model.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) return "Disk";

        return DISK_TYPE_CACHE.computeIfAbsent(key, PhysicalDiskCard::computeDiskType);
    }

    private static String computeDiskType(String m) {
        if (m == null || m.isBlank()) return "Disk";

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
                    || m.contains("evo") || m.contains("pro") || m.contains("mx") || m.contains("qvo")
                    || m.contains("ext") || m.contains("plus")) {
                return "SSD";
            }
            return "Disk";
        }

        return "Disk";
    }

    private static String diskTitle(int indexZeroBased) {
        int shown = Math.max(0, indexZeroBased) + 1; // UI: 1-based
        return "Disk " + shown;
    }

    public static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
