# RemoteConfigService Class Documentation

## Overview

**Package:** `fxShield.DB`
**Class:** `RemoteConfigService`
**Type:** `public final class`

---

## Purpose

`RemoteConfigService` is responsible for fetching remote configuration from a Firestore backend. It provides:

- **HTTP-based configuration fetching** from Google Firestore REST API
- **Automatic retry logic** with exponential backoff
- **ETag-based caching** to minimize bandwidth usage
- **GZIP decompression** support
- **Graceful fallback** to cached configuration on network failures
- **Thread-safe caching** mechanism

---

## Architecture

### Key Features

1. **Resilient Network Communication**
   - Automatic retries (up to 2 retries)
   - Exponential backoff with jitter
   - Timeout handling (6s connect, 8s request)

2. **Efficient Caching**
   - ETag-based HTTP caching (304 Not Modified)
   - In-memory cache for offline fallback
   - Volatile fields for thread-safe access

3. **Firestore Integration**
   - Direct REST API access (no Firebase SDK needed)
   - Parses Firestore document structure
   - Supports both legacy and new field names

4. **Robust Parsing**
   - Lenient JSON parsing
   - BOM and XSSI prefix handling
   - GZIP auto-detection and decompression

---

## Constants

### Network Configuration

```java
private static final String DEFAULT_CONFIG_URL =
    "https://firestore.googleapis.com/v1/projects/fx-shield-aqu/databases/(default)/documents/fxShield/config";

private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(6);
private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
private static final int MAX_RETRIES = 2;
```

### Firestore Field Names

| Constant | Firestore Field | Description |
|----------|----------------|-------------|
| `F_APP_STATUS` | `appStatus` | Application status (online/maintenance) |
| `F_LATEST_VER` | `latestVersion` | Latest app version |
| `F_MIN_VER` | `minVersion` | Minimum required version |
| `F_DL_URL` | `downloadUrl` | Download URL for updates |
| `F_UPD_MSG` | `updateMessage` | Update notification message |
| `F_FORCE_UPD` | `forceUpdate` | Force update flag |
| `F_FREE_RAM` | `FreeRam_Script` | RAM optimization script |
| `F_DISK` | `OptimizeDisk_Script` | Disk optimization script |
| `F_NET` | `OptimizeNetwork_Script` | Network optimization script |
| `F_PERF` | `PerformanceMode_Script` | Performance power mode script |
| `F_BAL` | `BalancedMode_Script` | Balanced power mode script |
| `F_QUIET` | `QuietMode_Script` | Quiet/Power Saver mode script |
| `F_QUIT_LEGACY` | `QuitMode_Script` | Legacy typo field |
| `F_SCAN_FIX` | `ScanAndFix_Script` | System file scan script |

---

## Constructors

### `RemoteConfigService()`
Creates a service instance with default configuration URL.

**Example:**
```java
RemoteConfigService service = new RemoteConfigService();
```

---

### `RemoteConfigService(String configUrl)`
Creates a service instance with custom configuration URL.

**Parameters:**
- `configUrl` - Custom Firestore document URL

**Example:**
```java
String customUrl = "https://firestore.googleapis.com/.../myConfig";
RemoteConfigService service = new RemoteConfigService(customUrl);
```

---

### `RemoteConfigService(HttpClient client, String configUrl)`
Creates a service instance with custom HTTP client and URL.

**Parameters:**
- `client` - Custom HttpClient (or null for default)
- `configUrl` - Custom configuration URL (or null for default)

**Example:**
```java
HttpClient customClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();
RemoteConfigService service = new RemoteConfigService(customClient, customUrl);
```

---

## Methods

### `fetchConfig()`

Fetches the remote configuration from Firestore.

**Returns:** `RemoteConfig` - The fetched configuration, or cached config on failure

**Behavior:**
1. Sends HTTP GET request with ETag header (if cached)
2. Returns cached config if server responds with 304 Not Modified
3. Retries on transient errors (429, 5xx) with exponential backoff
4. Falls back to cached config on persistent failures
5. Parses Firestore JSON response into `RemoteConfig` object
6. Updates cache with new ETag and configuration

**Thread Safety:** Safe to call from any thread (uses volatile cache)

