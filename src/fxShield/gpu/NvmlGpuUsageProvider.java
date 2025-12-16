package fxShield.gpu;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;

public final class NvmlGpuUsageProvider implements GpuUsageProvider {

    private interface Nvml extends Library {
        Nvml INSTANCE = Native.load("nvml", Nvml.class);

        int nvmlInit_v2();
        int nvmlShutdown();
        int nvmlDeviceGetHandleByIndex_v2(int index, PointerByReference device);
        int nvmlDeviceGetUtilizationRates(Pointer device, NvmlUtilization utilization);
    }

    @Structure.FieldOrder({"gpu", "memory"})
    public static class NvmlUtilization extends Structure {
        public int gpu;    // percent
        public int memory; // percent
    }

    // Global NVML lifecycle (thread-safe, ref-counted)
    private static final Object NVML_LOCK = new Object();
    private static boolean NVML_INITIALIZED = false;
    private static int NVML_REFCOUNT = 0;

    private static boolean ensureNvmlInitialized() {
        synchronized (NVML_LOCK) {
            if (NVML_INITIALIZED) {
                NVML_REFCOUNT++;
                return true;
            }
            try {
                int r = Nvml.INSTANCE.nvmlInit_v2();
                if (r == 0) {
                    NVML_INITIALIZED = true;
                    NVML_REFCOUNT = 1;
                    return true;
                }
            } catch (Throwable ignored) {}
            return false;
        }
    }

    private static void shutdownNvmlIfIdle() {
        synchronized (NVML_LOCK) {
            if (!NVML_INITIALIZED) return;
            NVML_REFCOUNT--;
            if (NVML_REFCOUNT <= 0) {
                try { Nvml.INSTANCE.nvmlShutdown(); } catch (Throwable ignored) {}
                NVML_INITIALIZED = false;
                NVML_REFCOUNT = 0;
            }
        }
    }

    // Instance state
    private final int deviceIndex;
    private volatile boolean ready = false;
    private volatile Pointer device;
    private final NvmlUtilization util = new NvmlUtilization();

    public NvmlGpuUsageProvider() {
        this(0);
    }

    public NvmlGpuUsageProvider(int deviceIndex) {
        this.deviceIndex = deviceIndex;
        init();
    }

    private void init() {
        if (!ensureNvmlInitialized()) { ready = false; return; }
        try {
            PointerByReference ref = new PointerByReference();
            int r = Nvml.INSTANCE.nvmlDeviceGetHandleByIndex_v2(deviceIndex, ref);
            if (r == 0 && ref.getValue() != null) {
                device = ref.getValue();
                ready = true;
            } else {
                ready = false;
                shutdownNvmlIfIdle();
            }
        } catch (Throwable t) {
            ready = false;
            shutdownNvmlIfIdle();
        }
    }

    @Override
    public int readGpuUsagePercent() {
        if (!ready || device == null) return -1;
        try {
            int r = Nvml.INSTANCE.nvmlDeviceGetUtilizationRates(device, util);
            if (r != 0) {
                // simple one-time retry to handle transient issues
                r = Nvml.INSTANCE.nvmlDeviceGetUtilizationRates(device, util);
                if (r != 0) return -1;
            }
            int v = util.gpu;
            if (v < 0) v = 0;
            if (v > 100) v = 100;
            return v;
        } catch (Throwable t) {
            return -1;
        }
    }

    @Override
    public void close() {
        if (!ready) return;
        ready = false;
        device = null;
        shutdownNvmlIfIdle();
    }

    // Instance override to align with GpuUsageProvider interface
    @Override
    public boolean isAvailable() {
        return isNvmlLibraryPresent();
    }

    // Static helper, if you still need a static check elsewhere
    public static boolean isNvmlLibraryPresent() {
        try {
            NativeLibrary.getInstance("nvml");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}