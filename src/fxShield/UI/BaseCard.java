package fxShield.UI;

import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

public abstract class BaseCard {

    protected static final String COLOR_PRIMARY = StyleConstants.COLOR_PRIMARY;
    protected static final String COLOR_WARN    = StyleConstants.COLOR_WARN;
    protected static final String COLOR_DANGER  = StyleConstants.COLOR_DANGER;
    protected static final String COLOR_INFO    = StyleConstants.COLOR_INFO;

    protected static final String COLOR_TEXT_LIGHT  = StyleConstants.COLOR_TEXT_LIGHT;
    protected static final String COLOR_TEXT_MEDIUM = StyleConstants.COLOR_TEXT_MEDIUM;
    protected static final String COLOR_TEXT_DIM    = StyleConstants.COLOR_TEXT_DIM;
    protected static final String COLOR_TEXT_MUTED  = StyleConstants.COLOR_TEXT_MUTED;

    protected static final String BAR_BG_STYLE = StyleConstants.PROGRESS_BAR_BACKGROUND;
    protected static final String FONT_FAMILY = StyleConstants.FONT_FAMILY;

    public abstract Region getRoot();

    public abstract void setCompact(boolean compact);

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

    public static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    protected static Color colorFromHex(String hex) {
        if (hex == null || hex.isBlank()) return Color.WHITE;
        try {
            return Color.web(hex);
        } catch (IllegalArgumentException e) {
            return Color.WHITE;
        }
    }
}
