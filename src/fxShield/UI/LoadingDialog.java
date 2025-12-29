package fxShield.UI;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/**
 * Unified loading dialog with animated dots, completion states, and base dialog logic.
 * Merged from BaseDialog and LoadingDialog.
 */
public final class LoadingDialog {

    private static final double DEFAULT_WIDTH = 380;
    private static final double DEFAULT_HEIGHT = 180;
    private static final double DEFAULT_BLUR_RADIUS = 18.0;

    private static final Duration DOTS_INTERVAL = Duration.millis(260);
    private static final Duration DONE_DELAY = Duration.millis(900);
    private static final Duration FAIL_DELAY = Duration.millis(1200);

    // Cache fonts
    private static final Font FONT_TITLE = StyleConstants.FONT_DIALOG_TITLE;
    private static final Font FONT_MESSAGE = StyleConstants.FONT_DIALOG_SUBTITLE;
    private static final Font FONT_BUTTON = StyleConstants.FONT_DIALOG_BUTTON;
    private static final Font FONT_DOTS = Font.font(StyleConstants.FONT_FAMILY, 20);

    // Reference counter for stacked dialogs blur.
    private static int blurReferenceCount = 0;

    private final Stage stage;
    private final Stage owner;
    private final Node ownerRoot;
    private final Effect previousOwnerEffect;

    private Pane root;
    private boolean closing;

    private final double width;
    private final double height;

    private final Duration fadeInDuration = Duration.millis(180);
    private final Duration fadeOutDuration = Duration.millis(150);

    private final Label titleLabel;
    private final Label messageLabel;
    private final Label dotsLabel;

    private final Label rebootNoteLabel;
    private final HBox buttonsRow;
    private final Button rebootNowBtn;
    private final Button rebootLaterBtn;

    private final Timeline dotsTimeline;
    private int dotState;

    private final boolean supportsReboot;

    public static LoadingDialog show(Stage owner, String title, String message) {
        return show(owner, title, message, false);
    }

    public static LoadingDialog show(Stage owner, String title, String message, boolean supportsReboot) {
        LoadingDialog dialog = new LoadingDialog(owner, title, message, supportsReboot);
        dialog.show();
        dialog.startDotsAnimation();
        return dialog;
    }

