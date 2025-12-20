package fxShield;

import java.util.Scanner;

public class Launcher {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   FX SHIELD - BOOTSTRAP LAUNCHER");
        System.out.println("========================================");
        System.out.println("Current Directory: " + System.getProperty("user.dir"));
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("ClassPath: " + System.getProperty("java.class.path"));
        
        try {
            System.out.println("[Launcher] Testing dependencies...");
            Class.forName("com.google.gson.Gson");
            System.out.println("  - GSON: OK");
            Class.forName("com.sun.jna.Pointer");
            System.out.println("  - JNA: OK");
            Class.forName("javafx.application.Application");
            System.out.println("  - JavaFX: OK");
            
            System.out.println("[Launcher] Starting main application...");
            DashBoardPage.main(args);
        } catch (Throwable t) {
            System.err.println("\n[CRITICAL ERROR] Application failed to start:");
            if (t instanceof ClassNotFoundException) {
                System.err.println("MISSING LIBRARY: " + t.getMessage());
                System.err.println("Ensure your 'libs' folder contains the required JAR files.");
            } else {
                t.printStackTrace();
            }
            
            System.out.println("\nPress ENTER to close this window...");
            new Scanner(System.in).nextLine();
        }
    }
}
