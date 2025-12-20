package fxShield;

import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.util.Duration;

/**
 * A card component that displays an action with icon, title, description, and button.
 * Extends BaseCard for common styling and utility methods.
 */
public final class ActionCard extends BaseCard {

    private static final Duration HOVER_DURATION = Duration.millis(140);

    // Button styles
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

    // Card styles
    private static final String CARD_BASE =
            "-fx-background-color: linear-gradient(to bottom right, rgba(15, 15, 35, 0.7), rgba(5, 5, 15, 0.85));" +
                    "-fx-background-radius: 22;" +
                    "-fx-border-radius: 22;" +
                    "-fx-border-width: 1.2;";

    private static final String CARD_NORMAL =
            CARD_BASE +
                    "-fx-border-color: rgba(255,255,255,0.10);" +
                    "-fx-effect: dropshadow(gaussian, rgba(140,65,255,0.15), 10, 0.2, 0, 0);";

    private static final String CARD_HOVER =
            CARD_BASE +
                    "-fx-border-color: rgba(255,255,255,0.16);" +
                    "-fx-effect: dropshadow(gaussian, rgba(140,65,255,0.22), 14, 0.25, 0, 0);";

    private final HBox root;
    private final Label titleLabel;
    private final Label descLabel;
    private final Button actionButton;
    private final SVGPath iconShape;

    public ActionCard(String title, String desc, String buttonText, String svgPath) {
        this(title, desc, buttonText, svgPath, 1.5);
    }

    public ActionCard(String title, String desc, String buttonText, String svgPath, double iconScale) {
        // Icon
        iconShape = new SVGPath();
        iconShape.setContent(svgPath);
        iconShape.setFill(colorFromHex("#cbd5e1"));
        iconShape.setScaleX(iconScale);
        iconShape.setScaleY(iconScale);

        // Title
        titleLabel = new Label(title);
        titleLabel.setFont(Font.font(FONT_FAMILY, 17));
        titleLabel.setTextFill(colorFromHex(COLOR_TEXT_LIGHT));
        titleLabel.setStyle("-fx-font-weight: bold; -fx-effect: none;");

        // Description
        descLabel = new Label(desc);
        descLabel.setFont(Font.font(FONT_FAMILY, 13));
        descLabel.setTextFill(colorFromHex(COLOR_TEXT_MUTED));
        descLabel.setStyle("-fx-effect: none;");
        descLabel.setWrapText(true);

        VBox textBox = new VBox(4, titleLabel, descLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);
        textBox.setMaxWidth(Double.MAX_VALUE);

        // Bind description width to text container to ensure proper wrapping
        descLabel.maxWidthProperty().bind(textBox.widthProperty());

        // Button
        actionButton = new Button(buttonText);
        actionButton.setMnemonicParsing(false);
        actionButton.setFont(Font.font(FONT_FAMILY, 13));
        actionButton.setStyle(BTN_NORMAL);
        actionButton.setOnMouseEntered(e -> actionButton.setStyle(BTN_HOVER));
        actionButton.setOnMouseExited(e -> actionButton.setStyle(BTN_NORMAL));

        // Card container
        root = new HBox(16, iconShape, textBox, actionButton);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(26));
        root.setMinHeight(110);
        root.setMinWidth(300);
        root.setMaxWidth(650);
        root.setStyle(CARD_NORMAL);
        root.setAccessibleText(title);

        HBox.setHgrow(textBox, Priority.ALWAYS);

        addCardHover(root);
    }

    private void addCardHover(HBox card) {
        TranslateTransition up = new TranslateTransition(HOVER_DURATION, card);
        up.setToY(-2);

        TranslateTransition down = new TranslateTransition(HOVER_DURATION, card);
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

    // Convenience API
    public void setOnAction(EventHandler<ActionEvent> handler) {
        actionButton.setOnAction(handler);
    }

    // Accessors
    @Override
    public HBox getRoot() { return root; }
    
    @Override
    public void setCompact(boolean compact) {
        if (compact) {
            titleLabel.setFont(Font.font(FONT_FAMILY, 14));
            descLabel.setFont(Font.font(FONT_FAMILY, 11));
            actionButton.setFont(Font.font(FONT_FAMILY, 11));
            root.setPadding(new Insets(16));
            root.setMinHeight(80);
        } else {
            titleLabel.setFont(Font.font(FONT_FAMILY, 17));
            descLabel.setFont(Font.font(FONT_FAMILY, 13));
            actionButton.setFont(Font.font(FONT_FAMILY, 13));
            root.setPadding(new Insets(26));
            root.setMinHeight(110);
        }
    }
    
    public Button getButton() { return actionButton; }
    public Button getActionButton() { return actionButton; }
    public Label getTitleLabel() { return titleLabel; }
    public Label getDescLabel() { return descLabel; }
    public SVGPath getIconShape() { return iconShape; }
}