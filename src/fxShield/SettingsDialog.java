package fxShield;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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

/**
 * Settings dialog with small UX upgrades:
 * - ESC closes, Enter applies
 * - Loads/saves via SettingsStore
 * - Subtle animations; keyboard focus traversal
 * - Consistent styles and spacing
 */
public final class SettingsDialog {

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

    private static final String OPTION_BOX_STYLE =
            "-fx-background-color: rgba(255,255,255,0.06);" +
                    "-fx-background-radius: 16;" +
                    "-fx-border-color: rgba(255,255,255,0.08);" +
                    "-fx-border-radius: 16;" +
                    "-fx-border-width: 1;" +
                    "-fx-padding: 14;";

    public static void show(Stage owner) {
        FxSettings s = SettingsStore.load();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Settings");

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
        Label title = new Label("Settings");
        title.setFont(Font.font("Segoe UI", 18));
        title.setTextFill(Color.web("#e5e7eb"));
        title.setStyle("-fx-font-weight: bold;");

        Label sub = new Label("Choose what you want to run automatically.");
        sub.setFont(Font.font("Segoe UI", 12));
        sub.setTextFill(Color.web("#9ca3af"));

        VBox titleBox = new VBox(4, title, sub);
        root.setTop(titleBox);

        // Options
        CheckBox autoFreeRam = styledCheck("Auto Free RAM");
        CheckBox autoDisk = styledCheck("Auto Optimize HardDisk");
        CheckBox autoStartup = styledCheck("Auto Start when PC Startup");

        autoFreeRam.setSelected(s.autoFreeRam);
        autoDisk.setSelected(s.autoOptimizeHardDisk);
        autoStartup.setSelected(s.autoStartWithWindows);

        VBox box = new VBox(12, autoFreeRam, autoDisk, autoStartup);
        box.setStyle(OPTION_BOX_STYLE);

        VBox options = new VBox(12, box);
        options.setPadding(new Insets(20, 0, 10, 0));
        root.setCenter(options);

        // Buttons
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(BTN_CANCEL_NORMAL);
        cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle(BTN_CANCEL_HOVER));
        cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle(BTN_CANCEL_NORMAL));
        cancelBtn.setOnAction(e -> dialog.close());

        Button applyBtn = new Button("Apply");
        applyBtn.setDefaultButton(true);
        applyBtn.setStyle(BTN_APPLY_NORMAL);
        applyBtn.setOnMouseEntered(e -> applyBtn.setStyle(BTN_APPLY_HOVER));
        applyBtn.setOnMouseExited(e -> applyBtn.setStyle(BTN_APPLY_NORMAL));
        applyBtn.setOnAction(e -> {
            FxSettings ns = new FxSettings();
            ns.autoFreeRam = autoFreeRam.isSelected();
            ns.autoOptimizeHardDisk = autoDisk.isSelected();
            ns.autoStartWithWindows = autoStartup.isSelected();
            SettingsStore.save(ns);
            // Optionally apply OS startup change:
            // WindowsStartupManager.apply(ns.autoStartWithWindows);
            dialog.close();
        });

        HBox bottom = new HBox(10, cancelBtn, applyBtn);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(bottom);

        Scene scene = new Scene(root, 700, 320);
        scene.setFill(Color.TRANSPARENT);

        // Keyboard: ESC to cancel
        scene.setOnKeyPressed(k -> {
            if (k.getCode() == KeyCode.ESCAPE) dialog.close();
        });

        dialog.setScene(scene);
        dialog.centerOnScreen();

        // Pop-in animation
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

    private static CheckBox styledCheck(String text) {
        CheckBox cb = new CheckBox(text);
        cb.setFont(Font.font("Segoe UI", 13));
        cb.setTextFill(Color.web("#e5e7eb"));
        cb.setStyle("-fx-padding: 6 6 6 6; -fx-cursor: hand;");
        return cb;
    }
}