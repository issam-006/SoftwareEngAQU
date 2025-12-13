package fxShield;

import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.util.Duration;

public class ActionCard {

    private final HBox root;
    private final Label titleLabel;
    private final Label descLabel;
    private final Button actionButton;
    private final SVGPath iconShape;

    // ====== Styles (same as your current UI) ======
    private static final String BTN_NORMAL =
            "-fx-background-color: rgba(129, 71, 219, 0.45);" +
                    "-fx-text-fill: white;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-radius: 14;" +
                    "-fx-padding: 6 18;" +
                    "-fx-cursor: hand;" +
                    "-fx-effect: none;";

    private static final String BTN_HOVER =
            "-fx-background-color: rgba(168, 85, 247, 0.65);" +
                    "-fx-text-fill: white;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-radius: 14;" +
                    "-fx-padding: 6 18;" +
                    "-fx-cursor: hand;" +
                    "-fx-effect: none;";

    private static final String CARD_NORMAL =
            "-fx-background-color: rgba(7,7,20,0.65);" +
                    "-fx-background-radius: 22;" +
                    "-fx-border-radius: 22;" +
                    "-fx-border-color: rgba(255,255,255,0.10);" +
                    "-fx-border-width: 1;" +
                    "-fx-effect: dropshadow(gaussian, rgba(140,65,255,0.15), 10, 0.2, 0, 0);";

    // hover بسيط جداً بدون تغيير شكل التصميم
    private static final String CARD_HOVER =
            "-fx-background-color: rgba(7,7,20,0.65);" +
                    "-fx-background-radius: 22;" +
                    "-fx-border-radius: 22;" +
                    "-fx-border-color: rgba(255,255,255,0.16);" +
                    "-fx-border-width: 1;" +
                    "-fx-effect: dropshadow(gaussian, rgba(140,65,255,0.22), 14, 0.25, 0, 0);";

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
        descLabel.setWrapText(true);

        VBox textBox = new VBox(4, titleLabel, descLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);
        textBox.setMaxWidth(Double.MAX_VALUE);

        // ===== Button =====
        actionButton = new Button(buttonText);
        actionButton.setFont(Font.font("Segoe UI", 13));
        actionButton.setStyle(BTN_NORMAL);

        actionButton.setOnMouseEntered(e -> actionButton.setStyle(BTN_HOVER));
        actionButton.setOnMouseExited(e -> actionButton.setStyle(BTN_NORMAL));

        // ===== Card Container =====
        root = new HBox(16, iconShape, textBox, actionButton);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(26));
        root.setMinHeight(110);
        root.setStyle(CARD_NORMAL);

        HBox.setHgrow(textBox, Priority.ALWAYS);

        // ===== Card Hover Animation (subtle) =====
        addCardHover(root);
    }

    private void addCardHover(HBox card) {
        TranslateTransition up = new TranslateTransition(Duration.millis(140), card);
        up.setToY(-2);

        TranslateTransition down = new TranslateTransition(Duration.millis(140), card);
        down.setToY(0);

        card.setOnMouseEntered(e -> {
            card.setStyle(CARD_HOVER);
            down.stop();
            up.playFromStart();
        });

        card.setOnMouseExited(e -> {
            card.setStyle(CARD_NORMAL);
            up.stop();
            down.playFromStart();
        });
    }

    public HBox getRoot() {
        return root;
    }

    public Button getButton() {
        return actionButton;
    }

    // اختياري: إذا بدك توصل للعنوان/الوصف لاحقاً
    public Label getTitleLabel() {
        return titleLabel;
    }

    public Label getDescLabel() {
        return descLabel;
    }
}
