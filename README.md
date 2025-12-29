  # FX Shield - System Monitor & Optimizer

![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)
![JavaFX](https://img.shields.io/badge/JavaFX-25.0.1-blue?style=flat-square)
![Platform](https://img.shields.io/badge/Platform-Windows%2011-0078D6?style=flat-square&logo=windows)
![License](https://img.shields.io/badge/License-Proprietary-red?style=flat-square)

A modern, frameless JavaFX application for Windows system monitoring and optimization with real-time metrics, remote configuration, and PowerShell-based system tweaks.

---

## 🌟 Features

### 📊 Real-Time System Monitoring
- **CPU Usage** - Multi-core monitoring with percentage display
- **RAM Usage** - Memory consumption with detailed breakdown
- **GPU Usage** - Graphics card utilization (NVIDIA, AMD, Intel)
- **Physical Disks** - Per-disk usage, read/write activity, and capacity

### ⚡ System Optimization Tools
- **Free RAM** - Memory cleanup and cache clearing
- **Optimize Disk** - Disk cleanup and defragmentation
- **Optimize Network** - DNS flush, TCP/IP reset, network tweaks
- **Scan & Fix Files** - SFC and DISM system file repair
- **Power Modes** - Quick switch between Performance/Balanced/Quiet modes
- **One-Click Boost** - Run all optimizations in sequence

### 🎨 Modern UI/UX
- **Frameless Design** - Custom window chrome with Windows 11 styling
- **Dark Mode** - Immersive dark theme with DWM integration
- **Responsive Layout** - Adapts to window size (3-column, 2-column, 1-column)
- **Smooth Animations** - Fade transitions, hover effects, lift animations
- **Compact Mode** - Space-efficient layout for smaller screens

### 🌐 Remote Configuration
- **Firestore Backend** - Fetch configuration from Google Firestore
- **Maintenance Mode** - Server-side control to disable features
- **Dynamic Scripts** - PowerShell scripts delivered remotely
- **Version Management** - Update notifications and forced updates
- **ETag Caching** - Efficient bandwidth usage with HTTP caching

### 🔧 Advanced Features
- **Admin Elevation** - Automatic privilege escalation when needed
- **Startup Management** - Windows startup registry integration
- **Settings Persistence** - Atomic file-based settings storage
- **Tray Icon** - System tray integration with minimize to tray
- **Splash Screen** - Separate stage for loading with fade transitions

---

## 🏗️ Architecture

### Project Structure

```
SoftwareEngAQU/
├── src/
│   ├── fxShield/
│   │   ├── DB/                    # Remote configuration
│   │   │   ├── RemoteConfig.java
│   │   │   └── RemoteConfigService.java
│   │   ├── UI/                    # UI components
│   │   │   ├── BaseCard.java
│   │   │   ├── MeterCard.java
│   │   │   ├── ActionCard.java
│   │   │   ├── TopBarIcons.java
│   │   │   ├── StyleConstants.java
│   │   │   ├── LoadingDialog.java
│   │   │   ├── SettingsDialog.java
│   │   │   ├── PowerModeDialog.java
│   │   │   ├── DeviceInfoDialog.java
│   │   │   └── MaintenanceDialog.java
│   │   ├── UX/                    # Main application
│   │   │   ├── DashBoardPage.java
│   │   │   └── SystemMonitorService.java
│   │   ├── WIN/                   # Windows integration
│   │   │   ├── WindowsUtils.java
│   │   │   ├── FxSettings.java
│   │   │   └── AutomationService.java
│   │   ├── GPU/                   # GPU monitoring
│   │   │   ├── GpuUsageProvider.java
│   │   │   ├── GPUStabilizer.java
│   │   │   ├── NvmlGpuUsageProvider.java
│   │   │   ├── PdhGpuUsageProvider.java
│   │   │   ├── TypeperfGpuUsageProvider.java
│   │   │   └── HybridGpuUsageProvider.java
│   │   └── DISK/                  # Disk monitoring
│   │       ├── PhysicalDiskCard.java
│   │       └── PhysicalDiskSwitcher.java
│   └── META-INF/
│       └── MANIFEST.MF
└── docs/                          # Documentation
    ├── RemoteConfig_Documentation.md
    ├── RemoteConfigService_Documentation.md
    ├── UI_Components_Documentation.md
    ├── Windows_Utilities_Documentation.md
    └── README.md (this file)
```

---

## 🚀 Getting Started

### Prerequisites

- **Java Development Kit (JDK) 25** or higher
- **JavaFX SDK 25.0.1** or higher
- **Windows 10 1809+** or **Windows 11**
- **Administrator privileges** (for system optimization features)

### Dependencies

```xml
<!-- Maven dependencies -->
<dependencies>
    <!-- JavaFX -->
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-controls</artifactId>
        <version>25.0.1</version>
    </dependency>

    <!-- JSON parsing -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.10.1</version>
    </dependency>

    <!-- Windows API -->
    <dependency>
        <groupId>net.java.dev.jna</groupId>
        <artifactId>jna</artifactId>
        <version>5.13.0</version>
    </dependency>
    <dependency>
        <groupId>net.java.dev.jna</groupId>
        <artifactId>jna-platform</artifactId>
        <version>5.13.0</version>
    </dependency>

    <!-- Logging -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.9</version>
    </dependency>
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.4.11</version>
    </dependency>
</dependencies>
```

### Building

#### Using IntelliJ IDEA

1. Open project in IntelliJ IDEA
2. Configure JDK 25 in Project Structure
3. Add JavaFX SDK to libraries
4. Build → Build Project
5. Run `fxShield.UX.DashBoardPage`

#### Using Maven

```bash
mvn clean package
```

#### Using Command Line

```bash
javac --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml -d out src/fxShield/**/*.java
```

### Running

#### From IntelliJ IDEA

1. Right-click `DashBoardPage.java`
2. Run 'DashBoardPage.main()'

#### From Command Line

```bash
java --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml -cp out fxShield.UX.DashBoardPage
```

#### With Arguments

```bash
# Start minimized to tray
java ... fxShield.UX.DashBoardPage --minimized
```

---

## 📖 Documentation

Comprehensive documentation is available in the `docs/` directory:

- **[RemoteConfig Documentation](docs/RemoteConfig_Documentation.md)** - Configuration data model
- **[RemoteConfigService Documentation](docs/RemoteConfigService_Documentation.md)** - Firestore integration
- **[UI Components Documentation](docs/UI_Components_Documentation.md)** - Card components and styling
- **[Windows Utilities Documentation](docs/Windows_Utilities_Documentation.md)** - PowerShell, admin elevation, DWM styling

---

## 🎨 UI Components

### MeterCard
Real-time metric display with color-coded indicators:
- **Purple (0-59%)** - Normal usage
- **Orange (60-84%)** - High usage
- **Red (85-100%)** - Critical usage

### ActionCard
Interactive action buttons with:
- SVG icons
- Hover lift animation
- Custom button actions

### TopBarIcons
Circular icon buttons for:
- Device information
- Settings dialog

---

## 🔌 Remote Configuration

### Firestore Structure

```json
{
  "fields": {
    "appStatus": { "stringValue": "online" },
    "latestVersion": { "stringValue": "2.1.0" },
    "forceUpdate": { "booleanValue": false },
    "FreeRam_Script": { "stringValue": "Get-Process | ..." },
    "OptimizeDisk_Script": { "stringValue": "cleanmgr /sagerun:1" },
    "OptimizeNetwork_Script": { "stringValue": "ipconfig /flushdns" },
    "PerformanceMode_Script": { "stringValue": "powercfg /setactive ..." },
    "BalancedMode_Script": { "stringValue": "powercfg /setactive ..." },
    "QuietMode_Script": { "stringValue": "powercfg /setactive ..." },
    "ScanAndFix_Script": { "stringValue": "sfc /scannow" }
  }
}
```

### Configuration URL

```
https://firestore.googleapis.com/v1/projects/fx-shield-aqu/databases/(default)/documents/fxShield/config
```

---

## 🛠️ System Requirements

### Minimum Requirements
- **OS:** Windows 10 1809 or later
- **CPU:** Dual-core processor
- **RAM:** 4 GB
- **Disk:** 100 MB free space
- **Display:** 1280x720 resolution

### Recommended Requirements
- **OS:** Windows 11
- **CPU:** Quad-core processor
- **RAM:** 8 GB or more
- **Disk:** 500 MB free space
- **Display:** 1920x1080 resolution

---

## ⚙️ Configuration

### Settings File Location

```
%APPDATA%\FxShield\settings.properties
```

### Available Settings

```properties
autoFreeRam=false
autoOptimizeHardDisk=false
autoStartWithWindows=false
```

### Programmatic Access

```java
// Load settings
FxSettings settings = FxSettings.load();

// Modify
settings.autoFreeRam = true;

// Save
FxSettings.save(settings);
```

---

## 🔒 Security

### PowerShell Execution
- **Execution Policy Bypass** - Required for script execution
- **Input Escaping** - All user input is escaped
- **Timeout Protection** - Scripts timeout after 25 seconds
- **Admin Elevation** - Automatic privilege escalation when needed

### Best Practices
```java
// ✅ Safe - Escaped input
String userInput = getUserInput();
String escaped = WindowsUtils.escapeForPowerShell(userInput);
String script = "Write-Host '" + escaped + "'";

// ❌ Unsafe - Injection risk
String script = "Write-Host '" + getUserInput() + "'";
```

---

## 🐛 Troubleshooting

### Dashboard Not Showing After Splash

**Symptom:** Splash screen shows, but dashboard never appears

**Solution:**
1. Check if `WindowsUtils.styleStage()` is called after `stage.show()`
2. Ensure `Platform.runLater()` is used for `stage.setMaximized(true)`
3. Verify `FadeTransition` is played after stage is shown

### PowerShell Scripts Not Running

**Symptom:** Scripts fail with access denied

**Solution:**
1. Run application as Administrator
2. Check `WindowsUtils.isAdmin()` returns `true`
3. Use `WindowsUtils.requestAdminAndExit()` to elevate

### GPU Monitoring Not Working

**Symptom:** GPU card shows "N/A"

**Solution:**
1. Check if GPU drivers are installed
2. Verify GPU is detected by Windows
3. Try different GPU providers (NVML, PDH, typeperf)

---

## 📊 Performance

### Monitoring Overhead
- **CPU:** ~1-2% on modern systems
- **RAM:** ~150-200 MB
- **Disk:** Minimal (settings file only)
- **Network:** ~1-5 KB per config fetch

### Optimization
- **UI Coalescing** - Prevents backlog from high-frequency updates
- **Change Detection** - Only updates changed UI elements
- **Cached Fonts** - Single allocation per font
- **ETag Caching** - Minimizes network bandwidth

---

## 🤝 Contributing

This is a proprietary project. Contributions are not currently accepted.

---

## 📝 License

**Proprietary License** - All rights reserved.

This software is proprietary and confidential. Unauthorized copying, distribution, or use is strictly prohibited.

---

## 👥 Authors

**FX Shield Team**

---

## 🙏 Acknowledgments

- **JavaFX** - Modern UI framework
- **Google Firestore** - Remote configuration backend
- **JNA** - Windows API integration
- **Gson** - JSON parsing
- **SLF4J/Logback** - Logging framework

---

## 📞 Support

For support, please contact the FX Shield team.

---

## 🗺️ Roadmap

### Version 2.1.0 (Current)
- ✅ Frameless window design
- ✅ Windows 11 DWM styling
- ✅ Remote configuration
- ✅ Real-time monitoring
- ✅ System optimization tools

### Version 2.2.0 (Planned)
- 🔲 Multi-language support
- 🔲 Custom themes
- 🔲 Scheduled optimizations
- 🔲 Performance history graphs
- 🔲 Export reports

### Version 3.0.0 (Future)
- 🔲 Cloud sync
- 🔲 Mobile companion app
- 🔲 AI-powered optimization
- 🔲 Advanced analytics

---

## 📸 Screenshots

### Main Dashboard
![Dashboard](docs/screenshots/dashboard.png)

### System Monitoring
![Monitoring](docs/screenshots/monitoring.png)

### Optimization Tools
![Tools](docs/screenshots/tools.png)

---

## 🔗 Links

- **Documentation:** [docs/](docs/)
- **Firestore Console:** [Firebase Console](https://console.firebase.google.com/)
- **JavaFX:** [openjfx.io](https://openjfx.io/)

---

## ⚡ Quick Start Guide

### 1. Clone and Build

```bash
git clone <repository-url>
cd SoftwareEngAQU
mvn clean package
```

### 2. Run Application

```bash
java --module-path /path/to/javafx-sdk/lib \
     --add-modules javafx.controls \
     -jar target/fxshield.jar
```

### 3. First Launch

1. Application shows splash screen
2. Fetches configuration from Firestore
3. Displays main dashboard
4. Starts system monitoring

### 4. Using Optimization Tools

1. Click any action card (e.g., "Free RAM")
2. Loading dialog appears
3. PowerShell script executes
4. Results shown in dialog

---

## 🎯 Key Features Explained

### Frameless Window
- Custom window chrome without Windows title bar
- Drag region in header
- Custom minimize/maximize/close buttons
- Windows 11 Snap Layout support

### Responsive Layout
- **Wide (>1350px):** 3-column grid
- **Medium (880-1350px):** 2-column grid
- **Narrow (<880px):** 1-column grid
- Automatic compact mode for smaller windows

### Remote Configuration
- Fetches scripts from Firestore
- ETag-based caching
- Automatic retry with exponential backoff
- Graceful fallback to cached config

### System Monitoring
- **Sampling Rate:** 1 second
- **Thread-Safe:** Background monitoring thread
- **UI Updates:** Coalesced to prevent backlog
- **Auto-Pause:** Pauses when minimized

---

## 💡 Tips & Tricks

### Performance
- Minimize to tray to reduce CPU usage
- Enable compact mode on smaller screens
- Close unused applications before optimization

### Customization
- Edit `StyleConstants.java` for custom colors
- Modify `FxSettings.java` for new settings
- Add custom scripts to Firestore

### Debugging
- Check logs in console output
- Use `WindowsUtils.runPowerShellLogged()` for script debugging
- Enable verbose logging in `logback.xml`

---

## 🔧 Advanced Configuration

### Custom Firestore URL

```java
RemoteConfigService service = new RemoteConfigService(
    "https://firestore.googleapis.com/.../custom-config"
);
```

### Custom HTTP Client

```java
HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

RemoteConfigService service = new RemoteConfigService(client, url);
```

### Custom Monitoring Interval

```java
SystemMonitorService monitor = new SystemMonitorService();
// Modify sampling interval in SystemMonitorService.java
```

---

## 📚 Additional Resources

- [JavaFX Documentation](https://openjfx.io/javadoc/25/)
- [Firestore REST API](https://firebase.google.com/docs/firestore/use-rest-api)
- [Windows PowerShell Documentation](https://docs.microsoft.com/en-us/powershell/)
- [JNA Documentation](https://github.com/java-native-access/jna)

---

**Made with ❤️ by FX Shield Team**
