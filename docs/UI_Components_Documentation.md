# UI Components Documentation

## Overview

This document covers all UI components in the `fxShield.UI` package:

- **BaseCard** - Abstract base class for all card components
- **MeterCard** - Real-time metric display cards (CPU, RAM, GPU)
- **ActionCard** - Interactive action buttons with hover effects
- **TopBarIcons** - Top bar icon buttons (Info, Settings)
- **StyleConstants** - Centralized style constants and color palette

---

# BaseCard

## Overview

**Package:** `fxShield.UI`
**Class:** `BaseCard`
**Type:** `public abstract class`

---

## Purpose

`BaseCard` is the abstract base class for all card-based UI components in FX Shield. It provides:

- **Shared color constants** from StyleConstants
- **Common utility methods** for progress bars and color management
- **Consistent styling** across all card types
- **Compact mode support** interface

---

## Abstract Methods

### `getRoot()`
Returns the root JavaFX node for this card.

**Returns:** `Region` - The root node to add to the scene graph

---

### `setCompact(boolean compact)`
Switches between normal and compact display modes.

**Parameters:**
- `compact` - `true` for compact mode, `false` for normal mode

---

## Protected Constants

### Color Constants

```java
protected static final String COLOR_PRIMARY = "#a78bfa";  // Purple
protected static final String COLOR_WARN    = "#fb923c";  // Orange
protected static final String COLOR_DANGER  = "#f97373";  // Red
protected static final String COLOR_INFO    = "#7dd3fc";  // Light Blue

protected static final String COLOR_TEXT_LIGHT  = "#f5e8ff";
protected static final String COLOR_TEXT_MEDIUM = "#e9d8ff";
protected static final String COLOR_TEXT_DIM    = "#cbb8ff";
protected static final String COLOR_TEXT_MUTED  = "#d5c8f7";
```

---

## Protected Utility Methods

### `setBarAccentColor(ProgressBar bar, String accentHex)`
Sets the accent color of a progress bar.

**Parameters:**
- `bar` - The progress bar to style
- `accentHex` - Hex color code (e.g., "#a78bfa")

**Example:**
```java
ProgressBar bar = new ProgressBar();
setBarAccentColor(bar, COLOR_PRIMARY);
```

---

### `getColorByUsage(double percent)`
Returns appropriate color based on usage percentage.

**Color Thresholds:**
- **0-59%:** Primary (Purple) - Normal
- **60-84%:** Warning (Orange) - High usage
- **85-100%:** Danger (Red) - Critical

**Parameters:**
- `percent` - Usage percentage (0-100)

**Returns:** Hex color string

**Example:**
```java
double cpuUsage = 75.0;
String color = getColorByUsage(cpuUsage); // Returns COLOR_WARN
```

---

### `clamp(double value, double min, double max)`
Clamps a value between min and max bounds.

**Handles:**
- NaN values (returns min)
- Infinite values (returns min)
- Out-of-bounds values

**Parameters:**
- `value` - Value to clamp
- `min` - Minimum bound
- `max` - Maximum bound

**Returns:** Clamped value

---

### `clamp01(double value)`
Clamps a value between 0.0 and 1.0.

**Parameters:**
- `value` - Value to clamp

**Returns:** Clamped value (0.0 to 1.0)

---

### `colorFromHex(String hex)`
Converts hex color string to JavaFX Color object.

**Parameters:**
- `hex` - Hex color string (e.g., "#a78bfa")

**Returns:** `Color` object, or `Color.WHITE` on parse error

---

## Usage Example

```java
public class MyCard extends BaseCard {
    private final VBox root;
    private final ProgressBar bar;

    public MyCard() {
        bar = new ProgressBar();
        setBarAccentColor(bar, COLOR_PRIMARY);

        root = new VBox(bar);
        root.setStyle(CARD_STANDARD);
    }

    @Override
    public Region getRoot() {
        return root;
    }

    @Override
    public void setCompact(boolean compact) {
        if (compact) {
            root.setPadding(new Insets(12));
        } else {
            root.setPadding(new Insets(22));
        }
    }

    public void updateUsage(double percent) {
        String color = getColorByUsage(percent);
        setBarAccentColor(bar, color);
        bar.setProgress(clamp01(percent / 100.0));
    }
}
```

