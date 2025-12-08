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
        titleLabel = new Label(titleText);
        titleLabel.setTextFill(Color.web("#93c5fd"));
        titleLabel.setFont(Font.font("Segoe UI", 18));
        titleLabel.setStyle("-fx-font-weight: bold;");

        valueLabel = new Label("Loading...");
        extraLabel = new Label("Waiting for first sample...");
        valueLabel.setTextFill(Color.web("#e5e7eb"));
        valueLabel.setFont(Font.font("Segoe UI", 16));

        bar = new ProgressBar(0);
        bar.setPrefWidth(320);
        bar.setStyle(
                "-fx-accent: #22c55e;" +
                        "-fx-control-inner-background: #020617;"
        );

        extraLabel = new Label("");
        extraLabel.setTextFill(Color.web("#9ca3af"));
        extraLabel.setFont(Font.font("Segoe UI", 12));

        root = new VBox();
        root.setSpacing(10);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(18));
        root.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #020617, #111827);" +
                        "-fx-background-radius: 20;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 18, 0.1, 0, 8);"
        );
        root.setMinHeight(170);
        root.setMaxWidth(Double.MAX_VALUE);
        root.getChildren().addAll(titleLabel, valueLabel, bar, extraLabel);
    }

    public VBox getRoot() {
        return root;
    }

    public Label getTitleLabel() {
        return titleLabel;
    }

    public Label getValueLabel() {
        return valueLabel;
    }

    public Label getExtraLabel() {
        return extraLabel;
    }

    public ProgressBar getBar() {
        return bar;
    }
}
