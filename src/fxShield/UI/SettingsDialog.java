package fxShield.UI;

import fxShield.WIN.AutomationService;
import fxShield.WIN.FxSettings;
import fxShield.WIN.SettingsStore;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
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

import java.util.function.Consumer;

public final class SettingsDialog {

    // ====== Palette (Dark Blue) ======
    private static final String BG_TOP = "#020617";   // very dark blue
    private static final String BG_MID = "#0b1224";   // deep blue
    private static final String ACCENT1 = "#2563eb";  // blue
    private static final String ACCENT2 = "#7c3aed";  // purple
    private static final String TEXT_MAIN = "#e5e7eb";
    private static final String TEXT_SUB  = "#9ca3af";

    // ====== Styles ======
    private static final String DIALOG_ROOT_STYLE =
            "-fx-background-color: linear-gradient(to bottom, " + BG_TOP + ", " + BG_MID + ");" +
                    "-fx-background-radius: 22;" +
                    "-fx-border-radius: 22;" +
                    "-fx-border-color: rgba(147,197,253,0.18);" + // soft blue border
                    "-fx-border-width: 1;" +
                    "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.22), 28, 0.22, 0, 0)," +
                    "           dropshadow(gaussian, rgba(124,58,237,0.18), 22, 0.18, 0, 0)," +
                    "           dropshadow(gaussian, rgba(0,0,0,0.60), 26, 0.20, 0, 14);";

    private static final String OPTION_BOX_STYLE =
            "-fx-background-color: rgba(255,255,255,0.04);" +
                    "-fx-background-radius: 18;" +
                    "-fx-border-radius: 18;" +
                    "-fx-border-color: rgba(255,255,255,0.08);" +
                    "-fx-border-width: 1;" +
                    "-fx-padding: 14;" +
                    "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.12), 18, 0.18, 0, 0);";

    private static final String BTN_CANCEL_NORMAL =
            "-fx-background-color: rgba(255,255,255,0.04);" +
                    "-fx-text-fill: " + TEXT_MAIN + ";" +
                    "-fx-border-color: rgba(255,255,255,0.14);" +
                    "-fx-border-width: 1.1;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 7 18 7 18;" +
                    "-fx-cursor: hand;";

    private static final String BTN_CANCEL_HOVER =
            "-fx-background-color: rgba(255,255,255,0.08);" +
                    "-fx-text-fill: " + TEXT_MAIN + ";" +
                    "-fx-border-color: rgba(147,197,253,0.28);" +
                    "-fx-border-width: 1.1;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 7 18 7 18;" +
                    "-fx-cursor: hand;";

    private static final String BTN_APPLY_NORMAL =
            "-fx-background-color: linear-gradient(to bottom, " + ACCENT1 + ", " + ACCENT2 + ");" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 999;" +
                    "-fx-padding: 7 22 7 22;" +
                    "-fx-cursor: hand;" +
                    "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.25), 18, 0.20, 0, 0);";

    private static final String BTN_APPLY_HOVER =
            "-fx-background-color: linear-gradient(to bottom, #1d4ed8, #6d28d9);" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 999;" +
                    "-fx-padding: 7 22 7 22;" +
                    "-fx-cursor: hand;" +
                    "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.32), 22, 0.22, 0, 0);";

    public static void show(Stage owner) {
        FxSettings s = SettingsStore.load();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Settings");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16, 24, 28, 24));
        root.setStyle(DIALOG_ROOT_STYLE);

        // ✅ Clip للزوايا المنحنية
        Rectangle clip = new Rectangle();
        clip.setArcWidth(44);
        clip.setArcHeight(44);
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);

        // ===== Header =====
        Label title = new Label("Settings");
        title.setFont(Font.font("Segoe UI", 18));
        title.setTextFill(Color.web(TEXT_MAIN));
        title.setStyle("-fx-font-weight: 800;");

        Label sub = new Label("Choose what you want to run automatically.");
        sub.setFont(Font.font("Segoe UI", 12));
        sub.setTextFill(Color.web(TEXT_SUB));

        VBox titleBox = new VBox(4, title, sub);
        root.setTop(titleBox);

        // ===== Values =====
        final boolean[] vFree  = { s.autoFreeRam };
        final boolean[] vDisk  = { s.autoOptimizeHardDisk };
        final boolean[] vStart = { s.autoStartWithWindows };

        HBox r1 = toggleRow("Auto Free RAM", "Free memory periodically in the background.", vFree[0], b -> vFree[0] = b);
        HBox r2 = toggleRow("Auto Optimize Disk", "Run cleanup & optimization suggestions.", vDisk[0], b -> vDisk[0] = b);
        HBox r3 = toggleRow("Start with Windows", "Launch FxShield when your PC starts.", vStart[0], b -> vStart[0] = b);

        VBox box = new VBox(12, r1, r2, r3);
        box.setStyle(OPTION_BOX_STYLE);

        VBox options = new VBox(12, box);
        options.setPadding(new Insets(18, 0, 10, 0));
        root.setCenter(options);

        // ===== Buttons =====
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
            ns.autoFreeRam = vFree[0];
            ns.autoOptimizeHardDisk = vDisk[0];
            ns.autoStartWithWindows = vStart[0];
            
            // Save to persistent store
            SettingsStore.save(ns);
            
