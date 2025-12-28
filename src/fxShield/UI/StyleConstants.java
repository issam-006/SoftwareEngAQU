package fxShield.UI;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Centralized style constants for the fxShield application.
 * Provides consistent color palette, typography, and reusable CSS styles
 * across all UI components, dialogs, and cards.
 *
 * IMPORTANT:
 * - Keep fonts as Font objects (setFont) and avoid embedding font properties in CSS strings.
 * - CSS strings here should focus on colors/background/border/shadow only.
 */
public final class StyleConstants {

    private StyleConstants() {
        throw new AssertionError("StyleConstants is a utility class and should not be instantiated");
    }

    // =========================================================================
    // COLOR PALETTE
    // =========================================================================

    public static final String COLOR_PRIMARY = "#a78bfa";
    public static final String COLOR_WARN    = "#fb923c";
    public static final String COLOR_DANGER  = "#f97373";
    public static final String COLOR_INFO    = "#7dd3fc";
    public static final String COLOR_SUCCESS = "#22c55e";

    public static final String COLOR_TEXT_LIGHT     = "#f5e8ff";
    public static final String COLOR_TEXT_MEDIUM    = "#e9d8ff";
    public static final String COLOR_TEXT_DIM       = "#cbb8ff";
    public static final String COLOR_TEXT_MUTED     = "#d5c8f7";
    public static final String COLOR_TEXT_WHITE     = "#e5e7eb";
    public static final String COLOR_TEXT_SECONDARY = "#9ca3af";
    public static final String COLOR_TEXT_TERTIARY  = "#64748b";

    public static final String COLOR_BLUE        = "#3b82f6";
    public static final String COLOR_BLUE_DARK   = "#2563eb";
    public static final String COLOR_BLUE_DARKER = "#1d4ed8";

    public static final String COLOR_PURPLE     = "#7c3aed";
    public static final String COLOR_LIGHT_BLUE = "#60a5fa";
    public static final String COLOR_SKY        = "#93C5FD";
    public static final String COLOR_AMBER      = "#fbbf24";

    public static final String BG_DARK_BLUE  = "#020617";
    public static final String BG_DEEP_BLUE  = "#0b1224";
    public static final String BG_DARK_SLATE = "#0f172a";
    public static final String BG_DARK_GRAY  = "#111827";
    public static final String BG_VERY_DARK  = "#14161c";

    // =========================================================================
    // TYPOGRAPHY
    // =========================================================================

    public static final String FONT_FAMILY = "Segoe UI";
    public static final String FONT_EMOJI  = "Segoe UI Emoji";

    // Base cached fonts (avoid repeated allocations across UI)
    public static final Font FONT_TITLE_52_BOLD      = Font.font(FONT_FAMILY, FontWeight.BOLD, 52);
    public static final Font FONT_TITLE_22_BOLD      = Font.font(FONT_FAMILY, FontWeight.BOLD, 22);
    public static final Font FONT_EXTRA_BOLD_18      = Font.font(FONT_FAMILY, FontWeight.EXTRA_BOLD, 18);
    public static final Font FONT_BOLD_18            = Font.font(FONT_FAMILY, FontWeight.BOLD, 18);
    public static final Font FONT_EMOJI_18           = Font.font(FONT_EMOJI, FontWeight.NORMAL, 18);
    public static final Font FONT_HEADER_22          = Font.font(FONT_FAMILY, FontWeight.NORMAL, 22);

    public static final Font FONT_CARD_TITLE_20_BOLD = Font.font(FONT_FAMILY, FontWeight.BOLD, 20);
    public static final Font FONT_TITLE_17_BOLD      = Font.font(FONT_FAMILY, FontWeight.BOLD, 17);
    public static final Font FONT_CARD_TITLE_16_BOLD = Font.font(FONT_FAMILY, FontWeight.BOLD, 16);
    public static final Font FONT_CARD_TITLE_15_BOLD = Font.font(FONT_FAMILY, FontWeight.BOLD, 15);
    public static final Font FONT_CARD_TITLE_14_BOLD = Font.font(FONT_FAMILY, FontWeight.BOLD, 14);

    public static final Font FONT_VALUE_18           = Font.font(FONT_FAMILY, FontWeight.NORMAL, 18);
    public static final Font FONT_VALUE_17           = Font.font(FONT_FAMILY, FontWeight.NORMAL, 17);
    public static final Font FONT_VALUE_15           = Font.font(FONT_FAMILY, FontWeight.NORMAL, 15);
    public static final Font FONT_VALUE_14           = Font.font(FONT_FAMILY, FontWeight.NORMAL, 14);

