package fxShield;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class MaintenanceDialog {

    private static final Duration ANIM = Duration.millis(220);

    // Styles
    private static final String CARD_STYLE =
            "-fx-background-color: #14161c;" +
                    "-fx-background-radius: 18;" +
                    "-fx-border-radius: 18;" +
                    "-fx-border-color: #2e3340;" +
                    "-fx-border-width: 1;";

    private static final String BTN_EXIT_STYLE =
            "-fx-background-radius: 12;" +
                    "-fx-background-color: transparent;" +
                    "-fx-border-radius: 12;" +
                    "-fx-border-color: #3b3f4a;" +
                    "-fx-border-width: 1;" +
                    "-fx-text-fill: #d4d7e2;" +
                    "-fx-padding: 8 18 8 18;";

    private static final String BTN_RETRY_STYLE =
            "-fx-background-radius: 12;" +
                    "-fx-background-color: #4f8cff;" +
                    "-fx-text-fill: white;" +
                    "-fx-padding: 8 18 8 18;";

    public static void show(Stage owner, RemoteConfig config, Supplier<Boolean> onRetry) {
        final AtomicBoolean retryRunning = new AtomicBoolean(false);

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setResizable(false);
        dialog.setTitle("fxShield Maintenance");

        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: transparent;");

        VBox card = new VBox(18);
        card.setPadding(new Insets(24));
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(520);
        card.setStyle(CARD_STYLE);

        DropShadow ds = new DropShadow(18, Color.rgb(0, 0, 0, 0.45));
        ds.setOffsetY(6);
        card.setEffect(ds);

        // Icon
        Circle iconCircle = new Circle(26, Color.web("#ffb020"));
        Label iconLabel = new Label("⚙");
        iconLabel.setFont(Font.font("Segoe UI Emoji", 24));
        iconLabel.setTextFill(Color.web("#1f2430"));
        StackPane iconStack = new StackPane(iconCircle, iconLabel);

        // Texts
        Label title = new Label("fxShield Under Maintenance");
        title.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 18));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("The service is temporarily unavailable.");
        subtitle.setFont(Font.font("Segoe UI", 13));
        subtitle.setTextFill(Color.web("#9da3b4"));

        String msgText = (config != null && config.getUpdateMessage() != null && !config.getUpdateMessage().isBlank())
                ? config.getUpdateMessage()
                : "We are performing some upgrades to keep your protection up to date.";

        Label message = new Label(msgText);
        message.setWrapText(true);
        message.setFont(Font.font("Segoe UI", 13));
        message.setTextFill(Color.web("#d4d7e2"));

        VBox centerBox = new VBox(6, title, subtitle, message);
        centerBox.setAlignment(Pos.TOP_LEFT);

        HBox header = new HBox(14, iconStack, centerBox);
        header.setAlignment(Pos.CENTER_LEFT);

        // Details + version
        Hyperlink detailsLink = new Hyperlink("More details");
        detailsLink.setFont(Font.font("Segoe UI", 12));
        detailsLink.setBorder(Border.EMPTY);
        detailsLink.setTextFill(Color.web("#8aa4ff"));

        String url = (config != null ? config.getDownloadUrl() : null);
        boolean hasUrl = url != null && !url.isBlank();
        detailsLink.setDisable(!hasUrl);
        detailsLink.setOpacity(hasUrl ? 1.0 : 0.45);

        detailsLink.setOnAction(e -> {
            if (!hasUrl) return;
            var hs = safeHostServices(owner);
            if (hs != null) hs.showDocument(url);
        });

        Label versionLabel = new Label();
        if (config != null && config.getLatestVersion() != null) {
            String text = "Planned version: " + config.getLatestVersion();
            if (config.isForceUpdate()) text += "  •  Required update";
            versionLabel.setText(text);
        } else {
            versionLabel.setText("Maintenance window – no version info");
        }
        versionLabel.setFont(Font.font("Segoe UI", 11));
        versionLabel.setTextFill(Color.web("#7e8496"));

        HBox bottomInfo = new HBox(8, versionLabel, detailsLink);
        bottomInfo.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Buttons + spinner
        Button closeButton = new Button("Exit");
        closeButton.setFont(Font.font("Segoe UI", 13));
        closeButton.setCancelButton(true);
        closeButton.setStyle(BTN_EXIT_STYLE);
        closeButton.setOnAction(e -> dialog.close());

        Button retryButton = new Button("Retry");
        retryButton.setFont(Font.font("Segoe UI", 13));
        retryButton.setDefaultButton(true);
        retryButton.setStyle(BTN_RETRY_STYLE);

        ProgressIndicator retrySpinner = new ProgressIndicator();
        retrySpinner.setMaxSize(18, 18);
        retrySpinner.setVisible(false);
        retrySpinner.setOpacity(0);

        StackPane retryContainer = new StackPane(retryButton, retrySpinner);
        retryContainer.setAlignment(Pos.CENTER_RIGHT);

        retryButton.setOnAction(e -> {
            if (!retryRunning.compareAndSet(false, true)) return;

            retryButton.setDisable(true);
            retryButton.setText("Checking...");

            retrySpinner.setVisible(true);
            fade(retrySpinner, 0, 1, 120).play();

            Thread t = new Thread(() -> {
                boolean online = false;
                try {
                    if (onRetry != null) {
                        Boolean result = onRetry.get();
                        online = Boolean.TRUE.equals(result);
                    }
                } catch (Exception ignored) {}

                final boolean ok = online;
                Platform.runLater(() -> {
                    retryRunning.set(false);
                    var out = fade(retrySpinner, 1, 0, 120);
                    out.setOnFinished(ev -> retrySpinner.setVisible(false));
                    out.play();

                    retryButton.setDisable(false);
                    retryButton.setText("Retry");

                    if (ok) dialog.close();
                });
            }, "MaintenanceRetry");
            t.setDaemon(true);
            t.start();
        });

        HBox buttons = new HBox(10, closeButton, retryContainer);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(header, spacer, bottomInfo, buttons);
        root.getChildren().add(card);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(k -> {
            if (k.getCode() == KeyCode.ESCAPE) dialog.close();
            if (k.getCode() == KeyCode.ENTER) retryButton.fire();
        });

        dialog.setScene(scene);
        dialog.setWidth(520);
        dialog.setHeight(260);
        dialog.centerOnScreen();

        // Open animation
        card.setScaleX(0.85);
        card.setScaleY(0.85);
        card.setOpacity(0);

        ParallelTransition appear = new ParallelTransition(
                fade(card, 0, 1, ANIM.toMillis()),
                scale(card, 0.85, 1.0, ANIM.toMillis())
        );
        dialog.show();
        appear.play();
    }

    private static javafx.animation.FadeTransition fade(javafx.scene.Node n, double from, double to, double ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setFromValue(from);
        ft.setToValue(to);
        return ft;
    }

    private static ScaleTransition scale(javafx.scene.Node n, double from, double to, double ms) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), n);
        st.setFromX(from); st.setFromY(from);
        st.setToX(to);     st.setToY(to);
        return st;
    }

    // Safe HostServices from owner’s properties (put your Application under "appInstance")
    private static javafx.application.HostServices safeHostServices(Stage owner) {
        try {
            Object app = owner.getProperties().get("appInstance");
            if (app instanceof Application a) return a.getHostServices();
        } catch (Exception ignored) {}
        return null;
    }
}