            // Apply changes immediately to running service
            AutomationService.get().apply(ns);
            
            dialog.close();
        });

        HBox bottom = new HBox(10, cancelBtn, applyBtn);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(18, 0, 6, 0));
        root.setBottom(bottom);

        // ✅ لا تجعل الخلفية شفافة (عشان ما يصير أبيض)
        Scene scene = new Scene(root, 900, 420);
        scene.setFill(Color.web(String.valueOf(Color.TRANSPARENT)));

        scene.setOnKeyPressed(k -> {
            if (k.getCode() == KeyCode.ESCAPE) dialog.close();
        });

        dialog.setScene(scene);
        dialog.centerOnScreen();

        // ===== Animation =====
        root.setOpacity(0);
        root.setScaleX(0.96);
        root.setScaleY(0.96);
        dialog.show();

        FadeTransition fade = new FadeTransition(Duration.millis(220), root);
        fade.setFromValue(0);
        fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(220), root);
        scale.setFromX(0.96);
        scale.setFromY(0.96);
        scale.setToX(1);
        scale.setToY(1);

        ParallelTransition popIn = new ParallelTransition(fade, scale);
        popIn.setInterpolator(Interpolator.EASE_OUT);
        popIn.play();
    }

    // ===== Modern Toggle Row =====
    private static HBox toggleRow(String title, String desc, boolean initial, Consumer<Boolean> onChange) {
        Label t = new Label(title);
        t.setFont(Font.font("Segoe UI", 14));
        t.setTextFill(Color.web(TEXT_MAIN));
        t.setStyle("-fx-font-weight: 800;");

        Label d = new Label(desc);
        d.setFont(Font.font("Segoe UI", 12));
        d.setTextFill(Color.web(TEXT_SUB));

        VBox texts = new VBox(2, t, d);

        // ===== Switch (Track + Knob) =====
        Region track = new Region();
        track.setPrefSize(56, 30);
        track.setMinSize(56, 30);
        track.setMaxSize(56, 30);

        Region knob = new Region();
        knob.setPrefSize(22, 22);
        knob.setMinSize(22, 22);
        knob.setMaxSize(22, 22);

        StackPane switcher = new StackPane(track, knob);
        switcher.setPrefSize(56, 30);
        switcher.setMinSize(56, 30);
        switcher.setMaxSize(56, 30);
        switcher.setCursor(Cursor.HAND);

        final boolean[] state = { initial };

        Runnable applySwitchStyle = () -> {
            if (state[0]) {
                track.setStyle(
                        "-fx-background-radius: 999;" +
                                "-fx-border-radius: 999;" +
                                "-fx-background-color: rgba(37,99,235,0.78);" +       // ✅ أزرق داكن
                                "-fx-border-color: rgba(147,197,253,0.65);" +
                                "-fx-border-width: 1;" +
                                "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.40), 16, 0.22, 0, 0);"
                );
            } else {
                track.setStyle(
                        "-fx-background-radius: 999;" +
                                "-fx-border-radius: 999;" +
                                "-fx-background-color: rgba(255,255,255,0.14);" +
                                "-fx-border-color: rgba(255,255,255,0.18);" +
                                "-fx-border-width: 1;"
                );
            }

            // ✅ غيرنا الأبيض: صارت الدائرة "أوف وايت" ناعمة مع حد خفيف
            knob.setStyle(
                    "-fx-background-radius: 999;" +
                            "-fx-border-radius: 999;" +
                            "-fx-background-color: #eaf2ff;" +                 // بدل white
                            "-fx-border-color: rgba(147,197,253,0.35);" +
                            "-fx-border-width: 1;" +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 10, 0.22, 0, 2);"
            );
        };

        applySwitchStyle.run();

        // initial knob position
        knob.setTranslateX(state[0] ? 14 : -14);

        Runnable toggle = () -> {
            state[0] = !state[0];
            applySwitchStyle.run();

            TranslateTransition move = new TranslateTransition(Duration.millis(140), knob);
            move.setToX(state[0] ? 14 : -14);
            move.setInterpolator(Interpolator.EASE_BOTH);
            move.play();

            if (onChange != null) onChange.accept(state[0]);
        };

        switcher.setOnMouseClicked(e -> toggle.run());

        // ===== Row Layout =====
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, texts, spacer, switcher);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 14, 12, 14));

        String normalRow =
                "-fx-background-color: rgba(255,255,255,0.03);" +
                        "-fx-background-radius: 14;" +
                        "-fx-border-radius: 14;" +
                        "-fx-border-color: rgba(255,255,255,0.06);" +
                        "-fx-border-width: 1;";

        String hoverRow =
                "-fx-background-color: rgba(255,255,255,0.05);" +
                        "-fx-background-radius: 14;" +
                        "-fx-border-radius: 14;" +
                        "-fx-border-color: rgba(147,197,253,0.22);" +
                        "-fx-border-width: 1;";

        row.setStyle(normalRow);
        row.setOnMouseEntered(e -> row.setStyle(hoverRow));
        row.setOnMouseExited(e -> row.setStyle(normalRow));

        // click on row toggles too (but not double click on switch)
        row.setOnMouseClicked(e -> {
            if (e.getTarget() == switcher || e.getTarget() == track || e.getTarget() == knob) return;
            toggle.run();
        });

        return row;
    }
}
