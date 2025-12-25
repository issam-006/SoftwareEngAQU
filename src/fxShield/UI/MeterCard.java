package fxShield.UI;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.text.DecimalFormat;

public final class MeterCard extends BaseCard {

    private static final DecimalFormat DF = new DecimalFormat("0.0");

    private static final String CARD_STYLE =
            "-fx-background-color: linear-gradient(to bottom right, rgba(23, 18, 48, 0.65), rgba(13, 10, 28, 0.85));" +
                    "-fx-background-radius: 28;" +
                    "-fx-border-radius: 28;" +
                    "-fx-border-color: rgba(255,255,255,0.14);" +
                    "-fx-border-width: 1.2;" +
                    "-fx-effect: dropshadow(gaussian, rgba(130, 80, 255, 0.22), 20, 0, 0, 4);";

    // Cache fonts (avoid re-allocations)
    private static final Font TITLE_NORMAL = Font.font(FONT_FAMILY, 20);
    private static final Font VALUE_NORMAL = Font.font(FONT_FAMILY, 18);
    private static final Font EXTRA_NORMAL = Font.font(FONT_FAMILY, 13);

    private static final Font TITLE_COMPACT = Font.font(FONT_FAMILY, 16);
    private static final Font VALUE_COMPACT = Font.font(FONT_FAMILY, 15);
    private static final Font EXTRA_COMPACT = Font.font(FONT_FAMILY, 11);

    // Cache Insets too (avoid allocations on toggles)
    private static final Insets PAD_NORMAL = new Insets(22);
    private static final Insets PAD_COMPACT = new Insets(12);

    private final VBox root;
    private final Label titleLabel;
    private final Label valueLabel;
    private final Label extraLabel;
    private final ProgressBar bar;

    // ✅ compact gating
    private boolean compactState = false;
    private boolean compactInitialized = false;

    public MeterCard(String titleText) {
        titleLabel = new Label(titleText);
        titleLabel.setTextFill(colorFromHex(COLOR_TEXT_MEDIUM));
        titleLabel.setFont(TITLE_NORMAL);
        titleLabel.setStyle("-fx-font-weight: bold;");
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

        root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setPadding(PAD_NORMAL);
        root.setStyle(CARD_STYLE);
        root.setMinHeight(240);
        root.setMinWidth(280);
        root.setPrefWidth(320);
        root.setMaxWidth(520);

        extraLabel.maxWidthProperty().bind(root.widthProperty().subtract(32));
        watchFont(titleLabel, "MeterCard.title");
        watchFont(valueLabel, "MeterCard.value");
        watchFont(extraLabel, "MeterCard.extra");
        watchScale(root, "MeterCard.root");
        root.getChildren().addAll(titleLabel, valueLabel, bar, extraLabel);

        // ensure first-time apply is correct
        setCompact(false);
    }

    public void setValuePercent(double percent, String extraText) {
        percent = clamp(percent, 0, 100);
        valueLabel.setText(DF.format(percent) + " %");
        extraLabel.setText(extraText != null ? extraText : "");
        bar.setProgress(percent / 100.0);
        applyColorByUsage(percent);
    }

    public void setValuePercent(double percent) {
        setValuePercent(percent, "");
    }

    public void setValuePercentAsync(double percent, String extraText) {
        Platform.runLater(() -> setValuePercent(percent, extraText));
    }

    public void setUnavailable(String message) {
        valueLabel.setText("N/A");
        extraLabel.setText(message != null ? message : "Not available");
        bar.setProgress(0);
        applyUnavailableStyle();
    }

    @Override
    public VBox getRoot() { return root; }

    public Label getTitleLabel() { return titleLabel; }

    @Override
    public void setCompact(boolean compact) {
        // ✅ idempotent: ignore repeats
        if (compactInitialized && compact == compactState) return;

        compactInitialized = true;
        compactState = compact;

        debugCompact("MeterCard", compact);

        if (compact) {
            titleLabel.setFont(TITLE_COMPACT);
            valueLabel.setFont(VALUE_COMPACT);
            extraLabel.setFont(EXTRA_COMPACT);

            root.setPadding(PAD_COMPACT);
            root.setSpacing(8);
            root.setMinWidth(200);
            root.setPrefWidth(240);
            root.setMinHeight(180);
        } else {
            titleLabel.setFont(TITLE_NORMAL);
            valueLabel.setFont(VALUE_NORMAL);
            extraLabel.setFont(EXTRA_NORMAL);

            root.setPadding(PAD_NORMAL);
            root.setSpacing(14);
            root.setMinWidth(280);
            root.setPrefWidth(320);
            root.setMinHeight(240);
        }
    }

    private void applyColorByUsage(double percent) {
        String color = getColorByUsage(percent);
        valueLabel.setTextFill(colorFromHex(color));
        setBarAccentColor(bar, color);
    }

    private void applyUnavailableStyle() {
        valueLabel.setTextFill(colorFromHex(COLOR_TEXT_DIM));
        setBarAccentColor(bar, COLOR_PRIMARY);
    }
}
