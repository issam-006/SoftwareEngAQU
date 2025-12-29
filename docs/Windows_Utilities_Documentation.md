# Windows Utilities Documentation

## Overview

This document covers Windows-specific utility classes:

- **WindowsUtils** - Windows API integration, PowerShell execution, admin elevation
- **FxSettings** - Application settings persistence and management

---

# WindowsUtils

## Overview

**Package:** `fxShield.WIN`
**Class:** `WindowsUtils`
**Type:** `public final class` (utility)

---

## Purpose

`WindowsUtils` provides comprehensive Windows integration:

- **PowerShell Execution** - Safe, logged, timeout-controlled script execution
- **Admin Elevation** - Request administrator privileges
- **Window Styling** - DWM dark mode and custom caption colors
- **Startup Management** - Windows startup registry integration
- **JavaFX Stage Utilities** - Centering, clamping, modal dialogs
- **Blur Effects** - Background blur for modal dialogs

---

## Inner Classes

### `PsResult`

Represents the result of a PowerShell script execution.

**Fields:**
```java
public final int exitCode;      // Process exit code
public final String stdout;     // Standard output
public final String stderr;     // Standard error
public final boolean timedOut;  // Whether execution timed out
public final boolean success;   // true if exitCode == 0 && !timedOut
```

**Example:**
```java
PsResult result = WindowsUtils.runPowerShellLogged(script, "[MyScript]");
if (result.success) {
    System.out.println("Output: " + result.stdout);
} else {
    System.err.println("Error: " + result.stderr);
    System.err.println("Exit code: " + result.exitCode);
}
```

---

### `BlurGuard`

Auto-closeable resource for managing background blur effects.

**Usage Pattern:**
```java
try (BlurGuard blur = WindowsUtils.applyBlur(ownerStage, 10.0)) {
    // Show modal dialog
    dialog.showAndWait();
} // Blur automatically removed
```

**Methods:**
- `close()` - Removes blur effect and restores previous effect

---

## PowerShell Execution

### `runPowerShellLogged(String script, String logTag)`

Executes PowerShell script with full logging.

**Features:**
- Concurrent stdout/stderr reading (no deadlock)
- UTF-8 encoding
- Progress preference disabled
- Execution policy bypass
- Base64-encoded command (handles special characters)

**Parameters:**
- `script` - PowerShell script to execute
- `logTag` - Tag for log messages (e.g., "[FreeRAM]")

**Returns:** `PsResult` - Execution result

**Thread Safety:** ⚠️ **DO NOT call from FX thread** (blocks UI)

**Example:**
```java
String script = "Get-Process | Where-Object {$_.WorkingSet -gt 100MB}";
PsResult result = WindowsUtils.runPowerShellLogged(script, "[ProcessCheck]");

if (result.success) {
    System.out.println("Processes: " + result.stdout);
} else if (result.timedOut) {
    System.err.println("Script timed out after 30 seconds");
} else {
    System.err.println("Script failed: " + result.stderr);
}
```

---

### `runPowerShellSilent(String script, long timeoutSec)`

Executes PowerShell script without logging.

**Parameters:**
- `script` - PowerShell script
- `timeoutSec` - Timeout in seconds

**Example:**
```java
String script = "Set-ItemProperty -Path 'HKCU:\\Software\\MyApp' -Name 'Setting' -Value 'true'";
WindowsUtils.runPowerShellSilent(script, 5);
```

---

### `runPowerShellCapture(String script, long timeoutSec)`

Executes PowerShell and returns combined stdout/stderr.

**Parameters:**
- `script` - PowerShell script
- `timeoutSec` - Timeout in seconds

**Returns:** Combined output string

**Example:**
```java
String script = "Get-WmiObject Win32_OperatingSystem | Select-Object Caption";
String output = WindowsUtils.runPowerShellCapture(script, 5);
System.out.println("OS: " + output.trim());
```

---

### `escapeForPowerShell(String s)`

Escapes string for safe use in PowerShell single quotes.

**Escaping:** Replaces `'` with `''`

**Parameters:**
- `s` - String to escape

**Returns:** Escaped string

**Example:**
```java
String userInput = "It's a test";
String escaped = WindowsUtils.escapeForPowerShell(userInput);
// Result: "It''s a test"

String script = "$msg = '" + escaped + "'; Write-Host $msg";
WindowsUtils.runPowerShellSilent(script, 3);
```

---

## Admin Elevation

### `isAdmin()`

Checks if the current process has administrator privileges.

**Returns:** `true` if running as admin, `false` otherwise

**Implementation:** Uses PowerShell to check WindowsPrincipal role

