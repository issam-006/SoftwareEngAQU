package fxShield.UI;

import fxShield.DB.RemoteConfig;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Border;
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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MaintenanceDialog {

    private MaintenanceDialog() {}

    private static final Duration ANIM = Duration.millis(220);

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

    private static final Font FONT_TITLE = Font.font("Segoe UI", FontWeight.SEMI_BOLD, 18);
    private static final Font FONT_BODY  = Font.font("Segoe UI", 13);
    private static final Font FONT_SMALL = Font.font("Segoe UI", 11);
    private static final Font FONT_LINK  = Font.font("Segoe UI", 12);
    private static final Font FONT_ICON  = Font.font("Segoe UI Emoji", 24);

    private static final DropShadow CARD_SHADOW = new DropShadow(18, Color.rgb(0, 0, 0, 0.45));
    static { CARD_SHADOW.setOffsetY(6); }

    /**
     * retryFetch -> RemoteConfigService.fetchConfig()
     * onOnline -> called on FX thread when config becomes ONLINE (not maintenance)
     */
    public static void show(
            Stage owner,
            RemoteConfig config,
            Supplier<RemoteConfig> retryFetch,
            Consumer<RemoteConfig> onOnline
    ) {
        final AtomicBoolean retryRunning = new AtomicBoolean(false);

        final Stage dialog = new Stage();
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setResizable(false);
        dialog.setTitle("fxShield Maintenance");

        final StackPane root = new StackPane();
        root.setStyle("-fx-background-color: transparent;");

        final VBox card = new VBox(18);
        card.setPadding(new Insets(24));
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(520);
        card.setStyle(CARD_STYLE);
        card.setEffect(CARD_SHADOW);

        // Message label (updates after retry)
        final Label message = new Label(getMessage(config));
        message.setWrapText(true);
        message.setFont(FONT_BODY);
        message.setTextFill(Color.web("#d4d7e2"));

        final VBox textBox = buildTextBox(message);
        final HBox header = new HBox(14, buildIcon(), textBox);
        header.setAlignment(Pos.CENTER_LEFT);

        // Details + version row
        final Hyperlink detailsLink = new Hyperlink("More details");
        detailsLink.setFont(FONT_LINK);
        detailsLink.setBorder(Border.EMPTY);
        detailsLink.setTextFill(Color.web("#8aa4ff"));

        final Label versionLabel = new Label(getVersionText(config));
        versionLabel.setFont(FONT_SMALL);
        versionLabel.setTextFill(Color.web("#7e8496"));

        applyDetailsLink(owner, detailsLink, config);

        final HBox infoRow = new HBox(8, versionLabel, detailsLink);
        infoRow.setAlignment(Pos.CENTER_LEFT);

        final Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Exit button (hard kill)
        final Button closeButton = new Button("Exit");
        closeButton.setFont(FONT_BODY);
        closeButton.setCancelButton(true);
        closeButton.setStyle(BTN_EXIT_STYLE);
        closeButton.setOnAction(e -> killApp(owner, dialog));

        // Retry button + spinner
        final Button retryButton = new Button("Retry");
        retryButton.setFont(FONT_BODY);
        retryButton.setDefaultButton(true);
        retryButton.setStyle(BTN_RETRY_STYLE);

        final ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(18, 18);
        spinner.setOpacity(0);
        spinner.setVisible(false);
        spinner.setManaged(false);

        final StackPane retryContainer = new StackPane(retryButton, spinner);
        retryContainer.setAlignment(Pos.CENTER_RIGHT);

        retryButton.setOnAction(e -> {
            if (!retryRunning.compareAndSet(false, true)) return;

            retryButton.setDisable(true);
            retryButton.setText("Checking...");
            showProcessing(spinner);

            Thread t = new Thread(() -> {
                RemoteConfig latest = null;
                try {
                    if (retryFetch != null) latest = retryFetch.get();
                } catch (Exception ignored) {}

                final RemoteConfig finalLatest = latest;

                Platform.runLater(() -> {
                    retryRunning.set(false);
                    hideProcessing(spinner);

                    retryButton.setDisable(false);
                    retryButton.setText("Retry");

                    if (finalLatest == null) {
                        message.setText("Still unavailable. Please try again.");
                        return;
                    }

                    // live update dialog content
                    message.setText(getMessage(finalLatest));
                    versionLabel.setText(getVersionText(finalLatest));
                    applyDetailsLink(owner, detailsLink, finalLatest);

                    // if online => close + start normal UI
                    if (!finalLatest.isMaintenance() && finalLatest.isOnline()) {
                        try { dialog.close(); } catch (Exception ignored) {}
                        if (onOnline != null) onOnline.accept(finalLatest);
                    }
                });
            }, "fxShield-maintenance-retry");

            t.setDaemon(true);
            t.start();
        });

        final HBox buttons = new HBox(10, closeButton, retryContainer);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(header, spacer, infoRow, buttons);
        root.getChildren().add(card);

        final Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        scene.setOnKeyPressed(k -> {
            if (k.getCode() == KeyCode.ESCAPE) closeButton.fire();
            if (k.getCode() == KeyCode.ENTER) retryButton.fire();
        });

        dialog.setScene(scene);

        // open animation
        card.setScaleX(0.85);
        card.setScaleY(0.85);
        card.setOpacity(0);

        ParallelTransition appear = new ParallelTransition(
                fade(card, 0, 1, ANIM.toMillis()),
                scale(card, 0.85, 1.0, ANIM.toMillis())
        );

        dialog.show();
        dialog.sizeToScene();
        dialog.centerOnScreen();
        appear.play();
    }

    // ---------- UI builders ----------

    private static StackPane buildIcon() {
        Circle iconCircle = new Circle(26, Color.web("#ffb020"));
        Label iconLabel = new Label("⚙");
        iconLabel.setFont(FONT_ICON);
        iconLabel.setTextFill(Color.web("#1f2430"));
        return new StackPane(iconCircle, iconLabel);
    }

    private static VBox buildTextBox(Label messageLabel) {
        Label title = new Label("fxShield Under Maintenance");
        title.setFont(FONT_TITLE);
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("The service is temporarily unavailable.");
        subtitle.setFont(FONT_BODY);
        subtitle.setTextFill(Color.web("#9da3b4"));

        VBox box = new VBox(6, title, subtitle, messageLabel);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    private static void applyDetailsLink(Stage owner, Hyperlink link, RemoteConfig config) {
        String url = (config != null) ? config.getDownloadUrl() : null;
        boolean hasUrl = !isBlank(url);

        link.setDisable(!hasUrl);
        link.setOpacity(hasUrl ? 1.0 : 0.45);

        link.setOnAction(e -> {
            if (!hasUrl) return;
            var hs = safeHostServices(owner);
            if (hs != null) hs.showDocument(url);
        });
    }

    private static String getMessage(RemoteConfig config) {
        String msg = (config != null) ? config.getUpdateMessage() : null;
        if (!isBlank(msg)) return msg.trim();
        return "We are performing some upgrades to keep your protection up to date.";
    }

    private static String getVersionText(RemoteConfig config) {
        String latest = (config != null) ? config.getLatestVersion() : null;
        if (isBlank(latest)) return "Maintenance window – no version info";
        String text = "Planned version: " + latest.trim();
        if (config != null && config.isForceUpdate()) text += "  •  Required update";
        return text;
    }

    // ---------- HARD EXIT ----------

    private static void killApp(Stage owner, Stage dialog) {
        try { if (dialog != null) dialog.hide(); } catch (Exception ignored) {}
        try { if (owner != null) owner.hide(); } catch (Exception ignored) {}
        try { Platform.exit(); } catch (Exception ignored) {}
        System.exit(0);
    }

    // ---------- Retry processing animation (fallback-safe) ----------

    private static void showProcessing(ProgressIndicator spinner) {
        if (spinner == null) return;

        spinner.setManaged(false);
        spinner.setVisible(true);

        try {
            spinner.setOpacity(0);
            FadeTransition in = fade(spinner, 0, 1, 140);
            in.setOnFinished(e -> spinner.setOpacity(1));
            in.play();
        } catch (Exception ignored) {
            spinner.setOpacity(1);
            spinner.setVisible(true);
        }
    }

    private static void hideProcessing(ProgressIndicator spinner) {
        if (spinner == null) return;

        try {
            FadeTransition out = fade(spinner, spinner.getOpacity(), 0, 140);
            out.setOnFinished(e -> {
                spinner.setOpacity(0);
                spinner.setVisible(false);
            });
            out.play();
        } catch (Exception ignored) {
            spinner.setOpacity(0);
            spinner.setVisible(false);
        }
    }

    // ---------- Anim helpers ----------

    private static FadeTransition fade(Node n, double from, double to, double ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setFromValue(from);
        ft.setToValue(to);
        return ft;
    }

    private static ScaleTransition scale(Node n, double from, double to, double ms) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), n);
        st.setFromX(from);
        st.setFromY(from);
        st.setToX(to);
        st.setToY(to);
        return st;
    }

    // Safe HostServices from owner’s properties ("appInstance" should be your Application)
    private static javafx.application.HostServices safeHostServices(Stage owner) {
        if (owner == null) return null;
        try {
            Object app = owner.getProperties().get("appInstance");
            if (app instanceof Application a) return a.getHostServices();
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isBlank(String s) {
        if (s == null) return true;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) return false;
        }
        return true;
    }
}
