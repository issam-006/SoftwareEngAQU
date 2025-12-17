package fxShield;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.util.Duration;

import java.util.function.IntConsumer;

public final class PhysicalDiskSwitcher {

    private final HBox root;
    private final Button pill;
    private final ContextMenu menu;

    private final Label title;
    private final Label arrow;

    private int count;
    private int selected;
    private IntConsumer onSelect = i -> {};

    /* ================= iOS STYLES ================= */

    private static final String PILL =
            "-fx-background-radius: 999;" +
                    "-fx-padding: 6 14;" +
                    "-fx-background-color: rgba(255,255,255,0.10);" +
                    "-fx-border-color: rgba(255,255,255,0.18);" +
                    "-fx-border-width: 1;" +
                    "-fx-border-radius: 999;";

    private static final String PILL_HOVER =
            "-fx-background-radius: 999;" +
                    "-fx-padding: 6 14;" +
                    "-fx-background-color: rgba(255,255,255,0.16);" +
                    "-fx-border-color: rgba(255,255,255,0.28);" +
                    "-fx-border-width: 1;" +
                    "-fx-border-radius: 999;";

    private static final String MENU_CARD =
            "-fx-background-color: rgba(36,36,48,0.92);" +
                    "-fx-background-insets: 0;" +
                    "-fx-background-radius: 22;" +
                    "-fx-padding: 10;" +
                    "-fx-border-color: rgba(255,255,255,0.12);" +
                    "-fx-border-width: 1;" +
                    "-fx-border-radius: 22;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 28, 0.35, 0, 16);";

    private static final String MENU_CARD_STRONG =
            MENU_CARD +
                    "-fx-background-insets: 0;" +
                    "-fx-padding: 10;";

    private static final String TRANSPARENT_OVERRIDE =
            "-fx-background-color: transparent;" +
                    "-fx-background-insets: 0;" +
                    "-fx-padding: 0;";

    private static final String ROW_NORMAL =
            "-fx-background-color: transparent;" +
                    "-fx-background-radius: 16;" +
                    "-fx-padding: 10 14;";

    private static final String ROW_HOVER =
            "-fx-background-color: rgba(147,197,253,0.14);" +
                    "-fx-background-radius: 16;" +
                    "-fx-padding: 10 14;";

    private static final String ROW_SELECTED =
            "-fx-background-color: linear-gradient(to right, rgba(147,197,253,0.55), rgba(167,139,250,0.55));" +
                    "-fx-background-radius: 16;" +
                    "-fx-padding: 10 14;";

    private static final String TXT =
            "-fx-text-fill: rgba(233,216,255,0.95);" +
                    "-fx-font-size: 13;" +
                    "-fx-font-weight: 600;";

    /* ================================================= */