**Example:**
```java
RemoteConfigService service = new RemoteConfigService();

// Fetch config (may take a few seconds)
RemoteConfig config = service.fetchConfig();

if (config != null) {
    if (config.isMaintenance()) {
        showMaintenanceDialog(config.getUpdateMessage());
    } else {
        String script = config.getFreeRamScript();
        if (script != null) {
            executeScript(script);
        }
    }
} else {
    // Fallback to offline mode
    showOfflineNotification();
}
```

---

## Internal Methods

### `isTransient(int code)`
Determines if an HTTP status code represents a transient error.

**Parameters:**
- `code` - HTTP status code

**Returns:** `true` if code is 429 (Too Many Requests) or 5xx (Server Error)

---

### `sleepBackoff(int attempt)`
Sleeps for exponential backoff duration with jitter.

**Formula:** `base * (attempt + 1) + random(0-120ms)`

**Parameters:**
- `attempt` - Current retry attempt number (0-based)

---

### `decodeBody(HttpResponse<byte[]> resp)`
Decodes HTTP response body, handling GZIP compression.

**Features:**
- Checks `Content-Encoding: gzip` header
- Auto-detects GZIP magic bytes (0x1F 0x8B)
- Falls back to UTF-8 decoding on decompression failure

**Parameters:**
- `resp` - HTTP response with byte array body

**Returns:** Decoded string body

---

### `parseJsonLenient(String body)`
Parses JSON with lenient mode and sanitization.

**Features:**
- Strips BOM (Byte Order Mark)
- Removes XSSI prefix (`)]}'`)
- Uses lenient JSON reader for malformed JSON

**Parameters:**
- `body` - Raw JSON string

**Returns:** Parsed `JsonObject`

---

### `sanitizeJsonBody(String s)`
Sanitizes JSON string before parsing.

**Removes:**
- UTF-8 BOM (`\uFEFF`)
- XSSI protection prefix (`)]}'`)

**Parameters:**
- `s` - Raw JSON string

**Returns:** Sanitized JSON string

---

### `safeObject(JsonObject parent, String key)`
Safely extracts a nested JSON object.

**Parameters:**
- `parent` - Parent JSON object
- `key` - Key to extract

**Returns:** Nested `JsonObject`, or `null` if not found/invalid

---

### `getString(JsonObject fields, String name)`
Extracts a string value from Firestore field structure.

**Firestore Structure:**
```json
{
  "fieldName": {
    "stringValue": "actual value"
  }
}
```

**Parameters:**
- `fields` - Firestore fields object
- `name` - Field name

**Returns:** String value, or `null` if not found

---

### `getBool(JsonObject fields, String name)`
Extracts a boolean value from Firestore field structure.

**Firestore Structure:**
```json
{
  "fieldName": {
    "booleanValue": true
  }
}
```

**Parameters:**
- `fields` - Firestore fields object
- `name` - Field name

**Returns:** Boolean value, or `false` if not found

---

### `firstNonBlank(String a, String b)`
Returns the first non-blank string.

**Parameters:**
- `a` - First string
- `b` - Second string (fallback)

**Returns:** First non-blank string, or `null` if both are blank

---

## Usage Examples

### Example 1: Basic Configuration Fetch

```java
RemoteConfigService service = new RemoteConfigService();
RemoteConfig config = service.fetchConfig();

if (config != null && config.isOnline()) {
    System.out.println("App is online!");
    System.out.println("Latest version: " + config.getLatestVersion());
} else if (config != null && config.isMaintenance()) {
    System.out.println("App is under maintenance");
    System.out.println("Message: " + config.getUpdateMessage());
}
```

---

### Example 2: Retry Logic in Action

```java
// Service automatically retries on transient errors
RemoteConfigService service = new RemoteConfigService();

// This will retry up to 2 times on 429/5xx errors
RemoteConfig config = service.fetchConfig();

// Always returns a config (cached or fresh)
// Never returns null unless no cache exists
```

---

### Example 3: ETag Caching

```java
RemoteConfigService service = new RemoteConfigService();

// First fetch: Downloads full config
RemoteConfig config1 = service.fetchConfig();
// ETag: "abc123" is cached

// Second fetch: Server returns 304 Not Modified
RemoteConfig config2 = service.fetchConfig();
// Returns cached config (no download)

// Third fetch: Config changed on server
RemoteConfig config3 = service.fetchConfig();
// Downloads new config, updates ETag
```

---

### Example 4: Custom HTTP Client

