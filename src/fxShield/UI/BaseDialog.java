package fxShield.UI;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;

/**
 * Abstract base class for all modal dialogs in the fxShield application.
 * Provides common functionality including:
 * <ul>
 *   <li>Owner window blur management with reference counting</li>
 *   <li>Smooth fade-in/fade-out transitions</li>
 *   <li>ESC key handling for closing</li>
 *   <li>Modal setup and centering</li>
 *   <li>Transparent stage styling</li>
 *   <li>Automatic cleanup on close</li>
 * </ul>
 * 
 * <p>Subclasses must implement {@link #buildContent()} to provide the dialog's
 * visual content. The base class handles all stage setup, animations, and lifecycle management.</p>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * public class MyDialog extends BaseDialog {
 *     public MyDialog(Stage owner) {
 *         super(owner, 400, 300);
 *     }
 *     
 *     @Override
 *     protected Pane buildContent() {
 *         VBox content = new VBox();
 *         content.getChildren().add(new Label("My Dialog"));
 *         return content;
 *     }
 * }
 * }</pre>
 * 
 * <p><b>Thread Safety:</b> This class is not thread-safe and must only be used
 * from the JavaFX Application Thread.</p>
 * 
 * @author fxShield Team
 * @version 2.0
 */
public abstract class BaseDialog {

    // ========== BLUR MANAGEMENT ==========
    
    /**
     * Reference counter for blur effect management.
     * Tracks how many dialogs are currently applying blur to prevent
     * premature blur removal when multiple dialogs are stacked.
     */
    private static int blurReferenceCount = 0;

    // ========== INSTANCE FIELDS ==========
    
    /** The dialog stage */
    protected final Stage stage;
    
    /** The owner stage (can be null) */
    protected final Stage owner;
    
    /** The root node of the owner's scene (for blur effect) */
    private final Node ownerRoot;
    
    /** The previous effect applied to the owner root (restored on close) */
    private final Effect previousOwnerEffect;
    
    /** The root pane containing the dialog content */
    protected Pane root;
    
    /** Flag to prevent multiple close operations */
    private boolean closing = false;
    
    /** Dialog width */
    private final double width;
    
    /** Dialog height */
    private final double height;
    
    /** Fade-in duration */
    private Duration fadeInDuration = Duration.millis(180);
    
    /** Fade-out duration */
    private Duration fadeOutDuration = Duration.millis(150);
    
    /** Whether ESC key closes the dialog */
    private boolean escapeClosesDialog = true;

    // ========== CONSTRUCTOR ==========
    
    /**
     * Creates a new base dialog with the specified owner and dimensions.
     * 
     * @param owner the owner stage (can be null for standalone dialogs)
     * @param width the dialog width in pixels
     * @param height the dialog height in pixels
     */
    protected BaseDialog(Stage owner, double width, double height) {
        this.owner = owner;
        this.width = width;
        this.height = height;
        
        // Create stage
        this.stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
        
        // Apply blur to owner and save previous effect
        ownerRoot = (owner != null && owner.getScene() != null) 
                ? owner.getScene().getRoot() 
                : null;
        previousOwnerEffect = (ownerRoot != null) ? ownerRoot.getEffect() : null;
        
        if (ownerRoot != null) {
            // Only apply blur if this is the first dialog
            if (blurReferenceCount == 0) {
                ownerRoot.setEffect(new GaussianBlur(18));
            }
            blurReferenceCount++;
        }
        
        // Setup close handlers
        stage.setOnCloseRequest(e -> close());
        stage.setOnHidden(e -> restoreOwnerBlur());
    }

    // ========== ABSTRACT METHODS ==========
    
    /**
     * Builds and returns the content pane for this dialog.
     * This method is called once during dialog initialization.
     * Subclasses must implement this to provide their specific UI.
     * 
     * @return the root pane containing the dialog's content
     */
    protected abstract Pane buildContent();

    // ========== INITIALIZATION ==========
    
