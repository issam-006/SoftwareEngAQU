package fxShield.gpu;

import com.sun.jna.*;
import com.sun.jna.ptr.*;

public final class PdhGpuUsageProvider implements GpuUsageProvider {

    private interface Pdh extends Library {
        Pdh INSTANCE = Native.load("pdh", Pdh.class);

        int PdhOpenQueryW(WString dataSource, Pointer userData, PointerByReference query);
        int PdhCloseQuery(Pointer query);

        int PdhAddEnglishCounterW(Pointer query, WString fullCounterPath, Pointer userData, PointerByReference counter);

        int PdhCollectQueryData(Pointer query);

        int PdhGetFormattedCounterValue(Pointer counter, int format, IntByReference type, PDH_FMT_COUNTERVALUE value);
    }

    public static class PDH_FMT_COUNTERVALUE extends Structure {
        public int CStatus;
        public double doubleValue;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.List.of("CStatus", "doubleValue");
        }
    }

    private static final int PDH_FMT_DOUBLE = 0x00000200;
    private static final int ERROR_SUCCESS = 0;
    private static final String COUNTER_PATH = "\\\\GPU Engine(*)\\\\Utilization Percentage";

    // State (thread-sensitive)
    private volatile boolean ready = false;
    private volatile boolean warmed = false; // first collect done
    private Pointer query;
    private Pointer counter;

    // Reused structs to reduce allocations
    private final PDH_FMT_COUNTERVALUE value = new PDH_FMT_COUNTERVALUE();
    private final IntByReference typeOut = null; // not used; PDH allows null

    public PdhGpuUsageProvider() {
        // Lazy init on first read to avoid blocking constructor.
    }

    private synchronized void ensureReady() {
        if (ready) return;
        try {
            PointerByReference q = new PointerByReference();
            int r = Pdh.INSTANCE.PdhOpenQueryW(null, null, q);
            if (r != ERROR_SUCCESS) return;

            query = q.getValue();

            PointerByReference c = new PointerByReference();
            r = Pdh.INSTANCE.PdhAddEnglishCounterW(query, new WString(COUNTER_PATH), null, c);
            if (r != ERROR_SUCCESS) {
                safeCloseQuery();
                return;
            }

            counter = c.getValue();

            // Do an initial collect without sleeping; we’ll accept first read as warmup.
            Pdh.INSTANCE.PdhCollectQueryData(query);
            warmed = true;

            ready = true;
        } catch (Throwable t) {
            safeCloseQuery();
            ready = false;
            warmed = false;
        }
    }

    @Override
    public int readGpuUsagePercent() {
        if (!ready) ensureReady();
        if (!ready) return -1;

        try {
            // Collect latest data; minimal retry for transient errors.
            int r = Pdh.INSTANCE.PdhCollectQueryData(query);
            if (r != ERROR_SUCCESS) {
                r = Pdh.INSTANCE.PdhCollectQueryData(query);
                if (r != ERROR_SUCCESS) return -1;
            }

            // Fetch formatted value
            r = Pdh.INSTANCE.PdhGetFormattedCounterValue(counter, PDH_FMT_DOUBLE, typeOut, value);
            if (r != ERROR_SUCCESS) {
                // Retry once after a fresh collect
                Pdh.INSTANCE.PdhCollectQueryData(query);
                r = Pdh.INSTANCE.PdhGetFormattedCounterValue(counter, PDH_FMT_DOUBLE, typeOut, value);
                if (r != ERROR_SUCCESS) return -1;
            }

            double d = value.doubleValue;
            if (Double.isNaN(d) || Double.isInfinite(d)) return -1;

            int out = (int) Math.round(d);
            if (out < 0) out = 0;
            if (out > 100) out = 100;
            return out;
        } catch (Throwable t) {
            return -1;
        }
    }

    @Override
    public synchronized void close() {
        if (!ready) return;
        safeCloseQuery();
        ready = false;
        warmed = false;
        query = null;
        counter = null;
    }

    private void safeCloseQuery() {
        try { if (query != null) Pdh.INSTANCE.PdhCloseQuery(query); } catch (Throwable ignored) {}
    }

    @Override
    public boolean isAvailable() { return isAvailableStatic(); }

    public static boolean isAvailableStatic() {
        try { NativeLibrary.getInstance("pdh"); return true; } catch (Throwable t) { return false; }
    }
}