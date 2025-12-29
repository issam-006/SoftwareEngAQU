# RemoteConfig Class Documentation

## Overview

**Package:** `fxShield.DB`
**Class:** `RemoteConfig`
**Type:** `public final class`
**Implements:** `Serializable`

---

## Purpose

`RemoteConfig` is a data transfer object (DTO) that encapsulates all remote configuration settings fetched from a server. It serves as the central configuration hub for the FX Shield application, managing:

- **Application Status & Version Control** - Server-side control of app availability and updates
- **Maintenance Mode** - Ability to disable features during server maintenance
- **Dynamic Script Delivery** - PowerShell optimization scripts delivered remotely
- **Configuration Caching** - Serializable for offline fallback scenarios

---

## Architecture & Design Patterns

### Serialization
- Implements `Serializable` for network transmission and local caching
- `serialVersionUID = 1L` ensures version compatibility
- All fields are private with public getters/setters

### Data Validation
- All string setters use `trimOrNull()` utility method
- Automatically converts empty/whitespace strings to `null`
- Ensures consistent data handling across all fields

### Backward Compatibility
- Maintains legacy field `quitModeScript` (typo) alongside correct `quietModeScript`
- Deprecated methods provide migration path for old code
- Getter falls back to legacy field if new field is null

---

## Class Structure

### Fields

#### Application Status & Version Fields

| Field | Type | Description |
|-------|------|-------------|
| `appStatus` | `String` | Current app status: "online", "maintenance", or null |
| `latestVersion` | `String` | Latest available version (e.g., "2.1.0") |
| `minVersion` | `String` | Minimum required version (e.g., "2.0.0") |
| `downloadUrl` | `String` | Download URL for latest version |
| `updateMessage` | `String` | Custom message for updates/maintenance |
| `forceUpdate` | `boolean` | Whether update is mandatory |

#### PowerShell Script Fields

| Field | Type | Description |
|-------|------|-------------|
| `freeRamScript` | `String` | Script to free RAM (memory cleanup) |
| `optimizeDiskScript` | `String` | Script for disk optimization |
| `optimizeNetworkScript` | `String` | Script for network optimization |
| `performanceModeScript` | `String` | Script to switch to High Performance power plan |
| `balancedModeScript` | `String` | Script to switch to Balanced power plan |
| `quietModeScript` | `String` | Script to switch to Power Saver mode |
| `quitModeScript` | `String` | **[DEPRECATED]** Legacy typo field |
| `scanAndFixScript` | `String` | Script to scan and repair system files |

---

## Methods

### Application Status Methods

#### `getAppStatus()` / `setAppStatus(String)`
Gets/sets the current application status from the server.

**Valid Values:**
- `"online"` - Application is operational
- `"maintenance"` - Application is in maintenance mode
- `null` - Treated as "online"

**Example:**
```java
config.setAppStatus("maintenance");
String status = config.getAppStatus(); // "maintenance"
```

---

#### `isMaintenance()`
Checks if the application is in maintenance mode.

**Returns:** `true` if `appStatus` equals "maintenance" (case-insensitive)

**Example:**
```java
if (config.isMaintenance()) {
    showMaintenanceDialog(config.getUpdateMessage());
    return;
}
```

---

#### `isOnline()`
Checks if the application is online and operational.

**Returns:** `true` if `appStatus` is `null` or "online" (case-insensitive)

**Example:**
```java
if (config.isOnline()) {
    launchNormalUi(stage, config);
}
```

---

### Version Management Methods

#### `getLatestVersion()` / `setLatestVersion(String)`
Gets/sets the latest available version of the application.

**Example:**
```java
config.setLatestVersion("2.1.0");
String latest = config.getLatestVersion(); // "2.1.0"
```

---

#### `getMinVersion()` / `setMinVersion(String)`
Gets/sets the minimum required version to run the application.