    private LoadingDialog(Stage owner, String title, String message, boolean supportsReboot) {
        this.owner = owner;
        this.width = DEFAULT_WIDTH;
        this.height = supportsReboot ? 240 : DEFAULT_HEIGHT;
        this.supportsReboot = supportsReboot;

        this.stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
        stage.setTitle(title);

        ownerRoot = (owner != null && owner.getScene() != null) ? owner.getScene().getRoot() : null;
        previousOwnerEffect = (ownerRoot != null) ? ownerRoot.getEffect() : null;

        applyOwnerBlurIfNeeded();

        stage.setOnCloseRequest(e -> {
            e.consume();
            close();
        });

        stage.setOnHidden(e -> {
            stopDotsAnimation();
            restoreOwnerBlur();
        });

        // UI Components
        titleLabel = new Label(safe(title));
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setTextFill(Color.web(StyleConstants.COLOR_TEXT_WHITE));

        messageLabel = new Label(safe(message));
        messageLabel.setFont(FONT_MESSAGE);
        messageLabel.setTextFill(Color.web(StyleConstants.COLOR_TEXT_SECONDARY));
        messageLabel.setWrapText(true);
        messageLabel.setMinWidth(0);

        dotsLabel = new Label("● ○ ○");
        dotsLabel.setFont(FONT_DOTS);
        dotsLabel.setTextFill(Color.web(StyleConstants.COLOR_LIGHT_BLUE));

        rebootNoteLabel = new Label("Restart is required to apply changes.");
        rebootNoteLabel.setFont(StyleConstants.FONT_BODY_12);
        rebootNoteLabel.setTextFill(Color.web(StyleConstants.COLOR_AMBER));
        rebootNoteLabel.setVisible(false);
        rebootNoteLabel.setManaged(false);

        rebootLaterBtn = createButton("Reboot later", StyleConstants.BUTTON_SECONDARY, StyleConstants.BUTTON_SECONDARY_HOVER);
        rebootLaterBtn.setOnAction(e -> close());

        rebootNowBtn = createButton("Reboot now", StyleConstants.BUTTON_PRIMARY, StyleConstants.BUTTON_PRIMARY_HOVER);
        rebootNowBtn.setOnAction(e -> performReboot());

        buttonsRow = new HBox(10, rebootLaterBtn, rebootNowBtn);
        buttonsRow.setAlignment(Pos.CENTER);
        buttonsRow.setVisible(false);
        buttonsRow.setManaged(false);

        dotsTimeline = new Timeline(new KeyFrame(DOTS_INTERVAL, e -> advanceDots()));
        dotsTimeline.setCycleCount(Animation.INDEFINITE);

        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> stopDotsAnimation());
    }

    private Pane buildContent() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(supportsReboot ? 32 : 16, 22, supportsReboot ? 32 : 16, 22));
        pane.setStyle(supportsReboot ? StyleConstants.DIALOG_REBOOT : StyleConstants.DIALOG_LOADING);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(supportsReboot ? 28 : 24);
        clip.setArcHeight(supportsReboot ? 28 : 24);
        clip.widthProperty().bind(pane.widthProperty());
        clip.heightProperty().bind(pane.heightProperty());
        pane.setClip(clip);

        VBox contentBox;
        if (supportsReboot) {
            contentBox = new VBox(14, titleLabel, messageLabel, dotsLabel, rebootNoteLabel, buttonsRow);
            contentBox.setAlignment(Pos.CENTER);
        } else {
            VBox textBox = new VBox(6, titleLabel, messageLabel);
            textBox.setAlignment(Pos.CENTER_LEFT);
            contentBox = new VBox(12, textBox, dotsLabel);
            contentBox.setAlignment(Pos.CENTER_LEFT);
        }

        pane.setCenter(contentBox);
        return pane;
    }

    private void initialize() {
        root = buildContent();

        Scene scene = new Scene(root, width, height);
        scene.setFill(Color.TRANSPARENT);

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) close();
        });

        stage.setScene(scene);

        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - width) / 2.0);
            stage.setY(owner.getY() + (owner.getHeight() - height) / 2.0);
        } else {
            stage.centerOnScreen();
        }

        root.setOpacity(0.0);
        FadeTransition fadeInTransition = fadeIn(root, fadeInDuration);
        stage.setOnShown(e -> fadeInTransition.playFromStart());
    }

    public void show() {
        if (root == null) initialize();
        stage.show();
    }

    public void close() {
        if (closing) return;
        closing = true;

        if (root == null || !stage.isShowing()) {
            stage.hide();
            return;
        }

        stopDotsAnimation();
        FadeTransition fadeOutTransition = fadeOut(root, fadeOutDuration);
        fadeOutTransition.setOnFinished(e -> stage.hide());
        fadeOutTransition.playFromStart();
    }

    private void applyOwnerBlurIfNeeded() {
        if (ownerRoot == null) return;
        if (blurReferenceCount == 0) {
            ownerRoot.setEffect(new GaussianBlur(DEFAULT_BLUR_RADIUS));
        }
        blurReferenceCount++;
    }

    private void restoreOwnerBlur() {
        if (ownerRoot == null) return;
        blurReferenceCount = Math.max(0, blurReferenceCount - 1);
        if (blurReferenceCount == 0) {
            ownerRoot.setEffect(previousOwnerEffect);
        }
    }

    private void startDotsAnimation() {
        dotsTimeline.play();
    }

    private void stopDotsAnimation() {
        stopSafely(dotsTimeline);
    }

    private void advanceDots() {
        dotState = (dotState + 1) % 3;
        if (dotState == 0) dotsLabel.setText("● ○ ○");
        else if (dotState == 1) dotsLabel.setText("○ ● ○");
        else dotsLabel.setText("○ ○ ●");
    }

    public void setTitleText(String title) {
        runOnFxThread(() -> titleLabel.setText(safe(title)));
    }

    public void setMessageText(String message) {
        runOnFxThread(() -> messageLabel.setText(safe(message)));
    }

    public void setDone(String doneMessage) {
        runOnFxThread(() -> {
            stopDotsAnimation();
            messageLabel.setText(safe(doneMessage));
            dotsLabel.setText("✓");
            dotsLabel.setTextFill(Color.web(StyleConstants.COLOR_SUCCESS));

            PauseTransition wait = pauseThen(DONE_DELAY, this::close);
            wait.playFromStart();
        });
    }

    public void setFailed(String failMessage) {
        runOnFxThread(() -> {
            stopDotsAnimation();
            messageLabel.setText(safe(failMessage));
            dotsLabel.setText("✕");
            dotsLabel.setTextFill(Color.web(StyleConstants.COLOR_DANGER));

            PauseTransition wait = pauseThen(FAIL_DELAY, this::close);
            wait.playFromStart();
        });
    }

    public void setDoneRequiresReboot(String doneMessage) {
        if (!supportsReboot) {
            setDone(doneMessage);
            return;
        }

        runOnFxThread(() -> {
            stopDotsAnimation();
            dotsLabel.setText("✓");
            dotsLabel.setTextFill(Color.web(StyleConstants.COLOR_SUCCESS));
            messageLabel.setText(safe(doneMessage));

            rebootNoteLabel.setVisible(true);
            rebootNoteLabel.setManaged(true);

            showButtons();
        });
    }

    private Button createButton(String text, String normalStyle, String hoverStyle) {
        Button btn = new Button(text);
        btn.setFont(FONT_BUTTON);
        btn.setFocusTraversable(false);
        btn.setMnemonicParsing(false);
        btn.setStyle(normalStyle);
        btn.hoverProperty().addListener((obs, wasHover, isHover) -> btn.setStyle(isHover ? hoverStyle : normalStyle));
        return btn;
    }

    private void showButtons() {
        buttonsRow.setVisible(true);
        buttonsRow.setManaged(true);
        fadeIn(buttonsRow, Duration.millis(160)).playFromStart();
    }

    private void performReboot() {
        rebootNowBtn.setDisable(true);
        rebootLaterBtn.setDisable(true);
        messageLabel.setText("Rebooting now...");

        Thread rebootThread = new Thread(() -> {
            try {
                new ProcessBuilder("cmd", "/c", "shutdown", "/r", "/t", "0").start();
            } catch (Exception ex) {
                setFailed("Failed to reboot. Run as Administrator.");
            }
        }, "RebootNow");
        rebootThread.setDaemon(true);
        rebootThread.start();
    }

    private void runOnFxThread(Runnable action) {
        if (action == null) return;
        if (Platform.isFxApplicationThread()) action.run();
        else Platform.runLater(action);
    }

    private static FadeTransition fadeIn(Node node, Duration duration) {
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);
        return fade;
    }

    private static FadeTransition fadeOut(Node node, Duration duration) {
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_IN);
        return fade;
    }

    private static void stopSafely(Animation animation) {
        if (animation == null) return;
        try { animation.stop(); } catch (Exception ignored) {}
    }

    private static PauseTransition pauseThen(Duration duration, Runnable onFinished) {
        PauseTransition pause = new PauseTransition(duration);
        if (onFinished != null) pause.setOnFinished(e -> onFinished.run());
        return pause;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
