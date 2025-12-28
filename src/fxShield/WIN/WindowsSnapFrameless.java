package fxShield.WIN;

import com.sun.jna.IntegerType;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WindowsSnapFrameless {

    private WindowsSnapFrameless() {}

    /** LONG_PTR compatible with any JNA version */
    public static final class LONG_PTR extends IntegerType {
        public LONG_PTR() { this(0); }
        public LONG_PTR(long value) { super(Native.POINTER_SIZE, value, true); }
    }

    public static void install(Stage stage,
                               Scene scene,
                               Node dragRegion,
                               Node maximizeButton,
                               int resizeBorderPx,
                               List<Node> excludeFromDrag) {
        if (stage == null || scene == null) return;
        if (!WindowsUtils.isWindows()) return;

        int border = Math.max(8, resizeBorderPx);

        Platform.runLater(() -> {
            HWND hwnd = findHwndForStage(stage);
            if (hwnd == null) return;

            try { stage.setFullScreen(false); } catch (Throwable ignored) {}
            try { stage.setResizable(true); } catch (Throwable ignored) {}

            enableResizableStyles(hwnd);

            if (dragRegion != null) {
                installNativeDrag(hwnd, dragRegion, excludeFromDrag);
            }
            installNativeResize(hwnd, scene, border);

            if (maximizeButton != null) {
                SnapLayoutPopup.install(stage, maximizeButton);
            }
        });
    }

    private static void installNativeDrag(HWND hwnd, Node dragRegion, List<Node> excludes) {
        dragRegion.setOnMousePressed(e -> {
            if (!e.isPrimaryButtonDown()) return;

            Object t = e.getTarget();
            if (t instanceof Node target && excludes != null) {
                for (Node ex : excludes) {
                    if (ex != null && isDescendant(target, ex)) return;
                }
            }

            startNcDrag(hwnd);
            e.consume();
        });
    }

    private static boolean isDescendant(Node target, Node ancestor) {
        Node n = target;
        while (n != null) {
            if (n == ancestor) return true;
            n = n.getParent();
        }
        return false;
    }

    private static void startNcDrag(HWND hwnd) {
        try {
            User32Ex.INSTANCE.ReleaseCapture();
            User32Ex.INSTANCE.SendMessageW(hwnd, WM_NCLBUTTONDOWN, new WPARAM(HTCAPTION), new LPARAM(0));
        } catch (Throwable ignored) {}
    }

    private static void installNativeResize(HWND hwnd, Scene scene, int borderPx) {
        AtomicBoolean resizing = new AtomicBoolean(false);

        scene.setOnMouseMoved(e -> {
            if (resizing.get()) return;
            int hit = hitTest(scene, e.getSceneX(), e.getSceneY(), borderPx);
            scene.setCursor(cursorForHit(hit));
        });

        scene.setOnMousePressed(e -> {
            if (!e.isPrimaryButtonDown()) return;

            int hit = hitTest(scene, e.getSceneX(), e.getSceneY(), borderPx);
            if (hit == HTCLIENT || hit == HTCAPTION) return;

            resizing.set(true);
            try {
                User32Ex.INSTANCE.ReleaseCapture();
                User32Ex.INSTANCE.SendMessageW(hwnd, WM_NCLBUTTONDOWN, new WPARAM(hit), new LPARAM(0));
            } catch (Throwable ignored) {
            } finally {
                resizing.set(false);
            }
            e.consume();
        });
    }

    private static Cursor cursorForHit(int hit) {
        return switch (hit) {
            case HTLEFT, HTRIGHT -> Cursor.H_RESIZE;
            case HTTOP, HTBOTTOM -> Cursor.V_RESIZE;
            case HTTOPLEFT, HTBOTTOMRIGHT -> Cursor.NW_RESIZE;
            case HTTOPRIGHT, HTBOTTOMLEFT -> Cursor.NE_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }

    private static int hitTest(Scene scene, double x, double y, int b) {
        double w = scene.getWidth();
        double h = scene.getHeight();

        boolean left   = x >= 0 && x < b;
        boolean right  = x <= w && x > (w - b);
        boolean top    = y >= 0 && y < b;
        boolean bottom = y <= h && y > (h - b);

        if (top && left) return HTTOPLEFT;
        if (top && right) return HTTOPRIGHT;
        if (bottom && left) return HTBOTTOMLEFT;
        if (bottom && right) return HTBOTTOMRIGHT;

        if (left) return HTLEFT;
        if (right) return HTRIGHT;
        if (top) return HTTOP;
        if (bottom) return HTBOTTOM;

        return HTCLIENT;
    }

    private static void enableResizableStyles(HWND hwnd) {
        try {
            long style = User32Ex.INSTANCE.GetWindowLongPtrW(hwnd, GWL_STYLE).longValue();

            // resizable + max/min
            style |= WS_THICKFRAME | WS_MAXIMIZEBOX | WS_MINIMIZEBOX;

            // remove system caption/menu
            style &= ~WS_SYSMENU;
            style &= ~WS_CAPTION;

            User32Ex.INSTANCE.SetWindowLongPtrW(hwnd, GWL_STYLE, new LONG_PTR(style));
            User32Ex.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0,
                    SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED);
        } catch (Throwable ignored) {}
    }

    private static HWND findHwndForStage(Stage stage) {
        try {
            long pid = ProcessHandle.current().pid();

            // 1) foreground window (often the correct one)
            HWND fg = User32Ex.INSTANCE.GetForegroundWindow();
            if (fg != null) {
                IntByReference p = new IntByReference();
                User32Ex.INSTANCE.GetWindowThreadProcessId(fg, p);
                if (p.getValue() == (int) pid) return fg;
            }

            // 2) fallback: enumerate by pid + title
            String title = stage.getTitle();
            if (title == null) title = "";
            String finalTitle = title.trim();

            HWND[] out = new HWND[1];

            User32Ex.INSTANCE.EnumWindows((h, data) -> {
                IntByReference p = new IntByReference();
                User32Ex.INSTANCE.GetWindowThreadProcessId(h, p);
                if (p.getValue() != (int) pid) return true;

                if (!finalTitle.isEmpty()) {
                    char[] buf = new char[512];
                    User32Ex.INSTANCE.GetWindowTextW(h, buf, buf.length);
                    String t = Native.toString(buf);
                    if (t != null && t.trim().equalsIgnoreCase(finalTitle)) {
                        out[0] = h;
                        return false;
                    }
                    return true;
                } else {
                    out[0] = h;
                    return false;
                }
            }, null);

            return out[0];
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ===== Fake Snap Layout Popup =====
    private static final class SnapLayoutPopup {

        static void install(Stage stage, Node maximizeButton) {
            Popup popup = new Popup();
            popup.setAutoHide(true);
            popup.setHideOnEscape(true);

            SnapGrid grid = new SnapGrid(stage);
            grid.setStyle(
                    "-fx-background-color: rgba(15,23,42,0.98);" +
                            "-fx-background-radius: 14;" +
                            "-fx-padding: 10;" +
                            "-fx-border-color: rgba(255,255,255,0.10);" +
                            "-fx-border-width: 1;" +
                            "-fx-border-radius: 14;" +
                            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.55), 18, 0, 0, 10);"
            );
            popup.getContent().add(grid);

            HoverDelay hover = new HoverDelay(140);

            maximizeButton.setOnMouseEntered(e -> hover.start(() -> {
                if (popup.isShowing()) return;
                var b = maximizeButton.localToScreen(maximizeButton.getBoundsInLocal());
                if (b == null) return;
                popup.show(stage, b.getMinX() - 160, b.getMaxY() + 8);
            }));

            maximizeButton.setOnMouseExited(e -> hover.cancel(() -> {
                if (popup.isShowing()) popup.hide();
            }));
        }

        private static final class SnapGrid extends VBox {
            private final Stage stage;

            SnapGrid(Stage stage) {
                this.stage = stage;
                setSpacing(8);

                var title = new javafx.scene.control.Label("Snap Layout");
                title.setStyle("-fx-text-fill: rgba(226,232,240,1); -fx-font-size: 13; -fx-font-weight: 700;");

                var row1 = new HBox(6);
                var row2 = new HBox(6);

                row1.getChildren().addAll(
                        cell("L", () -> snapHalf(true)),
                        cell("R", () -> snapHalf(false)),
                        cell("4", this::snapQuarter)
                );

                row2.getChildren().addAll(
                        cell("3", this::snapThird),
                        cell("T", () -> snapTopBottom(true)),
                        cell("B", () -> snapTopBottom(false))
                );

                getChildren().addAll(title, row1, row2);
            }

            private Node cell(String text, Runnable action) {
                var l = new javafx.scene.control.Label(text);
                l.setMinSize(34, 34);
                l.setPrefSize(34, 34);
                l.setMaxSize(34, 34);
                l.setStyle(
                        "-fx-alignment: center;" +
                                "-fx-text-fill: rgba(226,232,240,1);" +
                                "-fx-font-weight: 800;" +
                                "-fx-background-color: rgba(255,255,255,0.08);" +
                                "-fx-background-radius: 10;" +
                                "-fx-border-color: rgba(255,255,255,0.10);" +
                                "-fx-border-radius: 10;" +
                                "-fx-cursor: hand;"
                );

                l.setOnMouseEntered(e -> l.setStyle(
                        "-fx-alignment: center;" +
                                "-fx-text-fill: rgba(255,255,255,1);" +
                                "-fx-font-weight: 900;" +
                                "-fx-background-color: rgba(59,130,246,0.22);" +
                                "-fx-background-radius: 10;" +
                                "-fx-border-color: rgba(59,130,246,0.35);" +
                                "-fx-border-radius: 10;" +
                                "-fx-cursor: hand;"
                ));

                l.setOnMouseExited(e -> l.setStyle(
                        "-fx-alignment: center;" +
                                "-fx-text-fill: rgba(226,232,240,1);" +
                                "-fx-font-weight: 800;" +
                                "-fx-background-color: rgba(255,255,255,0.08);" +
                                "-fx-background-radius: 10;" +
                                "-fx-border-color: rgba(255,255,255,0.10);" +
                                "-fx-border-radius: 10;" +
                                "-fx-cursor: hand;"
                ));

                l.setOnMouseClicked(e -> {
                    try { action.run(); } catch (Throwable ignored) {}
                });

                return l;
            }

            private Rectangle2D workArea() {
                double cx = stage.getX() + stage.getWidth() / 2.0;
                double cy = stage.getY() + stage.getHeight() / 2.0;

                Screen best = null;
                for (Screen s : Screen.getScreens()) {
                    Rectangle2D b = s.getVisualBounds();
                    if (b.contains(cx, cy)) { best = s; break; }
                }
                if (best == null) best = Screen.getPrimary();
                return best.getVisualBounds();
            }

            private void snapHalf(boolean left) {
                Rectangle2D r = workArea();
                double w = r.getWidth() / 2.0;
                setBounds(left ? r.getMinX() : (r.getMinX() + w), r.getMinY(), w, r.getHeight());
            }

            private void snapTopBottom(boolean top) {
                Rectangle2D r = workArea();
                double h = r.getHeight() / 2.0;
                setBounds(r.getMinX(), top ? r.getMinY() : (r.getMinY() + h), r.getWidth(), h);
            }

            private void snapQuarter() {
                Rectangle2D r = workArea();
                setBounds(r.getMinX(), r.getMinY(), r.getWidth() / 2.0, r.getHeight() / 2.0);
            }

            private void snapThird() {
                Rectangle2D r = workArea();
                setBounds(r.getMinX(), r.getMinY(), r.getWidth() / 3.0, r.getHeight());
            }

            private void setBounds(double x, double y, double w, double h) {
                Platform.runLater(() -> {
                    try { stage.setIconified(false); } catch (Throwable ignored) {}
                    try { stage.setMaximized(false); } catch (Throwable ignored) {}
                    stage.setX(x);
                    stage.setY(y);
                    stage.setWidth(Math.max(320, w));
                    stage.setHeight(Math.max(220, h));
                });
            }
        }

        private static final class HoverDelay {
            private final long ms;
            private final ScheduledExecutorService es =
                    Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "fxShield-snap-hover");
                        t.setDaemon(true);
                        return t;
                    });

            private ScheduledFuture<?> f;

            HoverDelay(long ms) { this.ms = Math.max(0, ms); }

            void start(Runnable show) {
                cancel(null);
                f = es.schedule(() -> Platform.runLater(show), ms, TimeUnit.MILLISECONDS);
            }

            void cancel(Runnable after) {
                if (f != null) {
                    f.cancel(true);
                    f = null;
                }
                if (after != null) Platform.runLater(after);
            }
        }
    }

    // ===== User32 minimal =====
    private interface User32Ex extends Library {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class);

        HWND GetForegroundWindow();

        boolean EnumWindows(WinUser.WNDENUMPROC lpEnumFunc, Pointer data);
        int GetWindowTextW(HWND hWnd, char[] lpString, int nMaxCount);
        int GetWindowThreadProcessId(HWND hWnd, IntByReference lpdwProcessId);

        boolean ReleaseCapture();
        LRESULT SendMessageW(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam);

        LONG_PTR GetWindowLongPtrW(HWND hWnd, int nIndex);
        LONG_PTR SetWindowLongPtrW(HWND hWnd, int nIndex, LONG_PTR dwNewLong);

        boolean SetWindowPos(HWND hWnd, HWND hWndInsertAfter, int X, int Y, int cx, int cy, int uFlags);
    }

    private static final int WM_NCLBUTTONDOWN = 0x00A1;

    private static final int HTCLIENT  = 1;
    private static final int HTCAPTION = 2;

    private static final int HTLEFT        = 10;
    private static final int HTRIGHT       = 11;
    private static final int HTTOP         = 12;
    private static final int HTTOPLEFT     = 13;
    private static final int HTTOPRIGHT    = 14;
    private static final int HTBOTTOM      = 15;
    private static final int HTBOTTOMLEFT  = 16;
    private static final int HTBOTTOMRIGHT = 17;

    private static final int GWL_STYLE = -16;

    private static final long WS_THICKFRAME  = 0x00040000L;
    private static final long WS_MAXIMIZEBOX = 0x00010000L;
    private static final long WS_MINIMIZEBOX = 0x00020000L;
    private static final long WS_SYSMENU     = 0x00080000L;
    private static final long WS_CAPTION     = 0x00C00000L;

    private static final int SWP_NOSIZE       = 0x0001;
    private static final int SWP_NOMOVE       = 0x0002;
    private static final int SWP_NOZORDER     = 0x0004;
    private static final int SWP_FRAMECHANGED = 0x0020;
}
