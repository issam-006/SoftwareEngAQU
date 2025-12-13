package fxShield;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Popup;
import javafx.util.Duration;

public class TopBarIcons {

    private final HBox root;

    private final Button bellButton;
    private final Button userButton;
    private final Button settingsButton;

    private Popup notificationPopup;
    private VBox messagesBox;

    private int messageCount = 5;
    private final Label countBadge;

    // ===== Styles =====
    private static final String ICON_BTN =
            "-fx-background-color: transparent;" +
                    "-fx-text-fill: white;" +
                    "-fx-cursor: hand;";

    private static final String ICON_BTN_HOVER =
            "-fx-background-color: rgba(255,255,255,0.12);" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 8;";

    public TopBarIcons() {

        root = new HBox(14);
        root.setAlignment(Pos.CENTER_RIGHT);
        root.setPadding(new Insets(6, 20, 6, 6));

        bellButton = createIconButton("🔔");
        userButton = createIconButton("👤");
        settingsButton = createIconButton("⚙");

        // ===== Badge =====
        countBadge = new Label(String.valueOf(messageCount));
        countBadge.setFont(Font.font(10));
        countBadge.setTextFill(Color.WHITE);
        countBadge.setStyle("-fx-background-color: #ff3b30; -fx-background-radius: 20;");
        countBadge.setPadding(new Insets(2));
        countBadge.setVisible(messageCount > 0);
        countBadge.setManaged(false);

        StackPane bellContainer = new StackPane(bellButton, countBadge);
        StackPane.setAlignment(countBadge, Pos.TOP_RIGHT);
        countBadge.setTranslateX(8);
        countBadge.setTranslateY(-2);

        bellButton.setOnAction(e -> togglePopup(bellButton));

        root.getChildren().addAll(bellContainer, userButton, settingsButton);
    }

    // ======================================================
    //                  ICON BUTTON
    // ======================================================

    private Button createIconButton(String icon) {
        Button btn = new Button(icon);
        btn.setFont(Font.font("Segoe UI Emoji", 20));
        btn.setStyle(ICON_BTN);

        btn.setOnMouseEntered(e -> btn.setStyle(ICON_BTN_HOVER));
        btn.setOnMouseExited(e -> btn.setStyle(ICON_BTN));

        return btn;
    }

    // ======================================================
    //                  POPUP
    // ======================================================

    private void togglePopup(Node anchor) {
        if (notificationPopup != null && notificationPopup.isShowing()) {
            notificationPopup.hide();
            return;
        }
        showPopup(anchor);
    }

    private void showPopup(Node anchor) {

        if (notificationPopup != null) {
            notificationPopup.hide();
        }

        notificationPopup = new Popup();

        VBox box = new VBox(20);
        box.setPadding(new Insets(22));
        box.setPrefSize(320, 450);
        box.setOpacity(0);

        box.setStyle(
                "-fx-background-color: rgba(20,20,30,0.96);" +
                        "-fx-background-radius: 22;" +
                        "-fx-border-color: rgba(255,255,255,0.08);" +
                        "-fx-border-width: 1.2;" +
                        "-fx-border-radius: 22;"
        );

        // ===== Header =====
        HBox header = new HBox();
        Label title = new Label("Allow Notifications");
        title.setFont(Font.font("Segoe UI", 20));
        title.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setFont(Font.font(16));
        closeBtn.setStyle(ICON_BTN);
        closeBtn.setOnAction(e -> notificationPopup.hide());

        header.getChildren().addAll(title, spacer, closeBtn);

        // ===== Switch =====
        HBox notifRow = new HBox(14);
        notifRow.setAlignment(Pos.CENTER_LEFT);

        Label notifLabel = new Label("Notifications");
        notifLabel.setFont(Font.font(15));
        notifLabel.setTextFill(Color.WHITE);

        boolean[] state = {false};
        HBox switchControl = createIOSSwitch(state);

        notifRow.getChildren().addAll(notifLabel, switchControl);

        // ===== Messages =====
        Label msgTitle = new Label("Messages");
        msgTitle.setFont(Font.font(17));
        msgTitle.setTextFill(Color.WHITE);

        Button clearBtn = new Button("Clear Messages");
        clearBtn.setFont(Font.font(13));
        clearBtn.setTextFill(Color.WHITE);
        clearBtn.setStyle("-fx-background-color: rgba(255,255,255,0.12); -fx-background-radius: 20;");
        clearBtn.setDisable(messageCount == 0);

        messagesBox = new VBox(12);
        for (int i = 1; i <= messageCount; i++) {
            Label msg = new Label("• Notification message " + i);
            msg.setFont(Font.font(14));
            msg.setTextFill(Color.web("#d4d7dd"));
            messagesBox.getChildren().add(msg);
        }

        ScrollPane scrollPane = new ScrollPane(messagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(240);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");

        clearBtn.setOnAction(e -> clearMessages(clearBtn));

        box.getChildren().addAll(header, notifRow, msgTitle, clearBtn, scrollPane);
        notificationPopup.getContent().add(box);

        Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        notificationPopup.show(anchor, b.getMinX() - 330, b.getMaxY() + 8);

        // Fade in
        FadeTransition fade = new FadeTransition(Duration.millis(180), box);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private void clearMessages(Button clearBtn) {
        if (messagesBox.getChildren().isEmpty()) return;

        ParallelTransition all = new ParallelTransition();

        for (Node msg : messagesBox.getChildren()) {
            FadeTransition fade = new FadeTransition(Duration.millis(300), msg);
            fade.setToValue(0);

            TranslateTransition slide = new TranslateTransition(Duration.millis(300), msg);
            slide.setByY(20);

            all.getChildren().add(new ParallelTransition(fade, slide));
        }

        all.setOnFinished(ev -> {
            messagesBox.getChildren().clear();
            messageCount = 0;
            countBadge.setVisible(false);
            clearBtn.setDisable(true);
        });

        all.play();
    }

    // ======================================================
    //                 IOS SWITCH
    // ======================================================

    private HBox createIOSSwitch(boolean[] state) {

        StackPane track = new StackPane();
        track.setPrefSize(48, 26);
        track.setStyle("-fx-background-color: #3f4860; -fx-background-radius: 30;");

        Circle knob = new Circle(10, Color.WHITE);
        knob.setTranslateX(-12);

        track.getChildren().add(knob);

        track.setOnMouseClicked(e -> {
            state[0] = !state[0];

            TranslateTransition tt = new TranslateTransition(Duration.millis(180), knob);
            tt.setToX(state[0] ? 12 : -12);
            tt.play();

            track.setStyle(
                    state[0]
                            ? "-fx-background-color: #4b73ff; -fx-background-radius: 30;"
                            : "-fx-background-color: #3f4860; -fx-background-radius: 30;"
            );
        });

        return new HBox(track);
    }

    public Node getRoot() {
        return root;
    }
}
