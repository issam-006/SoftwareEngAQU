package fxShield.GPU;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

public final class PdhGpuUsageProvider implements GpuUsageProvider {

    private interface Pdh extends Library {
        int PdhOpenQueryW(WString dataSource, Pointer userData, PointerByReference query);
        int PdhCloseQuery(Pointer query);

        int PdhAddEnglishCounterW(Pointer query, WString fullCounterPath, Pointer userData, PointerByReference counter);

        int PdhCollectQueryData(Pointer query);

        int PdhGetFormattedCounterValue(Pointer counter, int format, IntByReference type, PDH_FMT_COUNTERVALUE value);

        // Expand wildcard to a MULTI-SZ list of full counter paths
        int PdhExpandWildCardPathW(WString dataSource, WString wildCardPath, Pointer expandedPathList,
                                   IntByReference pathListLength, int flags);
    }

    @Structure.FieldOrder({"CStatus", "doubleValue"})
    public static final class PDH_FMT_COUNTERVALUE extends Structure {
        public int CStatus;
        public double doubleValue;
    }

    private static final int PDH_FMT_DOUBLE = 0x00000200;

    private static final int ERROR_SUCCESS = 0;
    private static final int PDH_MORE_DATA = 0x800007D2;

    private static final int PDH_CSTATUS_VALID_DATA = 0x00000000;
    private static final int PDH_CSTATUS_NEW_DATA   = 0x00000001;

    // Wildcard counter (we will EXPAND it, then add each expanded path as its own counter)
    private static final String COUNTER_WILDCARD = "\\\\GPU Engine(*)\\\\Utilization Percentage";

    // Minimum time between the first and second sample for rate-based counters
    private static final long MIN_WARMUP_INTERVAL_MS = 220;

    // ---------- Lazy PDH loading ----------
    private static volatile Pdh PDH;

    private static Pdh pdh() {
        Pdh inst = PDH;
        if (inst != null) return inst;
        try {
            // Make sure pdh.dll exists
            NativeLibrary.getInstance("pdh");
        } catch (Throwable t) {
            return null;
        }
        synchronized (PdhGpuUsageProvider.class) {
            if (PDH != null) return PDH;
            try {
                PDH = Native.load("pdh", Pdh.class);
                return PDH;
            } catch (Throwable t) {
                return null;
            }
        }
    }

    // ---------- State ----------
    private final Object lock = new Object();

    private volatile boolean ready = false;

    private Pointer query;
    private Pointer[] counters = new Pointer[0];

    private final PDH_FMT_COUNTERVALUE value = new PDH_FMT_COUNTERVALUE();
    private final IntByReference typeOut = new IntByReference();

    private boolean warmedUp = false;
    private long lastCollectMs = 0;

    public PdhGpuUsageProvider() {
        // Lazy init on first read
    }

    private void ensureReady() {
        synchronized (lock) {
            if (ready) return;

            Pdh api = pdh();
            if (api == null) {
                ready = false;
                return;
            }

            PointerByReference qRef = new PointerByReference();
            int r = api.PdhOpenQueryW(null, null, qRef);
            if (r != ERROR_SUCCESS) {
                ready = false;
                return;
            }

            query = qRef.getValue();
            if (query == null) {
                safeCloseQuery();
                ready = false;
                return;
            }

            String[] paths = expandWildcardPaths(api, COUNTER_WILDCARD);

            // Fallback: try adding the wildcard directly if expansion failed
            if (paths.length == 0) {
                PointerByReference cRef = new PointerByReference();
                r = api.PdhAddEnglishCounterW(query, new WString(COUNTER_WILDCARD), null, cRef);
                if (r == ERROR_SUCCESS && cRef.getValue() != null) {
                    counters = new Pointer[]{ cRef.getValue() };
                } else {
                    safeCloseQuery();
                    ready = false;
                    return;
                }
            } else {
                // Add each expanded counter
                Pointer[] tmp = new Pointer[paths.length];
                int added = 0;

                for (int i = 0; i < paths.length; i++) {
                    PointerByReference cRef = new PointerByReference();
                    r = api.PdhAddEnglishCounterW(query, new WString(paths[i]), null, cRef);
                    Pointer cPtr = (r == ERROR_SUCCESS) ? cRef.getValue() : null;
                    if (cPtr != null) tmp[added++] = cPtr;
                }

                if (added <= 0) {
                    safeCloseQuery();
                    ready = false;
                    return;
                }

                counters = new Pointer[added];
                System.arraycopy(tmp, 0, counters, 0, added);
            }

            // First collect = warmup sample
            api.PdhCollectQueryData(query);
            lastCollectMs = System.currentTimeMillis();
            warmedUp = false;

            ready = true;
        }
    }

