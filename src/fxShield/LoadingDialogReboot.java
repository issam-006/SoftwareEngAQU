package fxShield;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public final class LoadingDialogReboot {

    // Styles
    private static final String ROOT_STYLE =
            "-fx-background-color: #020617;" +
                    "-fx-background-radius: 20;" +
                    "-fx-border-color: rgba(255,255,255,0.25);" +
                    "-fx-border-radius: 20;" +
                    "-fx-border-width: 1.2;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 30, 0.25, 0, 10);";
    private static final String BTN_PRIMARY =
            "-fx-background-color: #2563eb;" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 999;" +
                    "-fx-padding: 8 18;" +
                    "-fx-cursor: hand;";
    private static final String BTN_PRIMARY_HOVER =
            "-fx-background-color: #1d4ed8;" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 999;" +
                    "-fx-padding: 8 18;" +
                    "-fx-cursor: hand;";
    private static final String BTN_SECONDARY =
            "-fx-background-color: transparent;" +
                    "-fx-text-fill: #cbd5e1;" +
                    "-fx-border-color: rgba(255,255,255,0.25);" +
                    "-fx-border-width: 1.2;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 8 18;" +
                    "-fx-cursor: hand;";
    private static final String BTN_SECONDARY_HOVER =
            "-fx-background-color: rgba(255,255,255,0.08);" +
                    "-fx-text-fill: #ffffff;" +
                    "-fx-border-color: rgba(255,255,255,0.35);" +
                    "-fx-border-width: 1.2;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 8 18;" +
                    "-fx-cursor: hand;";

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
    private Effect prevOwnerEffect = null;
    private boolean closing = false;

    public static LoadingDialogReboot show(Stage owner, String title, String message) {
        LoadingDialogReboot dlg = new LoadingDialogReboot(owner, title, message);
        dlg.show();
        return dlg;
    }

    private LoadingDialogReboot(Stage owner, String title, String message) {
        this.ownerRef = owner;

        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setResizable(false);
        dialog.setTitle(title != null ? title : "Loading");

        // Apply blur to owner and remember previous effect
        if (owner != null && owner.getScene() != null && owner.getScene().getRoot() != null) {
            var rootNode = owner.getScene().getRoot();
            prevOwnerEffect = rootNode.getEffect();
            rootNode.setEffect(new GaussianBlur(18));
        }

        // Title
        titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", 18));
        titleLabel.setTextFill(Color.web("#e5e7eb"));
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Message
        messageLabel = new Label(message);
        messageLabel.setFont(Font.font("Segoe UI", 13));
        messageLabel.setTextFill(Color.web("#9ca3af"));
        messageLabel.setWrapText(true);

        // Dots
        dotsLabel = new Label("● ○ ○");
        dotsLabel.setFont(Font.font("Segoe UI", 20));
        dotsLabel.setTextFill(Color.web("#60a5fa"));

        // Reboot note
        rebootNoteLabel = new Label("Restart is required to apply changes.");
        rebootNoteLabel.setFont(Font.font("Segoe UI", 12));
        rebootNoteLabel.setTextFill(Color.web("#fbbf24"));
        rebootNoteLabel.setVisible(false);
        rebootNoteLabel.setManaged(false);

        dotsAnimation = new Timeline(new KeyFrame(Duration.millis(400), e -> animateDots()));
        dotsAnimation.setCycleCount(Animation.INDEFINITE);

        // Buttons
        rebootLaterBtn = new Button("Reboot later");
        rebootLaterBtn.setFont(Font.font("Segoe UI", 12));
        rebootLaterBtn.setStyle(BTN_SECONDARY);
        rebootLaterBtn.setOnMouseEntered(e -> rebootLaterBtn.setStyle(BTN_SECONDARY_HOVER));
        rebootLaterBtn.setOnMouseExited(e -> rebootLaterBtn.setStyle(BTN_SECONDARY));
        rebootLaterBtn.setOnAction(e -> close());

        rebootNowBtn = new Button("Reboot now");
        rebootNowBtn.setFont(Font.font("Segoe UI", 12));
        rebootNowBtn.setStyle(BTN_PRIMARY);
        rebootNowBtn.setOnMouseEntered(e -> rebootNowBtn.setStyle(BTN_PRIMARY_HOVER));
        rebootNowBtn.setOnMouseExited(e -> rebootNowBtn.setStyle(BTN_PRIMARY));
        rebootNowBtn.setOnAction(e -> rebootNow());

        buttonsRow = new HBox(10, rebootLaterBtn, rebootNowBtn);
        buttonsRow.setAlignment(Pos.CENTER);
        buttonsRow.setVisible(false);
        buttonsRow.setManaged(false);

        // Root
        root = new VBox(14, titleLabel, messageLabel, dotsLabel, rebootNoteLabel, buttonsRow);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(26));
        root.setMinWidth(380);
        root.setStyle(ROOT_STYLE);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        // Keyboard shortcuts
        scene.setOnKeyPressed(k -> {
            switch (k.getCode()) {
                case ESCAPE -> close();
                case ENTER -> { if (buttonsRow.isVisible()) rebootNowBtn.fire(); }
            }
        });

        dialog.setScene(scene);
        dialog.setOnHidden(e -> removeBlur());
        dialog.setOnCloseRequest(e -> {
            // ensure cleanup even if user closes via OS controls
            stopDots();
            removeBlur();
        });
    }

    private void show() {
        dialog.centerOnScreen();
        dialog.show();
        dotsAnimation.play();
    }

    public void setDoneRequiresReboot(String doneMessage) {
        Platform.runLater(() -> {
            stopDots();
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
            stopDots();
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
            stopDots();
            dotsLabel.setText("✕");
            dotsLabel.setTextFill(Color.web("#f87171"));
            messageLabel.setText(message);

            PauseTransition p = new PauseTransition(Duration.seconds(1.6));
            p.setOnFinished(e -> close());
            p.play();
        });
    }

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

    private void rebootNow() {
        rebootNowBtn.setDisable(true);
        rebootLaterBtn.setDisable(true);
        messageLabel.setText("Rebooting now...");

        Thread t = new Thread(() -> {
            try {
                new ProcessBuilder("cmd", "/c", "shutdown", "/r", "/t", "0").start();
            } catch (Exception ex) {
                Platform.runLater(() -> setFailed("Failed to reboot. Run as Administrator."));
            }
        }, "RebootNow");
        t.setDaemon(true);
        t.start();
    }

    private void stopDots() {
        try { dotsAnimation.stop(); } catch (Exception ignored) {}
    }

    private void close() {
        if (closing) return;
        closing = true;

        stopDots();
        FadeTransition fade = new FadeTransition(Duration.millis(140), root);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> dialog.close());
        fade.play();
    }

    private void removeBlur() {
        if (ownerRef != null && ownerRef.getScene() != null && ownerRef.getScene().getRoot() != null) {
            ownerRef.getScene().getRoot().setEffect(prevOwnerEffect);
            prevOwnerEffect = null;
        }
    }
}