---

# MeterCard

## Overview

**Package:** `fxShield.UI`
**Class:** `MeterCard`
**Type:** `public final class extends BaseCard`

---

## Purpose

`MeterCard` displays real-time system metrics (CPU, RAM, GPU) with:

- **Percentage display** with color-coded indicators
- **Progress bar** with dynamic accent color
- **Extra information** label (e.g., "8.2 GB / 16 GB")
- **Compact mode** support for responsive layouts
- **Thread-safe updates** with UI coalescing
- **Performance optimizations** to prevent UI backlog

---

## Constructor

### `MeterCard(String titleText)`

Creates a new meter card with the specified title.

**Parameters:**
- `titleText` - Card title (e.g., "CPU", "RAM", "GPU")

**Throws:**
- `IllegalArgumentException` - if titleText is null

**Example:**
```java
MeterCard cpuCard = new MeterCard("CPU");
MeterCard ramCard = new MeterCard("RAM");
MeterCard gpuCard = new MeterCard("GPU");
```

---

## Public Methods

### `getTitleLabel()`
Returns the title label for customization.

**Returns:** `Label` - The title label

**Example:**
```java
MeterCard gpuCard = new MeterCard("GPU");
gpuCard.getTitleLabel().setText("GPU - NVIDIA RTX 3080");
```

---

### `setValuePercent(double percent, String extraText)`
Updates the card with new percentage and extra information.

**Thread Safety:** Safe to call from any thread (auto-detects FX thread)

**Parameters:**
- `percent` - Usage percentage (0-100)
- `extraText` - Additional information (e.g., "8.2 GB / 16 GB")

**Example:**
```java
// From FX thread
cpuCard.setValuePercent(45.5, "12 cores active");

// From background thread (auto-queued to FX thread)
new Thread(() -> {
    double usage = getCpuUsage();
    cpuCard.setValuePercent(usage, "");
}).start();
```

---

### `setValuePercent(double percent)`
Updates the card with new percentage (no extra text).

**Parameters:**
- `percent` - Usage percentage (0-100)

**Example:**
```java
cpuCard.setValuePercent(67.3);
```

---

### `setValuePercentAsync(double percent, String extraText)`
Explicitly queues update to FX thread with coalescing.

**Coalescing:** If multiple updates are queued, only the latest is applied

**Parameters:**
- `percent` - Usage percentage (0-100)
- `extraText` - Additional information

**Example:**
```java
// High-frequency updates from background thread
while (monitoring) {
    double usage = getCpuUsage();
    cpuCard.setValuePercentAsync(usage, ""); // Coalesced
    Thread.sleep(100);
}
```

---

### `setUnavailable(String message)`
Marks the card as unavailable (N/A).

**Parameters:**
- `message` - Reason message (e.g., "Not available")

**Example:**
```java
if (gpuNotDetected) {
    gpuCard.setUnavailable("No GPU detected");
}
```

---

### `setCompact(boolean compact)`
Switches between normal and compact display modes.

**Normal Mode:**
- Padding: 22px
- Spacing: 14px
- Min width: 280px
- Min height: 240px
- Title font: 20pt bold
- Value font: 18pt
- Extra font: 13pt

**Compact Mode:**
- Padding: 12px
- Spacing: 8px
- Min width: 200px
- Min height: 180px
- Title font: 16pt bold
- Value font: 15pt
- Extra font: 11pt

**Parameters:**
- `compact` - `true` for compact mode, `false` for normal

**Example:**
```java
// Responsive layout
if (windowWidth < 1350) {
    cpuCard.setCompact(true);
} else {
    cpuCard.setCompact(false);
}
```

