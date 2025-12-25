package fxShield.UI;

/**
 * Centralized style constants for the fxShield application.
 * Provides consistent color palette, typography, and reusable CSS styles
 * across all UI components, dialogs, and cards.
 * 
 * <p>This class eliminates style duplication and ensures visual consistency
 * throughout the application. All style strings are immutable and thread-safe.</p>
 * 
 * @author fxShield Team
 * @version 2.0
 */
public final class StyleConstants {

    // Prevent instantiation
    private StyleConstants() {
        throw new AssertionError("StyleConstants is a utility class and should not be instantiated");
    }

    // ========== COLOR PALETTE ==========
    
    /** Primary accent color - Purple */
    public static final String COLOR_PRIMARY = "#a78bfa";
    
    /** Warning color - Orange */
    public static final String COLOR_WARN = "#fb923c";
    
    /** Danger/Error color - Red */
    public static final String COLOR_DANGER = "#f97373";
    
    /** Info color - Light Blue */
    public static final String COLOR_INFO = "#7dd3fc";
    
    /** Success color - Green */
    public static final String COLOR_SUCCESS = "#22c55e";
    
    /** Light text color */
    public static final String COLOR_TEXT_LIGHT = "#f5e8ff";
    
    /** Medium text color */
    public static final String COLOR_TEXT_MEDIUM = "#e9d8ff";
    
    /** Dim text color */
    public static final String COLOR_TEXT_DIM = "#cbb8ff";
    
    /** Muted text color */
    public static final String COLOR_TEXT_MUTED = "#d5c8f7";
    
    /** Standard white text */
    public static final String COLOR_TEXT_WHITE = "#e5e7eb";
    
    /** Secondary text color */
    public static final String COLOR_TEXT_SECONDARY = "#9ca3af";
    
    /** Tertiary text color */
    public static final String COLOR_TEXT_TERTIARY = "#64748b";
    
    /** Blue accent */
    public static final String COLOR_BLUE = "#3b82f6";
    
    /** Blue accent (darker) */
    public static final String COLOR_BLUE_DARK = "#2563eb";
    
    /** Blue accent (even darker) */
    public static final String COLOR_BLUE_DARKER = "#1d4ed8";
    
    /** Purple accent */
    public static final String COLOR_PURPLE = "#7c3aed";
    
    /** Light blue accent */
    public static final String COLOR_LIGHT_BLUE = "#60a5fa";
    
    /** Sky blue */
    public static final String COLOR_SKY = "#93C5FD";
    
    /** Yellow/Amber warning */
    public static final String COLOR_AMBER = "#fbbf24";
    
    /** Background - Very dark blue */
    public static final String BG_DARK_BLUE = "#020617";
    
    /** Background - Deep blue */
    public static final String BG_DEEP_BLUE = "#0b1224";
    
    /** Background - Dark slate */
    public static final String BG_DARK_SLATE = "#0f172a";
    
    /** Background - Dark gray */
    public static final String BG_DARK_GRAY = "#111827";
    
    /** Background - Very dark */
    public static final String BG_VERY_DARK = "#14161c";

    // ========== TYPOGRAPHY ==========
    
    /** Primary font family */
    public static final String FONT_FAMILY = "Segoe UI";
    
    /** Emoji font family */
    public static final String FONT_EMOJI = "Segoe UI Emoji";

    // ========== COMMON STYLES ==========
    
    /**
     * Standard progress bar background style.
     * Provides consistent appearance for all progress bars.
     */
    public static final String PROGRESS_BAR_BACKGROUND = 
            "-fx-control-inner-background: rgba(255,255,255,0.08);";
    
    /**
     * Transparent button base style.
     * Used for icon buttons and minimal UI elements.
     */
    public static final String BUTTON_TRANSPARENT = 
            "-fx-background-color: transparent;" +
            "-fx-text-fill: white;" +
            "-fx-padding: 0;";
    
    /**
     * Primary button style with blue background.
     */
    public static final String BUTTON_PRIMARY = 
            "-fx-background-color: #2563eb;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 999;" +
            "-fx-padding: 8 18;" +
            "-fx-cursor: hand;";
    
    /**
     * Primary button hover style.
     */
    public static final String BUTTON_PRIMARY_HOVER = 
            "-fx-background-color: #1d4ed8;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 999;" +
            "-fx-padding: 8 18;" +
            "-fx-cursor: hand;";
    
    /**
     * Secondary button style with transparent background and border.
     */
    public static final String BUTTON_SECONDARY = 
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #cbd5e1;" +
            "-fx-border-color: rgba(255,255,255,0.25);" +
            "-fx-border-width: 1.2;" +
            "-fx-background-radius: 999;" +
            "-fx-border-radius: 999;" +
            "-fx-padding: 8 18;" +
            "-fx-cursor: hand;";
    
