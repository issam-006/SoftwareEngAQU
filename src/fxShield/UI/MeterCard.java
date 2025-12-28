package fxShield.UI;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MeterCard extends BaseCard {

    private static final DecimalFormat DF = new DecimalFormat("0.0");

    // Fonts are cached (single allocation) via StyleConstants
    private static final Font TITLE_NORMAL = StyleConstants.FONT_CARD_TITLE_20_BOLD;
    private static final Font VALUE_NORMAL = StyleConstants.FONT_VALUE_18;
    private static final Font EXTRA_NORMAL = StyleConstants.FONT_BODY_13;

    private static final Font TITLE_COMPACT = StyleConstants.FONT_CARD_TITLE_16_BOLD;
    private static final Font VALUE_COMPACT = StyleConstants.FONT_VALUE_15;
    private static final Font EXTRA_COMPACT = StyleConstants.FONT_BODY_11;

    private final VBox root;
    private final Label titleLabel;
    private final Label valueLabel;
    private final Label extraLabel;
    private final ProgressBar bar;

    // Prevent UI backlog: keep only the latest update if called rapidly from background threads
    private final AtomicBoolean uiUpdateQueued = new AtomicBoolean(false);
    private volatile double pendingPercent = Double.NaN;
    private volatile String pendingExtra = "";

    // Small caches to avoid redundant UI work
    private String lastUsageColor = null;
    private double lastProgress01 = Double.NaN;
    private String lastValueText = null;
    private String lastExtraText = null;

    public MeterCard(String titleText) {
        if (titleText == null) throw new IllegalArgumentException("titleText cannot be null");

        titleLabel = new Label(titleText);
        titleLabel.setTextFill(colorFromHex(COLOR_TEXT_MEDIUM));
        titleLabel.setFont(TITLE_NORMAL);
        // Keep only non-font CSS here to avoid any font shrinking side effects
        titleLabel.setStyle("-fx-effect: none;");
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);

        valueLabel = new Label("Loading...");
        valueLabel.setTextFill(colorFromHex(COLOR_TEXT_LIGHT));
        valueLabel.setFont(VALUE_NORMAL);

        extraLabel = new Label("Waiting for first sample...");
        extraLabel.setTextFill(colorFromHex(COLOR_TEXT_DIM));
        extraLabel.setFont(EXTRA_NORMAL);
        extraLabel.setWrapText(true);

        bar = new ProgressBar(0);
        bar.setPrefWidth(260);
        setBarAccentColor(bar, COLOR_PRIMARY);
        lastUsageColor = COLOR_PRIMARY;
        lastProgress01 = 0.0;

        root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(22));
        root.setStyle(StyleConstants.CARD_STANDARD);
        root.setMinHeight(240);
        root.setMinWidth(280);
        root.setPrefWidth(320);
        root.setMaxWidth(520);

        extraLabel.maxWidthProperty().bind(root.widthProperty().subtract(32));
        root.getChildren().addAll(titleLabel, valueLabel, bar, extraLabel);
    }

    @Override
    public VBox getRoot() { return root; }

    public Label getTitleLabel() { return titleLabel; }

    // Safe single-entry update:
    // - If called from FX thread: updates immediately
    // - If called from background thread: coalesces updates (keeps latest only)
    public void setValuePercent(double percent, String extraText) {
        if (Platform.isFxApplicationThread()) {
            applyValue(clamp(percent, 0, 100), extraText);
        } else {
            setValuePercentAsync(percent, extraText);
        }
    }

    public void setValuePercent(double percent) {
        setValuePercent(percent, "");
    }

    public void setValuePercentAsync(double percent, String extraText) {
        pendingPercent = percent;
        pendingExtra = (extraText != null) ? extraText : "";

        if (!uiUpdateQueued.compareAndSet(false, true)) {
            return; // already queued; latest values will be applied soon
        }

        Platform.runLater(() -> {
            uiUpdateQueued.set(false);
            applyValue(clamp(pendingPercent, 0, 100), pendingExtra);
        });
    }

    public void setUnavailable(String message) {
        Runnable r = () -> {
            setTextIfChanged(valueLabel, "N/A", true);
            setTextIfChanged(extraLabel, (message != null) ? message : "Not available", false);
            setProgressIfChanged(0.0);
            applyUnavailableStyle();
        };

        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    @Override
    public void setCompact(boolean compact) {
        if (compact) {
            titleLabel.setFont(TITLE_COMPACT);
            valueLabel.setFont(VALUE_COMPACT);
            extraLabel.setFont(EXTRA_COMPACT);

            root.setPadding(new Insets(12));
            root.setSpacing(8);
            root.setMinWidth(200);
            root.setPrefWidth(240);
            root.setMinHeight(180);
        } else {
            titleLabel.setFont(TITLE_NORMAL);
            valueLabel.setFont(VALUE_NORMAL);
            extraLabel.setFont(EXTRA_NORMAL);

            root.setPadding(new Insets(22));
            root.setSpacing(14);
            root.setMinWidth(280);
            root.setPrefWidth(320);
            root.setMinHeight(240);
        }
    }

    // -------- internals --------

    private void applyValue(double percent, String extraText) {
        String valueText = DF.format(percent) + " %";
        setTextIfChanged(valueLabel, valueText, true);

        String ex = (extraText != null) ? extraText : "";
        setTextIfChanged(extraLabel, ex, false);

        setProgressIfChanged(percent / 100.0);
        applyColorByUsage(percent);
    }

    private void applyColorByUsage(double percent) {
        String color = getColorByUsage(percent);
        if (color.equals(lastUsageColor)) return;

        lastUsageColor = color;
        valueLabel.setTextFill(colorFromHex(color));
        setBarAccentColor(bar, color); // CSS change happens only when color actually changes
    }

    private void applyUnavailableStyle() {
        valueLabel.setTextFill(colorFromHex(COLOR_TEXT_DIM));
        if (!COLOR_PRIMARY.equals(lastUsageColor)) {
            lastUsageColor = COLOR_PRIMARY;
            setBarAccentColor(bar, COLOR_PRIMARY);
        }
    }

    private void setProgressIfChanged(double progress01) {
        double p = clamp01(progress01);
        if (Double.isNaN(lastProgress01) || Math.abs(lastProgress01 - p) > 0.000001) {
            lastProgress01 = p;
            bar.setProgress(p);
        }
    }

    private void setTextIfChanged(Label label, String text, boolean isValueLabel) {
        String t = (text != null) ? text : "";
        if (isValueLabel) {
            if (t.equals(lastValueText)) return;
            lastValueText = t;
        } else {
            if (t.equals(lastExtraText)) return;
            lastExtraText = t;
        }
        label.setText(t);
    }
}
