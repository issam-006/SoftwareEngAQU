package fxShield;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * LoadingDialogReboot
 * ------------------
 * Loading dialog مع Blur
 * وبعد الانتهاء يظهر:
 * - Reboot now
 * - Reboot later
 * مع ملاحظة أن إعادة التشغيل مطلوبة
 */
public class LoadingDialogReboot {

    private final Stage dialog;
    private final VBox root;

    private final Label titleLabel;
    private final Label messageLabel;
    private final Label dotsLabel;
    private final Label rebootNoteLabel;

    private final HBox buttonsRow;
    private final Button rebootNowBtn;
    private final Button rebootLaterBtn;

    private final Timeline dotsAnimation;

    private final Stage ownerRef;
    private boolean closing = false;

    /* ===================== SHOW ===================== */

    public static LoadingDialogReboot show(Stage owner, String title, String message) {
        LoadingDialogReboot dlg = new LoadingDialogReboot(owner, title, message);
        dlg.show();
        return dlg;
    }

    /* ================== CONSTRUCTOR ================= */

    private LoadingDialogReboot(Stage owner, String title, String message) {
        this.ownerRef = owner;

        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        // ===== Blur الخلفية =====
        if (owner != null && owner.getScene() != null) {
            owner.getScene().getRoot().setEffect(new GaussianBlur(18));
        }

        titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", 18));
        titleLabel.setTextFill(Color.web("#e5e7eb"));
        titleLabel.setStyle("-fx-font-weight: bold;");

        messageLabel = new Label(message);
        messageLabel.setFont(Font.font("Segoe UI", 13));
        messageLabel.setTextFill(Color.web("#9ca3af"));
        messageLabel.setWrapText(true);

        dotsLabel = new Label("● ○ ○");
        dotsLabel.setFont(Font.font("Segoe UI", 20));
        dotsLabel.setTextFill(Color.web("#60a5fa"));

        rebootNoteLabel = new Label("Restart is required to apply changes.");
        rebootNoteLabel.setFont(Font.font("Segoe UI", 12));
        rebootNoteLabel.setTextFill(Color.web("#fbbf24"));
        rebootNoteLabel.setVisible(false);
        rebootNoteLabel.setManaged(false);

        dotsAnimation = new Timeline(
                new KeyFrame(Duration.millis(400), e -> animateDots())
        );
        dotsAnimation.setCycleCount(Animation.INDEFINITE);

        rebootLaterBtn = new Button("Reboot later");
        rebootLaterBtn.setFont(Font.font("Segoe UI", 12));
        rebootLaterBtn.setStyle(secondaryStyle());
        rebootLaterBtn.setOnMouseEntered(e -> rebootLaterBtn.setStyle(secondaryHover()));
        rebootLaterBtn.setOnMouseExited(e -> rebootLaterBtn.setStyle(secondaryStyle()));
        rebootLaterBtn.setOnAction(e -> close());

        rebootNowBtn = new Button("Reboot now");
        rebootNowBtn.setFont(Font.font("Segoe UI", 12));
        rebootNowBtn.setStyle(primaryStyle());
        rebootNowBtn.setOnMouseEntered(e -> rebootNowBtn.setStyle(primaryHover()));
        rebootNowBtn.setOnMouseExited(e -> rebootNowBtn.setStyle(primaryStyle()));
        rebootNowBtn.setOnAction(e -> rebootNow());

        buttonsRow = new HBox(10, rebootLaterBtn, rebootNowBtn);
        buttonsRow.setAlignment(Pos.CENTER);
        buttonsRow.setVisible(false);
        buttonsRow.setManaged(false);

        root = new VBox(14,
                titleLabel,
                messageLabel,
                dotsLabel,
                rebootNoteLabel,
                buttonsRow
        );
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(26));
        root.setMinWidth(380);

