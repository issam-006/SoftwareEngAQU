package fxShield;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class MeterCard {

    private VBox root;
    private Label titleLabel;
    private Label valueLabel;
    private Label extraLabel;
    private ProgressBar bar;

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
                "-fx-accent: #a78bfa;" +                      // بنفسجي فاتح
                        "-fx-control-inner-background: rgba(255,255,255,0.08);" // شفاف ناعم
        );

        // ===== CARD CONTAINER =====
        root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(22));

        root.setStyle(
                "-fx-background-color: rgba(17,13,34,0.55);" + // خلفية بنفسجية داكنة شفافة
                        "-fx-background-radius: 28;" +
                        "-fx-border-radius: 28;" +
                        "-fx-border-color: rgba(255,255,255,0.10);" +
                        "-fx-border-width: 1;" +
                        "-fx-effect: dropshadow(gaussian, rgba(157,110,255,0.28), 25, 0.25, 0, 0);" // purple glow
        );

        root.setMinHeight(240);
        root.setMinWidth(260);
        root.setPrefWidth(0);
        root.setMaxWidth(Double.MAX_VALUE);

        // ترتيب العناصر
        root.getChildren().addAll(titleLabel, valueLabel, bar, extraLabel);
    }

    // ===== Getters =====
    public VBox getRoot() { return root; }
    public Label getTitleLabel() { return titleLabel; }
    public Label getValueLabel() { return valueLabel; }
    public Label getExtraLabel() { return extraLabel; }
    public ProgressBar getBar() { return bar; }
}
