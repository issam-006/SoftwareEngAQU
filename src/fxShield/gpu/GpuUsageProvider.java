package fxShield.GPU;

import java.util.OptionalInt;

/**
 * Provides a snapshot of overall GPU utilization.
 * Contract:
 * - Returns 0..100 inclusive on success.
 * - Returns -1 when unavailable/transiently failing.
 * - Implementations should be non-blocking or clearly document latency.
 * - Thread-safety is implementation-defined; callers should assume thread-safe reads if shared.
 *
 * Lifecycle:
 * - Implementations may hold native/system resources; always call close().
 */
public interface GpuUsageProvider extends AutoCloseable {

    /**
     * @return GPU usage 0..100, or -1 if unavailable or an error occurred.
     */
    int readGpuUsagePercent();

    /**
     * Convenience wrapper that maps the sentinel -1 to OptionalInt.empty().
     * This does not alter the underlying read semantics.
     */
    default OptionalInt tryReadGpuUsagePercent() {
        int v = readGpuUsagePercent();
        return (v >= 0 && v <= 100) ? OptionalInt.of(v) : OptionalInt.empty();
    }

    /**
     * Indicates whether this provider is expected to work on the current platform/runtime.
     * Default returns true; implementations can override (e.g., library presence checks).
     */
    default boolean isAvailable() {
        return true;
    }

    @Override
    default void close() {}
}