    /**
     * Initializes the dialog by building content, setting up the scene,
     * and configuring animations. This method should be called by subclasses
     * after construction and before showing the dialog.
     */
    protected void initialize() {
        // Build content from subclass
        root = buildContent();
        
        // Create scene
        Scene scene = new Scene(root, width, height);
        scene.setFill(Color.TRANSPARENT);
        
        // ESC key handling
        if (escapeClosesDialog) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    close();
                }
            });
        }
        
        stage.setScene(scene);
        
        // Center on owner or screen
        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - width) / 2.0);
            stage.setY(owner.getY() + (owner.getHeight() - height) / 2.0);
        } else {
            stage.centerOnScreen();
        }
        
        // Setup fade-in animation
        root.setOpacity(0);
        FadeTransition fadeIn = fadeIn(root, fadeInDuration);
        fadeIn.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        
        // Play fade-in when stage is shown
        stage.setOnShown(e -> fadeIn.play());
    }

    // ========== SHOW/HIDE METHODS ==========
    
    /**
     * Shows the dialog and blocks until it is closed.
     * This method initializes the dialog if not already initialized.
     */
    public void showAndWait() {
        if (root == null) {
            initialize();
        }
        stage.showAndWait();
    }
    
    /**
     * Shows the dialog without blocking.
     * This method initializes the dialog if not already initialized.
     */
    public void show() {
        if (root == null) {
            initialize();
        }
        stage.show();
    }
    
    /**
     * Closes the dialog with a smooth fade-out animation.
     * This method is idempotent and safe to call multiple times.
     */
    public void close() {
        if (closing) return;
        closing = true;
        
        // Fade out and close
        FadeTransition fadeOut = fadeOut(root, fadeOutDuration);
        fadeOut.setOnFinished(e -> {
            restoreOwnerBlur();
            stage.close();
        });
        fadeOut.play();
    }
    
    /**
     * Closes the dialog immediately without animation.
     * Use this for emergency cleanup or when animation is not desired.
     */
    public void closeImmediately() {
        if (closing) return;
        closing = true;
        restoreOwnerBlur();
        stage.close();
    }

    // ========== BLUR MANAGEMENT ==========
    
    /**
     * Restores the owner window's blur effect.
     * Uses reference counting to ensure blur is only removed when
     * all dialogs have been closed.
     */
    private void restoreOwnerBlur() {
        if (ownerRoot != null) {
            blurReferenceCount = Math.max(0, blurReferenceCount - 1);
            // Only restore effect if this was the last dialog
            if (blurReferenceCount == 0) {
                ownerRoot.setEffect(previousOwnerEffect);
            }
        }
    }

    // ========== CONFIGURATION METHODS ==========
    
    /**
     * Sets the fade-in animation duration.
     * Must be called before {@link #initialize()} to take effect.
     * 
     * @param duration the fade-in duration
     */
    protected void setFadeInDuration(Duration duration) {
        this.fadeInDuration = duration;
    }
    
    /**
     * Sets the fade-out animation duration.
     * 
     * @param duration the fade-out duration
     */
    protected void setFadeOutDuration(Duration duration) {
        this.fadeOutDuration = duration;
    }
    
    /**
     * Sets whether the ESC key closes the dialog.
     * Must be called before {@link #initialize()} to take effect.
     * 
     * @param escapeCloses true if ESC should close the dialog
     */
    protected void setEscapeClosesDialog(boolean escapeCloses) {
        this.escapeClosesDialog = escapeCloses;
    }
    
    /**
     * Sets the dialog title.
     * 
     * @param title the dialog title
     */
    protected void setTitle(String title) {
        stage.setTitle(title);
    }

    // ========== ACCESSORS ==========
    
    /**
     * Returns the dialog stage.
     * 
     * @return the stage
     */
    public Stage getStage() {
        return stage;
    }
    
    /**
     * Returns the owner stage.
     * 
     * @return the owner stage (can be null)
     */
    public Stage getOwner() {
        return owner;
    }
    
    /**
     * Returns the root pane.
     * 
     * @return the root pane (null if not yet initialized)
     */
    public Pane getRoot() {
        return root;
    }
    
    /**
     * Returns whether the dialog is currently closing.
     * 
     * @return true if the dialog is in the process of closing
     */
    public boolean isClosing() {
        return closing;
    }

    // ========== UTILITY METHODS ==========
    
    /**
     * Executes a runnable on the JavaFX Application Thread.
     * If already on the FX thread, executes immediately.
     * 
     * @param action the action to execute
     */
    protected void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
    
    /**
     * Executes a runnable on the JavaFX Application Thread after a delay.
     * 
     * @param action the action to execute
     * @param delayMillis the delay in milliseconds
     */
    protected void runOnFxThreadDelayed(Runnable action, long delayMillis) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Platform.runLater(action);
        }, "BaseDialog-Delayed").start();
    }

    /**
     * Creates a fade-in animation for the specified node.
     * 
     * @param node the node to animate
     * @param duration the animation duration
     * @return the configured FadeTransition
     */
    protected static FadeTransition fadeIn(Node node, Duration duration) {
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);
        return fade;
    }

    /**
     * Creates a fade-out animation for the specified node.
     * 
     * @param node the node to animate
     * @param duration the animation duration
     * @return the configured FadeTransition
     */
    protected static FadeTransition fadeOut(Node node, Duration duration) {
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_IN);
        return fade;
    }

    /**
     * Stops an animation safely, handling null values.
     * 
     * @param animation the animation to stop (can be null)
     */
    protected static void stopSafely(Animation animation) {
        if (animation != null) {
            try {
                animation.stop();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Creates a pause transition with a callback after completion.
     * 
     * @param duration the pause duration
     * @param onFinished the callback to execute when the pause completes
     * @return the configured PauseTransition
     */
    protected static PauseTransition pauseThen(Duration duration, Runnable onFinished) {
        PauseTransition pause = new PauseTransition(duration);
        if (onFinished != null) {
            pause.setOnFinished(e -> onFinished.run());
        }
        return pause;
    }
}
