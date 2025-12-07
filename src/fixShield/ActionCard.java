package fixShield;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class ActionCard {

    private final VBox root;
    private final Label titleLabel;
    private final Label descLabel;
    private final Button button;

    public ActionCard(String title, String description, String buttonText) {
        titleLabel = new Label(title);
        titleLabel.setTextFill(Color.web("#93c5fd"));
        titleLabel.setFont(Font.font("Segoe UI", 14));
        titleLabel.setStyle("-fx-font-weight: bold;");

        descLabel = new Label(description);
        descLabel.setTextFill(Color.web("#9ca3af"));
        descLabel.setFont(Font.font("Segoe UI", 11));
        descLabel.setWrapText(true);

        button = new Button(buttonText);
        button.setFont(Font.font("Segoe UI", 12));
        button.setStyle(
                "-fx-background-color: #2563eb;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 999;" +
                        "-fx-padding: 4 12 4 12;"
        );

        root = new VBox();
        root.setSpacing(8);
        root.setAlignment(Pos.TOP_LEFT);
        root.setPadding(new Insets(14));
        root.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #020617, #111827);" +
                        "-fx-background-radius: 18;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 12, 0.1, 0, 5);"
        );
        root.setMinHeight(120);
        root.setMaxWidth(Double.MAX_VALUE);
        root.getChildren().addAll(titleLabel, descLabel, button);
    }

    public VBox getRoot() { return root; }
    public Button getButton() { return button; }
}
