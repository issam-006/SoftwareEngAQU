package fxShield.GPU;

public final class GPUStabilizer {

    private final long failGraceMs;     // hold last good value during transient failures
    private final double alpha;         // EMA factor (0..1), higher = more responsive
    private final int zeroConfirm;      // consecutive zeros required to accept 0
    private final int unsupportedValue; // value to use before any valid sample

    private int stable;
    private int zeroStreak = 0;
    private long lastGoodMs = 0;

    public GPUStabilizer(long failGraceMs, double alpha, int zeroConfirm, int unsupportedValue) {
        this.failGraceMs = Math.max(0, failGraceMs);
        this.alpha = clampDouble(alpha, 0.05, 0.95);
        this.zeroConfirm = Math.max(1, zeroConfirm);
        this.unsupportedValue = unsupportedValue;
        this.stable = unsupportedValue;
    }

    // Convenience: uses current time
    public int update(int raw) {
        return update(raw, System.currentTimeMillis());
    }

    // Core update
    public synchronized int update(int raw, long nowMs) {

        // ----- Failed sample -----
        if (raw < 0) {
            // hold last good within grace window
            if (stable >= 0 && lastGoodMs > 0 && (nowMs - lastGoodMs) <= failGraceMs) {
                return stable;
            }

            // grace expired => drop to unsupported (so it doesn't freeze forever)
            if (stable >= 0 && (nowMs - lastGoodMs) > failGraceMs) {
                stable = unsupportedValue;
            }
            return stable;
        }

        // ----- Valid sample -----
        raw = clampInt(raw, 0, 100);

        // Handle zeros carefully (common false readings)
        if (raw == 0) {
            zeroStreak++;

            // If we are already at 0, accept immediately and refresh lastGoodMs
            if (stable == 0) {
                lastGoodMs = nowMs;
                return stable;
            }

            // Require consecutive zeros before accepting 0
            if (zeroStreak < zeroConfirm) {
                // do NOT refresh lastGoodMs here (so repeated fake zeros won't extend grace)
                return stable;
            }

            // Now we accept 0 as real
            lastGoodMs = nowMs;
            stable = smooth(stable, 0);
            return stable;
        }

        // Non-zero valid value
        zeroStreak = 0;
        lastGoodMs = nowMs;
        stable = smooth(stable, raw);
        return stable;
    }

    // Resets internal state to initial conditions
    public synchronized void reset() {
        stable = unsupportedValue;
        zeroStreak = 0;
        lastGoodMs = 0;
    }

    // Returns the last stabilized value (may be unsupportedValue)
    public synchronized int getStable() {
        return stable;
    }

    private int smooth(int prev, int next) {
        // first valid sample
        if (prev < 0) return next;

        double v = prev + alpha * (next - prev);
        return clampInt((int) Math.round(v), 0, 100);
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static double clampDouble(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
