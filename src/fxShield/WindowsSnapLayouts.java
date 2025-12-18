package fxShield;

import com.sun.jna.CallbackReference;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * Native helper to support Windows 11 Snap Layouts on custom-decorated JavaFX stages.
 * It subclasses the WndProc and handles WM_NCHITTEST to report the maximize button position.
 */
public final class WindowsSnapLayouts {

    private static final User32 u32 = User32.INSTANCE;

    private static final int WM_NCHITTEST = 0x0084;
    private static final int WM_NCLBUTTONDOWN = 0x00A1;
    private static final int WM_NCLBUTTONUP = 0x00A2;
    
    private static final int HTCAPTION = 2;
    private static final int HTCLIENT = 1;
    private static final int HTMAXBUTTON = 9;

    private static final java.util.Map<HWND, WindowsSnapLayouts> instances = new java.util.HashMap<>();

    private HWND hwnd;
    private Pointer oldWndProc;
    private WindowProc newWndProc;
    
    private Node maxButton;
    private Node dragArea;
    private final java.util.List<Node> interactives = new java.util.ArrayList<>();
    private Stage stage;

    private volatile double maxMinX, maxMaxX, maxMinY, maxMaxY;
    private volatile double dragMinX, dragMaxX, dragMinY, dragMaxY;
    private final java.util.Map<Node, double[]> interactiveBounds = new java.util.concurrent.ConcurrentHashMap<>();

    public WindowsSnapLayouts() {}

    public WindowsSnapLayouts install(Stage stage, Node maxButton) {
        this.stage = stage;
        this.maxButton = maxButton;

        // Listen for changes to update cached bounds
        maxButton.localToSceneTransformProperty().addListener((obs, old, newVal) -> updateBounds());
        stage.xProperty().addListener((obs, old, newVal) -> updateBounds());
        stage.yProperty().addListener((obs, old, newVal) -> updateBounds());
        stage.widthProperty().addListener((obs, old, newVal) -> updateBounds());
        stage.heightProperty().addListener((obs, old, newVal) -> updateBounds());

        // Give JavaFX time to show and create the native window
        Platform.runLater(() -> {
            try {
                updateBounds();
                String title = stage.getTitle();
                if (title == null || title.isEmpty()) title = "FX Shield";
                
                hwnd = u32.FindWindow(null, title);
                if (hwnd == null) {
                    return;
                }

                if (instances.containsKey(hwnd)) {
                    WindowsSnapLayouts existing = instances.get(hwnd);
                    existing.maxButton = this.maxButton;
                    existing.dragArea = this.dragArea;
                    existing.interactives.clear();
                    existing.interactives.addAll(this.interactives);
                    existing.interactiveBounds.clear();
                    existing.updateBounds();
                    return;
                }

                newWndProc = this::wndProc;
                
                // Subclassing
                oldWndProc = u32.GetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC).toPointer();
                u32.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, CallbackReference.getFunctionPointer(newWndProc));
                
                instances.put(hwnd, this);
                System.out.println("[SnapLayouts] Installed for HWND: " + hwnd);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return this;
    }

    public void setDragArea(Node node) {
        this.dragArea = node;
        if (node != null) {
            node.localToSceneTransformProperty().addListener((obs, old, newVal) -> updateBounds());
        }
        updateBounds();
    }

    public void addInteractive(Node node) {
        if (node == null) return;
        if (!interactives.contains(node)) {
            interactives.add(node);
            node.localToSceneTransformProperty().addListener((obs, old, newVal) -> updateBounds());
            updateBounds();
        }
    }

    private void updateBounds() {
        if (Platform.isFxApplicationThread()) {
            if (maxButton != null) {
                Bounds b = maxButton.localToScreen(maxButton.getBoundsInLocal());
                if (b != null) {
                    maxMinX = b.getMinX(); maxMaxX = b.getMaxX();
                    maxMinY = b.getMinY(); maxMaxY = b.getMaxY();
                }
            }
            if (dragArea != null) {
                Bounds b = dragArea.localToScreen(dragArea.getBoundsInLocal());
                if (b != null) {
                    dragMinX = b.getMinX(); dragMaxX = b.getMaxX();
                    dragMinY = b.getMinY(); dragMaxY = b.getMaxY();
                }
            }
            for (Node n : interactives) {
                Bounds b = n.localToScreen(n.getBoundsInLocal());
                if (b != null) {
                    interactiveBounds.put(n, new double[]{b.getMinX(), b.getMaxX(), b.getMinY(), b.getMaxY()});
                }
            }
        } else {
            Platform.runLater(this::updateBounds);
        }
    }

    private LRESULT wndProc(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
        switch (uMsg) {
            case WM_NCHITTEST: {
                int x = (short) (lParam.longValue() & 0xFFFF);
                int y = (short) ((lParam.longValue() >> 16) & 0xFFFF);

                if (isOverMaximizeButton(x, y)) {
                    return new LRESULT(HTMAXBUTTON);
                }
                if (isOverInteractive(x, y)) {
                    return new LRESULT(HTCLIENT);
                }
                if (isOverDragArea(x, y)) {
                    return new LRESULT(HTCAPTION);
                }
                break;
            }
            case WM_NCLBUTTONDOWN: {
                int ht = wParam.intValue();
                if (ht == HTMAXBUTTON) {
                    return new LRESULT(0);
                }
                break;
            }
            case WM_NCLBUTTONUP: {
                int ht = wParam.intValue();
                if (ht == HTMAXBUTTON) {
                    Platform.runLater(() -> {
                        stage.setFullScreen(!stage.isFullScreen());
                    });
                    return new LRESULT(0);
                }
                break;
            }
        }
        return u32.CallWindowProc(oldWndProc, hwnd, uMsg, wParam, lParam);
    }

    private boolean isOverMaximizeButton(int screenX, int screenY) {
        if (maxButton == null || !maxButton.isVisible()) return false;
        return screenX >= maxMinX && screenX <= maxMaxX &&
               screenY >= maxMinY && screenY <= maxMaxY;
    }

    private boolean isOverDragArea(int screenX, int screenY) {
        if (dragArea == null || !dragArea.isVisible()) return false;
        return screenX >= dragMinX && screenX <= dragMaxX &&
               screenY >= dragMinY && screenY <= dragMaxY;
    }

    private boolean isOverInteractive(int screenX, int screenY) {
        for (double[] b : interactiveBounds.values()) {
            if (screenX >= b[0] && screenX <= b[1] && screenY >= b[2] && screenY <= b[3]) {
                return true;
            }
        }
        return false;
    }
}
