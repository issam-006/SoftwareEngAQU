package fxShield;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.prefs.Preferences;

public class PowerModeDialog {

    private enum PowerMode {
        PERFORMANCE,
        BALANCED,
        QUIET
    }

    private static final String PREF_NODE = "fxshield";
    private static final String PREF_KEY_POWER_MODE = "powerMode";

    // ===== Styles (same look, no design change) =====
    private static final String DIALOG_ROOT_STYLE =
            "-fx-background-color: #020617;" +
                    "-fx-background-radius: 18;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 24, 0.2, 0, 10);";

    private static final String CANCEL_NORMAL =
            "-fx-background-color: transparent;" +
                    "-fx-text-fill: #9ca3af;" +
                    "-fx-border-color: #4b5563;" +
                    "-fx-border-width: 1.2;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 4 18 4 18;" +
                    "-fx-cursor: hand;";

    private static final String CANCEL_HOVER =
            "-fx-background-color: #1f2937;" +
                    "-fx-text-fill: #e5e7eb;" +
                    "-fx-border-color: #6b7280;" +
                    "-fx-border-width: 1.2;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 4 18 4 18;" +
                    "-fx-cursor: hand;";

    private static final String APPLY_NORMAL =
            "-fx-background-color: #2563eb;" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 999;" +
                    "-fx-padding: 4 22 4 22;" +
                    "-fx-cursor: hand;";

    private static final String APPLY_HOVER =
            "-fx-background-color: #1d4ed8;" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 999;" +
                    "-fx-padding: 4 22 4 22;" +
                    "-fx-cursor: hand;";