        root.setStyle(
                "-fx-background-color: #020617;" +
                        "-fx-background-radius: 20;" +
                        "-fx-border-color: rgba(255,255,255,0.25);" +
                        "-fx-border-radius: 20;" +
                        "-fx-border-width: 1.2;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 30, 0.25, 0, 10);"
        );

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);

        dialog.setOnHidden(e -> removeBlur());
    }

    /* ===================== API ===================== */

    private void show() {
        dialog.centerOnScreen();
        dialog.show();
        dotsAnimation.play();
    }

    public void setDoneRequiresReboot(String doneMessage) {
        Platform.runLater(() -> {
            dotsAnimation.stop();
            dotsLabel.setText("✓");
            dotsLabel.setTextFill(Color.web("#22c55e"));

            messageLabel.setText(doneMessage);
            rebootNoteLabel.setVisible(true);
            rebootNoteLabel.setManaged(true);

            showButtons();
        });
    }

    public void setDone(String doneMessage) {
        Platform.runLater(() -> {
            dotsAnimation.stop();
            dotsLabel.setText("✓");
            dotsLabel.setTextFill(Color.web("#22c55e"));
            messageLabel.setText(doneMessage);

            PauseTransition p = new PauseTransition(Duration.seconds(1.2));
            p.setOnFinished(e -> close());
            p.play();
        });
    }

    public void setFailed(String message) {
        Platform.runLater(() -> {
            dotsAnimation.stop();
            dotsLabel.setText("✕");
            dotsLabel.setTextFill(Color.web("#f87171"));
            messageLabel.setText(message);

            PauseTransition p = new PauseTransition(Duration.seconds(1.6));
            p.setOnFinished(e -> close());
            p.play();
        });
    }

    /* ===================== UI ===================== */

    private void showButtons() {
        buttonsRow.setVisible(true);
        buttonsRow.setManaged(true);

        FadeTransition fade = new FadeTransition(Duration.millis(160), buttonsRow);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private void animateDots() {
        String t = dotsLabel.getText();
        dotsLabel.setText(
                t.equals("● ○ ○") ? "○ ● ○" :
                        t.equals("○ ● ○") ? "○ ○ ●" : "● ○ ○"
        );
    }

    /* ===================== REBOOT ===================== */

    private void rebootNow() {
        rebootNowBtn.setDisable(true);
        rebootLaterBtn.setDisable(true);
        messageLabel.setText("Rebooting now...");

        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "cmd", "/c", "shutdown", "/r", "/t", "0"
                );
                pb.start();
            } catch (Exception ex) {
                Platform.runLater(() -> setFailed("Failed to reboot. Run as Administrator."));
            }
        }).start();
    }

    private void close() {
        if (closing) return;
        closing = true;

        FadeTransition fade = new FadeTransition(Duration.millis(140), root);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> dialog.close());
        fade.play();
    }

    private void removeBlur() {
        if (ownerRef != null && ownerRef.getScene() != null) {
            ownerRef.getScene().getRoot().setEffect(null);
        }
    }

    /* ===================== STYLES ===================== */

    private String primaryStyle() {
        return "-fx-background-color: #2563eb;" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 999;" +
                "-fx-padding: 8 18;" +
                "-fx-cursor: hand;";
    }

    private String primaryHover() {
        return "-fx-background-color: #1d4ed8;" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 999;" +
                "-fx-padding: 8 18;" +
                "-fx-cursor: hand;";
    }

    private String secondaryStyle() {
        return "-fx-background-color: transparent;" +
                "-fx-text-fill: #cbd5e1;" +
                "-fx-border-color: rgba(255,255,255,0.25);" +
                "-fx-border-width: 1.2;" +
                "-fx-background-radius: 999;" +
                "-fx-border-radius: 999;" +
                "-fx-padding: 8 18;" +
                "-fx-cursor: hand;";
    }

    private String secondaryHover() {
        return "-fx-background-color: rgba(255,255,255,0.08);" +
                "-fx-text-fill: #ffffff;" +
                "-fx-border-color: rgba(255,255,255,0.35);" +
                "-fx-border-width: 1.2;" +
                "-fx-background-radius: 999;" +
                "-fx-border-radius: 999;" +
                "-fx-padding: 8 18;" +
                "-fx-cursor: hand;";
    }
}
