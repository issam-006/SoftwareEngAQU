package fxShield;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;

public class ActionCard {

    private HBox root;
    private Label titleLabel;
    private Label descLabel;
    private Button actionButton;
    private SVGPath iconShape;

    public ActionCard(String title, String desc, String buttonText, String svgPath) {

        // ===== ICON =====
        iconShape = new SVGPath();
        iconShape.setContent(svgPath);
        iconShape.setFill(Color.web("#cbd5e1"));
        iconShape.setScaleX(1.5);
        iconShape.setScaleY(1.5);

        // ===== Title =====
        titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", 17));
        titleLabel.setTextFill(Color.web("#f1e8ff"));
        titleLabel.setStyle("-fx-font-weight: bold; -fx-effect: none;");

        // ===== Description =====
        descLabel = new Label(desc);
        descLabel.setFont(Font.font("Segoe UI", 13));
        descLabel.setTextFill(Color.web("#d5c8f7"));
        descLabel.setStyle("-fx-effect: none;");

        VBox textBox = new VBox(4, titleLabel, descLabel);

        // ===== Button =====
        actionButton = new Button(buttonText);
        actionButton.setFont(Font.font("Segoe UI", 13));
        actionButton.setStyle(
                "-fx-background-color: rgba(129, 71, 219, 0.45);" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 14;" +
                        "-fx-padding: 6 18;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: none;"
        );

        // Hover
        actionButton.setOnMouseEntered(e ->
                actionButton.setStyle(
                        "-fx-background-color: rgba(168, 85, 247, 0.65);" +
                                "-fx-text-fill: white;" +
                                "-fx-font-weight: bold;" +
                                "-fx-background-radius: 14;" +
                                "-fx-padding: 6 18;" +
                                "-fx-cursor: hand;" +
                                "-fx-effect: none;"
                )
        );

        actionButton.setOnMouseExited(e ->
                actionButton.setStyle(
                        "-fx-background-color: rgba(129, 71, 219, 0.45);" +
                                "-fx-text-fill: white;" +
                                "-fx-font-weight: bold;" +
                                "-fx-background-radius: 14;" +
                                "-fx-padding: 6 18;" +
                                "-fx-cursor: hand;" +
                                "-fx-effect: none;"
                )
        );

        // ===== Card Container =====
        root = new HBox(16, iconShape, textBox, actionButton);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(26));
        root.setMinHeight(110);

        root.setStyle(
                "-fx-background-color: rgba(7,7,20,0.65);" +
                        "-fx-background-radius: 22;" +
                        "-fx-border-radius: 22;" +
                        "-fx-border-color: rgba(255,255,255,0.10);" +
                        "-fx-border-width: 1;" +

                        // ✨ توهج خفيف جداً جداً
                        "-fx-effect: dropshadow(gaussian, rgba(140,65,255,0.15), 10, 0.2, 0, 0);"
        );


        HBox.setHgrow(textBox, javafx.scene.layout.Priority.ALWAYS);
    }

    public HBox getRoot() {
        return root;
    }

    public Button getButton() {
        return actionButton;
    }
}
