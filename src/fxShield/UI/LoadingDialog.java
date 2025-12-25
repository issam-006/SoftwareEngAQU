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
import javafx.util.Duration;

/**
 * Unified loading dialog that displays progress with animated dots and supports
 * multiple completion states: done, failed, and done-with-reboot-required.
 * 
 * <p>This dialog extends {@link BaseDialog} and provides a consistent loading
 * experience across the application. It features:</p>
 * <ul>
 *   <li>Animated loading dots (● ○ ○ → ○ ● ○ → ○ ○ ●)</li>
 *   <li>Live title and message updates</li>
 *   <li>Success state with checkmark (✓)</li>
 *   <li>Failure state with cross (✕)</li>
 *   <li>Reboot-required state with action buttons</li>
 *   <li>Auto-close after success/failure</li>
 *   <li>Manual reboot or defer options</li>
 * </ul>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Simple loading dialog
 * LoadingDialog dialog = LoadingDialog.show(
 *     ownerStage, "Processing", "Please wait...");
 * // ... perform work ...
 * dialog.setDone("Operation completed successfully!");
 * 
 * // Loading with reboot requirement
 * LoadingDialog dialog = LoadingDialog.show(
 *     ownerStage, "Updating System", "Applying changes...");
 * // ... perform work ...
 * dialog.setDoneRequiresReboot("Update complete. Restart required.");
 * 
 * // Loading with failure
 * dialog.setFailed("Operation failed. Please try again.");
 * }</pre>
 * 
 * @author fxShield Team
 * @version 2.0
 */
public final class LoadingDialog extends BaseDialog {

    // ========== CONSTANTS ==========
    
    /** Default dialog width */
    private static final double DEFAULT_WIDTH = 380;
    
    /** Default dialog height */
    private static final double DEFAULT_HEIGHT = 180;
    
    /** Interval between dot animation frames */
    private static final Duration DOTS_INTERVAL = Duration.millis(260);
    
    /** Delay before auto-closing on success */
    private static final Duration DONE_DELAY = Duration.millis(900);
    
    /** Delay before auto-closing on failure */
    private static final Duration FAIL_DELAY = Duration.millis(1200);

    // ========== UI COMPONENTS ==========
    
    private final Label titleLabel;
    private final Label messageLabel;
    private final Label dotsLabel;
    private final Label rebootNoteLabel;
    private final HBox buttonsRow;
    private final Button rebootNowBtn;
    private final Button rebootLaterBtn;
    
    private final Timeline dotsTimeline;
    private int dotState = 0;
    
    private final boolean supportsReboot;

    // ========== FACTORY METHODS ==========
    
    /**
     * Creates and shows a standard loading dialog.
     * 
     * @param owner the owner stage
     * @param title the dialog title
     * @param message the loading message
     * @return the created dialog instance
     */
    public static LoadingDialog show(Stage owner, String title, String message) {
        return show(owner, title, message, false);
    }
    
    /**
     * Creates and shows a loading dialog with optional reboot support.
     * 
     * @param owner the owner stage
     * @param title the dialog title
     * @param message the loading message
     * @param supportsReboot whether this dialog supports reboot functionality
     * @return the created dialog instance
     */
    public static LoadingDialog show(Stage owner, String title, String message, boolean supportsReboot) {
        LoadingDialog dialog = new LoadingDialog(owner, title, message, supportsReboot);
        dialog.show();
        dialog.startDotsAnimation();
        return dialog;
    }

    // ========== CONSTRUCTOR ==========
    
