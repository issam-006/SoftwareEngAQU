package fxShield.UI;

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
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

public final class ActionCard extends BaseCard {

    private static final Duration HOVER_DURATION = Duration.millis(140);

    private static final String BTN_BASE =
            "-fx-text-fill: white;" +
                    "-fx-background-radius: 14;" +
                    "-fx-padding: 6 18;" +
                    "-fx-cursor: hand;" +
                    "-fx-effect: none;";

    private static final String BTN_NORMAL =
            BTN_BASE + "-fx-background-color: rgba(129, 71, 219, 0.45);";

    private static final String BTN_HOVER =
            BTN_BASE + "-fx-background-color: rgba(168, 85, 247, 0.65);";

    // إذا عندك نفس ستايل الكرت داخل StyleConstants خليه هيك:
    private static final String CARD_NORMAL = StyleConstants.CARD_ACTION_NORMAL;
    private static final String CARD_HOVER  = StyleConstants.CARD_ACTION_HOVER;

    private static final Font TITLE_NORMAL_FONT  = Font.font(FONT_FAMILY, FontWeight.BOLD, 17);
    private static final Font DESC_NORMAL_FONT   = Font.font(FONT_FAMILY, FontWeight.NORMAL, 13);
    private static final Font BTN_NORMAL_FONT    = Font.font(FONT_FAMILY, FontWeight.BOLD, 13);

    private static final Font TITLE_COMPACT_FONT = Font.font(FONT_FAMILY, FontWeight.BOLD, 14);
    private static final Font DESC_COMPACT_FONT  = Font.font(FONT_FAMILY, FontWeight.NORMAL, 11);
    private static final Font BTN_COMPACT_FONT   = Font.font(FONT_FAMILY, FontWeight.BOLD, 12);

    private final HBox root;
    private final Label titleLabel;
    private final Label descLabel;
    private final Button actionButton;
    private final SVGPath iconShape;

    public ActionCard(String title, String desc, String buttonText, String svgPath) {
        this(title, desc, buttonText, svgPath, 1.5);
    }

    public ActionCard(String title, String desc, String buttonText, String svgPath, double iconScale) {
        validate("title", title);
        validate("desc", desc);
        validate("buttonText", buttonText);
        validate("svgPath", svgPath);

        iconShape = new SVGPath();
        iconShape.setContent(svgPath);
        iconShape.setFill(colorFromHex("#cbd5e1"));
        iconShape.setScaleX(iconScale);
        iconShape.setScaleY(iconScale);

        titleLabel = new Label(title);
        titleLabel.setFont(TITLE_NORMAL_FONT);
        titleLabel.setTextFill(colorFromHex(COLOR_TEXT_LIGHT));
        titleLabel.setStyle("-fx-effect: none;");
        titleLabel.setMinWidth(0);

        descLabel = new Label(desc);
        descLabel.setFont(DESC_NORMAL_FONT);
        descLabel.setTextFill(colorFromHex(COLOR_TEXT_MUTED));
        descLabel.setStyle("-fx-effect: none;");
        descLabel.setWrapText(true);
        descLabel.setMinWidth(0);

        VBox textBox = new VBox(4, titleLabel, descLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);
        textBox.setMinWidth(0);                 // مهم جداً داخل HBox
        textBox.setMaxWidth(Double.MAX_VALUE);

        descLabel.maxWidthProperty().bind(textBox.widthProperty());

        actionButton = new Button(buttonText);
        actionButton.setMnemonicParsing(false);
        actionButton.setFont(BTN_NORMAL_FONT);
        actionButton.setStyle(BTN_NORMAL);
        actionButton.setFocusTraversable(false);
        actionButton.setMinWidth(Region.USE_PREF_SIZE);

        actionButton.hoverProperty().addListener((obs, oldV, isHover) ->
                actionButton.setStyle(isHover ? BTN_HOVER : BTN_NORMAL));

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

        card.hoverProperty().addListener((obs, oldV, isHover) -> {
            card.setStyle(isHover ? CARD_HOVER : CARD_NORMAL);
            if (isHover) {
                down.stop();
                up.playFromStart();
            } else {
                up.stop();
                down.playFromStart();
            }
        });
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        actionButton.setOnAction(handler);
    }

    @Override
    public HBox getRoot() { return root; }

    @Override
    public void setCompact(boolean compact) {
        if (compact) {
            root.setPadding(new Insets(16));
            root.setMinHeight(80);

            titleLabel.setFont(TITLE_COMPACT_FONT);
            descLabel.setFont(DESC_COMPACT_FONT);
            actionButton.setFont(BTN_COMPACT_FONT);
        } else {
            root.setPadding(new Insets(26));
            root.setMinHeight(110);

            titleLabel.setFont(TITLE_NORMAL_FONT);
            descLabel.setFont(DESC_NORMAL_FONT);
            actionButton.setFont(BTN_NORMAL_FONT);
        }

        actionButton.setStyle(actionButton.isHover() ? BTN_HOVER : BTN_NORMAL);
    }

    public Button getButton() { return actionButton; }

    private static void validate(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be null/blank");
        }
    }
}