    public static void show(Stage owner) {
        PowerMode currentMode = loadSavedMode();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16, 24, 20, 24));
        root.setStyle(DIALOG_ROOT_STYLE);

        // ✅ Clip مربوط بالحجم الحقيقي (يحافظ على الزوايا حتى لو غيّرت الحجم)
        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);

        // ===== Title =====
        Label title = new Label("Power Mode Setting");
        title.setFont(Font.font("Segoe UI", 18));
        title.setTextFill(Color.web("#e5e7eb"));
        title.setStyle("-fx-font-weight: bold;");

        Label sub = new Label("Choose the mode you want to use.");
        sub.setFont(Font.font("Segoe UI", 12));
        sub.setTextFill(Color.web("#9ca3af"));

        VBox titleBox = new VBox(4, title, sub);

        // ===== Cards =====
        ModeCard performance = new ModeCard(
                "Performance Mode",
                "Boost your computer performance with higher power consumption.",
                PowerMode.PERFORMANCE
        );
        ModeCard balanced = new ModeCard(
                "Balanced Mode",
                "Automatically adjust performance and power usage.",
                PowerMode.BALANCED
        );
        ModeCard quiet = new ModeCard(
                "Quiet Mode",
                "Reduce noise and power usage with lower performance.",
                PowerMode.QUIET
        );

        ModeCard[] cards = {performance, balanced, quiet};

        // set initial selection
        for (ModeCard c : cards) {
            if (c.mode == currentMode) {
                setSelected(cards, c);
                break;
            }
        }

        // click to select
        for (ModeCard card : cards) {
            card.setOnMouseClicked(e -> setSelected(cards, card));
        }

        VBox modesBox = new VBox(12, performance, balanced, quiet);
        modesBox.setPadding(new Insets(20, 0, 10, 0));
        root.setCenter(modesBox);

        // ===== Bottom buttons =====
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());
        cancelBtn.setStyle(CANCEL_NORMAL);
        cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle(CANCEL_HOVER));
        cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle(CANCEL_NORMAL));

        Button applyBtn = new Button("Apply");
        applyBtn.setStyle(APPLY_NORMAL);
        applyBtn.setOnMouseEntered(e -> applyBtn.setStyle(APPLY_HOVER));
        applyBtn.setOnMouseExited(e -> applyBtn.setStyle(APPLY_NORMAL));
        applyBtn.setOnAction(e -> {
            ModeCard selected = getSelected(cards);
            if (selected != null) {
                System.out.println("[POWER MODE] Selected: " + selected.getTitleText());
                saveMode(selected.mode);
            }
            dialog.close();
        });

        HBox bottomButtons = new HBox(10, cancelBtn, applyBtn);
        bottomButtons.setAlignment(Pos.CENTER_RIGHT);
        bottomButtons.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(bottomButtons);

        // Scene
        Scene scene = new Scene(root, 700, 390);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.centerOnScreen();

        // animation init
        root.setOpacity(0);
        root.setScaleX(0.95);
        root.setScaleY(0.95);

        dialog.show();

        FadeTransition fade = new FadeTransition(Duration.millis(220), root);
        fade.setFromValue(0);
        fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(220), root);
        scale.setFromX(0.95);
        scale.setFromY(0.95);
        scale.setToX(1);
        scale.setToY(1);

        ParallelTransition popIn = new ParallelTransition(fade, scale);
        popIn.setInterpolator(Interpolator.EASE_OUT);
        popIn.play();
    }

    private static void setSelected(ModeCard[] cards, ModeCard selected) {
        for (ModeCard c : cards) {
            c.setSelected(c == selected);
        }
    }

    private static ModeCard getSelected(ModeCard[] cards) {
        for (ModeCard c : cards) {
            if (c.isSelected()) return c;
        }
        return null;
    }

    private static Preferences prefs() {
        return Preferences.userRoot().node(PREF_NODE);
    }

    private static PowerMode loadSavedMode() {
        String val = prefs().get(PREF_KEY_POWER_MODE, PowerMode.BALANCED.name());
        try {
            return PowerMode.valueOf(val);
        } catch (IllegalArgumentException ex) {
            return PowerMode.BALANCED;
        }
    }

    private static void saveMode(PowerMode mode) {
        prefs().put(PREF_KEY_POWER_MODE, mode.name());
    }

    // ===== Card =====
    private static class ModeCard extends VBox {
        private final Label radio;
        private final Label title;
        private final Label desc;
        private boolean selected = false;
        private final PowerMode mode;

        ModeCard(String titleText, String descText, PowerMode mode) {
            this.mode = mode;

            setSpacing(8);
            setPadding(new Insets(14));
            setAlignment(Pos.TOP_LEFT);
            setStyle(
                    "-fx-background-color: #020617;" +
                            "-fx-background-radius: 16;"
            );

            radio = new Label("○");
            radio.setFont(Font.font("Segoe UI", 16));
            radio.setTextFill(Color.web("#9ca3af"));

            title = new Label(titleText);
            title.setFont(Font.font("Segoe UI", 14));
            title.setTextFill(Color.web("#e5e7eb"));
            title.setStyle("-fx-font-weight: bold;");

            desc = new Label(descText);
            desc.setFont(Font.font("Segoe UI", 11));
            desc.setTextFill(Color.web("#9ca3af"));
            desc.setWrapText(true);

            HBox header = new HBox(10, radio, title);
            header.setAlignment(Pos.CENTER_LEFT);

            getChildren().addAll(header, desc);

            setOnMouseEntered(e -> {
                if (!selected) {
                    setStyle(
                            "-fx-background-color: #020617;" +
                                    "-fx-background-radius: 16;" +
                                    "-fx-border-color: #1f2937;" +
                                    "-fx-border-radius: 16;"
                    );
                }
            });

            setOnMouseExited(e -> {
                if (!selected) {
                    setStyle(
                            "-fx-background-color: #020617;" +
                                    "-fx-background-radius: 16;"
                    );
                }
            });
        }

        void setSelected(boolean sel) {
            selected = sel;
            if (sel) {
                radio.setText("●");
                radio.setTextFill(Color.web("#3b82f6"));
                setStyle(
                        "-fx-background-color: #020617;" +
                                "-fx-background-radius: 16;" +
                                "-fx-border-color: #3b82f6;" +
                                "-fx-border-width: 1.6;" +
                                "-fx-border-radius: 16;"
                );
            } else {
                radio.setText("○");
                radio.setTextFill(Color.web("#9ca3af"));
                setStyle(
                        "-fx-background-color: #020617;" +
                                "-fx-background-radius: 16;"
                );
            }
        }

        boolean isSelected() {
            return selected;
        }

        String getTitleText() {
            return title.getText();
        }
    }
}