    public PhysicalDiskSwitcher(int initialCount, int initialIndex, IntConsumer onSelect) {
        this.count = Math.max(0, initialCount);
        this.selected = clamp(initialIndex, 0, Math.max(0, count - 1));
        if (onSelect != null) this.onSelect = onSelect;

        // ===== UI labels =====
        title = new Label("Disks");
        title.setFont(Font.font("Segoe UI", 13));
        title.setStyle("-fx-text-fill: white; -fx-font-weight: 700;");

        arrow = new Label("▾");
        arrow.setFont(Font.font("Segoe UI", 12));
        arrow.setStyle("-fx-text-fill: rgba(255,255,255,0.8);");

        HBox pillContent = new HBox(8, title, arrow);
        pillContent.setAlignment(Pos.CENTER);

        // ===== Menu must be created BEFORE hover handlers that reference it =====
        menu = new ContextMenu();
        menu.setAutoHide(true);
        menu.setHideOnEscape(true);

        // ===== Pill button =====
        pill = new Button();
        pill.setGraphic(pillContent);
        pill.setStyle(PILL);
        pill.setFocusTraversable(false);

        pill.setOnMouseEntered(e -> pill.setStyle(PILL_HOVER));
        pill.setOnMouseExited(e -> {
            if (!menu.isShowing()) pill.setStyle(PILL);
        });

        // ===== Menu show/hide styles =====
        menu.setOnShowing(e -> {
            arrow.setText("▴");
            pill.setStyle(PILL_HOVER);
        });

        menu.setOnShown(e -> Platform.runLater(() -> {
            Node skin = (menu.getSkin() != null) ? menu.getSkin().getNode() : null;
            if (skin == null) return;

            // 1) Apply card style to skin
            skin.setStyle(MENU_CARD_STRONG);

            // 2) Force inner layers transparent (kills white rectangles on many JavaFX skins)
            for (Node n : skin.lookupAll(
                    ".root, .popup-container, .context-menu, .menu, .menu-item, .menu-item-container," +
                            ".scroll-pane, .viewport, .content, .corner, .list-view"
            )) {
                n.setStyle(TRANSPARENT_OVERRIDE);
            }

            // 3) Re-apply card style after forcing transparency
            skin.setStyle(MENU_CARD_STRONG);

            // animation (NO {{ }} because FadeTransition/TranslateTransition can be final)
            skin.setOpacity(0);
            skin.setTranslateY(-8);

            FadeTransition ft = new FadeTransition(Duration.millis(140), skin);
            ft.setFromValue(0);
            ft.setToValue(1);

            TranslateTransition tt = new TranslateTransition(Duration.millis(160), skin);
            tt.setFromY(-8);
            tt.setToY(0);

            new ParallelTransition(ft, tt).play();
        }));

        menu.setOnHidden(e -> {
            arrow.setText("▾");
            pill.setStyle(PILL);
        });

        // ===== Button action =====
        pill.setOnAction(e -> {
            if (!menu.isShowing()) menu.show(pill, Side.BOTTOM, 0, 10);
            else menu.hide();
        });

        // ===== Root =====
        root = new HBox(pill);
        root.setAlignment(Pos.CENTER_LEFT);

        // ===== Keyboard navigation =====
        root.setOnKeyPressed(e -> {
            if (count <= 0) return;

            if (e.getCode() == KeyCode.LEFT) {
                setSelectedIndex(Math.max(0, selected - 1));
                e.consume();
            } else if (e.getCode() == KeyCode.RIGHT) {
                setSelectedIndex(Math.min(count - 1, selected + 1));
                e.consume();
            } else if (e.getCode().isDigitKey()) {
                int idx = digitToIndex(e.getCode());
                if (idx >= 0 && idx < count) setSelectedIndex(idx);
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                pill.fire();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                menu.hide();
                e.consume();
            }
        });

        rebuildMenu();
    }

    private void rebuildMenu() {
        menu.getItems().clear();

        if (count <= 0) {
            MenuItem none = new MenuItem("No disks");
            none.setDisable(true);
            menu.getItems().add(none);
            return;
        }

        for (int i = 0; i < count; i++) {
            final int idx = i;

            Label txt = new Label("Disk " + idx);
            txt.setStyle(TXT);

            Label check = new Label(idx == selected ? "✓" : "");
            check.setStyle("-fx-text-fill: white; -fx-font-weight: 900;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(10, txt, spacer, check);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMinWidth(240);
            row.setStyle(idx == selected ? ROW_SELECTED : ROW_NORMAL);

            row.setOnMouseEntered(e -> row.setStyle(idx == selected ? ROW_SELECTED : ROW_HOVER));
            row.setOnMouseExited(e -> row.setStyle(idx == selected ? ROW_SELECTED : ROW_NORMAL));

            CustomMenuItem item = new CustomMenuItem(row, true);
            item.setOnAction(e -> {
                setSelectedIndex(idx);
                menu.hide();
            });

            menu.getItems().add(item);
        }
    }

    private void refresh() {
        rebuildMenu();
        try { onSelect.accept(selected); } catch (Exception ignored) {}
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int digitToIndex(KeyCode code) {
        return switch (code) {
            case DIGIT0, NUMPAD0 -> 0;
            case DIGIT1, NUMPAD1 -> 1;
            case DIGIT2, NUMPAD2 -> 2;
            case DIGIT3, NUMPAD3 -> 3;
            case DIGIT4, NUMPAD4 -> 4;
            case DIGIT5, NUMPAD5 -> 5;
            case DIGIT6, NUMPAD6 -> 6;
            case DIGIT7, NUMPAD7 -> 7;
            case DIGIT8, NUMPAD8 -> 8;
            case DIGIT9, NUMPAD9 -> 9;
            default -> -1;
        };
    }

    /* ================= API ================= */

    public HBox getRoot() { return root; }

    public void setOnSelect(IntConsumer c) {
        this.onSelect = (c != null) ? c : i -> {};
    }

    public void setCount(int c) {
        count = Math.max(0, c);
        selected = 0; // Disk 0 default
        refresh();
    }

    public void setSelectedIndex(int idx) {
        selected = clamp(idx, 0, count - 1);
        refresh();
    }

    public int getSelectedIndex() { return selected; }
}
