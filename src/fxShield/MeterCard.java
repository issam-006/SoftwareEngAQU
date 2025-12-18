
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

public final class MeterCard {

    private static final DecimalFormat DF = new DecimalFormat("0.0");

    private static final String CARD_STYLE =
            "-fx-background-color: linear-gradient(to bottom right, rgba(23, 18, 48, 0.65), rgba(13, 10, 28, 0.85));" +
                    "-fx-background-radius: 28;" +
                    "-fx-border-radius: 28;" +
                    "-fx-border-color: rgba(255,255,255,0.14);" +
                    "-fx-border-width: 1.2;" +
                    "-fx-effect: dropshadow(gaussian, rgba(130, 80, 255, 0.22), 20, 0, 0, 4);";

    private static final String BAR_BG_STYLE =
            "-fx-control-inner-background: rgba(255,255,255,0.08);";
    private static final String ACCENT_DEFAULT = "#a78bfa";
    private static final String ACCENT_WARN = "#fb923c";
    private static final String ACCENT_DANGER = "#f97373";

    private final VBox root;
    private final Label titleLabel;
    private final Label valueLabel;
    private final Label extraLabel;
    private final ProgressBar bar;

    public MeterCard(String titleText) {
        // Title
        titleLabel = new Label(titleText);
        titleLabel.setTextFill(Color.web("#e9d8ff"));
        titleLabel.setFont(Font.font("Segoe UI", 20));
        titleLabel.setStyle("-fx-font-weight: bold;");
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);

        // Value
        valueLabel = new Label("Loading...");
        valueLabel.setTextFill(Color.web("#f5e8ff"));
        valueLabel.setFont(Font.font("Segoe UI", 18));

        // Extra
        extraLabel = new Label("Waiting for first sample...");
        extraLabel.setTextFill(Color.web("#cbb8ff"));
        extraLabel.setFont(Font.font("Segoe UI", 13));
        extraLabel.setWrapText(true);

        // Bar
        bar = new ProgressBar(0);
        bar.setPrefWidth(260);
        setBarAccentColor(ACCENT_DEFAULT);

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

    public VBox getRoot() { return root; }
    public Label getTitleLabel() { return titleLabel; }
    public Label getValueLabel() { return valueLabel; }
    public Label getExtraLabel() { return extraLabel; }
    public ProgressBar getBar() { return bar; }

    public void setCompact(boolean compact) {
        if (compact) {
            titleLabel.setFont(Font.font("Segoe UI", 16));
            valueLabel.setFont(Font.font("Segoe UI", 15));
            extraLabel.setFont(Font.font("Segoe UI", 11));
            root.setPadding(new Insets(12));
            root.setSpacing(8);
            root.setMinWidth(200);
            root.setPrefWidth(240);
            root.setMinHeight(180);
        } else {
            titleLabel.setFont(Font.font("Segoe UI", 20));
            valueLabel.setFont(Font.font("Segoe UI", 18));
            extraLabel.setFont(Font.font("Segoe UI", 13));
            root.setPadding(new Insets(22));
            root.setSpacing(14);
            root.setMinWidth(280);
            root.setPrefWidth(320);
            root.setMinHeight(240);
        }
    }

    public void setTitleText(String title) { titleLabel.setText(title); }

    private void applyColorByUsage(double percent) {
        String color = ACCENT_DEFAULT;
        if (percent >= 85) color = ACCENT_DANGER;
        else if (percent >= 60) color = ACCENT_WARN;

        valueLabel.setTextFill(Color.web(color));
        setBarAccentColor(color);
    }

    private void applyUnavailableStyle() {
        valueLabel.setTextFill(Color.web("#cbb8ff"));
        setBarAccentColor(ACCENT_DEFAULT);
    }

    private void setBarAccentColor(String color) {
        bar.setStyle(BAR_BG_STYLE + "-fx-accent: " + color + ";");
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}