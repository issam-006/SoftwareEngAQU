package fxShield;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.text.DecimalFormat;

/**
 * A card component that displays a metric with a progress bar.
 * Extends BaseCard for common styling and utility methods.
 */
public final class MeterCard extends BaseCard {

    private static final DecimalFormat DF = new DecimalFormat("0.0");

    private static final String CARD_STYLE =
            "-fx-background-color: linear-gradient(to bottom right, rgba(23, 18, 48, 0.65), rgba(13, 10, 28, 0.85));" +
                    "-fx-background-radius: 28;" +
                    "-fx-border-radius: 28;" +
                    "-fx-border-color: rgba(255,255,255,0.14);" +
                    "-fx-border-width: 1.2;" +
                    "-fx-effect: dropshadow(gaussian, rgba(130, 80, 255, 0.22), 20, 0, 0, 4);";

    private final VBox root;
    private final Label titleLabel;
    private final Label valueLabel;
    private final Label extraLabel;
    private final ProgressBar bar;

    public MeterCard(String titleText) {
        // Title
        titleLabel = new Label(titleText);
        titleLabel.setTextFill(colorFromHex(COLOR_TEXT_MEDIUM));
        titleLabel.setFont(Font.font(FONT_FAMILY, 20));
        titleLabel.setStyle("-fx-font-weight: bold;");
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);

        // Value
        valueLabel = new Label("Loading...");
        valueLabel.setTextFill(colorFromHex(COLOR_TEXT_LIGHT));
        valueLabel.setFont(Font.font(FONT_FAMILY, 18));

        // Extra
        extraLabel = new Label("Waiting for first sample...");
        extraLabel.setTextFill(colorFromHex(COLOR_TEXT_DIM));
        extraLabel.setFont(Font.font(FONT_FAMILY, 13));
        extraLabel.setWrapText(true);

        // Bar
        bar = new ProgressBar(0);
        bar.setPrefWidth(260);
        setBarAccentColor(bar, COLOR_PRIMARY);

        // Card
        root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(22));
        root.setStyle(CARD_STYLE);
        root.setMinHeight(240);
        root.setMinWidth(280);
        root.setPrefWidth(320);
        root.setMaxWidth(520);

        // Wrap extra text within card width
        extraLabel.maxWidthProperty().bind(root.widthProperty().subtract(32));

        root.getChildren().addAll(titleLabel, valueLabel, bar, extraLabel);
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

    public void setUnavailable() {
        setUnavailable(null);
    }

    @Override
    public VBox getRoot() { return root; }
    
    public Label getTitleLabel() { return titleLabel; }
    public Label getValueLabel() { return valueLabel; }
    public Label getExtraLabel() { return extraLabel; }
    public ProgressBar getBar() { return bar; }

    @Override
    public void setCompact(boolean compact) {
        if (compact) {
            titleLabel.setFont(Font.font(FONT_FAMILY, 16));
            valueLabel.setFont(Font.font(FONT_FAMILY, 15));
            extraLabel.setFont(Font.font(FONT_FAMILY, 11));
            root.setPadding(new Insets(12));
            root.setSpacing(8);
            root.setMinWidth(200);
            root.setPrefWidth(240);
            root.setMinHeight(180);
        } else {
            titleLabel.setFont(Font.font(FONT_FAMILY, 20));
            valueLabel.setFont(Font.font(FONT_FAMILY, 18));
            extraLabel.setFont(Font.font(FONT_FAMILY, 13));
            root.setPadding(new Insets(22));
            root.setSpacing(14);
            root.setMinWidth(280);
            root.setPrefWidth(320);
            root.setMinHeight(240);
        }
    }

    public void setTitleText(String title) { titleLabel.setText(title); }

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
