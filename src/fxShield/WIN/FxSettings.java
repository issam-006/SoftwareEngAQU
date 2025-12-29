package fxShield.WIN;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;
import java.util.Properties;

/**
 * Application settings snapshot.
 *
 * <p>Design goals:
 * <ul>
 *   <li>Serializable (for persistence).</li>
 *   <li>Backward-compatible: keeps public fields (some code may access them directly).</li>
 *   <li>Convenient APIs: getters/setters, fluent "with*" methods, and Properties (de)serialization.</li>
 * </ul>
 */
public final class FxSettings implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // =========================================================================
    // Keys (Properties serialization)
    // =========================================================================

    public static final String KEY_AUTO_FREE_RAM = "autoFreeRam";
    public static final String KEY_AUTO_OPTIMIZE_DISK = "autoOptimizeHardDisk";
    public static final String KEY_AUTO_START_WINDOWS = "autoStartWithWindows";

    // =========================================================================
    // Defaults
    // =========================================================================

    public static final boolean DEFAULT_AUTO_FREE_RAM = false;
    public static final boolean DEFAULT_AUTO_OPTIMIZE_DISK = false;
    public static final boolean DEFAULT_AUTO_START_WINDOWS = false;

    // =========================================================================
    // State (kept public for backward-compatibility)
    // =========================================================================

    public boolean autoFreeRam = DEFAULT_AUTO_FREE_RAM;
    public boolean autoOptimizeHardDisk = DEFAULT_AUTO_OPTIMIZE_DISK;
    public boolean autoStartWithWindows = DEFAULT_AUTO_START_WINDOWS;

    // =========================================================================
    // Constructors
    // =========================================================================

    public FxSettings() {
    }

    public FxSettings(boolean autoFreeRam, boolean autoOptimizeHardDisk, boolean autoStartWithWindows) {
        this.autoFreeRam = autoFreeRam;
        this.autoOptimizeHardDisk = autoOptimizeHardDisk;
        this.autoStartWithWindows = autoStartWithWindows;
    }

    public FxSettings(FxSettings other) {
        if (other == null) return;
        this.autoFreeRam = other.autoFreeRam;
        this.autoOptimizeHardDisk = other.autoOptimizeHardDisk;
        this.autoStartWithWindows = other.autoStartWithWindows;
    }

    // =========================================================================
    // Factories / Copy
    // =========================================================================

    public static FxSettings defaults() {
        return new FxSettings(DEFAULT_AUTO_FREE_RAM, DEFAULT_AUTO_OPTIMIZE_DISK, DEFAULT_AUTO_START_WINDOWS);
    }

    public FxSettings copy() {
        return new FxSettings(this);
    }

    // =========================================================================
    // Fluent mutators (return this)
    // =========================================================================

    public FxSettings withAutoFreeRam(boolean v) {
        this.autoFreeRam = v;
        return this;
    }

    public FxSettings withAutoOptimizeHardDisk(boolean v) {
        this.autoOptimizeHardDisk = v;
        return this;
    }

    public FxSettings withAutoStartWithWindows(boolean v) {
        this.autoStartWithWindows = v;
        return this;
    }

    // =========================================================================
    // JavaBean accessors
    // =========================================================================

    public boolean isAutoFreeRam() {
        return autoFreeRam;
    }

    public void setAutoFreeRam(boolean autoFreeRam) {
        this.autoFreeRam = autoFreeRam;
    }

    public boolean isAutoOptimizeHardDisk() {
        return autoOptimizeHardDisk;
    }

    public void setAutoOptimizeHardDisk(boolean autoOptimizeHardDisk) {
        this.autoOptimizeHardDisk = autoOptimizeHardDisk;
    }

    public boolean isAutoStartWithWindows() {
        return autoStartWithWindows;
    }

    public void setAutoStartWithWindows(boolean autoStartWithWindows) {
        this.autoStartWithWindows = autoStartWithWindows;
    }

    // =========================================================================
    // Merge
    // =========================================================================

    /**
     * Overwrites this settings object with values from {@code other}.
     * (Booleans are concrete values, so merge = overwrite.)
     */
    public FxSettings merge(FxSettings other) {
        if (other == null) return this;
        this.autoFreeRam = other.autoFreeRam;
        this.autoOptimizeHardDisk = other.autoOptimizeHardDisk;
        this.autoStartWithWindows = other.autoStartWithWindows;
        return this;
    }

    // =========================================================================
    // Properties (de)serialization
    // =========================================================================

    public Properties toProperties() {
        Properties p = new Properties();
        p.setProperty(KEY_AUTO_FREE_RAM, Boolean.toString(autoFreeRam));
        p.setProperty(KEY_AUTO_OPTIMIZE_DISK, Boolean.toString(autoOptimizeHardDisk));
        p.setProperty(KEY_AUTO_START_WINDOWS, Boolean.toString(autoStartWithWindows));
        return p;
    }

    public static FxSettings fromProperties(Properties p, FxSettings fallback) {
        FxSettings base = (fallback != null) ? fallback.copy() : FxSettings.defaults();
        if (p == null) return base;

        base.autoFreeRam = parseBool(p.getProperty(KEY_AUTO_FREE_RAM), base.autoFreeRam);
        base.autoOptimizeHardDisk = parseBool(p.getProperty(KEY_AUTO_OPTIMIZE_DISK), base.autoOptimizeHardDisk);
        base.autoStartWithWindows = parseBool(p.getProperty(KEY_AUTO_START_WINDOWS), base.autoStartWithWindows);
        return base;
    }

    private static boolean parseBool(String s, boolean def) {
        if (s == null) return def;

        String v = s.trim().toLowerCase();
        return switch (v) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> def;
        };
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    private static final String DIR_NAME = "FxShield";
    private static final String FILE_NAME = "settings.properties";

    private static Path configDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            appData = System.getProperty("user.home", ".");
        }
        return Paths.get(appData, DIR_NAME);
    }

    private static Path configFile() {
        return configDir().resolve(FILE_NAME);
    }

    /**
     * Loads settings from disk.
     * Returns defaults if file is missing or corrupted.
     */
    public static synchronized FxSettings load() {
        Properties p = new Properties();
        Path file = configFile();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file);
                 Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (Exception ignored) {
            }
        }
        return fromProperties(p, defaults());
    }

    /**
     * Saves settings to disk atomically.
     */
    public static synchronized void save(FxSettings s) {
        if (s == null) return;
        Properties p = s.toProperties();

        Path dir = configDir();
        Path file = configFile();
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

        try {
            Files.createDirectories(dir);

            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                p.store(w, "FxShield Settings");
            }

            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException ignored) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {
            }
        }
    }

    /**
     * Deletes the settings file.
     */
    public static synchronized void reset() {
        try {
            Files.deleteIfExists(configFile());
        } catch (IOException ignored) {
        }
    }

    // =========================================================================
    // Builder
    // =========================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean autoFreeRam = DEFAULT_AUTO_FREE_RAM;
        private boolean autoOptimizeHardDisk = DEFAULT_AUTO_OPTIMIZE_DISK;
        private boolean autoStartWithWindows = DEFAULT_AUTO_START_WINDOWS;

        public Builder autoFreeRam(boolean v) {
            this.autoFreeRam = v;
            return this;
        }

        public Builder autoOptimizeHardDisk(boolean v) {
            this.autoOptimizeHardDisk = v;
            return this;
        }

        public Builder autoStartWithWindows(boolean v) {
            this.autoStartWithWindows = v;
            return this;
        }

        public FxSettings build() {
            return new FxSettings(autoFreeRam, autoOptimizeHardDisk, autoStartWithWindows);
        }
    }

    // =========================================================================
    // Object overrides
    // =========================================================================

    @Override
    public String toString() {
        return "FxSettings{" +
                "autoFreeRam=" + autoFreeRam +
                ", autoOptimizeHardDisk=" + autoOptimizeHardDisk +
                ", autoStartWithWindows=" + autoStartWithWindows +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FxSettings that)) return false;
        return autoFreeRam == that.autoFreeRam
                && autoOptimizeHardDisk == that.autoOptimizeHardDisk
                && autoStartWithWindows == that.autoStartWithWindows;
    }

    @Override
    public int hashCode() {
        return Objects.hash(autoFreeRam, autoOptimizeHardDisk, autoStartWithWindows);
    }
}
