package fxShield.GPU;

public final class HybridGpuUsageProvider implements GpuUsageProvider {

    // small cooldown to avoid thrashing when a provider transiently fails
    private static final long COOLDOWN_MS = 1500;

    private final boolean isWindows;

    // cached winner (fast path)
    private volatile GpuUsageProvider active;

    // lazy providers
    private GpuUsageProvider nvml;
    private GpuUsageProvider pdh;
    private GpuUsageProvider typeperf;

    // backoff per provider
    private volatile long nvmlNextTryMs = 0;
    private volatile long pdhNextTryMs = 0;
    private volatile long typeperfNextTryMs = 0;

    private volatile boolean closed = false;

    public HybridGpuUsageProvider(boolean isWindows) {
        this.isWindows = isWindows;
    }

    @Override
    public int readGpuUsagePercent() {
        if (closed || !isWindows) return -1;

        long now = System.currentTimeMillis();

        // 1) try cached winner first
        GpuUsageProvider a = active;
        if (a != null) {
            int v = safeRead(a);
            if (v >= 0) return v;
            active = null; // demote on failure
        }

        // 2) NVML (NVIDIA) first
        if (now >= nvmlNextTryMs) {
            GpuUsageProvider p = ensureNvml();
            int v = (p != null) ? safeRead(p) : -1;
            if (v >= 0) { active = p; return v; }
            nvmlNextTryMs = now + COOLDOWN_MS;
        }

        // 3) PDH (Windows counter) second
        if (now >= pdhNextTryMs) {
            GpuUsageProvider p = ensurePdh();
            int v = (p != null) ? safeRead(p) : -1;
            if (v >= 0) { active = p; return v; }
            pdhNextTryMs = now + COOLDOWN_MS;
        }

        // 4) typeperf (process fallback) last
        if (now >= typeperfNextTryMs) {
            GpuUsageProvider p = ensureTypeperf();
            int v = (p != null) ? safeRead(p) : -1;
            if (v >= 0) { active = p; return v; }
            typeperfNextTryMs = now + COOLDOWN_MS;
        }

        return -1;
    }

    private static int safeRead(GpuUsageProvider p) {
        try {
            int v = p.readGpuUsagePercent();
            return (v >= 0 && v <= 100) ? v : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    // ---- lazy init with safe “presence” checks قبل الإنشاء ----

    private synchronized GpuUsageProvider ensureNvml() {
        if (closed) return null;

        // don’t even instantiate if NVML library isn’t present
        if (!NvmlGpuUsageProvider.isNvmlLibraryPresent()) {
            if (nvml != null) { safeClose(nvml); nvml = null; }
            return null;
        }

        if (nvml == null) {
            try { nvml = new NvmlGpuUsageProvider(); }
            catch (Throwable t) { nvml = null; }
        }

        if (nvml != null && nvml.isAvailable()) return nvml;
        return null;
    }

    private synchronized GpuUsageProvider ensurePdh() {
        if (closed) return null;

        // avoid instantiating if PDH library isn’t present
        if (!PdhGpuUsageProvider.isAvailableStatic()) {
            if (pdh != null) { safeClose(pdh); pdh = null; }
            return null;
        }

        if (pdh == null) {
            try { pdh = new PdhGpuUsageProvider(); }
            catch (Throwable t) { pdh = null; }
        }

        if (pdh != null && pdh.isAvailable()) return pdh;
        return null;
    }

    private synchronized GpuUsageProvider ensureTypeperf() {
        if (closed) return null;

        if (typeperf == null) {
            try { typeperf = new TypeperfGpuUsageProvider(); }
            catch (Throwable t) { typeperf = null; }
        }

        if (typeperf != null && typeperf.isAvailable()) return typeperf;
        return null;
    }

    @Override
    public boolean isAvailable() {
        if (closed || !isWindows) return false;

        if (NvmlGpuUsageProvider.isNvmlLibraryPresent()) return true;
        if (PdhGpuUsageProvider.isAvailableStatic()) return true;

        // typeperf check is heavier (process). keep it last.
        try { return new TypeperfGpuUsageProvider().isAvailable(); }
        catch (Throwable t) { return false; }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;

        active = null;

        safeClose(nvml); nvml = null;
        safeClose(pdh); pdh = null;
        safeClose(typeperf); typeperf = null;
    }

    private static void safeClose(GpuUsageProvider p) {
        if (p == null) return;
        try { p.close(); } catch (Throwable ignored) {}
    }
}
