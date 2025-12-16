package fxShield;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.prefs.Preferences;

public final class PowerModeDialog {

    private enum PowerMode { PERFORMANCE, BALANCED, QUIET }

    private static final String PREF_NODE = "fxshield";
    private static final String PREF_KEY_POWER_MODE = "powerMode";

    // Styles
    private static final String DIALOG_ROOT_STYLE =
            "-fx-background-color: #020617;" +
                    "-fx-background-radius: 18;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 24, 0.2, 0, 10);";

    private static final String BTN_CANCEL_NORMAL =
            "-fx-background-color: transparent;" +
                    "-fx-text-fill: #9ca3af;" +
                    "-fx-border-color: #4b5563;" +
                    "-fx-border-width: 1.2;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 4 18 4 18;" +
                    "-fx-cursor: hand;";
    private static final String BTN_CANCEL_HOVER =
            "-fx-background-color: #1f2937;" +
                    "-fx-text-fill: #e5e7eb;" +
                    "-fx-border-color: #6b7280;" +
                    "-fx-border-width: 1.2;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 4 18 4 18;" +
                    "-fx-cursor: hand;";

    private static final String BTN_APPLY_NORMAL =
            "-fx-background-color: #2563eb;" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 999;" +
                    "-fx-padding: 4 22 4 22;" +
                    "-fx-cursor: hand;";
    private static final String BTN_APPLY_HOVER =
            "-fx-background-color: #1d4ed8;" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 999;" +
                    "-fx-padding: 4 22 4 22;" +
                    "-fx-cursor: hand;";

    private static final String CARD_BASE =
            "-fx-background-color: #020617;" +
                    "-fx-background-radius: 16;";
    private static final String CARD_HOVER =
            CARD_BASE +
                    "-fx-border-color: #1f2937;" +
                    "-fx-border-radius: 16;";
    private static final String CARD_SELECTED =
            CARD_BASE +
                    "-fx-border-color: #3b82f6;" +
                    "-fx-border-width: 1.6;" +
                    "-fx-border-radius: 16;";

    public static void show(Stage owner) {
        PowerMode currentMode = loadSavedMode();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Power Mode Setting");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16, 24, 20, 24));
        root.setStyle(DIALOG_ROOT_STYLE);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);

        // Header
        Label title = new Label("Power Mode Setting");
        title.setFont(Font.font("Segoe UI", 18));
        title.setTextFill(Color.web("#e5e7eb"));
        title.setStyle("-fx-font-weight: bold;");

