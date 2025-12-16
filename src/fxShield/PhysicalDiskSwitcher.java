package fxShield;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.IntConsumer;

public final class PhysicalDiskSwitcher {

    private final HBox root;
    private final FlowPane listPane;
    private final Label titleLabel;

    private int count;
    private int selected;

    private IntConsumer onSelect = i -> {};
    private final ToggleGroup toggleGroup = new ToggleGroup();

    // Styles (compact-friendly)
    private static final String STYLE_NORMAL =
            "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 4 8; " +
                    "-fx-background-color: transparent; -fx-text-fill: #dbe0ff; -fx-font-size: 12;";
    private static final String STYLE_HOVER =
            "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 4 8; " +
                    "-fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: #ffffff; -fx-font-size: 12;";
    private static final String STYLE_SELECTED =
            "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 4 8; " +
                    "-fx-background-color: linear-gradient(to bottom, #4f46e5, #7c3aed); -fx-text-fill: white; -fx-font-size: 12;";

    // Backward-compatible constructor (shows title)
    public PhysicalDiskSwitcher(int initialCount, int initialIndex, IntConsumer onSelect) {
        this(initialCount, initialIndex, onSelect, true);
    }

    // New: compact mode (no title) for overlay inside the disk card
    public PhysicalDiskSwitcher(int initialCount, int initialIndex, IntConsumer onSelect, boolean showTitle) {
        this.count = Math.max(0, initialCount);
        this.selected = clamp(initialIndex, 0, Math.max(0, this.count - 1));
        if (onSelect != null) this.onSelect = onSelect;

        listPane = new FlowPane();
        listPane.setHgap(6);
        listPane.setVgap(6);
        listPane.setPadding(new Insets(4));
        listPane.setPrefWrapLength(400);

        if (showTitle) {
            titleLabel = new Label("Disks:");
            titleLabel.setStyle("-fx-text-fill: #c5c7ce; -fx-font-size: 13; -fx-font-weight: bold;");
            VBox container = new VBox(6, titleLabel, listPane);
            container.setAlignment(Pos.CENTER_LEFT);
            root = new HBox(container);
            root.setPadding(new Insets(6));
        } else {
            titleLabel = null;
            root = new HBox(listPane);
            root.setPadding(new Insets(0));
        }

        root.setAlignment(Pos.CENTER_LEFT);

        toggleGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) return;
            Object ud = newT.getUserData();
            if (!(ud instanceof Integer idx)) return;
            if (idx == selected) { refreshStyles(); return; }
            selected = idx;
            refreshStyles();
            try { this.onSelect.accept(selected); } catch (Exception ignored) {}
        });

        // Keyboard navigation on container
        root.setOnKeyPressed(e -> {
            if (count <= 0) return;
            if (e.getCode() == KeyCode.LEFT) {
                setSelectedIndex(Math.max(0, selected - 1));
                e.consume();
            } else if (e.getCode() == KeyCode.RIGHT) {
                setSelectedIndex(Math.min(count - 1, selected + 1));
                e.consume();
            } else if (e.getCode().isDigitKey()) {
                int num = digitToIndex(e.getCode());
                if (num >= 1 && num <= count) setSelectedIndex(num - 1);
            }
        });

        rebuildButtons();
    }

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
            ToggleButton tb = createDiskButton(i);
            listPane.getChildren().add(tb);
        }

        selectToggleSilently(selected);
        refreshStyles();
    }

    private ToggleButton createDiskButton(int index) {
        ToggleButton tb = new ToggleButton("Disk " + (index + 1));
        tb.setUserData(index);
        tb.setToggleGroup(toggleGroup);
        tb.setStyle(STYLE_NORMAL);
        tb.setFocusTraversable(true);
        tb.setAccessibleText("Disk " + (index + 1));

        tb.setOnMouseEntered(e -> { if (!tb.isSelected()) tb.setStyle(STYLE_HOVER); });
        tb.setOnMouseExited(e -> { if (!tb.isSelected()) tb.setStyle(STYLE_NORMAL); });

        tb.selectedProperty().addListener((o, was, now) ->
                tb.setStyle(now ? STYLE_SELECTED : (tb.isHover() ? STYLE_HOVER : STYLE_NORMAL))
        );

        tb.setOnAction(e -> tb.requestFocus());
        return tb;
    }

    private void refreshStyles() {
        for (var node : listPane.getChildren()) {
            if (node instanceof ToggleButton tb) {
                tb.setStyle(tb.isSelected() ? STYLE_SELECTED : (tb.isHover() ? STYLE_HOVER : STYLE_NORMAL));
            }
        }
    }

    private void selectToggleSilently(int index) {
        Toggle toSelect = null;
        for (Toggle t : toggleGroup.getToggles()) {
            if (t.getUserData() instanceof Integer ud && ud == index) { toSelect = t; break; }
        }
        if (toSelect != null) toggleGroup.selectToggle(toSelect);
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static int digitToIndex(KeyCode code) {
        return switch (code) {
            case DIGIT1, NUMPAD1 -> 1;
            case DIGIT2, NUMPAD2 -> 2;
            case DIGIT3, NUMPAD3 -> 3;
            case DIGIT4, NUMPAD4 -> 4;
            case DIGIT5, NUMPAD5 -> 5;
            case DIGIT6, NUMPAD6 -> 6;
            case DIGIT7, NUMPAD7 -> 7;
            case DIGIT8, NUMPAD8 -> 8;
            case DIGIT9, NUMPAD9 -> 9;
            case DIGIT0, NUMPAD0 -> 0;
            default -> -1;
        };
    }

    // Public API
    public HBox getRoot() { return root; }

    public void setOnSelect(IntConsumer c) {
        this.onSelect = (c != null) ? c : i -> {};
    }

    public void setCount(int newCount) {
        this.count = Math.max(0, newCount);
        if (this.count == 0) selected = 0;
        else if (selected >= this.count) selected = this.count - 1;
        rebuildButtons();
    }

    public void setSelectedIndex(int index) {
        if (count <= 0) return;
        int newSel = clamp(index, 0, count - 1);
        if (newSel == selected) { refreshStyles(); return; }
        selected = newSel;
        selectToggleSilently(selected);
        refreshStyles();
        try { onSelect.accept(selected); } catch (Exception ignored) {}
    }

    public int getSelectedIndex() { return selected; }
}