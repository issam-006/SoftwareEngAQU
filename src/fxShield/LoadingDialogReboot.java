package fxShield;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class LoadingDialogReboot {

    private Stage stage;
    private Label titleLabel;
    private Label messageLabel;
    private Label dotsLabel;

    private Timeline dotsTimeline;

    private int dotState = 0;

    private LoadingDialogReboot(Stage owner, String title, String message) {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);

        // ----- Root container -----
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16, 22, 16, 22));
        root.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #020617, #111827);" +
                        "-fx-background-radius: 18;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 22, 0.25, 0, 8);"
        );

        Rectangle clip = new Rectangle(340, 160);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        root.setClip(clip);

        // ----- Title & message -----
        titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", 16));
        titleLabel.setTextFill(Color.web("#e5e7eb"));
        titleLabel.setStyle("-fx-font-weight: bold;");

        messageLabel = new Label(message);
        messageLabel.setFont(Font.font("Segoe UI", 12));
        messageLabel.setTextFill(Color.web("#9ca3af"));
        messageLabel.setWrapText(true);

        VBox textBox = new VBox(6, titleLabel, messageLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);

        // ----- Dots animation label -----
        dotsLabel = new Label("● ○ ○");
        dotsLabel.setFont(Font.font("Segoe UI", 18));
        dotsLabel.setTextFill(Color.web("#60a5fa"));

        VBox centerBox = new VBox(12, textBox, dotsLabel);
        centerBox.setAlignment(Pos.CENTER_LEFT);

        root.setCenter(centerBox);

        // small fade-in
        root.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(180), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        fade.play();

        Scene scene = new Scene(root, 340, 160);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.centerOnScreen();

        // ----- Dots timeline -----
        dotsTimeline = new Timeline(
                new KeyFrame(Duration.millis(350), e -> advanceDots())
        );
        dotsTimeline.setCycleCount(Animation.INDEFINITE);
    }

    private void advanceDots() {
        dotState = (dotState + 1) % 3;
        switch (dotState) {
            case 0 -> dotsLabel.setText("● ○ ○");
            case 1 -> dotsLabel.setText("● ● ○");
            case 2 -> dotsLabel.setText("● ● ●");
        }
    }

    public static LoadingDialogReboot show(Stage owner, String title, String message) {
        LoadingDialogReboot dlg = new LoadingDialogReboot(owner, title, message);
        dlg.stage.show();
        dlg.dotsTimeline.play();
        return dlg;
    }

    /**
     * استدعِها لما تخلص العملية بنجاح.
     * بيغير الحالة لـ "Done" ويعرض ✓ وبعدين يسكر.
     */
    public void setDone(String doneMessage) {
        dotsTimeline.stop();
        messageLabel.setText(doneMessage);
        dotsLabel.setText("✓");
        dotsLabel.setTextFill(Color.web("#22c55e"));

        PauseTransition wait = new PauseTransition(Duration.millis(900));
        wait.setOnFinished(e -> close());
        wait.play();
    }

    public void setFailed(String failMessage) {
        dotsTimeline.stop();
        messageLabel.setText(failMessage);
        dotsLabel.setText("✕");
        dotsLabel.setTextFill(Color.web("#f97373"));

        PauseTransition wait = new PauseTransition(Duration.millis(1200));
        wait.setOnFinished(e -> close());
        wait.play();
    }

    public void close() {
        // fade-out لطيف
        FadeTransition fade = new FadeTransition(Duration.millis(150), stage.getScene().getRoot());
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> stage.close());
        fade.play();
    }
}