---

## Performance Features

### UI Update Coalescing

```java
private final AtomicBoolean uiUpdateQueued = new AtomicBoolean(false);
private volatile double pendingPercent = Double.NaN;
private volatile String pendingExtra = "";
```

**How it works:**
1. Background thread calls `setValuePercentAsync(75.5, "info")`
2. If no update is queued, queue one and set flag
3. If update is already queued, just update pending values
4. When FX thread processes update, it uses latest values
5. Flag is cleared, allowing next update

**Benefit:** Prevents UI backlog from high-frequency updates

---

### Change Detection

```java
private String lastUsageColor = null;
private double lastProgress01 = Double.NaN;
private String lastValueText = null;
private String lastExtraText = null;
```

**Optimization:** Only updates UI elements that actually changed

**Example:**
```java
// First update: 45.5% -> Updates all
cpuCard.setValuePercent(45.5, "12 cores");

// Second update: 45.5% -> No UI changes (same value)
cpuCard.setValuePercent(45.5, "12 cores");

// Third update: 45.6% -> Only updates value label
cpuCard.setValuePercent(45.6, "12 cores");
```

---

## Usage Examples

### Example 1: Basic Usage

```java
MeterCard cpuCard = new MeterCard("CPU");
VBox container = new VBox(cpuCard.getRoot());

// Update from FX thread
cpuCard.setValuePercent(45.5, "8 cores active");
```

---

### Example 2: Background Monitoring

```java
MeterCard ramCard = new MeterCard("RAM");

SystemMonitorService monitor = new SystemMonitorService();
monitor.setListener((cpu, ram, disk, gpu) -> {
    // Called from background thread
    ramCard.setValuePercentAsync(
        ram.usedPercent,
        String.format("%.1f GB / %.1f GB", ram.usedGb, ram.totalGb)
    );
});

monitor.start();
```

---

### Example 3: Responsive Layout

```java
MeterCard cpuCard = new MeterCard("CPU");
MeterCard ramCard = new MeterCard("RAM");

scene.widthProperty().addListener((obs, old, newWidth) -> {
    boolean compact = newWidth.doubleValue() < 1350;
    cpuCard.setCompact(compact);
    ramCard.setCompact(compact);
});
```

---

### Example 4: Color-Coded Usage

```java
MeterCard cpuCard = new MeterCard("CPU");

// Low usage (0-59%): Purple
cpuCard.setValuePercent(45.0); // Purple bar

// High usage (60-84%): Orange
cpuCard.setValuePercent(75.0); // Orange bar

// Critical usage (85-100%): Red
cpuCard.setValuePercent(92.0); // Red bar
```

---

# ActionCard

## Overview

**Package:** `fxShield.UI`
**Class:** `ActionCard`
**Type:** `public final class extends BaseCard`

---

## Purpose

`ActionCard` provides interactive action buttons with:

- **Icon + Title + Description** layout
- **Hover animations** (lift effect)
- **Custom SVG icons** with scaling
- **Action button** with hover states
- **Compact mode** support
- **Accessible** design

---

## Constructors

### `ActionCard(String title, String desc, String buttonText, String svgPath)`

Creates an action card with default icon scale (1.5x).

**Parameters:**
- `title` - Card title (e.g., "Free RAM")
- `desc` - Description text
- `buttonText` - Button label (e.g., "Run")
- `svgPath` - SVG path data for icon

**Throws:**
- `IllegalArgumentException` - if any parameter is null/blank

---

### `ActionCard(String title, String desc, String buttonText, String svgPath, double iconScale)`

Creates an action card with custom icon scale.

**Parameters:**
- `title` - Card title
- `desc` - Description text
- `buttonText` - Button label
- `svgPath` - SVG path data
- `iconScale` - Icon scale factor (e.g., 2.0 for 2x size)

