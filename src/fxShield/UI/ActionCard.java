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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.util.Duration;

public final class ActionCard extends BaseCard {

    private static final Duration HOVER_DURATION = Duration.millis(140);

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

    // Font cache
    private static final Font TITLE_NORMAL_FONT = Font.font(FONT_FAMILY, 17);
    private static final Font DESC_NORMAL_FONT  = Font.font(FONT_FAMILY, 13);
    private static final Font BTN_NORMAL_FONT   = Font.font(FONT_FAMILY, 13);

    private static final Font TITLE_COMPACT_FONT = Font.font(FONT_FAMILY, 14);
    private static final Font DESC_COMPACT_FONT  = Font.font(FONT_FAMILY, 11);
    private static final Font BTN_COMPACT_FONT   = Font.font(FONT_FAMILY, 12);

    private static final Insets PAD_NORMAL = new Insets(26);
    private static final Insets PAD_COMPACT = new Insets(16);

    private final StackPane root;
    private final HBox content;
    private final Label titleLabel;
    private final Label descLabel;
    private final Button actionButton;
    private final SVGPath iconShape;

    // ✅ idempotent compact
    private Boolean compactState = null;

    public ActionCard(String title, String desc, String buttonText, String svgPath) {
        this(title, desc, buttonText, svgPath, 1.5);
    }

    public ActionCard(String title, String desc, String buttonText, String svgPath, double iconScale) {
        iconShape = new SVGPath();
        iconShape.setContent(svgPath);
        iconShape.setFill(colorFromHex("#cbd5e1"));
        iconShape.setScaleX(iconScale);
        iconShape.setScaleY(iconScale);

        titleLabel = new Label(title);
        titleLabel.setFont(TITLE_NORMAL_FONT);
        titleLabel.setTextFill(colorFromHex(COLOR_TEXT_LIGHT));
        titleLabel.setStyle("-fx-font-weight: bold; -fx-effect: none;");

        descLabel = new Label(desc);
        descLabel.setFont(DESC_NORMAL_FONT);
        descLabel.setTextFill(colorFromHex(COLOR_TEXT_MUTED));
        descLabel.setStyle("-fx-effect: none;");
        descLabel.setWrapText(true);

        VBox textBox = new VBox(4, titleLabel, descLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);
        textBox.setMaxWidth(Double.MAX_VALUE);

        descLabel.maxWidthProperty().bind(textBox.widthProperty());

        actionButton = new Button(buttonText);
        actionButton.setMnemonicParsing(false);
        actionButton.setFont(BTN_NORMAL_FONT);
        actionButton.setStyle(BTN_NORMAL);
        actionButton.setOnMouseEntered(e -> actionButton.setStyle(BTN_HOVER));
        actionButton.setOnMouseExited(e -> actionButton.setStyle(BTN_NORMAL));


        content = new HBox(16, iconShape, textBox, actionButton);
        content.setAlignment(Pos.CENTER_LEFT);

        root = new StackPane(content);
        root.setPadding(PAD_NORMAL);
        root.setMinHeight(110);
        root.setMinWidth(300);
        root.setMaxWidth(650);
        root.setStyle(CARD_NORMAL);
        root.setAccessibleText(title);
        watchFont(titleLabel, "ActionCard.title");
        watchFont(descLabel, "ActionCard.desc");
        watchFont(actionButton, "ActionCard.button");
        watchScale(root, "ActionCard.root");


        HBox.setHgrow(textBox, Priority.ALWAYS);

        addCardHover(root, content);

        // init
        setCompact(false);
    }

    private void addCardHover(StackPane hoverSurface, Node animatedNode) {
        TranslateTransition up = new TranslateTransition(HOVER_DURATION, animatedNode);
        up.setToY(-2);

        TranslateTransition down = new TranslateTransition(HOVER_DURATION, animatedNode);
        down.setToY(0);

        hoverSurface.setOnMouseEntered(e -> {
            hoverSurface.setStyle(CARD_HOVER);
            down.stop();
            up.playFromStart();
        });

        hoverSurface.setOnMouseExited(e -> {
            hoverSurface.setStyle(CARD_NORMAL);
            up.stop();
            down.playFromStart();
        });
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        actionButton.setOnAction(handler);
    }

    @Override
    public StackPane getRoot() { return root; }

    @Override
    public void setCompact(boolean compact) {
        if (compactState != null && compactState == compact) return;
        compactState = compact;

        debugCompact("ActionCard", compact);

        if (compact) {
            root.setPadding(PAD_COMPACT);
            root.setMinHeight(80);

            titleLabel.setFont(TITLE_COMPACT_FONT);
            descLabel.setFont(DESC_COMPACT_FONT);
            actionButton.setFont(BTN_COMPACT_FONT);
        } else {
            root.setPadding(PAD_NORMAL);
            root.setMinHeight(110);

            titleLabel.setFont(TITLE_NORMAL_FONT);
            descLabel.setFont(DESC_NORMAL_FONT);
            actionButton.setFont(BTN_NORMAL_FONT);
        }
    }

    public Button getButton() { return actionButton; }
}
