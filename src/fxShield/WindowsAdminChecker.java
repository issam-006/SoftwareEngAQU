package fxShield;

import java.io.File;

/**
 * Utility to detect Administrator privileges on Windows and request elevation.
 * Extends WindowsUtils for common Windows functionality.
 */
public final class WindowsAdminChecker extends WindowsUtils {

    private WindowsAdminChecker() {}

    /**
     * Checks if the current process has Administrator privileges.
     * Uses 'net session' command which returns 0 only if running as admin.
     */
    public static boolean isAdmin() {
        if (!isWindows()) return true; // Non-windows is always "admin" for our purposes

        try {
            // Using ProcessBuilder is preferred over Runtime.exec
            return new ProcessBuilder("net", "session")
                    .start()
                    .waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Relaunches the application with Administrator privileges using PowerShell 'RunAs' verb.
     * Then exits the current process.
     */
    public static void requestAdminAndExit() {
        try {
            String fullCommand = getFullCommand();
            if (fullCommand == null) return;

            String executable;
            String arguments;

            // Simple parsing for quoted executable paths
            if (fullCommand.startsWith("\"")) {
                int secondQuote = fullCommand.indexOf("\"", 1);
                if (secondQuote != -1) {
                    executable = fullCommand.substring(1, secondQuote);
                    arguments = fullCommand.substring(secondQuote + 1).trim();
                } else {
                    executable = fullCommand;
                    arguments = "";
                }
            } else {
                int firstSpace = fullCommand.indexOf(" ");
                if (firstSpace == -1) {
                    executable = fullCommand;
                    arguments = "";
                } else {
                    executable = fullCommand.substring(0, firstSpace);
                    arguments = fullCommand.substring(firstSpace + 1).trim();
                }
            }

            // PowerShell: Start-Process "executable" -ArgumentList "arguments" -Verb RunAs
            String psCommand;
            if (arguments.isEmpty()) {
                psCommand = String.format(
                    "Start-Process -FilePath '%s' -Verb RunAs -WindowStyle Normal",
                    escapeForPowerShell(executable)
                );
            } else {
                psCommand = String.format(
                    "Start-Process -FilePath '%s' -ArgumentList '%s' -Verb RunAs -WindowStyle Normal",
                    escapeForPowerShell(executable),
                    escapeForPowerShell(arguments)
                );
            }

            System.out.println("[Admin] Elevation command: powershell " + psCommand);
            
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psCommand)
                    .start();
            
            // Wait a bit to see if PowerShell started correctly
            if (p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                if (p.exitValue() != 0) {
                    System.err.println("[Admin] PowerShell failed with exit code: " + p.exitValue());
                    javax.swing.JOptionPane.showMessageDialog(null, 
                        "Failed to request Administrator permission.\nPlease try running the app manually as Administrator.", 
                        "Fx Shield - Permission Error", 
                        javax.swing.JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            
            System.out.println("[Admin] Elevation requested. Exiting current process.");
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getFullCommand() {
            // 1. Try ProcessHandle (Java 9+) - most accurate for jpackaged EXE
            try {
                var info = ProcessHandle.current().info();
                if (info.commandLine().isPresent()) {
                    String cmd = info.commandLine().get();
                    System.out.println("[Admin] Detected command line: " + cmd);
                    // If it's a jpackaged EXE, it might just return the EXE path.
                    // We need to ensure we have the absolute path.
                    if (cmd.endsWith(".exe") && !cmd.contains(File.separator)) {
                         File exeFile = new File(cmd);
                         String absolute = exeFile.getAbsolutePath();
                         System.out.println("[Admin] Resolving relative EXE to: " + absolute);
                         return absolute;
                    }
                    return cmd;
                } else if (info.command().isPresent()) {
                    String cmd = info.command().get();
                    System.out.println("[Admin] Detected command: " + cmd);
                    return cmd;
                }
            } catch (Exception ignored) {}

        // 2. Fallback: Manual construction (useful for IDE/JAR runs)
        try {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "javaw.exe";
            if (!new File(javaBin).exists()) {
                javaBin = javaHome + File.separator + "bin" + File.separator + "java.exe";
            }

            File loc = new File(WindowsAdminChecker.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            String path = loc.getAbsolutePath();

            if (path.endsWith(".jar")) {
                return "\"" + javaBin + "\" -jar \"" + path + "\"";
            } else if (path.endsWith(".exe")) {
                return "\"" + path + "\"";
            } else {
                String cp = System.getProperty("java.class.path");
                String mainClass = "fxShield.DashBoardPage";
                return "\"" + javaBin + "\" -cp \"" + cp + "\" " + mainClass;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
