package fxShield;

import java.io.*;
        import java.nio.charset.StandardCharsets;
import java.nio.file.*;
        import java.util.Objects;
import java.util.Properties;

/**
 * Persistent store for FxSettings.
 * - UTF-8 properties
 * - Robust APPDATA fallback
 * - Atomic saves (temp + move)
 * - Thread-safe public API
 */
public final class SettingsStore {

    private static final String DIR_NAME = "FxShield";
    private static final String FILE_NAME = "settings.properties";

    // Property keys (kept for backward compat)
    private static final String K_AUTO_FREE_RAM = "autoFreeRam";
    private static final String K_AUTO_OPTIMIZE_DISK = "autoOptimizeHardDisk";
    private static final String K_AUTO_START_WIN = "autoStartWithWindows";

    private SettingsStore() {}

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

    public static synchronized FxSettings load() {
        FxSettings s = new FxSettings(); // defaults
        Path f = configFile();
        if (!Files.exists(f)) return s;

        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f);
             Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            p.load(r);
            s.autoFreeRam = parseBool(p.getProperty(K_AUTO_FREE_RAM), s.autoFreeRam);
            s.autoOptimizeHardDisk = parseBool(p.getProperty(K_AUTO_OPTIMIZE_DISK), s.autoOptimizeHardDisk);
            s.autoStartWithWindows = parseBool(p.getProperty(K_AUTO_START_WIN), s.autoStartWithWindows);
        } catch (Exception ignored) {}
        return s;
    }

    public static synchronized void save(FxSettings s) {
        Objects.requireNonNull(s, "settings");
        Properties p = new Properties();
        p.setProperty(K_AUTO_FREE_RAM, Boolean.toString(s.autoFreeRam));
        p.setProperty(K_AUTO_OPTIMIZE_DISK, Boolean.toString(s.autoOptimizeHardDisk));
        p.setProperty(K_AUTO_START_WIN, Boolean.toString(s.autoStartWithWindows));

        Path dir = configDir();
        Path file = configFile();
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

        try {
            Files.createDirectories(dir);

            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                p.store(w, "FxShield Settings");
            }
            // Atomic replace if supported
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
        } catch (IOException ignored) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignore) {}
        }
    }

    public static synchronized void reset() {
        try { Files.deleteIfExists(configFile()); } catch (IOException ignored) {}
    }

    private static boolean parseBool(String v, boolean def) {
        if (v == null) return def;
        String s = v.trim().toLowerCase();
        return switch (s) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> def;
        };
    }
}