    public static final Font FONT_BODY_15            = Font.font(FONT_FAMILY, FontWeight.NORMAL, 15);
    public static final Font FONT_BODY_13            = Font.font(FONT_FAMILY, FontWeight.NORMAL, 13);
    public static final Font FONT_BODY_12            = Font.font(FONT_FAMILY, FontWeight.NORMAL, 12);
    public static final Font FONT_BODY_11            = Font.font(FONT_FAMILY, FontWeight.NORMAL, 11);

    public static final Font FONT_BTN_13_BOLD        = Font.font(FONT_FAMILY, FontWeight.BOLD, 13);
    public static final Font FONT_BTN_12_BOLD        = Font.font(FONT_FAMILY, FontWeight.BOLD, 12);
    public static final Font FONT_BTN_11_BOLD        = Font.font(FONT_FAMILY, FontWeight.BOLD, 11);

    public static final Font FONT_SMALL_11           = FONT_BODY_11;

    // Semantic aliases (keep names used across the project, but reuse base fonts)
    public static final Font FONT_DISK_TITLE_REG        = FONT_CARD_TITLE_20_BOLD;
    public static final Font FONT_DISK_USED_REG         = FONT_VALUE_17;
    public static final Font FONT_DISK_ACTIVE_REG       = FONT_VALUE_17;
    public static final Font FONT_DISK_SPACE_REG        = FONT_BODY_13;

    public static final Font FONT_DISK_TITLE_COMPACT    = FONT_CARD_TITLE_15_BOLD;
    public static final Font FONT_DISK_USED_COMPACT     = FONT_VALUE_14;
    public static final Font FONT_DISK_ACTIVE_COMPACT   = FONT_VALUE_14;
    public static final Font FONT_DISK_SPACE_COMPACT    = FONT_BODY_11;

    public static final Font FONT_APP_TITLE             = FONT_TITLE_22_BOLD;
    public static final Font FONT_SECTION_TITLE         = FONT_TITLE_22_BOLD;

    public static final Font FONT_ACTION_TITLE_REG      = FONT_CARD_TITLE_16_BOLD;
    public static final Font FONT_ACTION_DESC_REG       = FONT_BODY_13;
    public static final Font FONT_ACTION_BTN_REG        = FONT_BTN_12_BOLD;

    public static final Font FONT_ACTION_TITLE_COMPACT  = FONT_CARD_TITLE_14_BOLD;
    public static final Font FONT_ACTION_DESC_COMPACT   = FONT_BODY_12;
    public static final Font FONT_ACTION_BTN_COMPACT    = FONT_BTN_11_BOLD;

    public static final Font FONT_DIALOG_TITLE          = FONT_BOLD_18;
    public static final Font FONT_DIALOG_SUBTITLE       = FONT_BODY_12;
    public static final Font FONT_DIALOG_BUTTON         = FONT_BTN_13_BOLD;

    public static final Font FONT_METER_TITLE_REG       = FONT_CARD_TITLE_20_BOLD;
    public static final Font FONT_METER_VALUE_REG       = FONT_VALUE_18;
    public static final Font FONT_METER_EXTRA_REG       = FONT_VALUE_14;

    public static final Font FONT_METER_TITLE_COMPACT   = FONT_CARD_TITLE_15_BOLD;
    public static final Font FONT_METER_VALUE_COMPACT   = FONT_VALUE_14;
    public static final Font FONT_METER_EXTRA_COMPACT   = FONT_BODY_11;

    // =========================================================================
    // COMMON STYLES
    // =========================================================================

    public static final String PROGRESS_BAR_BACKGROUND =
            "-fx-control-inner-background: rgba(255,255,255,0.08);";

    public static final String BUTTON_TRANSPARENT =
            "-fx-background-color: transparent;" +
                    "-fx-text-fill: white;" +
                    "-fx-padding: 0;";

    public static final String BUTTON_PRIMARY =
            "-fx-background-color: #2563eb;" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 999;" +
                    "-fx-padding: 8 18;" +
                    "-fx-cursor: hand;";

    public static final String BUTTON_PRIMARY_HOVER =
            "-fx-background-color: #1d4ed8;" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 999;" +
                    "-fx-padding: 8 18;" +
                    "-fx-cursor: hand;";

