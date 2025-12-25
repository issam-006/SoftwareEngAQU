package fxShield.UI;

import fxShield.WIN.StageUtil;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public final class TopBarIcons {

    private static final int CIRCLE_SIZE = 38;

    // Fonts (reuse to avoid allocations)
    private static final Font FONT_UI_BOLD = Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 18);
    private static final Font FONT_EMOJI   = Font.font("Segoe UI Emoji", 18);

    // Button base (inner Button, wrapper handles the clicks)
    private static final String ICON_BTN =
            "-fx-background-color: transparent;" +
                    "-fx-text-fill: white;" +
                    "-fx-padding: 0;";

    // Circle styles
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

    // SVG paths
    private static final String MIN_PATH     = "M 6 18 L 18 18";
    private static final String MAX_PATH     = "M 4 4 H 20 V 20 H 4 Z";
    private static final String RESTORE_PATH = "M 4 4 H 16 V 16 H 4 Z";
    private static final String CLOSE_PATH   = "M 6 6 L 18 18 M 18 6 L 6 18";

    // Close styles
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

    private final Button infoButton;       // !
    private final Button settingsButton;   // ⚙

    private final StackPane minWrapper;
    private final SVGPath maxIcon;
    private final StackPane maxWrapper;
    private final StackPane closeWrapper;

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

        minWrapper = createIconWrapper(minIcon);
        minWrapper.setOnMouseClicked(e ->
                StageUtil.withOwner(minWrapper, owner -> owner.setIconified(true))
        );

        // -------- Fullscreen Toggle (instead of maximize hacks) --------
        maxIcon = new SVGPath();
        maxIcon.setContent(MAX_PATH);
        maxIcon.setStyle(ICON_STYLE);

        maxWrapper = createIconWrapper(maxIcon);
        maxWrapper.setOnMouseClicked(e ->
                StageUtil.withOwner(maxWrapper, owner -> toggleFullscreen(owner))
        );

        // keep icon synced when user changes fullscreen elsewhere
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.windowProperty().addListener((obsW, oldW, newW) -> {
                if (!(newW instanceof Stage stage)) return;
                stage.fullScreenProperty().addListener((o2, was, is) -> {
                    maxIcon.setContent(is ? RESTORE_PATH : MAX_PATH);
                });
                maxIcon.setContent(stage.isFullScreen() ? RESTORE_PATH : MAX_PATH);
            });
        });

        // -------- Close --------
        SVGPath closeIcon = new SVGPath();
        closeIcon.setContent(CLOSE_PATH);
        closeIcon.setStyle(ICON_STYLE);

        closeWrapper = createIconWrapper(closeIcon);
        closeWrapper.setStyle(CLOSE_NORMAL);
        closeWrapper.setOnMouseEntered(e -> closeWrapper.setStyle(CLOSE_HOVER));
        closeWrapper.setOnMouseExited(e -> closeWrapper.setStyle(CLOSE_NORMAL));
        closeWrapper.setOnMousePressed(e -> closeWrapper.setStyle(CLOSE_PRESSED));
        closeWrapper.setOnMouseReleased(e -> closeWrapper.setStyle(CLOSE_HOVER));
        closeWrapper.setOnMouseClicked(e -> {
            StageUtil.withOwner(closeWrapper, owner -> {
                owner.fireEvent(new WindowEvent(owner, WindowEvent.WINDOW_CLOSE_REQUEST));
                Platform.exit();
                System.exit(0);
            });
        });

        root.getChildren().addAll(infoCircle, settingsCircle, minWrapper, maxWrapper, closeWrapper);
    }

    private void toggleFullscreen(Stage owner) {
        if (owner == null) return;

        boolean newFs = !owner.isFullScreen();

        RotateTransition rt = new RotateTransition(Duration.millis(200), maxIcon);
        rt.setByAngle(360);
        rt.setInterpolator(Interpolator.EASE_BOTH);
        rt.setCycleCount(1);
        rt.play();

        owner.setFullScreen(newFs);
        owner.setFullScreenExitHint("");
        maxIcon.setContent(newFs ? RESTORE_PATH : MAX_PATH);
    }

    private static void prepareInnerButton(Button b, Font font) {
        b.setFont(font);
        b.setTextFill(Color.WHITE);
        b.setStyle(ICON_BTN);
        b.setFocusTraversable(false);

        // Important: wrapper handles clicks so the whole circle is clickable
        b.setMouseTransparent(true);
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
        wrap.setOnMouseReleased(e -> wrap.setStyle(CIRCLE_HOVER));

        return wrap;
    }

    private static StackPane createIconWrapper(Node icon) {
        StackPane wrap = new StackPane(icon);
        wrap.setAlignment(Pos.CENTER);
        wrap.setMinSize(CIRCLE_SIZE, CIRCLE_SIZE);
        wrap.setPrefSize(CIRCLE_SIZE, CIRCLE_SIZE);
        wrap.setMaxSize(CIRCLE_SIZE, CIRCLE_SIZE);
        wrap.setPickOnBounds(true);

        wrap.setStyle(CIRCLE_NORMAL);
        wrap.setOnMouseEntered(e -> wrap.setStyle(CIRCLE_HOVER));
        wrap.setOnMouseExited(e -> wrap.setStyle(CIRCLE_NORMAL));
        wrap.setOnMousePressed(e -> wrap.setStyle(CIRCLE_PRESSED));
        wrap.setOnMouseReleased(e -> wrap.setStyle(CIRCLE_HOVER));

        return wrap;
    }

    // ---- API ----
    public Node getRoot() {
        return root;
    }

    public Node getMaximizeButton() {
        return maxWrapper;
    }

    public List<Node> getInteractiveNodes() {
        List<Node> list = new ArrayList<>();
        if (infoButton != null && infoButton.getParent() != null) list.add((Node) infoButton.getParent());
        if (settingsButton != null && settingsButton.getParent() != null) list.add((Node) settingsButton.getParent());
        if (minWrapper != null) list.add(minWrapper);
        if (maxWrapper != null) list.add(maxWrapper);
        if (closeWrapper != null) list.add(closeWrapper);
        return list;
    }
}
