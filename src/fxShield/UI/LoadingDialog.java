package fxShield.UI;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/**
 * Unified loading dialog with animated dots and completion states.
 */
public final class LoadingDialog extends BaseDialog {

    private static final double DEFAULT_WIDTH = 380;
    private static final double DEFAULT_HEIGHT = 180;

    private static final Duration DOTS_INTERVAL = Duration.millis(260);
    private static final Duration DONE_DELAY = Duration.millis(900);
    private static final Duration FAIL_DELAY = Duration.millis(1200);

    // Cache fonts (avoid Font.font allocations per instance)
    private static final Font FONT_TITLE = StyleConstants.FONT_DIALOG_TITLE;
    private static final Font FONT_MESSAGE = StyleConstants.FONT_DIALOG_SUBTITLE;
    private static final Font FONT_BUTTON = StyleConstants.FONT_DIALOG_BUTTON;
    private static final Font FONT_DOTS = Font.font(StyleConstants.FONT_FAMILY, 20);

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
        super(owner, DEFAULT_WIDTH, supportsReboot ? 240 : DEFAULT_HEIGHT);

        this.supportsReboot = supportsReboot;
        setTitle(title);

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

        // Stop animation even if someone hides the window without calling close()
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> stopDotsAnimation());
    }

    @Override
    protected Pane buildContent() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(supportsReboot ? 32 : 16, 22, supportsReboot ? 32 : 16, 22));
        pane.setStyle(supportsReboot ? StyleConstants.DIALOG_REBOOT : StyleConstants.DIALOG_LOADING);

        // Rounded corners clipping (bind to actual size)
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

    private void startDotsAnimation() {
        dotsTimeline.play();
    }

    private void stopDotsAnimation() {
        BaseDialog.stopSafely(dotsTimeline);
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

            PauseTransition wait = BaseDialog.pauseThen(DONE_DELAY, this::close);
            wait.playFromStart();
        });
    }

    public void setFailed(String failMessage) {
        runOnFxThread(() -> {
            stopDotsAnimation();
            messageLabel.setText(safe(failMessage));
            dotsLabel.setText("✕");
            dotsLabel.setTextFill(Color.web(StyleConstants.COLOR_DANGER));

            PauseTransition wait = BaseDialog.pauseThen(FAIL_DELAY, this::close);
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

        // IMPORTANT: keep font via setFont(), style only for colors/padding
        btn.setStyle(normalStyle);
        btn.hoverProperty().addListener((obs, wasHover, isHover) -> btn.setStyle(isHover ? hoverStyle : normalStyle));
        return btn;
    }

    private void showButtons() {
        buttonsRow.setVisible(true);
        buttonsRow.setManaged(true);
        BaseDialog.fadeIn(buttonsRow, Duration.millis(160)).playFromStart();
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

    @Override
    public void close() {
        stopDotsAnimation();
        super.close();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
