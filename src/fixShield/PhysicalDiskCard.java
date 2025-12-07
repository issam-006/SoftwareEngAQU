package fixShield;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import oshi.hardware.HWDiskStore;

import java.text.DecimalFormat;

public class PhysicalDiskCard {

    private final VBox root;
    private final Label titleLabel;
    private final Label usedValueLabel;
    private final Label spaceLabel;
    private final Label activeValueLabel;
    private final ProgressBar usedBar;
    private final ProgressBar activeBar;

    public PhysicalDiskCard(int index, HWDiskStore store) {
        String titleText = "Disk " + index + " - " + store.getModel();
        titleLabel = new Label(titleText);
        titleLabel.setTextFill(Color.web("#93c5fd"));
        titleLabel.setFont(Font.font("Segoe UI", 15));
        titleLabel.setStyle("-fx-font-weight: bold;");

        usedValueLabel = new Label("Used: N/A");
        usedValueLabel.setTextFill(Color.web("#e5e7eb"));
        usedValueLabel.setFont(Font.font("Segoe UI", 13));

        usedBar = new ProgressBar(0);
        usedBar.setPrefWidth(240);
        usedBar.setStyle(
                "-fx-accent: #22c55e;" +
                        "-fx-control-inner-background: #020617;"
        );

        double sizeGb = store.getSize() / (1024.0 * 1024 * 1024);
        spaceLabel = new Label("Size: " + new DecimalFormat("0.0").format(sizeGb) + " GB");
        spaceLabel.setTextFill(Color.web("#9ca3af"));
        spaceLabel.setFont(Font.font("Segoe UI", 11));

        activeValueLabel = new Label("Active: 0 %");
        activeValueLabel.setTextFill(Color.web("#e5e7eb"));
        activeValueLabel.setFont(Font.font("Segoe UI", 13));

        activeBar = new ProgressBar(0);
        activeBar.setPrefWidth(240);
        activeBar.setStyle(
                "-fx-accent: #22c55e;" +
                        "-fx-control-inner-background: #020617;"
        );

        root = new VBox();
        root.setSpacing(8);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(14));
        root.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #020617, #111827);" +
                        "-fx-background-radius: 20;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 18, 0.1, 0, 8);"
        );
        root.setMinHeight(160);
        root.setMaxWidth(Double.MAX_VALUE);
        root.getChildren().addAll(titleLabel, usedValueLabel, usedBar, spaceLabel, activeValueLabel, activeBar);
    }

    public VBox getRoot() { return root; }
    public Label getUsedValueLabel() { return usedValueLabel; }
    public Label getSpaceLabel() { return spaceLabel; }
    public Label getActiveValueLabel() { return activeValueLabel; }
    public ProgressBar getUsedBar() { return usedBar; }
    public ProgressBar getActiveBar() { return activeBar; }
}
