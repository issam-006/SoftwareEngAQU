package fxShield;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class MaintenanceDialog {

    public static void show(Stage owner, RemoteConfig config, Runnable onRetry) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: rgba(0,0,0,0.55);");

        VBox card = new VBox();
        card.setSpacing(18);
        card.setPadding(new Insets(24));
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(420);
        card.setStyle(
                "-fx-background-color: #14161c;" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-radius: 18;" +
                        "-fx-border-color: #2e3340;" +
                        "-fx-border-width: 1;"
        );

        DropShadow ds = new DropShadow();
        ds.setRadius(18);
        ds.setOffsetY(6);
        ds.setColor(Color.rgb(0, 0, 0, 0.45));
        card.setEffect(ds);

        Circle iconCircle = new Circle(26);
        iconCircle.setFill(Color.web("#ffb020"));

        Label iconLabel = new Label("⚙");
        iconLabel.setFont(Font.font("Segoe UI Emoji", 24));
        iconLabel.setTextFill(Color.web("#1f2430"));

        StackPane iconStack = new StackPane(iconCircle, iconLabel);
        iconStack.setAlignment(Pos.CENTER);

        Label title = new Label("fxShield Under Maintenance");
        title.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 18));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("The service is temporarily unavailable.");
        subtitle.setFont(Font.font("Segoe UI", 13));
        subtitle.setTextFill(Color.web("#9da3b4"));

        String msgText;
        if (config != null && config.getUpdateMessage() != null && !config.getUpdateMessage().isBlank()) {
            msgText = config.getUpdateMessage();
        } else {
            msgText = "We are performing some upgrades to keep your protection up to date.";
        }

        Label message = new Label(msgText);
        message.setWrapText(true);
        message.setFont(Font.font("Segoe UI", 13));
        message.setTextFill(Color.web("#d4d7e2"));

        VBox centerBox = new VBox(title, subtitle, message);
        centerBox.setSpacing(6);
        centerBox.setAlignment(Pos.TOP_LEFT);

        HBox header = new HBox(iconStack, centerBox);
        header.setSpacing(14);
        header.setAlignment(Pos.CENTER_LEFT);

        Hyperlink detailsLink = new Hyperlink("More details");
        detailsLink.setFont(Font.font("Segoe UI", 12));
        detailsLink.setTextFill(Color.web("#8aa4ff"));
        detailsLink.setBorder(null);
        detailsLink.setOnAction(e -> {
            if (config != null && config.getDownloadUrl() != null &&
                    !config.getDownloadUrl().isBlank()) {
                getHostServices(owner).showDocument(config.getDownloadUrl());
            }
        });

        Label versionLabel = new Label();
        if (config != null && config.getLatestVersion() != null) {
            String text = "Planned version: " + config.getLatestVersion();
            if (config.isForceUpdate()) {
                text += "  •  Required update";
            }
            versionLabel.setText(text);
        } else {
            versionLabel.setText("Maintenance window – no version info");
        }
        versionLabel.setFont(Font.font("Segoe UI", 11));
        versionLabel.setTextFill(Color.web("#7e8496"));

        HBox bottomInfo = new HBox(versionLabel, detailsLink);
        bottomInfo.setSpacing(8);
        bottomInfo.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button retryButton = new Button("Retry");
        retryButton.setFont(Font.font("Segoe UI", 13));
        retryButton.setDefaultButton(true);
        retryButton.setStyle(
                "-fx-background-radius: 12;" +
                        "-fx-background-color: #4f8cff;" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 8 18 8 18;"
        );
        retryButton.setOnAction(e -> {
            dialog.close();
            if (onRetry != null) {
                onRetry.run();
            }
        });

        Button closeButton = new Button("Exit");
        closeButton.setFont(Font.font("Segoe UI", 13));
        closeButton.setCancelButton(true);
        closeButton.setStyle(
                "-fx-background-radius: 12;" +
                        "-fx-background-color: transparent;" +
                        "-fx-border-radius: 12;" +
                        "-fx-border-color: #3b3f4a;" +
                        "-fx-border-width: 1;" +
                        "-fx-text-fill: #d4d7e2;" +
                        "-fx-padding: 8 18 8 18;"
        );
        closeButton.setOnAction(e -> {
            dialog.close();
            owner.close();
        });

        HBox buttons = new HBox(closeButton, retryButton);
        buttons.setSpacing(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(header, spacer, bottomInfo, buttons);
        root.getChildren().add(card);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.setWidth(520);
        dialog.setHeight(260);
        dialog.centerOnScreen();

        card.setScaleX(0.85);
        card.setScaleY(0.85);
        card.setOpacity(0);

        FadeTransition fade = new FadeTransition(Duration.millis(220), card);
        fade.setFromValue(0);
        fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(220), card);
        scale.setFromX(0.85);
        scale.setFromY(0.85);
        scale.setToX(1);
        scale.setToY(1);

        ParallelTransition appear = new ParallelTransition(fade, scale);
        appear.setOnFinished(e -> card.requestFocus());
        dialog.show();
        appear.play();
    }

    private static javafx.application.HostServices getHostServices(Stage owner) {
        return ((javafx.application.Application)
                owner.getProperties().get("appInstance")).getHostServices();
    }
}
