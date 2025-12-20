package fxShield;

import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * Abstract base class for UI card components.
 * Provides common styling constants, color schemes, and utility methods
 * shared by MeterCard, ActionCard, and PhysicalDiskCard.
 */
public abstract class BaseCard {

    // Common color scheme
    protected static final String COLOR_PRIMARY = "#a78bfa";      // Purple accent
    protected static final String COLOR_WARN = "#fb923c";         // Orange warning
    protected static final String COLOR_DANGER = "#f97373";       // Red danger
    protected static final String COLOR_INFO = "#7dd3fc";         // Light blue info
    protected static final String COLOR_TEXT_LIGHT = "#f5e8ff";   // Light text
    protected static final String COLOR_TEXT_MEDIUM = "#e9d8ff";  // Medium text
    protected static final String COLOR_TEXT_DIM = "#cbb8ff";     // Dim text
    protected static final String COLOR_TEXT_MUTED = "#d5c8f7";   // Muted text

    // Common progress bar background style
    protected static final String BAR_BG_STYLE = "-fx-control-inner-background: rgba(255,255,255,0.08);";

    // Common font family
    protected static final String FONT_FAMILY = "Segoe UI";

    /**
     * Returns the root node of this card.
     * Subclasses must implement this to return their specific root container.
     */
    public abstract Region getRoot();

    /**
     * Sets the card to compact or normal mode.
     * Subclasses should override to adjust their layout accordingly.
     */
    public abstract void setCompact(boolean compact);

    /**
     * Sets the accent color for a progress bar.
     * @param bar the progress bar to style
     * @param accentHex the hex color code (e.g., "#a78bfa")
     */
    protected static void setBarAccentColor(ProgressBar bar, String accentHex) {
        bar.setStyle(BAR_BG_STYLE + "-fx-accent: " + accentHex + ";");

    }

    /**
     * Determines the appropriate color based on usage percentage.
     * @param percent the usage percentage (0-100)
     * @return the hex color code for the given usage level
     */
    protected static String getColorByUsage(double percent) {
        if (percent >= 85) return COLOR_DANGER;
        if (percent >= 60) return COLOR_WARN;
        return COLOR_PRIMARY;
    }

    /**
     * Clamps a value between min and max.
     * @param value the value to clamp
     * @param min the minimum allowed value
     * @param max the maximum allowed value
     * @return the clamped value
     */
    protected static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /**
     * Clamps a value between 0 and 1.
     * @param value the value to clamp
     * @return the clamped value
     */
    protected static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    /**
     * Converts a hex color string to a JavaFX Color.
     * @param hex the hex color code (e.g., "#a78bfa")
     * @return the JavaFX Color object
     */
    protected static Color colorFromHex(String hex) {
        return Color.web(hex);
    }
}