**Example:**
```java
if (!WindowsUtils.isAdmin()) {
    System.out.println("This app requires administrator privileges");
    WindowsUtils.requestAdminAndExit();
}
```

---

### `requestAdminAndExit()`

Requests administrator elevation and exits current process.

**Behavior:**
1. Detects current executable path and arguments
2. Launches new process with "Run as Administrator"
3. Exits current process with `System.exit(0)`
4. Shows error dialog if elevation fails

**Example:**
```java
if (!WindowsUtils.isAdmin()) {
    int choice = JOptionPane.showConfirmDialog(
        null,
        "This app requires administrator privileges. Restart as admin?",
        "Admin Required",
        JOptionPane.YES_NO_OPTION
    );

    if (choice == JOptionPane.YES_OPTION) {
        WindowsUtils.requestAdminAndExit();
    }
}
```

---

## Window Styling (DWM)

### `styleStage(Stage stage)`

Applies Windows 11 dark mode styling to a JavaFX stage.

**Features:**
- Enables Immersive Dark Mode
- Sets custom caption color (#020617)
- Sets white text color
- Sets custom border color

**Requirements:**
- Windows 10 1809+ or Windows 11
- Stage must have a title set
- Stage must be shown before calling

**Parameters:**
- `stage` - JavaFX Stage to style

**Example:**
```java
Stage stage = new Stage();
stage.setTitle("FX Shield");
stage.show();

// Apply Windows 11 dark mode styling
WindowsUtils.styleStage(stage);
```

**DWM Attributes Used:**
```java
DWMWA_USE_IMMERSIVE_DARK_MODE = 20  // Enable dark mode
DWMWA_CAPTION_COLOR = 35            // Title bar color
DWMWA_TEXT_COLOR = 36               // Title bar text color
DWMWA_BORDER_COLOR = 34             // Window border color
```

---

## Startup Management

### `applyStartup(boolean enable)`

Enables or disables Windows startup.

**Parameters:**
- `enable` - `true` to enable startup, `false` to disable

**Registry Location:**
```
HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run
```

**Example:**
```java
// Enable startup
WindowsUtils.applyStartup(true);

// Disable startup
WindowsUtils.applyStartup(false);
```

---

### `isStartupEnabled()`

Checks if Windows startup is enabled.

**Returns:** `true` if startup is enabled, `false` otherwise

**Example:**
```java
boolean isEnabled = WindowsUtils.isStartupEnabled();
CheckBox startupCheckbox = new CheckBox("Start with Windows");
startupCheckbox.setSelected(isEnabled);

startupCheckbox.setOnAction(e -> {
    WindowsUtils.applyStartup(startupCheckbox.isSelected());
});
```

---

### `currentStartupCommand()`

Gets the current startup command from registry.

**Returns:** Startup command string, or `null` if not set

**Example:**
```java
String cmd = WindowsUtils.currentStartupCommand();
if (cmd != null) {
    System.out.println("Startup command: " + cmd);
}
```

---

## JavaFX Stage Utilities

### `findOwner(Node anyNodeInsideStage)`

Finds the owner Stage of a node.

**Parameters:**
- `anyNodeInsideStage` - Any node in the scene graph

**Returns:** `Optional<Stage>` - The owner stage, or empty

**Example:**
```java
Button button = new Button("Click me");
button.setOnAction(e -> {
    WindowsUtils.findOwner(button).ifPresent(stage -> {
        System.out.println("Owner stage: " + stage.getTitle());
    });
});
```

---

### `withOwner(Node anyNodeInsideStage, Consumer<Stage> consumer)`

Executes consumer with owner stage if found.

**Parameters:**
- `anyNodeInsideStage` - Any node in the scene graph
- `consumer` - Consumer to execute with stage

**Example:**
```java
Button button = new Button("Show Dialog");
button.setOnAction(e -> {
    WindowsUtils.withOwner(button, owner -> {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.show();
    });
});
```

---

### `centerOnOwner(Stage child, Stage owner)`

Centers a child stage on its owner stage.

**Parameters:**
- `child` - Stage to center
- `owner` - Owner stage

**Features:**
- Automatically calls `sizeToScene()` if needed
- Clamps to screen bounds

**Example:**
```java
Stage dialog = new Stage();
dialog.setScene(new Scene(new VBox(), 400, 300));

WindowsUtils.centerOnOwner(dialog, primaryStage);
dialog.show();
```

---

### `clampToScreen(Stage stage)`

Clamps stage position and size to screen bounds.

**Parameters:**
- `stage` - Stage to clamp

**Behavior:**
- Shrinks stage if larger than screen
- Moves stage if outside screen bounds
- Uses visual bounds (excludes taskbar)

**Example:**
```java
Stage stage = new Stage();
stage.setX(5000); // Way off screen
stage.setY(-1000);

WindowsUtils.clampToScreen(stage);
// Stage is now within screen bounds
```

---

### `showModalOver(Node anchor, Stage dialog)`

Shows a modal dialog centered over the anchor's owner stage.

**Parameters:**
- `anchor` - Node to find owner from
- `dialog` - Dialog stage to show

**Example:**
```java
Button button = new Button("Show Settings");
button.setOnAction(e -> {
    Stage settingsDialog = new Stage();
    settingsDialog.setScene(new Scene(new VBox(), 500, 400));

    WindowsUtils.showModalOver(button, settingsDialog);
});
```

---

### `applyBlur(Stage owner, double radius)`

Applies Gaussian blur to owner stage background.

**Parameters:**
- `owner` - Stage to blur
- `radius` - Blur radius (0-100)

**Returns:** `BlurGuard` - Auto-closeable blur guard

**Example:**
```java
try (BlurGuard blur = WindowsUtils.applyBlur(primaryStage, 10.0)) {
    Stage dialog = new Stage();
    dialog.initOwner(primaryStage);
    dialog.initModality(Modality.APPLICATION_MODAL);
    dialog.showAndWait();
} // Blur automatically removed
```

---

### `runFx(Runnable r)`

Runs a task on the JavaFX Application Thread.

**Parameters:**
- `r` - Runnable to execute

**Behavior:**
- If already on FX thread, runs immediately
- Otherwise, queues with `Platform.runLater()`

**Example:**
```java
new Thread(() -> {
    String data = fetchDataFromNetwork();

    WindowsUtils.runFx(() -> {
        label.setText(data);
    });
}).start();
```

---

## System Detection

### `isWindows()`

Checks if the current OS is Windows.

**Returns:** `true` if Windows, `false` otherwise

**Example:**
```java
if (WindowsUtils.isWindows()) {
    // Windows-specific code
    WindowsUtils.styleStage(stage);
} else {
    // Cross-platform fallback
    stage.setStyle(StageStyle.DECORATED);
}
```

---

## Usage Examples

### Example 1: Safe PowerShell Execution

```java
// From background thread
new Thread(() -> {
    String script = """
        Get-Process | Where-Object {$_.WorkingSet -gt 100MB} |
        ForEach-Object {
            $_.CloseMainWindow()
        }
        """;

    PsResult result = WindowsUtils.runPowerShellLogged(script, "[FreeRAM]");

    Platform.runLater(() -> {
        if (result.success) {
            showNotification("RAM freed successfully");
        } else {
            showError("Failed to free RAM: " + result.stderr);
        }
    });
}, "free-ram").start();
```

---

### Example 2: Admin Check and Elevation

```java
@Override
public void start(Stage stage) {
    if (!WindowsUtils.isAdmin()) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Admin Required");
        alert.setHeaderText("Administrator privileges required");
        alert.setContentText("This app needs admin rights. Restart as admin?");

        ButtonType restart = new ButtonType("Restart as Admin");
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(restart, cancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == restart) {
            WindowsUtils.requestAdminAndExit();
        } else {
            System.exit(0);
        }
        return;
    }

    // Continue with normal startup
    buildUI(stage);
}
```

---

### Example 3: Windows 11 Styling

```java
Stage stage = new Stage();
stage.setTitle("FX Shield");
stage.setScene(scene);
stage.setResizable(true);

stage.show();

// Apply Windows 11 dark mode
WindowsUtils.styleStage(stage);
stage.toFront();
```

---

### Example 4: Startup Management

```java
CheckBox startupCheckbox = new CheckBox("Start with Windows");
startupCheckbox.setSelected(WindowsUtils.isStartupEnabled());

startupCheckbox.setOnAction(e -> {
    boolean enable = startupCheckbox.isSelected();
    WindowsUtils.applyStartup(enable);

    // Verify
    boolean actuallyEnabled = WindowsUtils.isStartupEnabled();
    if (actuallyEnabled != enable) {
        showError("Failed to update startup setting");
        startupCheckbox.setSelected(actuallyEnabled);
    }
});
```

---

### Example 5: Modal Dialog with Blur

```java
Button showDialogBtn = new Button("Show Settings");
showDialogBtn.setOnAction(e -> {
    WindowsUtils.withOwner(showDialogBtn, owner -> {
        try (BlurGuard blur = WindowsUtils.applyBlur(owner, 8.0)) {
            Stage dialog = new Stage();
            dialog.initOwner(owner);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Settings");
            dialog.setScene(new Scene(buildSettingsUI(), 600, 400));

            WindowsUtils.centerOnOwner(dialog, owner);
            dialog.showAndWait();
        }
    });
});
```

---

## Performance Considerations

### PowerShell Execution

**Overhead:**
- Process creation: ~50-200ms
- Script execution: varies
- Total typical: 100-500ms for simple scripts

**Best Practices:**
```java
// ✅ Good - Background thread
new Thread(() -> {
    PsResult result = WindowsUtils.runPowerShellLogged(script, "[Tag]");
    Platform.runLater(() -> updateUI(result));
}).start();

// ❌ Bad - Blocks UI thread
PsResult result = WindowsUtils.runPowerShellLogged(script, "[Tag]");
```

---

### Retry Logic

For critical operations, implement retry:

```java
int maxRetries = 3;
PsResult result = null;

for (int i = 0; i < maxRetries; i++) {
    result = WindowsUtils.runPowerShellLogged(script, "[Retry-" + i + "]");
    if (result.success) break;

    if (i < maxRetries - 1) {
        Thread.sleep(1000); // Wait before retry
    }
}

if (result == null || !result.success) {
    showError("Operation failed after " + maxRetries + " attempts");
}
```

---

## Security Considerations

### PowerShell Injection

**Always escape user input:**

```java
// ✅ Safe
String userInput = getUserInput();
String escaped = WindowsUtils.escapeForPowerShell(userInput);
String script = "Write-Host '" + escaped + "'";

// ❌ Unsafe - Injection risk
String script = "Write-Host '" + getUserInput() + "'";
```

---

### Admin Elevation

**Only request when necessary:**

```java
// Check if specific operation needs admin
if (needsAdminForOperation()) {
    if (!WindowsUtils.isAdmin()) {
        WindowsUtils.requestAdminAndExit();
    }
}
```

---

## Thread Safety

### Volatile Cache

Most methods are thread-safe, but PowerShell execution should be done from background threads:

```java
// ✅ Thread-safe
ExecutorService executor = Executors.newSingleThreadExecutor();
executor.submit(() -> {
    PsResult result = WindowsUtils.runPowerShellLogged(script, "[BG]");
    // Process result
});
```

---

## Error Handling

### Graceful Degradation

```java
if (WindowsUtils.isWindows()) {
    try {
        WindowsUtils.styleStage(stage);
    } catch (Exception e) {
        logger.warn("Failed to apply Windows styling", e);
        // Continue without styling
    }
}
```

---

## Dependencies

- **JNA (Java Native Access)** - For DWM API calls
- **SLF4J** - For logging
- **JavaFX** - For stage utilities

---

# FxSettings

## Overview

**Package:** `fxShield.WIN`
**Class:** `FxSettings`
**Type:** `public final class implements Serializable`

---

## Purpose

`FxSettings` manages application settings with:

- **Serializable** - For persistence
- **Properties-based** - Human-readable storage
- **Atomic saves** - Prevents corruption
- **Fluent API** - Convenient builder pattern
- **Backward compatible** - Public fields + getters/setters

---

## Fields

### Settings

```java
public boolean autoFreeRam = false;
public boolean autoOptimizeHardDisk = false;
public boolean autoStartWithWindows = false;
```

---

## Constants

### Property Keys

```java
public static final String KEY_AUTO_FREE_RAM = "autoFreeRam";
public static final String KEY_AUTO_OPTIMIZE_DISK = "autoOptimizeHardDisk";
public static final String KEY_AUTO_START_WINDOWS = "autoStartWithWindows";
```

### Defaults

```java
public static final boolean DEFAULT_AUTO_FREE_RAM = false;
public static final boolean DEFAULT_AUTO_OPTIMIZE_DISK = false;
public static final boolean DEFAULT_AUTO_START_WINDOWS = false;
```

---

## Constructors

### `FxSettings()`
Creates settings with default values.

---

### `FxSettings(boolean autoFreeRam, boolean autoOptimizeHardDisk, boolean autoStartWithWindows)`
Creates settings with specified values.

---

### `FxSettings(FxSettings other)`
Copy constructor.

---

## Static Methods

### `defaults()`
Returns settings with default values.

**Example:**
```java
FxSettings settings = FxSettings.defaults();
```

---

### `load()`
Loads settings from disk.

**Location:** `%APPDATA%\FxShield\settings.properties`

**Returns:** Loaded settings, or defaults if file doesn't exist

**Example:**
```java
FxSettings settings = FxSettings.load();
if (settings.autoFreeRam) {
    startAutoFreeRam();
}
```

---

### `save(FxSettings s)`
Saves settings to disk atomically.

**Parameters:**
- `s` - Settings to save

**Atomic Save:**
1. Write to temporary file
2. Atomic move to final location
3. Prevents corruption on crash

**Example:**
```java
FxSettings settings = FxSettings.load();
settings.autoFreeRam = true;
FxSettings.save(settings);
```

---

### `reset()`
Deletes settings file.

**Example:**
```java
FxSettings.reset(); // Delete settings
FxSettings settings = FxSettings.load(); // Returns defaults
```

---

## Instance Methods

### Getters/Setters

```java
public boolean isAutoFreeRam()
public void setAutoFreeRam(boolean autoFreeRam)

public boolean isAutoOptimizeHardDisk()
public void setAutoOptimizeHardDisk(boolean autoOptimizeHardDisk)

public boolean isAutoStartWithWindows()
public void setAutoStartWithWindows(boolean autoStartWithWindows)
```

---

### Fluent API

```java
public FxSettings withAutoFreeRam(boolean v)
public FxSettings withAutoOptimizeHardDisk(boolean v)
public FxSettings withAutoStartWithWindows(boolean v)
```

**Example:**
```java
FxSettings settings = FxSettings.defaults()
    .withAutoFreeRam(true)
    .withAutoOptimizeHardDisk(true);
```

---

### `copy()`
Creates a deep copy.

**Returns:** New `FxSettings` instance

---

### `merge(FxSettings other)`
Merges another settings object into this one.

**Parameters:**
- `other` - Settings to merge from

**Returns:** `this` (for chaining)

---

## Builder Pattern

### `builder()`
Creates a new builder.

**Example:**
```java
FxSettings settings = FxSettings.builder()
    .autoFreeRam(true)
    .autoOptimizeHardDisk(false)
    .autoStartWithWindows(true)
    .build();
```

---

## Usage Examples

### Example 1: Load and Save

```java
// Load settings
FxSettings settings = FxSettings.load();

// Modify
settings.autoFreeRam = true;
settings.autoStartWithWindows = true;

// Save
FxSettings.save(settings);
```

---

### Example 2: Fluent API

```java
FxSettings settings = FxSettings.defaults()
    .withAutoFreeRam(true)
    .withAutoOptimizeHardDisk(true);

FxSettings.save(settings);
```

---

### Example 3: Builder Pattern

```java
FxSettings settings = FxSettings.builder()
    .autoFreeRam(true)
    .autoOptimizeHardDisk(false)
    .autoStartWithWindows(true)
    .build();

FxSettings.save(settings);
```

---

### Example 4: UI Binding

```java
FxSettings settings = FxSettings.load();

CheckBox autoFreeRamCb = new CheckBox("Auto Free RAM");
autoFreeRamCb.setSelected(settings.autoFreeRam);

CheckBox autoOptimizeDiskCb = new CheckBox("Auto Optimize Disk");
autoOptimizeDiskCb.setSelected(settings.autoOptimizeHardDisk);

Button saveBtn = new Button("Save");
saveBtn.setOnAction(e -> {
    settings.autoFreeRam = autoFreeRamCb.isSelected();
    settings.autoOptimizeHardDisk = autoOptimizeDiskCb.isSelected();
    FxSettings.save(settings);

    showNotification("Settings saved");
});
```

---

## File Format

### Example settings.properties

```properties
#FxShield Settings
#Mon Jan 15 12:30:00 UTC 2024
autoFreeRam=true
autoOptimizeHardDisk=false
autoStartWithWindows=true
```

---

## Thread Safety

### Synchronized Methods

```java
public static synchronized FxSettings load()
public static synchronized void save(FxSettings s)
public static synchronized void reset()
```

**Safe for concurrent access** from multiple threads.

---

## Best Practices

### ✅ DO: Load Once, Save When Changed

```java
// Load at startup
FxSettings settings = FxSettings.load();

// Save only when user changes settings
saveButton.setOnAction(e -> {
    updateSettingsFromUI(settings);
    FxSettings.save(settings);
});
```

---

### ❌ DON'T: Save on Every Change

```java
// Bad - Excessive disk I/O
checkbox.setOnAction(e -> {
    settings.autoFreeRam = checkbox.isSelected();
    FxSettings.save(settings); // Too frequent
});
```

---

## Author

**FX Shield Team**

---

## See Also

- [WindowsUtils Documentation](#windowsutils)
- [AutomationService Documentation](AutomationService_Documentation.md)
