package fxShield.WIN;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Small utilities for working with JavaFX stages/owners safely.
 * - Resolve owner Stage from any child Node
 * - Center/Clamp windows to visible screen
 * - Apply/restore blur on owner via AutoCloseable guard
 * - Helpers to show modal dialogs over an owner
 */
public final class StageUtil {

    private StageUtil() {
    }

    // -------- Owner resolution --------

    public static Optional<Stage> findOwner(Node anyNodeInsideStage) {
        if (anyNodeInsideStage == null || anyNodeInsideStage.getScene() == null) return Optional.empty();
        var w = anyNodeInsideStage.getScene().getWindow();
        return (w instanceof Stage s) ? Optional.of(s) : Optional.empty();
    }

    public static void withOwner(Node anyNodeInsideStage, Consumer<Stage> consumer) {
        if (consumer == null) return;
        findOwner(anyNodeInsideStage).ifPresent(consumer);
    }

    public static <T> T mapOwner(Node node, Function<Stage, T> fn, T fallback) {
        if (fn == null) return fallback;
        return findOwner(node).map(fn).orElse(fallback);
    }

    // -------- Positioning --------

    public static void centerOnOwner(Stage child, Stage owner) {
        if (child == null || owner == null) return;

        // Ensure sizes are computed
        if (child.getWidth() <= 1 || child.getHeight() <= 1) {
            child.sizeToScene();
        }

        double x = owner.getX() + (owner.getWidth() - child.getWidth()) / 2.0;
        double y = owner.getY() + (owner.getHeight() - child.getHeight()) / 2.0;

        child.setX(x);
        child.setY(y);

        clampToScreen(child);
    }

    public static void clampToScreen(Stage stage) {
        if (stage == null) return;

        Rectangle2D vb = Screen.getScreensForRectangle(
                        stage.getX(), stage.getY(),
                        Math.max(1, stage.getWidth()), Math.max(1, stage.getHeight()))
                .stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();

        double x = stage.getX();
        double y = stage.getY();
        double w = stage.getWidth();
        double h = stage.getHeight();

        // If the stage is bigger than the visible area, shrink it (actual stage size)
        if (w > vb.getWidth()) {
            w = vb.getWidth();
            stage.setWidth(w);
        }
        if (h > vb.getHeight()) {
            h = vb.getHeight();
            stage.setHeight(h);
        }

        if (x < vb.getMinX()) x = vb.getMinX();
        if (y < vb.getMinY()) y = vb.getMinY();
        if (x + w > vb.getMaxX()) x = vb.getMaxX() - w;
        if (y + h > vb.getMaxY()) y = vb.getMaxY() - h;

        stage.setX(x);
        stage.setY(y);
    }

    // -------- Modal helpers --------

    public static void showModalOver(Node anchor, Stage dialog) {
        Objects.requireNonNull(dialog, "dialog");

        withOwner(anchor, owner -> {
            dialog.initOwner(owner);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.sizeToScene();
            centerOnOwner(dialog, owner);
            dialog.show();
        });
    }

    // -------- Blur guard --------

    public static BlurGuard applyBlur(Stage owner, double radius) {
        if (owner == null || owner.getScene() == null || owner.getScene().getRoot() == null) {
            return BlurGuard.noop();
        }

        var root = owner.getScene().getRoot();
        Effect prev = root.getEffect();

        runFx(() -> root.setEffect(new GaussianBlur(Math.max(0, radius))));
        return new BlurGuard(owner, prev);
    }

    public static final class BlurGuard implements AutoCloseable {
        private final Stage owner;
        private final Effect previous;
        private boolean closed;

        private BlurGuard(Stage owner, Effect previous) {
            this.owner = owner;
            this.previous = previous;
        }

        static BlurGuard noop() {
            return new BlurGuard(null, null);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;

            if (owner == null || owner.getScene() == null || owner.getScene().getRoot() == null) return;
            runFx(() -> owner.getScene().getRoot().setEffect(previous));
        }
    }

    // -------- Thread helper --------

    public static void runFx(Runnable r) {
        if (r == null) return;
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }
}
