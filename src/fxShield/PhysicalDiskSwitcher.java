package fxShield;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.IntConsumer;

public class PhysicalDiskSwitcher {

    private final HBox root;
    private final FlowPane listPane;
    private final Label titleLabel;

    private int count = 0;
    private int selected = 0;
    private IntConsumer onSelect = i -> {};

    private final ToggleGroup toggleGroup = new ToggleGroup();

    public PhysicalDiskSwitcher(int initialCount, int initialIndex, IntConsumer onSelect) {
        this.count = Math.max(0, initialCount);
        this.selected = Math.max(0, Math.min(initialIndex, Math.max(0, count - 1)));
        if (onSelect != null) this.onSelect = onSelect;

        titleLabel = new Label("Disks:");
        titleLabel.setStyle("-fx-text-fill: #c5c7ce; -fx-font-size: 13; -fx-font-weight: bold;");

        listPane = new FlowPane();
        listPane.setHgap(8);
        listPane.setVgap(8);
        listPane.setPadding(new Insets(6));
        listPane.setPrefWrapLength(400); // wrap if many disks

        VBox container = new VBox(6, titleLabel, listPane);
        container.setAlignment(Pos.CENTER_LEFT);

        root = new HBox(container);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(6));

        rebuildButtons();
    }

    // rebuild buttons based on current count and selected
    private void rebuildButtons() {
        listPane.getChildren().clear();
        toggleGroup.getToggles().clear();

        if (count <= 0) {
            Label none = new Label("No disks");
            none.setStyle("-fx-text-fill: #9ca3af;");
            listPane.getChildren().add(none);
            return;
        }

        for (int i = 0; i < count; i++) {
            final int idx = i;
            ToggleButton tb = new ToggleButton("Disk " + (i + 1));
            tb.setUserData(i);
            tb.setToggleGroup(toggleGroup);
            tb.setStyle(
                    "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 6 10; " +
                            "-fx-background-color: transparent; -fx-text-fill: #dbe0ff;"
            );
            if (i == selected) {
                tb.setSelected(true);
                tb.setStyle(
                        "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 6 10; " +
                                "-fx-background-color: linear-gradient(to bottom, #4f46e5, #7c3aed); -fx-text-fill: white;"
                );
            }

            tb.selectedProperty().addListener((obs, was, now) -> {
                if (now) {
                    selected = idx;
                    // visually highlight selected (simple style swap)
                    for (var node : listPane.getChildren()) {
                        if (node instanceof ToggleButton) {
                            ((ToggleButton) node).setStyle(
                                    "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 6 10; " +
                                            "-fx-background-color: transparent; -fx-text-fill: #dbe0ff;"
                            );
                        }
                    }
                    tb.setStyle(
                            "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 6 10; " +
                                    "-fx-background-color: linear-gradient(to bottom, #4f46e5, #7c3aed); -fx-text-fill: white;"
                    );

                    onSelect.accept(selected);
                }
            });

            listPane.getChildren().add(tb);
        }
    }

    // public API
    public HBox getRoot() { return root; }

    public void setOnSelect(IntConsumer c) {
        this.onSelect = (c != null) ? c : i -> {};
    }

    public void setCount(int newCount) {
        this.count = Math.max(0, newCount);
        if (selected >= count) selected = Math.max(0, count - 1);
        rebuildButtons();
    }

    public void setSelectedIndex(int index) {
        if (count == 0) return;
        selected = Math.max(0, Math.min(index, count - 1));
        // select the toggle button programmatically
        int i = 0;
        for (var node : listPane.getChildren()) {
            if (node instanceof ToggleButton) {
                ToggleButton tb = (ToggleButton) node;
                if (i == selected) {
                    tb.setSelected(true);
                    tb.requestFocus();
                } else {
                    tb.setSelected(false);
                }
                i++;
            }
        }
        // notify
        onSelect.accept(selected);
    }

    public int getSelectedIndex() { return selected; }
}