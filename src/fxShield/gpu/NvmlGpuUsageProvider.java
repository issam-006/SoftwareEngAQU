package fxShield.GPU;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;

public final class NvmlGpuUsageProvider implements GpuUsageProvider {

    private interface Nvml extends Library {
        int nvmlInit_v2();
        int nvmlShutdown();
        int nvmlDeviceGetHandleByIndex_v2(int index, PointerByReference device);
        int nvmlDeviceGetUtilizationRates(Pointer device, NvmlUtilization utilization);
    }

    @Structure.FieldOrder({"gpu", "memory"})
    public static final class NvmlUtilization extends Structure {
        public int gpu;    // percent (NVML uses unsigned int; we clamp)
        public int memory; // percent
    }

    // ---------- Lazy NVML loading + global lifecycle ----------
    private static final Object NVML_LOCK = new Object();
    private static volatile Nvml NVML;                 // lazy-loaded instance
    private static boolean NVML_INITIALIZED = false;
    private static int NVML_REFCOUNT = 0;

    private static Nvml nvml() {
        Nvml inst = NVML;
        if (inst != null) return inst;
        synchronized (NVML_LOCK) {
            if (NVML != null) return NVML;
            try {
                NVML = Native.load("nvml", Nvml.class);
                return NVML;
            } catch (Throwable t) {
                return null;
            }
        }
    }

    private static boolean ensureNvmlInitialized() {
        synchronized (NVML_LOCK) {
            Nvml inst = nvml();
            if (inst == null) return false;

            if (NVML_INITIALIZED) {
                NVML_REFCOUNT++;
                return true;
            }

            try {
                int r = inst.nvmlInit_v2();
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
                try {
                    Nvml inst = nvml();
                    if (inst != null) inst.nvmlShutdown();
                } catch (Throwable ignored) {}

                NVML_INITIALIZED = false;
                NVML_REFCOUNT = 0;
            }
        }
    }

    // ---------- Instance state ----------
    private final int deviceIndex;
    private final Object ioLock = new Object(); // protects device + util

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
        if (!ensureNvmlInitialized()) {
            ready = false;
            device = null;
            return;
        }

        try {
            Nvml inst = nvml();
            if (inst == null) {
                ready = false;
                device = null;
                shutdownNvmlIfIdle();
                return;
            }

            PointerByReference ref = new PointerByReference();
            int r = inst.nvmlDeviceGetHandleByIndex_v2(deviceIndex, ref);

            Pointer p = (r == 0) ? ref.getValue() : null;
            if (p != null) {
                device = p;
                ready = true;
            } else {
                ready = false;
                device = null;
                shutdownNvmlIfIdle();
            }
        } catch (Throwable t) {
            ready = false;
            device = null;
            shutdownNvmlIfIdle();
        }
    }

    @Override
    public int readGpuUsagePercent() {
        if (!ready || device == null) return -1;

        synchronized (ioLock) {
            if (!ready || device == null) return -1;

            try {
                Nvml inst = nvml();
                if (inst == null) return -1;

                int r = inst.nvmlDeviceGetUtilizationRates(device, util);
                if (r != 0) {
                    // one retry (transient)
                    r = inst.nvmlDeviceGetUtilizationRates(device, util);
                    if (r != 0) return -1;
                }

                // Make sure the native-filled fields are visible
                try { util.read(); } catch (Throwable ignored) {}

                int v = util.gpu;
                if (v < 0) v = 0;
                if (v > 100) v = 100;
                return v;
            } catch (Throwable t) {
                return -1;
            }
        }
    }

    @Override
    public void close() {
        synchronized (ioLock) {
            if (!ready) return;
            ready = false;
            device = null;
        }
        shutdownNvmlIfIdle();
    }

    @Override
    public boolean isAvailable() {
        return isNvmlLibraryPresent();
    }

    public static boolean isNvmlLibraryPresent() {
        try {
            NativeLibrary.getInstance("nvml");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
