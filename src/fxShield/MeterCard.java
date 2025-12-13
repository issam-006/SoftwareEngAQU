package fxShield;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.text.DecimalFormat;

public class MeterCard {

    private final VBox root;
    private final Label titleLabel;
    private final Label valueLabel;
    private final Label extraLabel;
    private final ProgressBar bar;

    // Formatter جاهز (اختياري)
    private static final DecimalFormat DF = new DecimalFormat("0.0");

    public MeterCard(String titleText) {

        // ===== TITLE =====
        titleLabel = new Label(titleText);
        titleLabel.setTextFill(Color.web("#e9d8ff"));
        titleLabel.setFont(Font.font("Segoe UI", 20));
        titleLabel.setStyle("-fx-font-weight: bold;");

        // ===== VALUE =====
        valueLabel = new Label("Loading...");
        valueLabel.setTextFill(Color.web("#f5e8ff"));
        valueLabel.setFont(Font.font("Segoe UI", 18));

        // ===== EXTRA TEXT =====
        extraLabel = new Label("Waiting for first sample...");
        extraLabel.setTextFill(Color.web("#cbb8ff"));
        extraLabel.setFont(Font.font("Segoe UI", 13));

        // ===== PROGRESS BAR =====
        bar = new ProgressBar(0);
        bar.setPrefWidth(260);
        bar.setStyle(
                "-fx-accent: #a78bfa;" +
                        "-fx-control-inner-background: rgba(255,255,255,0.08);"
        );

        // ===== CARD CONTAINER =====
        root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(22));

        root.setStyle(
                "-fx-background-color: rgba(17,13,34,0.55);" +
                        "-fx-background-radius: 28;" +
                        "-fx-border-radius: 28;" +
                        "-fx-border-color: rgba(255,255,255,0.10);" +
                        "-fx-border-width: 1;" +
                        "-fx-effect: dropshadow(gaussian, rgba(157,110,255,0.28), 25, 0.25, 0, 0);"
        );

        root.setMinHeight(240);
        root.setMinWidth(260);
        root.setPrefWidth(0);
        root.setMaxWidth(Double.MAX_VALUE);

        // ترتيب العناصر (نفس ترتيبك)
        root.getChildren().addAll(titleLabel, valueLabel, bar, extraLabel);
    }

    // =========================
    //  Update helpers (اختياري)
    // =========================

    public void setValuePercent(double percent, String extraText) {
        valueLabel.setText(DF.format(percent) + " %");
        extraLabel.setText(extraText != null ? extraText : "");
        bar.setProgress(clamp01(percent / 100.0));
        applyColorByUsage(percent);
    }

    public void setUnavailable(String message) {
        valueLabel.setText("N/A");
        extraLabel.setText(message != null ? message : "Not available");
        bar.setProgress(0);
        applyUnavailableStyle();
    }

    // =========================
    //  Styling helpers
    // =========================

    private void applyColorByUsage(double percent) {
        String color;
        if (percent < 60) {
            color = "#a78bfa"; // بنفسجي هادئ
        } else if (percent < 85) {
            color = "#fb923c"; // برتقالي
        } else {
            color = "#f97373"; // أحمر
        }

        valueLabel.setTextFill(Color.web(color));
        bar.setStyle(
                "-fx-accent: " + color + ";" +
                        "-fx-control-inner-background: rgba(255,255,255,0.08);"
        );
    }

    private void applyUnavailableStyle() {
        valueLabel.setTextFill(Color.web("#cbb8ff"));
        bar.setStyle(
                "-fx-accent: #a78bfa;" +
                        "-fx-control-inner-background: rgba(255,255,255,0.08);"
        );
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    // ===== Getters (كما هي) =====
    public VBox getRoot() { return root; }
    public Label getTitleLabel() { return titleLabel; }
    public Label getValueLabel() { return valueLabel; }
    public Label getExtraLabel() { return extraLabel; }
    public ProgressBar getBar() { return bar; }
}