```java
HttpClient customClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build();

RemoteConfigService service = new RemoteConfigService(
    customClient,
    "https://my-custom-firestore-url.com/config"
);

RemoteConfig config = service.fetchConfig();
```

---

## Error Handling

### Network Failures

The service handles network failures gracefully:

1. **Transient Errors (429, 5xx):**
   - Automatically retries with exponential backoff
   - Up to 2 retries (3 total attempts)

2. **Persistent Errors:**
   - Returns cached configuration
   - Never throws exceptions

3. **Timeout:**
   - Connect timeout: 6 seconds
   - Request timeout: 8 seconds
   - Returns cached config on timeout

### Parsing Failures

- Lenient JSON parsing handles malformed JSON
- Falls back to cached config on parse errors
- Logs errors but never crashes

---

## Thread Safety

### Volatile Cache

```java
private volatile String cachedEtag;
private volatile RemoteConfig cachedConfig;
```

- **Safe for concurrent reads** from multiple threads
- **Not synchronized** - last write wins
- **Recommended:** Call from background thread, not FX thread

### Example: Background Fetch

```java
new Thread(() -> {
    RemoteConfigService service = new RemoteConfigService();
    RemoteConfig config = service.fetchConfig();

    Platform.runLater(() -> {
        // Update UI with config
        updateUI(config);
    });
}, "config-fetch").start();
```

---

## Firestore Response Format

### Example Firestore Document

```json
{
  "name": "projects/fx-shield-aqu/databases/(default)/documents/fxShield/config",
  "fields": {
    "appStatus": {
      "stringValue": "online"
    },
    "latestVersion": {
      "stringValue": "2.1.0"
    },
    "forceUpdate": {
      "booleanValue": false
    },
    "FreeRam_Script": {
      "stringValue": "Get-Process | Stop-Process"
    }
  },
  "createTime": "2024-01-01T00:00:00.000000Z",
  "updateTime": "2024-01-15T12:30:00.000000Z"
}
```

---

## Best Practices

### 1. Call from Background Thread

```java
// ✅ Good
new Thread(() -> {
    RemoteConfig config = service.fetchConfig();
    Platform.runLater(() -> applyConfig(config));
}).start();

// ❌ Bad (blocks UI)
RemoteConfig config = service.fetchConfig(); // on FX thread
```

---

### 2. Handle Null Gracefully

```java
RemoteConfig config = service.fetchConfig();

// Always check for null (though rare)
if (config != null) {
    String script = config.getFreeRamScript();
    if (script != null && !script.isEmpty()) {
        executeScript(script);
    }
}
```

---

### 3. Use Failsafe Timeout

```java
AtomicBoolean fetched = new AtomicBoolean(false);

// Failsafe: continue after 7 seconds even if fetch hangs
PauseTransition failsafe = new PauseTransition(Duration.seconds(7));
failsafe.setOnFinished(e -> {
    if (fetched.compareAndSet(false, true)) {
        launchApp(null); // Launch without config
    }
});
failsafe.play();

// Fetch in background
new Thread(() -> {
    RemoteConfig config = service.fetchConfig();
    if (fetched.compareAndSet(false, true)) {
        failsafe.stop();
        Platform.runLater(() -> launchApp(config));
    }
}).start();
```

---

## Performance Characteristics

| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| First fetch | O(network) | ~1-3 seconds typical |
| Cached fetch (304) | O(network) | ~100-500ms (no body download) |
| Retry backoff | O(attempts) | 200ms, 400ms, 600ms |
| JSON parsing | O(n) | n = response size |
| GZIP decompression | O(n) | n = compressed size |

---

## Dependencies

- **Java 11+** - HttpClient, Duration
- **Gson** - JSON parsing
- **JNA** - Not used in this class

---

## Related Classes

- **`RemoteConfig`** - Configuration data model
- **`RemoteConfigService`** - This class (fetcher)
- **`DashBoardPage`** - Main consumer of configuration

---

## Version History

| Version | Changes |
|---------|---------|
| 1.0 | Initial implementation with Firestore REST API |
| 1.1 | Added ETag caching and GZIP support |
| 1.2 | Added retry logic with exponential backoff |
| 1.3 | Added support for legacy "QuitMode" field |

---

## Author

**FX Shield Team**

---

## See Also

- [RemoteConfig Documentation](RemoteConfig_Documentation.md)
- [Firestore REST API Reference](https://firebase.google.com/docs/firestore/use-rest-api)
