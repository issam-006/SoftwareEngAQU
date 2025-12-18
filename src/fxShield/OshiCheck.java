package fxShield;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import java.lang.reflect.Method;
import java.util.List;

public class OshiCheck {
    public static void main(String[] args) {
        SystemInfo si = new SystemInfo();
        CentralProcessor cpu = si.getHardware().getProcessor();
        System.out.println("Methods of CentralProcessor:");
        for (Method m : cpu.getClass().getMethods()) {
            if (m.getName().toLowerCase().contains("cache") || m.getName().toLowerCase().contains("freq")) {
                System.out.println(m.getName() + " -> " + m.getReturnType().getName());
            }
        }
        try {
            List<oshi.hardware.PowerSource> powerSources = si.getHardware().getPowerSources();
            for (oshi.hardware.PowerSource ps : powerSources) {
                System.out.println("PS: " + ps.getName());
                for (Method m : ps.getClass().getMethods()) {
                    if (m.getDeclaringClass() == ps.getClass() || m.getDeclaringClass().getName().contains("oshi")) {
                        if (m.getParameterCount() == 0) {
                            try {
                                Object val = m.invoke(ps);
                                System.out.println("  " + m.getName() + " -> " + val);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
