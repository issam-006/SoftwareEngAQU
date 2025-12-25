package fxShield.WIN;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Properties;

/**
 * Application settings snapshot with utilities for mutation, copy, and (de)serialization.
 * Backward-compatible: public fields retained; getters/setters and builder added.
 */
public final class FxSettings implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    // Keys for serialization
    public static final String KEY_AUTO_FREE_RAM = "autoFreeRam";
    public static final String KEY_AUTO_OPTIMIZE_DISK = "autoOptimizeHardDisk";
    public static final String KEY_AUTO_START_WINDOWS = "autoStartWithWindows";

    // Defaults
    public static final boolean DEFAULT_AUTO_FREE_RAM = false;
    public static final boolean DEFAULT_AUTO_OPTIMIZE_DISK = false;
    public static final boolean DEFAULT_AUTO_START_WINDOWS = false;

    // Retain public fields for compatibility
    public boolean autoFreeRam = DEFAULT_AUTO_FREE_RAM;
    public boolean autoOptimizeHardDisk = DEFAULT_AUTO_OPTIMIZE_DISK;
    public boolean autoStartWithWindows = DEFAULT_AUTO_START_WINDOWS;

    // Constructors
    public FxSettings() {}

    public FxSettings(boolean autoFreeRam, boolean autoOptimizeHardDisk, boolean autoStartWithWindows) {
        this.autoFreeRam = autoFreeRam;
        this.autoOptimizeHardDisk = autoOptimizeHardDisk;
        this.autoStartWithWindows = autoStartWithWindows;
    }

    public FxSettings(FxSettings other) {
        if (other != null) {
            this.autoFreeRam = other.autoFreeRam;
            this.autoOptimizeHardDisk = other.autoOptimizeHardDisk;
            this.autoStartWithWindows = other.autoStartWithWindows;
        }
    }

    // Factory
    public static FxSettings defaults() {
        return new FxSettings(DEFAULT_AUTO_FREE_RAM, DEFAULT_AUTO_OPTIMIZE_DISK, DEFAULT_AUTO_START_WINDOWS);
    }

    public FxSettings copy() {
        return new FxSettings(this);
    }

    // Fluent mutators (return this)
    public FxSettings withAutoFreeRam(boolean v) { this.autoFreeRam = v; return this; }
    public FxSettings withAutoOptimizeHardDisk(boolean v) { this.autoOptimizeHardDisk = v; return this; }
    public FxSettings withAutoStartWithWindows(boolean v) { this.autoStartWithWindows = v; return this; }

    // JavaBean accessors
    public boolean isAutoFreeRam() { return autoFreeRam; }
    public void setAutoFreeRam(boolean autoFreeRam) { this.autoFreeRam = autoFreeRam; }

    public boolean isAutoOptimizeHardDisk() { return autoOptimizeHardDisk; }
    public void setAutoOptimizeHardDisk(boolean autoOptimizeHardDisk) { this.autoOptimizeHardDisk = autoOptimizeHardDisk; }

    public boolean isAutoStartWithWindows() { return autoStartWithWindows; }
    public void setAutoStartWithWindows(boolean autoStartWithWindows) { this.autoStartWithWindows = autoStartWithWindows; }

    // Merge: prefer non-null settings from 'other' (booleans always concrete, so just take other)
    public FxSettings merge(FxSettings other) {
        if (other == null) return this;
        this.autoFreeRam = other.autoFreeRam;
        this.autoOptimizeHardDisk = other.autoOptimizeHardDisk;
        this.autoStartWithWindows = other.autoStartWithWindows;
        return this;
    }

    // Properties (de)serialization (string-based)
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
        return switch (s.trim().toLowerCase()) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> def;
        };
    }

    // Builder
    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private boolean autoFreeRam = DEFAULT_AUTO_FREE_RAM;
        private boolean autoOptimizeHardDisk = DEFAULT_AUTO_OPTIMIZE_DISK;
        private boolean autoStartWithWindows = DEFAULT_AUTO_START_WINDOWS;

        public Builder autoFreeRam(boolean v) { this.autoFreeRam = v; return this; }
        public Builder autoOptimizeHardDisk(boolean v) { this.autoOptimizeHardDisk = v; return this; }
        public Builder autoStartWithWindows(boolean v) { this.autoStartWithWindows = v; return this; }
        public FxSettings build() { return new FxSettings(autoFreeRam, autoOptimizeHardDisk, autoStartWithWindows); }
    }

    // Object
    @Override public String toString() {
        return "FxSettings{" +
                "autoFreeRam=" + autoFreeRam +
                ", autoOptimizeHardDisk=" + autoOptimizeHardDisk +
                ", autoStartWithWindows=" + autoStartWithWindows +
                '}';
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FxSettings that)) return false;
        return autoFreeRam == that.autoFreeRam &&
                autoOptimizeHardDisk == that.autoOptimizeHardDisk &&
                autoStartWithWindows == that.autoStartWithWindows;
    }

    @Override public int hashCode() {
        return Objects.hash(autoFreeRam, autoOptimizeHardDisk, autoStartWithWindows);
    }
}