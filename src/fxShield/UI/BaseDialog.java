package fxShield.UI;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
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

/**
 * Base class for modal dialogs in fxShield.
 * Handles:
 * - owner blur (reference counted)
 * - fade-in / fade-out
 * - ESC close
 * - safe close lifecycle
 */
public abstract class BaseDialog {

    private static final double DEFAULT_BLUR_RADIUS = 18.0;

    // Reference counter for stacked dialogs blur.
    private static int blurReferenceCount = 0;

    protected final Stage stage;
    protected final Stage owner;

    private final Node ownerRoot;
    private final Effect previousOwnerEffect;

    protected Pane root;

    private boolean closing;

    private final double width;
    private final double height;

    private Duration fadeInDuration = Duration.millis(180);
    private Duration fadeOutDuration = Duration.millis(150);

    private boolean escapeClosesDialog = true;

    protected BaseDialog(Stage owner, double width, double height) {
        this.owner = owner;
        this.width = width;
        this.height = height;

        this.stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);

        ownerRoot = (owner != null && owner.getScene() != null) ? owner.getScene().getRoot() : null;
        previousOwnerEffect = (ownerRoot != null) ? ownerRoot.getEffect() : null;

        applyOwnerBlurIfNeeded();

        stage.setOnCloseRequest(e -> {
            e.consume(); // important: let us animate close
            close();
        });

        // Single place to restore blur (avoid double decrement)
        stage.setOnHidden(e -> restoreOwnerBlur());
    }

    protected abstract Pane buildContent();

    protected void initialize() {
        root = buildContent();

        Scene scene = new Scene(root, width, height);
        scene.setFill(Color.TRANSPARENT);

        if (escapeClosesDialog) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) close();
            });
        }

        stage.setScene(scene);

        // Positioning
        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - width) / 2.0);
            stage.setY(owner.getY() + (owner.getHeight() - height) / 2.0);
        } else {
            stage.centerOnScreen();
        }

        // Fade in
        root.setOpacity(0.0);
        FadeTransition fadeIn = fadeIn(root, fadeInDuration);

        stage.setOnShown(e -> fadeIn.playFromStart());
    }

    public void showAndWait() {
        if (root == null) initialize();
        stage.showAndWait();
    }

    public void show() {
        if (root == null) initialize();
        stage.show();
    }

    public void close() {
        if (closing) return;
        closing = true;

        // If not initialized or already not showing, just hide safely.
        if (root == null || !stage.isShowing()) {
            stage.hide();
            return;
        }

        FadeTransition fadeOut = fadeOut(root, fadeOutDuration);
        fadeOut.setOnFinished(e -> stage.hide());
        fadeOut.playFromStart();
    }

    public void closeImmediately() {
        if (closing) return;
        closing = true;
        stage.hide();
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

    protected void setFadeInDuration(Duration duration) {
        if (duration != null) this.fadeInDuration = duration;
    }

    protected void setFadeOutDuration(Duration duration) {
        if (duration != null) this.fadeOutDuration = duration;
    }

    protected void setEscapeClosesDialog(boolean escapeCloses) {
        this.escapeClosesDialog = escapeCloses;
    }

    protected void setTitle(String title) {
        stage.setTitle(title);
    }

    public Stage getStage() { return stage; }
    public Stage getOwner() { return owner; }
    public Pane getRoot() { return root; }
    public boolean isClosing() { return closing; }

    protected void runOnFxThread(Runnable action) {
        if (action == null) return;
        if (Platform.isFxApplicationThread()) action.run();
        else Platform.runLater(action);
    }

    protected void runOnFxThreadDelayed(Runnable action, long delayMillis) {
        if (action == null) return;

        runOnFxThread(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(Math.max(0, delayMillis)));
            pause.setOnFinished(e -> action.run());
            pause.playFromStart();
        });
    }

    protected static FadeTransition fadeIn(Node node, Duration duration) {
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);
        return fade;
    }

    protected static FadeTransition fadeOut(Node node, Duration duration) {
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_IN);
        return fade;
    }

    protected static void stopSafely(Animation animation) {
        if (animation == null) return;
        try { animation.stop(); } catch (Exception ignored) {}
    }

    protected static PauseTransition pauseThen(Duration duration, Runnable onFinished) {
        PauseTransition pause = new PauseTransition(duration);
        if (onFinished != null) pause.setOnFinished(e -> onFinished.run());
        return pause;
    }
}
