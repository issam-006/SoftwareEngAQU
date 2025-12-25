package fxShield.UI;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

public abstract class BaseCard {

    // Debug flags (opt-in via JVM -D properties)
    //
    // Compact:
    //   -DfxShield.debugCompact=true       -> log compact state changes
    //   -DfxShield.debugCompactStack=true  -> also dump stack traces
    //
    // Font:
    //   -DfxShield.debugFont=true          -> log font changes
    //   -DfxShield.debugFontStack=true     -> also dump stack traces
    //
    // Scale:
    //   -DfxShield.debugScale=true         -> log scale changes
    //   -DfxShield.debugScaleStack=true    -> also dump stack traces
    protected static final boolean DEBUG_COMPACT = Boolean.getBoolean("fxShield.debugCompact");
    protected static final boolean DEBUG_COMPACT_STACKTRACE = Boolean.getBoolean("fxShield.debugCompactStack");
    protected static final boolean DEBUG_FONT_CHANGES = Boolean.getBoolean("fxShield.debugFont");
    protected static final boolean DEBUG_FONT_STACKTRACE = Boolean.getBoolean("fxShield.debugFontStack");
    protected static final boolean DEBUG_SCALE_CHANGES = Boolean.getBoolean("fxShield.debugScale");
    protected static final boolean DEBUG_SCALE_STACKTRACE = Boolean.getBoolean("fxShield.debugScaleStack");

    protected static final String COLOR_PRIMARY = StyleConstants.COLOR_PRIMARY;
    protected static final String COLOR_WARN = StyleConstants.COLOR_WARN;
    protected static final String COLOR_DANGER = StyleConstants.COLOR_DANGER;
    protected static final String COLOR_INFO = StyleConstants.COLOR_INFO;

    protected static final String COLOR_TEXT_LIGHT = StyleConstants.COLOR_TEXT_LIGHT;
    protected static final String COLOR_TEXT_MEDIUM = StyleConstants.COLOR_TEXT_MEDIUM;
    protected static final String COLOR_TEXT_DIM = StyleConstants.COLOR_TEXT_DIM;
    protected static final String COLOR_TEXT_MUTED = StyleConstants.COLOR_TEXT_MUTED;

    protected static final String BAR_BG_STYLE = StyleConstants.PROGRESS_BAR_BACKGROUND;
    protected static final String FONT_FAMILY = StyleConstants.FONT_FAMILY;

    public abstract Region getRoot();
    public abstract void setCompact(boolean compact);

    protected static void debugCompact(Object who, boolean compact) {
        if (!DEBUG_COMPACT && !DEBUG_COMPACT_STACKTRACE) return;

        System.err.println("[COMPACT] " + who + " setCompact(" + compact + ")");
        if (DEBUG_COMPACT_STACKTRACE) Thread.dumpStack();
    }

    // ======== NEW: font/scale probes ========

    protected static void watchFont(Labeled l, String tag) {
        if ((!DEBUG_FONT_CHANGES && !DEBUG_FONT_STACKTRACE) || l == null) return;
        l.fontProperty().addListener((obs, oldF, newF) -> {
            System.err.println("[FONT] " + tag + "  " + oldF + "  ->  " + newF);
            if (DEBUG_FONT_STACKTRACE) Thread.dumpStack();
        });
    }

    protected static void watchScale(Node n, String tag) {
        if ((!DEBUG_SCALE_CHANGES && !DEBUG_SCALE_STACKTRACE) || n == null) return;

        ChangeListener<Number> cl = (obs, o, v) -> {
            System.err.println("[SCALE] " + tag + " scaleX=" + n.getScaleX() + " scaleY=" + n.getScaleY());
            if (DEBUG_SCALE_STACKTRACE) Thread.dumpStack();
        };

        n.scaleXProperty().addListener(cl);
        n.scaleYProperty().addListener(cl);
    }

    // ======== existing helpers ========

    protected static void setBarAccentColor(ProgressBar bar, String accentHex) {
        if (bar == null) return;
        String hex = (accentHex == null || accentHex.isBlank()) ? COLOR_PRIMARY : accentHex;
        bar.setStyle(BAR_BG_STYLE + "-fx-accent: " + hex + ";");
    }

    protected static String getColorByUsage(double percent) {
        if (percent >= 85) return COLOR_DANGER;
        if (percent >= 60) return COLOR_WARN;
        return COLOR_PRIMARY;
    }

    protected static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return min;
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    protected static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    protected static Color colorFromHex(String hex) {
        if (hex == null || hex.isBlank()) return Color.web("#ffffff");
        return Color.web(hex);
    }
}