    /**
     * Creates a new loading dialog.
     * Use {@link #show(Stage, String, String)} factory method instead.
     * 
     * @param owner the owner stage
     * @param title the dialog title
     * @param message the loading message
     * @param supportsReboot whether this dialog supports reboot functionality
     */
    private LoadingDialog(Stage owner, String title, String message, boolean supportsReboot) {
        double height = supportsReboot ? 240 : DEFAULT_HEIGHT;
        super(owner, DEFAULT_WIDTH, height);
        
        this.supportsReboot = supportsReboot;
        setTitle(title);
        
        // Title label
        titleLabel = new Label(title);
        titleLabel.setFont(Font.font(StyleConstants.FONT_FAMILY, 18));
        titleLabel.setTextFill(Color.web(StyleConstants.COLOR_TEXT_WHITE));
        titleLabel.setStyle("-fx-font-weight: bold;");
        
        // Message label
        messageLabel = new Label(message);
        messageLabel.setFont(Font.font(StyleConstants.FONT_FAMILY, 13));
        messageLabel.setTextFill(Color.web(StyleConstants.COLOR_TEXT_SECONDARY));
        messageLabel.setWrapText(true);
        
        // Dots label (animated loading indicator)
        dotsLabel = new Label("● ○ ○");
        dotsLabel.setFont(Font.font(StyleConstants.FONT_FAMILY, 20));
        dotsLabel.setTextFill(Color.web(StyleConstants.COLOR_LIGHT_BLUE));
        
        // Reboot note label (hidden by default)
        rebootNoteLabel = new Label("Restart is required to apply changes.");
        rebootNoteLabel.setFont(Font.font(StyleConstants.FONT_FAMILY, 12));
        rebootNoteLabel.setTextFill(Color.web(StyleConstants.COLOR_AMBER));
        rebootNoteLabel.setVisible(false);
        rebootNoteLabel.setManaged(false);
        
        // Reboot buttons (hidden by default)
        rebootLaterBtn = createButton("Reboot later", StyleConstants.BUTTON_SECONDARY, 
                                      StyleConstants.BUTTON_SECONDARY_HOVER);
        rebootLaterBtn.setOnAction(e -> close());
        
        rebootNowBtn = createButton("Reboot now", StyleConstants.BUTTON_PRIMARY, 
                                    StyleConstants.BUTTON_PRIMARY_HOVER);
        rebootNowBtn.setOnAction(e -> performReboot());
        
        buttonsRow = new HBox(10, rebootLaterBtn, rebootNowBtn);
        buttonsRow.setAlignment(Pos.CENTER);
        buttonsRow.setVisible(false);
        buttonsRow.setManaged(false);
        
        // Dots animation timeline
        dotsTimeline = new Timeline(new KeyFrame(DOTS_INTERVAL, e -> advanceDots()));
        dotsTimeline.setCycleCount(Animation.INDEFINITE);
    }

    // ========== CONTENT BUILDING ==========
    