    /**
     * Secondary button hover style.
     */
    public static final String BUTTON_SECONDARY_HOVER = 
            "-fx-background-color: rgba(255,255,255,0.08);" +
            "-fx-text-fill: #ffffff;" +
            "-fx-border-color: rgba(255,255,255,0.35);" +
            "-fx-border-width: 1.2;" +
            "-fx-background-radius: 999;" +
            "-fx-border-radius: 999;" +
            "-fx-padding: 8 18;" +
            "-fx-cursor: hand;";

    // ========== CARD STYLES ==========
    
    /**
     * Standard card style with gradient background and shadow.
     * Used for meter cards (CPU, RAM, GPU).
     */
    public static final String CARD_STANDARD = 
            "-fx-background-color: linear-gradient(to bottom right, rgba(23, 18, 48, 0.65), rgba(13, 10, 28, 0.85));" +
            "-fx-background-radius: 28;" +
            "-fx-border-radius: 28;" +
            "-fx-border-color: rgba(255,255,255,0.14);" +
            "-fx-border-width: 1.2;" +
            "-fx-effect: dropshadow(gaussian, rgba(130, 80, 255, 0.22), 20, 0, 0, 4);";
    
    /**
     * Action card base style.
     */
    public static final String CARD_ACTION_BASE = 
            "-fx-background-color: linear-gradient(to bottom right, rgba(15, 15, 35, 0.7), rgba(5, 5, 15, 0.85));" +
            "-fx-background-radius: 22;" +
            "-fx-border-radius: 22;" +
            "-fx-border-width: 1.2;";
    
    /**
     * Action card normal state.
     */
    public static final String CARD_ACTION_NORMAL = 
            CARD_ACTION_BASE +
            "-fx-border-color: rgba(255,255,255,0.10);" +
            "-fx-effect: dropshadow(gaussian, rgba(140,65,255,0.15), 10, 0.2, 0, 0);";
    
    /**
     * Action card hover state.
     */
    public static final String CARD_ACTION_HOVER = 
            CARD_ACTION_BASE +
            "-fx-border-color: rgba(255,255,255,0.16);" +
            "-fx-effect: dropshadow(gaussian, rgba(140,65,255,0.22), 14, 0.25, 0, 0);";
    
    /**
     * Physical disk card style.
     */
    public static final String CARD_DISK = 
            "-fx-background-color: rgba(17,13,34,0.55);" +
            "-fx-background-radius: 28;" +
            "-fx-border-radius: 28;" +
            "-fx-border-color: rgba(255,255,255,0.10);" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(157,110,255,0.28), 25, 0.25, 0, 0);";

    // ========== DIALOG STYLES ==========
    
    /**
     * Standard dialog root style with gradient and shadow.
     */
    public static final String DIALOG_ROOT = 
            "-fx-background-color: linear-gradient(to bottom, #020617, #111827);" +
            "-fx-background-radius: 18;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 22, 0.25, 0, 8);";
    
    /**
     * Loading dialog root style.
     */
    public static final String DIALOG_LOADING = 
            "-fx-background-color: linear-gradient(to bottom right, #020617, #111827);" +
            "-fx-background-radius: 18;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 22, 0.25, 0, 8);";
    
    /**
     * Reboot dialog root style with darker background.
     */
    public static final String DIALOG_REBOOT = 
            "-fx-background-color: rgba(20,20,30,0.96);" +
            "-fx-background-radius: 22;" +
            "-fx-border-color: rgba(255,255,255,0.08);" +
            "-fx-border-width: 1.2;" +
            "-fx-border-radius: 22";
    
    /**
     * Maintenance dialog card style.
     */
    public static final String DIALOG_MAINTENANCE = 
            "-fx-background-color: #14161c;" +
            "-fx-background-radius: 18;" +
            "-fx-border-radius: 18;" +
            "-fx-border-color: #2e3340;" +
            "-fx-border-width: 1;";

    // ========== UTILITY METHODS ==========
    
    /**
     * Creates a progress bar style with the specified accent color.
     * 
     * @param accentColor the hex color code for the progress bar accent (e.g., "#a78bfa")
     * @return the complete CSS style string for the progress bar
     */
    public static String progressBarStyle(String accentColor) {
        return PROGRESS_BAR_BACKGROUND + "-fx-accent: " + accentColor + ";";
    }
    
    /**
     * Determines the appropriate color based on usage percentage.
     * Returns danger color for high usage (≥85%), warning for medium (≥60%),
     * or primary color for normal usage.
     * 
     * @param percent the usage percentage (0-100)
     * @return the hex color code for the given usage level
     */
    public static String colorByUsage(double percent) {
        if (percent >= 85) return COLOR_DANGER;
        if (percent >= 60) return COLOR_WARN;
        return COLOR_PRIMARY;
    }
    
    /**
     * Creates a button style with custom background color.
     * 
     * @param backgroundColor the background color in CSS format
     * @param textColor the text color in CSS format
     * @return the complete CSS style string for the button
     */
    public static String buttonStyle(String backgroundColor, String textColor) {
        return "-fx-background-color: " + backgroundColor + ";" +
               "-fx-text-fill: " + textColor + ";" +
               "-fx-background-radius: 999;" +
               "-fx-padding: 8 18;" +
               "-fx-cursor: hand;";
    }
}
