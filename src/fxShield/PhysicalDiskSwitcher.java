package fxShield;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
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

    // ---------- styles (نفس ألوانك) ----------
    private static String normalStyle() {
        return "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 6 10; " +
                "-fx-background-color: transparent; -fx-text-fill: #dbe0ff;";
    }

    private static String hoverStyle() {
        return "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 6 10; " +
                "-fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: #ffffff;";
    }

    private static String selectedStyle() {
        return "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 6 10; " +
                "-fx-background-color: linear-gradient(to bottom, #4f46e5, #7c3aed); -fx-text-fill: white;";
    }

    public PhysicalDiskSwitcher(int initialCount, int initialIndex, IntConsumer onSelect) {
        this.count = Math.max(0, initialCount);
        this.selected = clamp(initialIndex, 0, Math.max(0, this.count - 1));
        if (onSelect != null) this.onSelect = onSelect;

        titleLabel = new Label("Disks:");
        titleLabel.setStyle("-fx-text-fill: #c5c7ce; -fx-font-size: 13; -fx-font-weight: bold;");

        listPane = new FlowPane();
        listPane.setHgap(8);
        listPane.setVgap(8);
        listPane.setPadding(new Insets(6));
        listPane.setPrefWrapLength(400);

        VBox container = new VBox(6, titleLabel, listPane);
        container.setAlignment(Pos.CENTER_LEFT);

        root = new HBox(container);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(6));

        // Listener واحد على المجموعة (أنظف وأثبت)
        toggleGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) return;

            Object ud = newT.getUserData();
            if (!(ud instanceof Integer)) return;

            int idx = (Integer) ud;
            if (idx == selected) {
                // نفس الاختيار → لا تعمل callback مرة ثانية
                refreshStyles();
                return;
            }

            selected = idx;
            refreshStyles();
            this.onSelect.accept(selected);
        });

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
            ToggleButton tb = createDiskButton(i);
            listPane.getChildren().add(tb);
        }

        // Select initial
        selectToggleSilently(selected);
        refreshStyles();
    }

    private ToggleButton createDiskButton(int index) {
        ToggleButton tb = new ToggleButton("Disk " + (index + 1));
        tb.setUserData(index);
        tb.setToggleGroup(toggleGroup);
        tb.setStyle(normalStyle());

        tb.setOnMouseEntered(e -> {
            if (!tb.isSelected()) tb.setStyle(hoverStyle());
        });

        tb.setOnMouseExited(e -> {
            if (!tb.isSelected()) tb.setStyle(normalStyle());
        });

        // لما يتحدد/ينشال تحديده، بس حدّث الستايلات
        tb.selectedProperty().addListener((o, was, now) -> refreshStyles());

        return tb;
    }

    private void refreshStyles() {
        for (var node : listPane.getChildren()) {
            if (node instanceof ToggleButton tb) {
                if (tb.isSelected()) {
                    tb.setStyle(selectedStyle());
                } else {
                    // إذا الماوس فوقه، خليه hover — غير هيك normal
                    if (tb.isHover()) tb.setStyle(hoverStyle());
                    else tb.setStyle(normalStyle());
                }
            }
        }
    }

    private void selectToggleSilently(int index) {
        Toggle toSelect = null;
        for (Toggle t : toggleGroup.getToggles()) {
            if (t.getUserData() instanceof Integer ud && ud == index) {
                toSelect = t;
                break;
            }
        }

        if (toSelect != null) {
            // لا تعمل onSelect هنا لأن listener يعمل عند التغيير؛
            // نحن نستخدمه للتهيئة/التعديل فقط
            toggleGroup.selectToggle(toSelect);
        }
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    // ---------------- public API ----------------
    public HBox getRoot() { return root; }

    public void setOnSelect(IntConsumer c) {
        this.onSelect = (c != null) ? c : i -> {};
    }

    public void setCount(int newCount) {
        this.count = Math.max(0, newCount);
        if (this.count == 0) {
            selected = 0;
        } else if (selected >= this.count) {
            selected = this.count - 1;
        }
        rebuildButtons();
    }

    public void setSelectedIndex(int index) {
        if (count <= 0) return;

        int newSel = clamp(index, 0, count - 1);
        if (newSel == selected) {
            // نفس الاختيار، بس حدّث شكل
            refreshStyles();
            return;
        }

        selected = newSel;
        selectToggleSilently(selected);
        refreshStyles();

        // notify once
        onSelect.accept(selected);
    }

    public int getSelectedIndex() { return selected; }
}