    @Override
    protected Pane buildContent() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(supportsReboot ? 32 : 16, 22, supportsReboot ? 32 : 16, 22));
        root.setStyle(supportsReboot ? StyleConstants.DIALOG_REBOOT : StyleConstants.DIALOG_LOADING);
        
        // Rounded corners clipping
        Rectangle clip = new Rectangle(DEFAULT_WIDTH, supportsReboot ? 240 : DEFAULT_HEIGHT);
        clip.setArcWidth(supportsReboot ? 28 : 24);
        clip.setArcHeight(supportsReboot ? 28 : 24);
        root.setClip(clip);
        
        // Content layout
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
        
        root.setCenter(contentBox);
        return root;
    }

    // ========== ANIMATION CONTROL ==========
    
    /**
     * Starts the dots animation.
     */
    private void startDotsAnimation() {
        dotsTimeline.play();
    }
    
    /**
     * Stops the dots animation.
     */
    private void stopDotsAnimation() {
        BaseDialog.stopSafely(dotsTimeline);
    }
    
    /**
     * Advances the dots animation to the next frame.
     */
    private void advanceDots() {
        dotState = (dotState + 1) % 3;
        switch (dotState) {
            case 0 -> dotsLabel.setText("● ○ ○");
            case 1 -> dotsLabel.setText("○ ● ○");
            case 2 -> dotsLabel.setText("○ ○ ●");
        }
    }

    // ========== PUBLIC API ==========
    
    /**
     * Updates the dialog title.
     * 
     * @param title the new title text
     */
    public void setTitleText(String title) {
        runOnFxThread(() -> titleLabel.setText(title != null ? title : ""));
    }
    
    /**
     * Updates the dialog message.
     * 
     * @param message the new message text
     */
    public void setMessageText(String message) {
        runOnFxThread(() -> messageLabel.setText(message != null ? message : ""));
    }
    
    /**
     * Sets the dialog to "done" state with success indicator.
     * The dialog will auto-close after a short delay.
     * 
     * @param doneMessage the completion message
     */
    public void setDone(String doneMessage) {
        runOnFxThread(() -> {
            stopDotsAnimation();
            messageLabel.setText(doneMessage);
            dotsLabel.setText("✓");
            dotsLabel.setTextFill(Color.web(StyleConstants.COLOR_SUCCESS));
            
            PauseTransition wait = BaseDialog.pauseThen(DONE_DELAY, this::close);
            wait.play();
        });
    }
    
    /**
     * Sets the dialog to "failed" state with error indicator.
     * The dialog will auto-close after a short delay.
     * 
     * @param failMessage the failure message
     */
    public void setFailed(String failMessage) {
        runOnFxThread(() -> {
            stopDotsAnimation();
            messageLabel.setText(failMessage);
            dotsLabel.setText("✕");
            dotsLabel.setTextFill(Color.web(StyleConstants.COLOR_DANGER));
            
            PauseTransition wait = BaseDialog.pauseThen(FAIL_DELAY, this::close);
            wait.play();
        });
    }
    
    /**
     * Sets the dialog to "done with reboot required" state.
     * Shows reboot action buttons and does not auto-close.
     * Only available if dialog was created with reboot support.
     * 
     * @param doneMessage the completion message
     */
    public void setDoneRequiresReboot(String doneMessage) {
        if (!supportsReboot) {
            // Fallback to regular done if reboot not supported
            setDone(doneMessage);
            return;
        }
        
        runOnFxThread(() -> {
            stopDotsAnimation();
            dotsLabel.setText("✓");
            dotsLabel.setTextFill(Color.web(StyleConstants.COLOR_SUCCESS));
            messageLabel.setText(doneMessage);
            
            rebootNoteLabel.setVisible(true);
            rebootNoteLabel.setManaged(true);
            
            showButtons();
        });
    }

    // ========== PRIVATE HELPERS ==========
    
    /**
     * Creates a styled button with hover effects.
     * 
     * @param text the button text
     * @param normalStyle the normal state style
     * @param hoverStyle the hover state style
     * @return the configured button
     */
    private Button createButton(String text, String normalStyle, String hoverStyle) {
        Button btn = new Button(text);
        btn.setFont(Font.font(StyleConstants.FONT_FAMILY, 12));
        btn.setStyle(normalStyle);
        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(normalStyle));
        return btn;
    }
    
    /**
     * Shows the reboot action buttons with fade-in animation.
     */
    private void showButtons() {
        buttonsRow.setVisible(true);
        buttonsRow.setManaged(true);
        BaseDialog.fadeIn(buttonsRow, Duration.millis(160)).play();
    }
    
    /**
     * Performs system reboot via Windows shutdown command.
     * Disables buttons and shows reboot message.
     */
    private void performReboot() {
        rebootNowBtn.setDisable(true);
        rebootLaterBtn.setDisable(true);
        messageLabel.setText("Rebooting now...");
        
        Thread rebootThread = new Thread(() -> {
            try {
                new ProcessBuilder("cmd", "/c", "shutdown", "/r", "/t", "0").start();
            } catch (Exception ex) {
                Platform.runLater(() -> setFailed("Failed to reboot. Run as Administrator."));
            }
        }, "RebootNow");
        rebootThread.setDaemon(true);
        rebootThread.start();
    }
    
    /**
     * Overrides close to ensure dots animation is stopped.
     */
    @Override
    public void close() {
        stopDotsAnimation();
        super.close();
    }
}
