package fxShield;

import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Popup;
import javafx.util.Duration;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.ParallelTransition;
import javafx.util.Duration;

public class TopBarIcons {

    private final HBox root;

    private final Button bellButton;
    private final Button userButton;
    private final Button settingsButton;

    private Popup notificationPopup = null;

    // الرسائل لعرض العدد الحمر
    private int messageCount = 5;
    private Label countBadge;


    public TopBarIcons() {

        root = new HBox(14);
        root.setAlignment(Pos.CENTER_RIGHT);
        root.setPadding(new Insets(6, 20, 6, 6));

        bellButton = createIconButton("🔔");
        userButton = createIconButton("👤");
        settingsButton = createIconButton("⚙");

        // Badge (النقطة الحمراء)
        countBadge = new Label(String.valueOf(messageCount));
        countBadge.setFont(Font.font(10));
        countBadge.setTextFill(Color.WHITE);
        countBadge.setStyle("-fx-background-color: #ff3b30; -fx-background-radius: 20;");
        countBadge.setPadding(new Insets(2, 4, 2, 4));
        countBadge.setVisible(messageCount > 0);

        StackPane bellContainer = new StackPane(bellButton, countBadge);
        StackPane.setAlignment(countBadge, Pos.TOP_RIGHT);
        countBadge.setTranslateX(8);
        countBadge.setTranslateY(-2);

        bellButton.setOnAction(e -> togglePopup(bellButton));

        root.getChildren().addAll(bellContainer, userButton, settingsButton);
    }


    private Button createIconButton(String icon) {
        Button btn = new Button(icon);
        btn.setFont(Font.font("Segoe UI Emoji", 20));
        btn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: white; -fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: white; -fx-background-radius: 8;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"));
        return btn;
    }

    private void togglePopup(Node anchor) {
        if (notificationPopup != null && notificationPopup.isShowing()) {
            notificationPopup.hide();
            return;
        }
        showPopup(anchor);
    }


    private void showPopup(Node anchor) {

        if (notificationPopup != null)
            notificationPopup.hide();

        notificationPopup = new Popup();

        VBox box = new VBox(20);
        box.setPadding(new Insets(22));
        box.setPrefSize(320, 450);

        box.setStyle(
                "-fx-background-color: rgba(20,20,30,0.96);" +
                        "-fx-background-radius: 22;" +
                        "-fx-border-color: rgba(255,255,255,0.08);" +
                        "-fx-border-width: 1.2;" +
                        "-fx-border-radius: 22;"
        );

        // ================= HEADER =================

        HBox header = new HBox();
        Label title = new Label("Allow Notifications");
        title.setFont(Font.font("Segoe UI", 20));
        title.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setFont(Font.font(16));
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> notificationPopup.hide());

        header.getChildren().addAll(title, spacer, closeBtn);

        // ================= SWITCH =================

        HBox notifRow = new HBox(14);
        notifRow.setAlignment(Pos.CENTER_LEFT);

        Label notifLabel = new Label("Notifications");
        notifLabel.setFont(Font.font(15));
        notifLabel.setTextFill(Color.WHITE);

        boolean[] state = {false};
        HBox switchControl = createIOSSwitch(state);

        notifRow.getChildren().addAll(notifLabel, switchControl);

        // ================= MESSAGES TITLE =================

        Label msgTitle = new Label("Messages");
        msgTitle.setFont(Font.font(17));
        msgTitle.setTextFill(Color.WHITE);

        // ================= CLEAR BTN =================

        Button clearBtn = new Button("Clear Messages");
        clearBtn.setFont(Font.font(13));
        clearBtn.setTextFill(Color.WHITE);
        clearBtn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.12);" +
                        "-fx-background-radius: 20;"
        );

        clearBtn.setOnAction(e -> {
            if (messagesBox.getChildren().isEmpty()) return;

            // Animation group
            ParallelTransition all = new ParallelTransition();

            for (Node msg : messagesBox.getChildren()) {

                // Fade out
                FadeTransition fade = new FadeTransition(Duration.millis(300), msg);
                fade.setToValue(0);

                // Slide down
                TranslateTransition slide = new TranslateTransition(Duration.millis(300), msg);
                slide.setByY(20);

                // combine animations
                ParallelTransition pt = new ParallelTransition(fade, slide);
                all.getChildren().add(pt);
            }

            all.setOnFinished(ev -> {
                messagesBox.getChildren().clear();
                messageCount = 0;
                countBadge.setVisible(false);
                clearBtn.setDisable(true);
            });

            all.play();
        });

        // ================= MESSAGES LIST =================

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        scrollPane.setPrefHeight(240);

        messagesBox = new VBox(12);

        for (int i = 1; i <= messageCount; i++) {
            Label msg = new Label("• Notification message " + i);
            msg.setFont(Font.font(14));
            msg.setTextFill(Color.web("#d4d7dd"));
            messagesBox.getChildren().add(msg);
        }

        scrollPane.setContent(messagesBox);

        box.getChildren().addAll(header, notifRow, msgTitle, clearBtn, scrollPane);

        notificationPopup.getContent().add(box);

        Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        notificationPopup.show(anchor, b.getMinX() - 330, b.getMaxY() + 8);
    }


    private VBox messagesBox;

    // ======================================================
    //                IOS SWITCH READY
    // ======================================================

    private HBox createIOSSwitch(boolean[] state) {

        StackPane track = new StackPane();
        track.setPrefSize(48, 26);
        track.setStyle(
                "-fx-background-color: #3f4860;" +
                        "-fx-background-radius: 30;"
        );

        Circle knob = new Circle(10);
        knob.setFill(Color.WHITE);
        knob.setTranslateX(-12);

        track.getChildren().add(knob);

        track.setOnMouseClicked(e -> {
            state[0] = !state[0];

            double target = state[0] ? 12 : -12;

            TranslateTransition tt = new TranslateTransition(Duration.millis(180), knob);
            tt.setToX(target);
            tt.play();

            if (state[0]) {
                track.setStyle("-fx-background-color: #4b73ff; -fx-background-radius: 30;");
            } else {
                track.setStyle("-fx-background-color: #3f4860; -fx-background-radius: 30;");
            }
        });

        return new HBox(track);
    }

    public Node getRoot() {
        return root;
    }
}