**Example:**
```java
config.setMinVersion("2.0.0");
if (currentVersion.compareTo(config.getMinVersion()) < 0) {
    showForceUpdateDialog();
}
```

---

#### `getDownloadUrl()` / `setDownloadUrl(String)`
Gets/sets the download URL for the latest application version.

**Example:**
```java
config.setDownloadUrl("https://example.com/download/fxshield-2.1.0.exe");
String url = config.getDownloadUrl();
```

---

#### `getUpdateMessage()` / `setUpdateMessage(String)`
Gets/sets a custom message to display during update or maintenance notifications.

**Example:**
```java
config.setUpdateMessage("New features available! Update now.");
showNotification(config.getUpdateMessage());
```

---

#### `isForceUpdate()` / `setForceUpdate(boolean)`
Checks/sets whether the update is mandatory.

**Example:**
```java
config.setForceUpdate(true);
if (config.isForceUpdate()) {
    blockUsageUntilUpdate();
}
```

---

### PowerShell Script Methods

All script methods follow the same pattern:

#### `getFreeRamScript()` / `setFreeRamScript(String)`
Gets/sets the PowerShell script for freeing RAM.

**Example:**
```java
String script = config.getFreeRamScript();
if (script != null) {
    powerShellExecutor.execute(script);
}
```

---

#### `getOptimizeDiskScript()` / `setOptimizeDiskScript(String)`
Gets/sets the PowerShell script for disk optimization.

**Typical Script Content:**
- Disk cleanup
- Temp file removal
- Defragmentation commands

---

#### `getOptimizeNetworkScript()` / `setOptimizeNetworkScript(String)`
Gets/sets the PowerShell script for network optimization.

**Typical Script Content:**
- DNS flush (`ipconfig /flushdns`)
- TCP/IP reset
- Network adapter tweaks

---

#### `getPerformanceModeScript()` / `setPerformanceModeScript(String)`
Gets/sets the script to switch Windows power plan to "High Performance".

---

#### `getBalancedModeScript()` / `setBalancedModeScript(String)`
Gets/sets the script to switch Windows power plan to "Balanced".

---

#### `getQuietModeScript()` / `setQuietModeScript(String)`
Gets/sets the script to switch Windows power plan to "Power Saver" (Quiet Mode).

**Backward Compatibility:**
- Falls back to `quitModeScript` if `quietModeScript` is null
- Setter updates both fields to maintain compatibility

**Example:**
```java
config.setQuietModeScript("powercfg /setactive SCHEME_MIN");
String script = config.getQuietModeScript();
```

---

#### `getQuitModeScript()` / `setQuitModeScript(String)` **[DEPRECATED]**
Legacy methods for backward compatibility with the "quit" typo.

**⚠️ Deprecated:** Use `getQuietModeScript()` / `setQuietModeScript()` instead.

---

#### `getScanAndFixScript()` / `setScanAndFixScript(String)`
Gets/sets the script to scan and repair system files.

**Typical Script Content:**
- SFC (System File Checker): `sfc /scannow`
- DISM commands for system repair

---

### Utility Methods

#### `trimOrNull(String s)` **[PRIVATE]**
Trims whitespace from a string and converts empty strings to null.

**Behavior:**
- If input is `null`, returns `null`
- If input is empty or whitespace-only after trimming, returns `null`
- Otherwise, returns the trimmed string

**Example:**
```java
trimOrNull("  hello  ") // "hello"
trimOrNull("   ")       // null
trimOrNull(null)        // null
trimOrNull("")          // null
```

---

## Usage Examples

### Example 1: Fetching and Using Configuration

```java
RemoteConfigService service = new RemoteConfigService();
RemoteConfig config = service.fetchConfig();

// Check maintenance mode
if (config.isMaintenance()) {
    MaintenanceDialog.show(stage, config, () -> {
        // Retry after maintenance
        RemoteConfig newConfig = service.fetchConfig();
        if (newConfig.isOnline()) {
            launchNormalUi(stage, newConfig);
        }
    });
    return;
}

// Execute optimization script
String ramScript = config.getFreeRamScript();
if (ramScript != null) {
    powerShellExecutor.execute(ramScript);
}
```