**Example:**
```java
ActionCard card = new ActionCard(
    "Free RAM",
    "Clean memory and free resources",
    "Run",
    "M10 2 L14 2 L8 14 L4 14 Z",
    1.5
);
```

---

## Public Methods

### `setOnAction(EventHandler<ActionEvent> handler)`
Sets the action handler for the button.

**Parameters:**
- `handler` - Event handler to execute on button click

**Example:**
```java
ActionCard freeRamCard = new ActionCard(
    "Free RAM",
    "Clean memory and free resources",
    "Run",
    "M10 2 L14 2 L8 14 L4 14 Z"
);

freeRamCard.setOnAction(e -> {
    System.out.println("Freeing RAM...");
    freeRamScript();
});
```

---

### `getButton()`
Returns the action button for direct access.

**Returns:** `Button` - The action button

**Example:**
```java
ActionCard card = new ActionCard(...);
Button btn = card.getButton();
btn.setDisable(true); // Disable button
```

---

### `setCompact(boolean compact)`
Switches between normal and compact display modes.

**Normal Mode:**
- Padding: 26px
- Min height: 110px
- Title font: 17pt bold
- Description font: 13pt
- Button font: 13pt bold

**Compact Mode:**
- Padding: 16px
- Min height: 80px
- Title font: 14pt bold
- Description font: 11pt
- Button font: 12pt bold

---

## Visual Features

### Hover Animation

**Effect:** Card lifts up 2px on hover with smooth transition

**Duration:** 140ms

**Implementation:**
```java
TranslateTransition up = new TranslateTransition(Duration.millis(140), card);
up.setToY(-2);

card.hoverProperty().addListener((obs, oldV, isHover) -> {
    if (isHover) {
        up.playFromStart();
    } else {
        down.playFromStart();
    }
});
```

---

### Button States

**Normal:**
```css
-fx-background-color: rgba(129, 71, 219, 0.45);
```

**Hover:**
```css
-fx-background-color: rgba(168, 85, 247, 0.65);
```

---

## Usage Examples

### Example 1: Basic Action Card

```java
ActionCard freeRamCard = new ActionCard(
    "Free RAM",
    "Clean memory and free resources",
    "Run",
    "M10 2 L14 2 L8 14 L4 14 Z"
);

freeRamCard.setOnAction(e -> {
    runPowerShellScript(config.getFreeRamScript());
});

container.getChildren().add(freeRamCard.getRoot());
```

---

### Example 2: Multiple Action Cards

```java
ActionCard[] cards = {
    new ActionCard("Free RAM", "Clean memory", "Run", "..."),
    new ActionCard("Optimize Disk", "Clean disk", "Optimize", "..."),
    new ActionCard("Optimize Network", "Reset network", "Optimize", "..."),
    new ActionCard("Scan & Fix", "Repair system files", "Scan", "..."),
    new ActionCard("Power Modes", "Switch power plan", "Open", "..."),
    new ActionCard("One Click", "Full optimization", "Boost", "...")
};

cards[0].setOnAction(e -> runFreeRam());
cards[1].setOnAction(e -> runOptimizeDisk());
cards[2].setOnAction(e -> runOptimizeNetwork());
cards[3].setOnAction(e -> runScanAndFix());
cards[4].setOnAction(e -> showPowerModeDialog());
cards[5].setOnAction(e -> runAllInOne());
```

---

### Example 3: Grid Layout

```java
GridPane grid = new GridPane();
grid.setHgap(18);
grid.setVgap(18);

int cols = 3;
for (int i = 0; i < actionCards.length; i++) {
    int row = i / cols;
    int col = i % cols;
    grid.add(actionCards[i].getRoot(), col, row);
}
```

---

# TopBarIcons

## Overview

**Package:** `fxShield.UI`
**Class:** `TopBarIcons`
**Type:** `public final class`

---

## Purpose

`TopBarIcons` provides the top bar icon buttons:

