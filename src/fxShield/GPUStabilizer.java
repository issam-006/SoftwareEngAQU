package fxShield;

public class GPUStabilizer {

    private int stable = -1;

    private int zeroStreak = 0;
    private int failStreak = 0;

    private long lastGoodMs = 0;

    // tuning (runtime)
    private final long minUpdateMs;     // مثلاً 1800
    private final double alpha;         // مثلاً 0.22
    private final int zeroConfirm;      // مثلاً 2 أو 3
    private final int unsupportedValue; // مثلاً -1

    private final long failGraceMs;     // مدة مسموح فيها نثبت آخر قراءة
    private long lastUpdateMs = 0;

    public GPUStabilizer(long minUpdateMs, double alpha, int zeroConfirm, int unsupportedValue) {
        this.minUpdateMs = Math.max(250, minUpdateMs);
        this.alpha = clampDouble(alpha, 0.08, 0.45);
        this.zeroConfirm = Math.max(1, zeroConfirm);
        this.unsupportedValue = unsupportedValue;

        // خليها تقريباً 3–4 دورات من التحديث (مثلاً 1800ms => ~6-7s)
        this.failGraceMs = Math.max(1500, this.minUpdateMs * 4);
    }

    public synchronized int update(int raw, long nowMs) {

        // throttle: ما تعمل smoothing وتغيير قيمة إلا كل minUpdateMs
        if (lastUpdateMs != 0 && (nowMs - lastUpdateMs) < minUpdateMs) {
            return (stable < 0) ? unsupportedValue : stable;
        }
        lastUpdateMs = nowMs;

        // raw = -1 => فشل قياس
        if (raw < 0) {
            failStreak++;

            // إذا عندك قيمة سابقة “صالحة” وخلال فترة grace → ثبّت عليها وما تنزل 0
            if (stable >= 0 && (nowMs - lastGoodMs) <= failGraceMs) {
                return stable;
            }

            // بعد grace وما في قراءة → اعتبرها unsupportedValue (مش 0)
            stable = unsupportedValue;
            return stable;
        }

        // raw 0..100
        raw = clampInt(raw, 0, 100);
        lastGoodMs = nowMs;
        failStreak = 0;

        if (raw == 0) {
            zeroStreak++;

            // تجاهل “0” المؤقت لو كانت عندك قيمة >0 ولسا ما وصلنا zeroConfirm
            if (stable > 0 && zeroStreak < zeroConfirm) {
                return stable;
            }

            stable = smooth(stable, 0);
            return stable;
        }

        // raw > 0
        zeroStreak = 0;
        stable = smooth(stable, raw);
        return stable;
    }

    private int smooth(int prev, int next) {
        if (prev < 0) return next;
        double v = prev + alpha * (next - prev);
        return (int) Math.round(v);
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
