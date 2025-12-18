package fxShield.gpu;

public final class HybridGpuUsageProvider implements GpuUsageProvider {

    private final boolean isWindows;

    // Lazy-created providers
    private volatile GpuUsageProvider active;
    private GpuUsageProvider nvml;
    private GpuUsageProvider pdh;
    private GpuUsageProvider typeperf;

    public HybridGpuUsageProvider(boolean isWindows) {
        this.isWindows = isWindows;
    }

    @Override
    public int readGpuUsagePercent() {
        if (!isWindows) return -1;

        // Try cached winner first
        GpuUsageProvider a = active;
        if (a != null) {
            int v = a.readGpuUsagePercent();
            if (v >= 0) return v;
            active = null; // demote on failure
        }

        // Try providers in priority order
        for (GpuUsageProvider p : providersInOrder()) {
            if (p == null) continue;
            int v = p.readGpuUsagePercent();
            if (v >= 0) { active = p; return v; }
        }

        return -1;
    }

    private GpuUsageProvider[] providersInOrder() {
        if (nvml == null) {
            try { nvml = new NvmlGpuUsageProvider(); } catch (Throwable ignored) {}
        }
        if (pdh == null) {
            try { pdh = new PdhGpuUsageProvider(); } catch (Throwable ignored) {}
        }
        if (typeperf == null) {
            try { typeperf = new TypeperfGpuUsageProvider(); } catch (Throwable ignored) {}
        }
        return new GpuUsageProvider[]{
                (nvml != null && nvml.isAvailable()) ? nvml : null,
                (pdh != null && pdh.isAvailable()) ? pdh : null,
                (typeperf != null && typeperf.isAvailable()) ? typeperf : null
        };
    }

    @Override
    public void close() {
        try { if (nvml != null) nvml.close(); } catch (Exception ignored) {}
        try { if (pdh != null) pdh.close(); } catch (Exception ignored) {}
        try { if (typeperf != null) typeperf.close(); } catch (Exception ignored) {}
        nvml = null;
        pdh = null;
        typeperf = null;
        active = null;
    }
}