    public static final String BUTTON_SECONDARY =
            "-fx-background-color: transparent;" +
                    "-fx-text-fill: #cbd5e1;" +
                    "-fx-border-color: rgba(255,255,255,0.25);" +
                    "-fx-border-width: 1.2;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 8 18;" +
                    "-fx-cursor: hand;";

    public static final String BUTTON_SECONDARY_HOVER =
            "-fx-background-color: rgba(255,255,255,0.08);" +
                    "-fx-text-fill: #ffffff;" +
                    "-fx-border-color: rgba(255,255,255,0.35);" +
                    "-fx-border-width: 1.2;" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-radius: 999;" +
                    "-fx-padding: 8 18;" +
                    "-fx-cursor: hand;";

    // =========================================================================
    // CARD STYLES
    // =========================================================================

    public static final String CARD_STANDARD =
            "-fx-background-color: linear-gradient(to bottom right, rgba(23, 18, 48, 0.65), rgba(13, 10, 28, 0.85));" +
                    "-fx-background-radius: 28;" +
                    "-fx-border-radius: 28;" +
                    "-fx-border-color: rgba(255,255,255,0.14);" +
                    "-fx-border-width: 1.2;" +
                    "-fx-effect: dropshadow(gaussian, rgba(130, 80, 255, 0.22), 20, 0, 0, 4);";

    public static final String CARD_ACTION_BASE =
            "-fx-background-color: linear-gradient(to bottom right, rgba(15, 15, 35, 0.7), rgba(5, 5, 15, 0.85));" +
                    "-fx-background-radius: 22;" +
                    "-fx-border-radius: 22;" +
                    "-fx-border-width: 1.2;";

    public static final String CARD_ACTION_NORMAL =
            CARD_ACTION_BASE +
                    "-fx-border-color: rgba(255,255,255,0.10);" +
                    "-fx-effect: dropshadow(gaussian, rgba(140,65,255,0.15), 10, 0.2, 0, 0);";

    public static final String CARD_ACTION_HOVER =
            CARD_ACTION_BASE +
                    "-fx-border-color: rgba(255,255,255,0.16);" +
                    "-fx-effect: dropshadow(gaussian, rgba(140,65,255,0.22), 14, 0.25, 0, 0);";

    public static final String CARD_DISK =
            "-fx-background-color: rgba(17,13,34,0.55);" +
                    "-fx-background-radius: 28;" +
                    "-fx-border-radius: 28;" +
                    "-fx-border-color: rgba(255,255,255,0.10);" +
                    "-fx-border-width: 1;" +
                    "-fx-effect: dropshadow(gaussian, rgba(157,110,255,0.28), 25, 0.25, 0, 0);";

    // =========================================================================
    // DIALOG STYLES
    // =========================================================================

    public static final String DIALOG_ROOT =
            "-fx-background-color: linear-gradient(to bottom, #020617, #111827);" +
                    "-fx-background-radius: 18;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 22, 0.25, 0, 8);";

    public static final String DIALOG_LOADING =
            "-fx-background-color: linear-gradient(to bottom right, #020617, #111827);" +
                    "-fx-background-radius: 18;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 22, 0.25, 0, 8);";

    public static final String DIALOG_REBOOT =
            "-fx-background-color: rgba(20,20,30,0.96);" +
                    "-fx-background-radius: 22;" +
                    "-fx-border-color: rgba(255,255,255,0.08);" +
                    "-fx-border-width: 1.2;" +
                    "-fx-border-radius: 22;";

    public static final String DIALOG_MAINTENANCE =
            "-fx-background-color: #14161c;" +
                    "-fx-background-radius: 18;" +
                    "-fx-border-radius: 18;" +
                    "-fx-border-color: #2e3340;" +
                    "-fx-border-width: 1;";

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    public static String progressBarStyle(String accentColor) {
        return PROGRESS_BAR_BACKGROUND + "-fx-accent: " + accentColor + ";";
    }

    public static String colorByUsage(double percent) {
        if (percent >= 85) return COLOR_DANGER;
        if (percent >= 60) return COLOR_WARN;
        return COLOR_PRIMARY;
    }

    public static String buttonStyle(String backgroundColor, String textColor) {
        return "-fx-background-color: " + backgroundColor + ";" +
                "-fx-text-fill: " + textColor + ";" +
                "-fx-background-radius: 999;" +
                "-fx-padding: 8 18;" +
                "-fx-cursor: hand;";
    }
}
