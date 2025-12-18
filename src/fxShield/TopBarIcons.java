package fxShield;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

public class TopBarIcons {

    private static final int CIRCLE_SIZE = 38;

    // Fonts (reuse to avoid allocations)
    private static final Font FONT_UI_BOLD = Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 18);
    private static final Font FONT_EMOJI = Font.font("Segoe UI Emoji", 18);

    // Button base
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

    private final HBox root;

    private final Button infoButton;      // !
    private final Button settingsButton;  // ⚙
    private final Button minButton;       // -
    private final Button maxButton;       // ▢
    private final Button closeButton;     // ✕

    public TopBarIcons() {
        root = new HBox(12);
        root.setAlignment(Pos.CENTER_RIGHT);
        root.setPadding(new Insets(6, 20, 6, 6));

        // Info
        infoButton = new Button("!");
        prepareButton(infoButton, FONT_UI_BOLD);
        StackPane infoCircle = wrapInCircle(infoButton);
        infoCircle.setOnMouseClicked(e ->
                StageUtil.withOwner(infoCircle, owner -> DeviceInfoDialog.show(owner))
        );

        // Settings
        settingsButton = new Button("⚙");
        prepareButton(settingsButton, FONT_EMOJI);
        StackPane settingsCircle = wrapInCircle(settingsButton);
        settingsCircle.setOnMouseClicked(e ->
                StageUtil.withOwner(settingsCircle, owner -> SettingsDialog.show(owner))
        );

        // Minimize
        minButton = new Button("−");
        prepareButton(minButton, FONT_UI_BOLD);
        StackPane minCircle = wrapInCircle(minButton);
        minCircle.setOnMouseClicked(e ->
                StageUtil.withOwner(minCircle, owner -> owner.setIconified(true))
        );

        // Maximize / Toggle Fullscreen
        maxButton = new Button("▢");
        prepareButton(maxButton, FONT_UI_BOLD);
        StackPane maxCircle = wrapInCircle(maxButton);
        maxCircle.setOnMouseClicked(e ->
                StageUtil.withOwner(maxCircle, owner -> {
                    owner.setFullScreen(!owner.isFullScreen());
                })
        );

        // Sync button text with full screen state
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((obsW, oldW, newW) -> {
                    if (newW instanceof Stage stage) {
                        stage.fullScreenProperty().addListener((obsFs, oldFs, newFs) -> {
                            maxButton.setText(newFs ? "❐" : "▢");
                        });
                        // Initial state
                        maxButton.setText(stage.isFullScreen() ? "❐" : "▢");
                    }
                });
            }
        });

        // Close
        closeButton = new Button("✕");
        prepareButton(closeButton, FONT_UI_BOLD);
        StackPane closeCircle = wrapInCircle(closeButton);
        closeCircle.setStyle(CIRCLE_NORMAL + "-fx-background-color: rgba(248, 113, 113, 0.15);");
        closeCircle.setOnMouseEntered(e -> closeCircle.setStyle(CIRCLE_HOVER + "-fx-background-color: rgba(248, 113, 113, 0.3);"));
        closeCircle.setOnMouseExited(e -> closeCircle.setStyle(CIRCLE_NORMAL + "-fx-background-color: rgba(248, 113, 113, 0.15);"));
        closeCircle.setOnMouseClicked(e -> {
            StageUtil.withOwner(closeCircle, owner -> {
                // Trigger close request so DashBoardPage's onCloseRequest runs
                owner.fireEvent(new javafx.stage.WindowEvent(owner, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                javafx.application.Platform.exit();
                System.exit(0);
            });
        });

        root.getChildren().addAll(infoCircle, settingsCircle, minCircle, maxCircle, closeCircle);
    }

    private static void prepareButton(Button b, Font font) {
        b.setFont(font);
        b.setTextFill(Color.WHITE);
        b.setStyle(ICON_BTN);
        b.setFocusTraversable(false);
        // Let wrapper handle mouse events so clicks anywhere in circle work
        b.setMouseTransparent(true);
    }

    private static StackPane wrapInCircle(Button inner) {
        StackPane wrap = new StackPane(inner);
        wrap.setAlignment(Pos.CENTER);
        wrap.setMinSize(CIRCLE_SIZE, CIRCLE_SIZE);
        wrap.setPrefSize(CIRCLE_SIZE, CIRCLE_SIZE);
        wrap.setMaxSize(CIRCLE_SIZE, CIRCLE_SIZE);
        wrap.setPickOnBounds(true);

        wrap.setStyle(CIRCLE_NORMAL);
        wrap.setOnMouseEntered(e -> wrap.setStyle(CIRCLE_HOVER));
        wrap.setOnMouseExited(e -> wrap.setStyle(CIRCLE_NORMAL));

        return wrap;
    }

    public java.util.List<Node> getInteractiveNodes() {
        java.util.List<Node> list = new java.util.ArrayList<>();
        if (infoButton != null) list.add(infoButton.getParent());
        if (settingsButton != null) list.add(settingsButton.getParent());
        if (minButton != null) list.add(minButton.getParent());
        if (maxButton != null) list.add(maxButton.getParent());
        if (closeButton != null) list.add(closeButton.getParent());
        return list;
    }

    public Node getRoot() {
        return root;
    }

    public Node getMaximizeButton() {
        return maxButton.getParent(); // The StackPane wrapper
    }
}