- **Info button (!)** - Opens device information dialog
- **Settings button (⚙)** - Opens settings dialog
- **Circular design** with hover effects
- **Interactive exclusion** for drag regions

---

## Constructor

### `TopBarIcons()`

Creates the top bar icons component.

**Example:**
```java
TopBarIcons topIcons = new TopBarIcons();
HBox header = new HBox(topIcons.getRoot());
```

---

## Public Methods

### `getRoot()`
Returns the root HBox containing all icons.

**Returns:** `Node` - The root container

---

### `getMaximizeButton()`
Returns the maximize button (not implemented in this version).

**Returns:** `null` - No maximize button in current design

---

### `getInteractiveNodes()`
Returns list of interactive nodes to exclude from drag region.

**Returns:** `List<Node>` - Unmodifiable list of interactive nodes

**Usage:**
```java
TopBarIcons topIcons = new TopBarIcons();
WindowsSnapFrameless.install(
    stage,
    scene,
    header,                       // drag region
    null,                         // no maximize button
    8,
    topIcons.getInteractiveNodes() // exclude these from drag
);
```

---

## Visual Design

### Circle Specifications

- **Size:** 38x38 pixels
- **Border radius:** 999px (perfect circle)
- **Border width:** 1px

### States

**Normal:**
```css
-fx-background-color: rgba(255,255,255,0.10);
-fx-border-color: rgba(255,255,255,0.14);
```

**Hover:**
```css
-fx-background-color: rgba(255,255,255,0.14);
-fx-border-color: rgba(147,197,253,0.50);
```

**Pressed:**
```css
-fx-background-color: rgba(255,255,255,0.22);
-fx-border-color: rgba(147,197,253,0.60);
```

---

## Usage Example

```java
TopBarIcons topIcons = new TopBarIcons();

Region spacer = new Region();
HBox.setHgrow(spacer, Priority.ALWAYS);

HBox header = new HBox(appTitle, spacer, topIcons.getRoot());
header.setAlignment(Pos.CENTER_LEFT);
header.setPadding(new Insets(26, 0, 16, 0));

root.setTop(header);
```

---

# StyleConstants

## Overview

**Package:** `fxShield.UI`
**Class:** `StyleConstants`
**Type:** `public final class` (utility)

---

## Purpose

`StyleConstants` provides centralized style constants:

- **Color palette** - Consistent colors across the app
- **Typography** - Pre-cached Font objects
- **Common styles** - Reusable CSS strings
- **Card styles** - Standard card designs
- **Dialog styles** - Dialog backgrounds
- **Utility methods** - Color and style helpers

---

## Color Palette

### Primary Colors

```java
public static final String COLOR_PRIMARY = "#a78bfa";  // Purple
public static final String COLOR_WARN    = "#fb923c";  // Orange
public static final String COLOR_DANGER  = "#f97373";  // Red
public static final String COLOR_INFO    = "#7dd3fc";  // Light Blue
public static final String COLOR_SUCCESS = "#22c55e";  // Green
```

### Text Colors

```java
public static final String COLOR_TEXT_LIGHT     = "#f5e8ff";
public static final String COLOR_TEXT_MEDIUM    = "#e9d8ff";
public static final String COLOR_TEXT_DIM       = "#cbb8ff";
public static final String COLOR_TEXT_MUTED     = "#d5c8f7";
public static final String COLOR_TEXT_WHITE     = "#e5e7eb";
public static final String COLOR_TEXT_SECONDARY = "#9ca3af";
public static final String COLOR_TEXT_TERTIARY  = "#64748b";
```

### Background Colors

```java
public static final String BG_DARK_BLUE  = "#020617";
public static final String BG_DEEP_BLUE  = "#0b1224";
public static final String BG_DARK_SLATE = "#0f172a";
public static final String BG_DARK_GRAY  = "#111827";
public static final String BG_VERY_DARK  = "#14161c";
```

---

## Typography

### Font Family

```java
public static final String FONT_FAMILY = "Segoe UI";
public static final String FONT_EMOJI  = "Segoe UI Emoji";
```