        Label sub = new Label("Choose the mode you want to use.");
        sub.setFont(Font.font("Segoe UI", 12));
        sub.setTextFill(Color.web("#9ca3af"));
        VBox titleBox = new VBox(4, title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(BTN_CANCEL_NORMAL);
        cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle(BTN_CANCEL_HOVER));
        cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle(BTN_CANCEL_NORMAL));
        cancelBtn.setOnAction(e -> dialog.close());

        Button applyBtn = new Button("Apply");
        applyBtn.setStyle(BTN_APPLY_NORMAL);
        applyBtn.setOnMouseEntered(e -> applyBtn.setStyle(BTN_APPLY_HOVER));
        applyBtn.setOnMouseExited(e -> applyBtn.setStyle(BTN_APPLY_NORMAL));

        HBox header = new HBox(10, titleBox, spacer, cancelBtn, applyBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        root.setTop(header);

        // Cards
        ModeCard performance = new ModeCard("Performance Mode",
                "Boost your computer performance with higher power consumption.", PowerMode.PERFORMANCE);
        ModeCard balanced = new ModeCard("Balanced Mode",
                "Automatically adjust performance and power usage.", PowerMode.BALANCED);
        ModeCard quiet = new ModeCard("Quiet Mode",
                "Reduce noise and power usage with lower performance.", PowerMode.QUIET);

        ModeCard[] cards = {performance, balanced, quiet};
        for (ModeCard c : cards) {
            if (c.mode == currentMode) {
                setSelected(cards, c);
                break;
            }
        }
        for (ModeCard card : cards) {
            card.setOnMouseClicked(e -> setSelected(cards, card));
            card.setOnMouseEntered(e -> { if (!card.isSelected()) card.setStyle(CARD_HOVER); });
            card.setOnMouseExited(e -> { if (!card.isSelected()) card.setStyle(CARD_BASE); });
        }

        VBox modesBox = new VBox(12, performance, balanced, quiet);
        modesBox.setPadding(new Insets(20, 0, 10, 0));
        root.setCenter(modesBox);

        // Apply action persists current selection
        applyBtn.setOnAction(e -> {
            ModeCard selected = getSelected(cards);
            if (selected != null) {
                saveMode(selected.mode);
            }
            dialog.close();
        });

        // Scene and keyboard handling
        Scene scene = new Scene(root, 700, 390);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(k -> {
            if (k.getCode() == KeyCode.ESCAPE) dialog.close();
            if (k.getCode() == KeyCode.ENTER) applyBtn.fire();
            if (k.getCode() == KeyCode.DIGIT1 || k.getCode() == KeyCode.NUMPAD1) setSelected(cards, performance);
            if (k.getCode() == KeyCode.DIGIT2 || k.getCode() == KeyCode.NUMPAD2) setSelected(cards, balanced);
            if (k.getCode() == KeyCode.DIGIT3 || k.getCode() == KeyCode.NUMPAD3) setSelected(cards, quiet);
        });
        dialog.setScene(scene);
        dialog.centerOnScreen();

        // Pop-in animation
        root.setOpacity(0);
        root.setScaleX(0.95);
        root.setScaleY(0.95);
        dialog.show();

        ParallelTransition popIn = new ParallelTransition(
                fade(root, 0, 1, 220),
                scale(root, 0.95, 1, 220)
        );
        popIn.setInterpolator(Interpolator.EASE_OUT);
        popIn.play();
    }

    private static void setSelected(ModeCard[] cards, ModeCard selected) {
        for (ModeCard c : cards) c.setSelected(c == selected);
    }

    private static ModeCard getSelected(ModeCard[] cards) {
        for (ModeCard c : cards) if (c.isSelected()) return c;
        return null;
    }

    private static Preferences prefs() {
        return Preferences.userRoot().node(PREF_NODE);
    }

    private static PowerMode loadSavedMode() {
        String val = prefs().get(PREF_KEY_POWER_MODE, PowerMode.BALANCED.name());
        try { return PowerMode.valueOf(val); }
        catch (IllegalArgumentException ex) { return PowerMode.BALANCED; }
    }

    private static void saveMode(PowerMode mode) {
        prefs().put(PREF_KEY_POWER_MODE, mode.name());
    }

    private static FadeTransition fade(Region node, double from, double to, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(from); ft.setToValue(to);
        return ft;
    }

    private static ScaleTransition scale(Region node, double from, double to, int ms) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), node);
        st.setFromX(from); st.setFromY(from);
        st.setToX(to);     st.setToY(to);
        return st;
    }

    private static final class ModeCard extends VBox {
        private final Label radio = new Label("○");
        private final Label title = new Label();
        private final Label desc = new Label();
        private boolean selected = false;
        private final PowerMode mode;

        ModeCard(String titleText, String descText, PowerMode mode) {
            this.mode = mode;
            setSpacing(8);
            setPadding(new Insets(14));
            setAlignment(Pos.TOP_LEFT);
            setStyle(CARD_BASE);
            setFocusTraversable(true);

            radio.setFont(Font.font("Segoe UI", 16));
            radio.setTextFill(Color.web("#9ca3af"));

            title.setText(titleText);
            title.setFont(Font.font("Segoe UI", 14));
            title.setTextFill(Color.web("#e5e7eb"));
            title.setStyle("-fx-font-weight: bold;");

            desc.setText(descText);
            desc.setFont(Font.font("Segoe UI", 11));
            desc.setTextFill(Color.web("#9ca3af"));
            desc.setWrapText(true);

            HBox header = new HBox(10, radio, title);
            header.setAlignment(Pos.CENTER_LEFT);

            getChildren().addAll(header, desc);

            // Keyboard toggle
            setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                    // parent container will reset others
                    requestFocus();
                    setSelected(true);
                }
            });
        }

        void setSelected(boolean sel) {
            selected = sel;
            if (sel) {
                radio.setText("●");
                radio.setTextFill(Color.web("#3b82f6"));
                setStyle(CARD_SELECTED);
            } else {
                radio.setText("○");
                radio.setTextFill(Color.web("#9ca3af"));
                setStyle(CARD_BASE);
            }
        }

        boolean isSelected() { return selected; }
        String getTitleText() { return title.getText(); }
    }
}