    @Override
    public int readGpuUsagePercent() {
        if (!ready) ensureReady();
        if (!ready) return -1;

        synchronized (lock) {
            if (!ready || query == null || counters.length == 0) return -1;

            Pdh api = pdh();
            if (api == null) return -1;

            long now = System.currentTimeMillis();

            // Warm-up: rate counters often need two samples separated by a small interval
            if (!warmedUp) {
                if (now - lastCollectMs < MIN_WARMUP_INTERVAL_MS) {
                    return -1; // let your GPUStabilizer hold last good value
                }
            }

            int r = api.PdhCollectQueryData(query);
            if (r != ERROR_SUCCESS) {
                r = api.PdhCollectQueryData(query);
                if (r != ERROR_SUCCESS) return -1;
            }

            lastCollectMs = now;
            warmedUp = true;

            // Read all counters and take MAX (closest to Task Manager "overall" behavior)
            double max = -1.0;

            for (int i = 0; i < counters.length; i++) {
                Pointer c = counters[i];
                if (c == null) continue;

                r = api.PdhGetFormattedCounterValue(c, PDH_FMT_DOUBLE, typeOut, value);
                if (r != ERROR_SUCCESS) continue;

                try { value.read(); } catch (Throwable ignored) {}

                if (value.CStatus != PDH_CSTATUS_VALID_DATA && value.CStatus != PDH_CSTATUS_NEW_DATA) {
                    continue;
                }

                double d = value.doubleValue;
                if (Double.isNaN(d) || Double.isInfinite(d)) continue;
                if (d > max) max = d;
            }

            if (max < 0) return -1;

            int out = (int) Math.round(max);
            if (out < 0) out = 0;
            if (out > 100) out = 100;
            return out;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (!ready) return;

            safeCloseQuery();
            ready = false;
            warmedUp = false;
            lastCollectMs = 0;

            query = null;
            counters = new Pointer[0];
        }
    }

    private void safeCloseQuery() {
        try {
            Pdh api = pdh();
            if (api != null && query != null) api.PdhCloseQuery(query);
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isAvailable() { return isAvailableStatic(); }

    public static boolean isAvailableStatic() {
        try {
            NativeLibrary.getInstance("pdh");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String[] expandWildcardPaths(Pdh api, String wildcardPath) {
        try {
            IntByReference chars = new IntByReference(0);
            int r = api.PdhExpandWildCardPathW(null, new WString(wildcardPath), Pointer.NULL, chars, 0);

            if (r != PDH_MORE_DATA || chars.getValue() <= 0) {
                return new String[0];
            }

            long bytes = (long) chars.getValue() * Native.WCHAR_SIZE;
            Memory mem = new Memory(bytes);

            r = api.PdhExpandWildCardPathW(null, new WString(wildcardPath), mem, chars, 0);
            if (r != ERROR_SUCCESS) {
                return new String[0];
            }

            // MULTI-SZ parse (wide strings separated by '\0', ends with "\0\0")
            int cap = 256;
            String[] tmp = new String[cap];
            int count = 0;

            long off = 0;
            while (off < mem.size()) {
                String s = mem.getWideString(off);
                if (s == null || s.isEmpty()) break;

                if (count == tmp.length) {
                    String[] grow = new String[tmp.length * 2];
                    System.arraycopy(tmp, 0, grow, 0, tmp.length);
                    tmp = grow;
                }

                tmp[count++] = s;
                off += (long) (s.length() + 1) * Native.WCHAR_SIZE;
            }

            if (count == 0) return new String[0];

            String[] out = new String[count];
            System.arraycopy(tmp, 0, out, 0, count);
            return out;

        } catch (Throwable t) {
            return new String[0];
        }
    }
}
