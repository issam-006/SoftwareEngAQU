package fxShield.UI;

import fxShield.WIN.StageUtil;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TopBarIcons {

    private static final int CIRCLE_SIZE = 38;

    private static final Font FONT_UI_BOLD = StyleConstants.FONT_EXTRA_BOLD_18;
    private static final Font FONT_EMOJI   = StyleConstants.FONT_EMOJI_18;

    private static final String ICON_BTN =
            "-fx-background-color: transparent;" +
                    "-fx-text-fill: white;" +
                    "-fx-padding: 0;";

    private static final String CIRCLE_BASE =
            "-fx-background-radius: 999;" +
                    "-fx-border-width: 1;" +
                    "-fx-border-radius: 999;" +
                    "-fx-cursor: hand;";

    private static final String CIRCLE_NORMAL =
            CIRCLE_BASE +
                    "-fx-background-color: rgba(255,255,255,0.10);" +
                    "-fx-border-color: rgba(255,255,255,0.14);";

    private static final String CIRCLE_HOVER =
            CIRCLE_BASE +
                    "-fx-background-color: rgba(255,255,255,0.14);" +
                    "-fx-border-color: rgba(147,197,253,0.50);";

    private static final String CIRCLE_PRESSED =
            CIRCLE_BASE +
                    "-fx-background-color: rgba(255,255,255,0.22);" +
                    "-fx-border-color: rgba(147,197,253,0.60);";

    private static final String ICON_STYLE =
            "-fx-fill: none; -fx-stroke: white; -fx-stroke-width: 2.2; " +
                    "-fx-stroke-linecap: round; -fx-stroke-linejoin: round;";

    private static final String MIN_PATH       = "M 6 18 L 18 18";
    private static final String MAX_PATH       = "M 4 4 H 20 V 20 H 4 Z";
    private static final String RESTORE_PATH   = "M 7 5 H 20 V 18 H 7 Z M 4 8 H 17 V 21 H 4 Z";
    private static final String CLOSE_PATH     = "M 6 6 L 18 18 M 18 6 L 6 18";

    private static final String CLOSE_NORMAL =
            CIRCLE_BASE +
                    "-fx-background-color: rgba(248, 113, 113, 0.15);" +
                    "-fx-border-color: rgba(255,255,255,0.14);";

    private static final String CLOSE_HOVER =
            CIRCLE_BASE +
                    "-fx-background-color: rgba(248, 113, 113, 0.30);" +
                    "-fx-border-color: rgba(248, 113, 113, 0.60);";

    private static final String CLOSE_PRESSED =
            CIRCLE_BASE +
                    "-fx-background-color: rgba(248, 113, 113, 0.40);" +
                    "-fx-border-color: rgba(248, 113, 113, 0.80);";

    private final HBox root;

    private final Button infoButton;
    private final Button settingsButton;

    private final StackPane minWrapper;
    private final SVGPath maxIcon;
    private final StackPane maxWrapper;
    private final StackPane closeWrapper;

    private final List<Node> interactiveNodes;

    private Stage boundStage;

    public TopBarIcons() {
        root = new HBox(12);
        root.setAlignment(Pos.CENTER_RIGHT);
        root.setPadding(new Insets(6, 20, 6, 6));

        // -------- Info --------
        infoButton = new Button("!");
        prepareInnerButton(infoButton, FONT_UI_BOLD);
        StackPane infoCircle = wrapButtonInCircle(infoButton);
        infoCircle.setOnMouseClicked(e ->
                StageUtil.withOwner(infoCircle, owner -> DeviceInfoDialog.show(owner))
        );

        // -------- Settings --------
        settingsButton = new Button("⚙");
        prepareInnerButton(settingsButton, FONT_EMOJI);
        StackPane settingsCircle = wrapButtonInCircle(settingsButton);
        settingsCircle.setOnMouseClicked(e ->
                StageUtil.withOwner(settingsCircle, owner -> SettingsDialog.show(owner))
        );

        // -------- Minimize --------
        SVGPath minIcon = new SVGPath();
        minIcon.setContent(MIN_PATH);
        minIcon.setStyle(ICON_STYLE);

        minWrapper = createIconWrapper(minIcon, CIRCLE_NORMAL, CIRCLE_HOVER, CIRCLE_PRESSED);
        minWrapper.setOnMouseClicked(e ->
                StageUtil.withOwner(minWrapper, owner -> owner.setIconified(true))
        );

        // -------- Maximize / Restore --------
        maxIcon = new SVGPath();
        maxIcon.setContent(MAX_PATH);
        maxIcon.setStyle(ICON_STYLE);

        maxWrapper = createIconWrapper(maxIcon, CIRCLE_NORMAL, CIRCLE_HOVER, CIRCLE_PRESSED);
        maxWrapper.setOnMouseClicked(e ->
                StageUtil.withOwner(maxWrapper, this::toggleMaximize)
        );

        bindStageWhenReady();

        // -------- Close --------
        SVGPath closeIcon = new SVGPath();
        closeIcon.setContent(CLOSE_PATH);
        closeIcon.setStyle(ICON_STYLE);

        closeWrapper = createIconWrapper(closeIcon, CLOSE_NORMAL, CLOSE_HOVER, CLOSE_PRESSED);
        closeWrapper.setOnMouseClicked(e ->
                StageUtil.withOwner(closeWrapper, owner -> {
                    owner.fireEvent(new WindowEvent(owner, WindowEvent.WINDOW_CLOSE_REQUEST));
                    if (owner.isShowing()) owner.close();
                })
        );

        root.getChildren().addAll(infoCircle, settingsCircle, minWrapper, maxWrapper, closeWrapper);

        ArrayList<Node> tmp = new ArrayList<>(5);
        tmp.add(infoCircle);
        tmp.add(settingsCircle);
        tmp.add(minWrapper);
        tmp.add(maxWrapper);
        tmp.add(closeWrapper);
        interactiveNodes = Collections.unmodifiableList(tmp);
    }

    private void bindStageWhenReady() {
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) unbindStage(oldScene.getWindow());
            if (newScene == null) return;

            Window w = newScene.getWindow();
            if (w instanceof Stage s) bindStage(s);

            newScene.windowProperty().addListener((o2, ow, nw) -> {
                if (ow instanceof Stage os) unbindStage(os);
                if (nw instanceof Stage ns) bindStage(ns);
            });
        });
    }

    private void bindStage(Stage stage) {
        if (stage == null || stage == boundStage) return;
        boundStage = stage;

        stage.maximizedProperty().addListener((o, was, is) -> syncMaxIcon(Boolean.TRUE.equals(is)));
        syncMaxIcon(stage.isMaximized());
    }

    private void unbindStage(Window w) {
        if (w == boundStage) boundStage = null;
    }

    private void syncMaxIcon(boolean maximized) {
        maxIcon.setContent(maximized ? RESTORE_PATH : MAX_PATH);
    }

    private void toggleMaximize(Stage owner) {
        if (owner == null) return;

        RotateTransition rt = new RotateTransition(Duration.millis(180), maxIcon);
        rt.setByAngle(360);
        rt.setInterpolator(Interpolator.EASE_BOTH);
        rt.playFromStart();

        boolean next = !owner.isMaximized();
        owner.setMaximized(next);
        syncMaxIcon(next);
    }

    private static void prepareInnerButton(Button b, Font font) {
        b.setFont(font);
        b.setTextFill(Color.WHITE);
        b.setStyle(ICON_BTN);
        b.setFocusTraversable(false);
        b.setMouseTransparent(true); // wrapper handles clicks
    }

    private static StackPane wrapButtonInCircle(Button inner) {
        StackPane wrap = new StackPane(inner);
        wrap.setAlignment(Pos.CENTER);
        wrap.setMinSize(CIRCLE_SIZE, CIRCLE_SIZE);
        wrap.setPrefSize(CIRCLE_SIZE, CIRCLE_SIZE);
        wrap.setMaxSize(CIRCLE_SIZE, CIRCLE_SIZE);
        wrap.setPickOnBounds(true);

        wrap.setStyle(CIRCLE_NORMAL);
        wrap.setOnMouseEntered(e -> wrap.setStyle(CIRCLE_HOVER));
        wrap.setOnMouseExited(e -> wrap.setStyle(CIRCLE_NORMAL));
        wrap.setOnMousePressed(e -> wrap.setStyle(CIRCLE_PRESSED));
        wrap.setOnMouseReleased(e -> wrap.setStyle(wrap.isHover() ? CIRCLE_HOVER : CIRCLE_NORMAL));

        return wrap;
    }

    private static StackPane createIconWrapper(Node icon, String normal, String hover, String pressed) {
        StackPane wrap = new StackPane(icon);
        wrap.setAlignment(Pos.CENTER);
        wrap.setMinSize(CIRCLE_SIZE, CIRCLE_SIZE);
        wrap.setPrefSize(CIRCLE_SIZE, CIRCLE_SIZE);
        wrap.setMaxSize(CIRCLE_SIZE, CIRCLE_SIZE);
        wrap.setPickOnBounds(true);

        wrap.setStyle(normal);
        wrap.setOnMouseEntered(e -> wrap.setStyle(hover));
        wrap.setOnMouseExited(e -> wrap.setStyle(normal));
        wrap.setOnMousePressed(e -> wrap.setStyle(pressed));
        wrap.setOnMouseReleased(e -> wrap.setStyle(wrap.isHover() ? hover : normal));

        return wrap;
    }

    // ---- API ----
    public Node getRoot() { return root; }

    public Node getMaximizeButton() { return maxWrapper; }

    public List<Node> getInteractiveNodes() { return interactiveNodes; }
}
