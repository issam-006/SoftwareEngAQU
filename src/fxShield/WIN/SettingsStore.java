package fxShield.WIN;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Properties;

/**
 * Persistent store for {@link FxSettings}.
 *
 * <p>Characteristics:
 * <ul>
 *   <li>UTF-8 properties file.</li>
 *   <li>APPDATA location with user.home fallback.</li>
 *   <li>Atomic saves using temp file + move (with fallback if atomic not supported).</li>
 *   <li>Thread-safe public API (synchronized).</li>
 * </ul>
 */
public final class SettingsStore {

    private static final String DIR_NAME = "FxShield";
    private static final String FILE_NAME = "settings.properties";

    // Property keys (kept for backward compatibility)
    private static final String K_AUTO_FREE_RAM = "autoFreeRam";
    private static final String K_AUTO_OPTIMIZE_DISK = "autoOptimizeHardDisk";
    private static final String K_AUTO_START_WIN = "autoStartWithWindows";

    private SettingsStore() {
    }

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
     * If the file does not exist or cannot be read/parsed, returns defaults.
     */
    public static synchronized FxSettings load() {
        FxSettings s = new FxSettings(); // defaults
        Path file = configFile();
        if (!Files.exists(file)) return s;

        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file);
             Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {

            p.load(r);
            s.autoFreeRam = parseBool(p.getProperty(K_AUTO_FREE_RAM), s.autoFreeRam);
            s.autoOptimizeHardDisk = parseBool(p.getProperty(K_AUTO_OPTIMIZE_DISK), s.autoOptimizeHardDisk);
            s.autoStartWithWindows = parseBool(p.getProperty(K_AUTO_START_WIN), s.autoStartWithWindows);

        } catch (Exception ignored) {
            // Intentionally ignore: corrupted/missing permissions should not prevent app start.
        }
        return s;
    }

    /**
     * Saves settings to disk using a temp file then move-replace.
     */
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

            try (OutputStream out = Files.newOutputStream(
                    tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {

                p.store(w, "FxShield Settings");
            }

            // Atomic replace if supported by filesystem
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // ignore
            }
        } catch (IOException ignored) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {
                // ignore
            }
        }
    }

    /**
     * Deletes the settings file if it exists.
     */
    public static synchronized void reset() {
        try {
            Files.deleteIfExists(configFile());
        } catch (IOException ignored) {
            // ignore
        }
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