### Cached Fonts

**Benefits:**
- Single allocation per font
- No repeated Font.font() calls
- Improved performance

**Example Fonts:**
```java
public static final Font FONT_TITLE_52_BOLD      = Font.font(FONT_FAMILY, FontWeight.BOLD, 52);
public static final Font FONT_TITLE_22_BOLD      = Font.font(FONT_FAMILY, FontWeight.BOLD, 22);
public static final Font FONT_CARD_TITLE_20_BOLD = Font.font(FONT_FAMILY, FontWeight.BOLD, 20);
public static final Font FONT_VALUE_18           = Font.font(FONT_FAMILY, FontWeight.NORMAL, 18);
public static final Font FONT_BODY_13            = Font.font(FONT_FAMILY, FontWeight.NORMAL, 13);
```

---

## Card Styles

### Standard Card

```java
public static final String CARD_STANDARD =
    "-fx-background-color: linear-gradient(to bottom right, rgba(23, 18, 48, 0.65), rgba(13, 10, 28, 0.85));" +
    "-fx-background-radius: 28;" +
    "-fx-border-radius: 28;" +
    "-fx-border-color: rgba(255,255,255,0.14);" +
    "-fx-border-width: 1.2;" +
    "-fx-effect: dropshadow(gaussian, rgba(130, 80, 255, 0.22), 20, 0, 0, 4);";
```

### Action Card

```java
public static final String CARD_ACTION_NORMAL =
    CARD_ACTION_BASE +
    "-fx-border-color: rgba(255,255,255,0.10);" +
    "-fx-effect: dropshadow(gaussian, rgba(140,65,255,0.15), 10, 0.2, 0, 0);";
```

---

## Utility Methods

### `progressBarStyle(String accentColor)`
Generates progress bar style with custom accent color.

**Parameters:**
- `accentColor` - Hex color code

**Returns:** CSS style string

**Example:**
```java
String style = StyleConstants.progressBarStyle("#a78bfa");
progressBar.setStyle(style);
```

---

### `colorByUsage(double percent)`
Returns color based on usage percentage.

**Parameters:**
- `percent` - Usage percentage (0-100)

**Returns:** Hex color string

**Example:**
```java
String color = StyleConstants.colorByUsage(75.0); // Returns COLOR_WARN
```

---

### `buttonStyle(String backgroundColor, String textColor)`
Generates button style with custom colors.

**Parameters:**
- `backgroundColor` - Background hex color
- `textColor` - Text hex color

**Returns:** CSS style string

---

## Usage Examples

### Example 1: Using Color Constants

```java
Label title = new Label("FX Shield");
title.setTextFill(Color.web(StyleConstants.COLOR_TEXT_LIGHT));
```

---

### Example 2: Using Cached Fonts

```java
Label title = new Label("CPU");
title.setFont(StyleConstants.FONT_CARD_TITLE_20_BOLD);
```

---

### Example 3: Using Card Styles

```java
VBox card = new VBox();
card.setStyle(StyleConstants.CARD_STANDARD);
```

---

## Best Practices

### ✅ DO: Use Cached Fonts

```java
// Good - Uses cached font
label.setFont(StyleConstants.FONT_TITLE_22_BOLD);
```

### ❌ DON'T: Create Fonts Repeatedly

```java
// Bad - Creates new font every time
label.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
```

---

### ✅ DO: Use Color Constants

```java
// Good - Consistent colors
label.setTextFill(Color.web(StyleConstants.COLOR_PRIMARY));
```

### ❌ DON'T: Hardcode Colors

```java
// Bad - Inconsistent, hard to maintain
label.setTextFill(Color.web("#a78bfa"));
```

---

## Author

**FX Shield Team**

---

## See Also

- [BaseCard Documentation](#basecard)
- [MeterCard Documentation](#metercard)
- [ActionCard Documentation](#actioncard)
