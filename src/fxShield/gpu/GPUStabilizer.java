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
        if (raw < 0) { // failed sample
            if (stable >= 0 && (nowMs - lastGoodMs) <= failGraceMs) {
                return stable; // hold during grace window
            }
            return stable; // keep unsupported or last known
        }

        raw = clampInt(raw, 0, 100);
        lastGoodMs = nowMs;

        if (raw == 0) {
            zeroStreak++;
            if (stable > 0 && zeroStreak < zeroConfirm) {
                return stable; // ignore brief zero dips
            }
            stable = smooth(stable, 0);
            return stable;
        }

        zeroStreak = 0;
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
        if (prev < 0) return next; // first valid sample
        double v = prev + alpha * (next - prev);
        // clamp after rounding to avoid drift outside bounds
        return clampInt((int)Math.round(v), 0, 100);
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