---

### Example 2: Version Checking

```java
String currentVersion = "2.0.5";
RemoteConfig config = service.fetchConfig();

if (config.getLatestVersion() != null) {
    if (currentVersion.compareTo(config.getLatestVersion()) < 0) {
        // Update available
        if (config.isForceUpdate()) {
            showForceUpdateDialog(config.getDownloadUrl(), config.getUpdateMessage());
        } else {
            showOptionalUpdateDialog(config.getDownloadUrl(), config.getUpdateMessage());
        }
    }
}
```

---

### Example 3: Power Mode Switching

```java
RemoteConfig config = service.fetchConfig();

// Switch to Performance Mode
String perfScript = config.getPerformanceModeScript();
if (perfScript != null) {
    powerShellExecutor.execute(perfScript);
}

// Switch to Quiet Mode (Power Saver)
String quietScript = config.getQuietModeScript();
if (quietScript != null) {
    powerShellExecutor.execute(quietScript);
}
```

---

## Thread Safety

- **Read Operations:** Thread-safe (all fields are effectively immutable after deserialization)
- **Write Operations:** Not thread-safe (setters should only be called during deserialization)
- **Recommendation:** Treat instances as immutable after creation

---

## Serialization Notes

### Serialization Format
The class is designed to work with JSON/XML deserialization frameworks like:
- Jackson (JSON)
- Gson (JSON)
- JAXB (XML)

### Example JSON Payload

```json
{
  "appStatus": "online",
  "latestVersion": "2.1.0",
  "minVersion": "2.0.0",
  "downloadUrl": "https://example.com/download/fxshield-2.1.0.exe",
  "updateMessage": "New features available!",
  "forceUpdate": false,
  "freeRamScript": "Get-Process | Where-Object {$_.WorkingSet -gt 100MB} | ForEach-Object {$_.CloseMainWindow()}",
  "optimizeDiskScript": "cleanmgr /sagerun:1",
  "optimizeNetworkScript": "ipconfig /flushdns; netsh int ip reset",
  "performanceModeScript": "powercfg /setactive SCHEME_MIN",
  "balancedModeScript": "powercfg /setactive SCHEME_BALANCED",
  "quietModeScript": "powercfg /setactive SCHEME_MAX",
  "scanAndFixScript": "sfc /scannow"
}
```

---

## Best Practices

### 1. Always Check for Null Scripts
```java
String script = config.getFreeRamScript();
if (script != null && !script.isEmpty()) {
    executor.execute(script);
}
```

### 2. Handle Maintenance Mode First
```java
if (config.isMaintenance()) {
    // Show maintenance dialog
    return;
}
// Continue with normal flow
```

### 3. Use Correct Method Names
```java
// ✅ Correct
config.getQuietModeScript();

// ❌ Deprecated (typo)
config.getQuitModeScript();
```

### 4. Cache Configuration for Offline Use
```java
try {
    RemoteConfig config = service.fetchConfig();
    configCache.save(config); // Serialize to disk
} catch (Exception e) {
    RemoteConfig config = configCache.load(); // Load from cache
}
```

---

## Related Classes

- **`RemoteConfigService`** - Fetches configuration from remote server
- **`MaintenanceDialog`** - Displays maintenance mode UI
- **`PowerShellExecutor`** - Executes PowerShell scripts safely

---

## Version History

| Version | Changes |
|---------|---------|
| 1.0 | Initial implementation with all core features |
| 1.1 | Fixed "quit" → "quiet" typo, added backward compatibility |

---

## Author

**FX Shield Team**

---

## See Also

- [RemoteConfigService Documentation](RemoteConfigService_Documentation.md)
- [PowerShell Script Security Guidelines](PowerShell_Security.md)
- [Application Update Flow](Update_Flow.md)
