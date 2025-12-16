package fxShield;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public final class LoadingDialog {

    // Dimensions/durations
    private static final double WIDTH = 340;
    private static final double HEIGHT = 160;
    private static final Duration FADE_IN = Duration.millis(180);
    private static final Duration FADE_OUT = Duration.millis(150);
    private static final Duration DOTS_INTERVAL = Duration.millis(260);
    private static final Duration DONE_DELAY = Duration.millis(900);
    private static final Duration FAIL_DELAY = Duration.millis(1200);

    // Styles
    private static final String ROOT_STYLE =
            "-fx-background-color: linear-gradient(to bottom right, #020617, #111827);" +
                    "-fx-background-radius: 18;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 22, 0.25, 0, 8);";

    // Track how many dialogs applied blur
    private static int blurOwners = 0;

    private final Stage stage;
    private final Node ownerRoot;
    private final Effect previousEffect;

    private final Label titleLabel;
    private final Label messageLabel;
    private final Label dotsLabel;

    private final Timeline dotsTimeline;

    private int dotState = 0;
    private boolean closing = false;

    private LoadingDialog(Stage owner, String title, String message) {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);

        // Blur background once for the first dialog
        ownerRoot = (owner != null && owner.getScene() != null) ? owner.getScene().getRoot() : null;
        previousEffect = (ownerRoot != null) ? ownerRoot.getEffect() : null;
        if (ownerRoot != null) {
            if (blurOwners == 0) ownerRoot.setEffect(new GaussianBlur(18));
            blurOwners++;
        }

        // Root
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16, 22, 16, 22));
        root.setStyle(ROOT_STYLE);

        Rectangle clip = new Rectangle(WIDTH, HEIGHT);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        root.setClip(clip);

        // Title / message
        titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", 16));
        titleLabel.setTextFill(Color.web("#e5e7eb"));
        titleLabel.setStyle("-fx-font-weight: bold;");

        messageLabel = new Label(message);
        messageLabel.setFont(Font.font("Segoe UI", 12));
        messageLabel.setTextFill(Color.web("#9ca3af"));
        messageLabel.setWrapText(true);

        VBox textBox = new VBox(6, titleLabel, messageLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);

        // Dots
        dotsLabel = new Label("● ○ ○");
        dotsLabel.setFont(Font.font("Segoe UI", 18));
        dotsLabel.setTextFill(Color.web("#60a5fa"));

        VBox centerBox = new VBox(12, textBox, dotsLabel);
        centerBox.setAlignment(Pos.CENTER_LEFT);
        root.setCenter(centerBox);

        // Fade-in
        root.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(FADE_IN, root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setInterpolator(Interpolator.EASE_OUT);
        fadeIn.play();

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        scene.setFill(Color.TRANSPARENT);
        // ESC to close
        scene.setOnKeyPressed(e -> { if (e.getCode().toString().equals("ESCAPE")) close(); });
        stage.setScene(scene);

        // Center relative to owner if available
        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - WIDTH) / 2.0);
            stage.setY(owner.getY() + (owner.getHeight() - HEIGHT) / 2.0);
        } else {
            stage.centerOnScreen();
        }

        dotsTimeline = new Timeline(new KeyFrame(DOTS_INTERVAL, e -> advanceDots()));
        dotsTimeline.setCycleCount(Animation.INDEFINITE);

        // Ensure cleanup on OS-close
        stage.setOnCloseRequest(e -> close());
        stage.setOnHidden(e -> restoreBlurIfLast());
    }

    private void advanceDots() {
        dotState = (dotState + 1) % 3;
        switch (dotState) {
            case 0 -> dotsLabel.setText("● ○ ○");
            case 1 -> dotsLabel.setText("○ ● ○");
            case 2 -> dotsLabel.setText("○ ○ ●");
        }
    }

    // Factory
    public static LoadingDialog show(Stage owner, String title, String message) {
        LoadingDialog dlg = new LoadingDialog(owner, title, message);
        dlg.stage.show();
        dlg.dotsTimeline.play();
        return dlg;
    }

    // Live updates
    public void setTitleText(String title) { titleLabel.setText(title != null ? title : ""); }
    public void setMessageText(String message) { messageLabel.setText(message != null ? message : ""); }

    public void setDone(String doneMessage) {
        dotsTimeline.stop();
        messageLabel.setText(doneMessage);
        dotsLabel.setText("✓");
        dotsLabel.setTextFill(Color.web("#22c55e"));
        PauseTransition wait = new PauseTransition(DONE_DELAY);
        wait.setOnFinished(e -> close());
        wait.play();
    }

    public void setFailed(String failMessage) {
        dotsTimeline.stop();
        messageLabel.setText(failMessage);
        dotsLabel.setText("✕");
        dotsLabel.setTextFill(Color.web("#f97373"));
        PauseTransition wait = new PauseTransition(FAIL_DELAY);
        wait.setOnFinished(e -> close());
        wait.play();
    }

    public void close() {
        if (closing) return;
        closing = true;

        dotsTimeline.stop();
        FadeTransition fade = new FadeTransition(FADE_OUT, stage.getScene().getRoot());
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            restoreBlurIfLast();
            stage.close();
        });
        fade.play();
    }

    private void restoreBlurIfLast() {
        if (ownerRoot != null) {
            blurOwners = Math.max(0, blurOwners - 1);
            if (blurOwners == 0) ownerRoot.setEffect(previousEffect);
        }
    }
}