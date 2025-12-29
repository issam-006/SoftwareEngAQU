package fxShield.DB;

import java.io.Serial;
import java.io.Serializable;

/**
 * Configuration class for remote settings and PowerShell scripts.
 * Used for application updates, maintenance mode, and system optimization scripts.
 */
public final class RemoteConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Application status and version info
    private String appStatus;
    private String latestVersion;
    private String minVersion;
    private String downloadUrl;
    private String updateMessage;
    private boolean forceUpdate;

    // PowerShell scripts for system optimization
    private String freeRamScript;
    private String optimizeDiskScript;
    private String optimizeNetworkScript;
    private String performanceModeScript;
    private String balancedModeScript;

    // ✅ Fix: QUIET (not QUIT)
    // Keep backward compatibility with older payloads/field names.
    private String quietModeScript;
    private String quitModeScript; // legacy alias (old typo)

    private String scanAndFixScript;

    public RemoteConfig() {
    }

    // =========================================================================
    // Application Status and Version Methods
    // =========================================================================

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public String getAppStatus() {
        return appStatus;
    }

    public void setAppStatus(String appStatus) {
        this.appStatus = trimOrNull(appStatus);
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = trimOrNull(latestVersion);
    }

    public String getMinVersion() {
        return minVersion;
    }

    public void setMinVersion(String minVersion) {
        this.minVersion = trimOrNull(minVersion);
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = trimOrNull(downloadUrl);
    }

    public String getUpdateMessage() {
        return updateMessage;
    }

    public void setUpdateMessage(String updateMessage) {
        this.updateMessage = trimOrNull(updateMessage);
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    /**
     * @return true if appStatus is "maintenance"
     */
    public boolean isMaintenance() {
        return appStatus != null && appStatus.equalsIgnoreCase("maintenance");
    }

    // =========================================================================
    // Script Accessor Methods
    // =========================================================================

    /**
     * @return true if appStatus is null or "online"
     */
    public boolean isOnline() {
        return appStatus == null || appStatus.equalsIgnoreCase("online");
    }

    public String getFreeRamScript() {
        return freeRamScript;
    }

    public void setFreeRamScript(String v) {
        this.freeRamScript = trimOrNull(v);
    }

    public String getOptimizeDiskScript() {
        return optimizeDiskScript;
    }

    public void setOptimizeDiskScript(String v) {
        this.optimizeDiskScript = trimOrNull(v);
    }

    public String getOptimizeNetworkScript() {
        return optimizeNetworkScript;
    }

    public void setOptimizeNetworkScript(String v) {
        this.optimizeNetworkScript = trimOrNull(v);
    }

    public String getPerformanceModeScript() {
        return performanceModeScript;
    }

    public void setPerformanceModeScript(String v) {
        this.performanceModeScript = trimOrNull(v);
    }

    public String getBalancedModeScript() {
        return balancedModeScript;
    }

    public void setBalancedModeScript(String v) {
        this.balancedModeScript = trimOrNull(v);
    }

    /**
     * ✅ Correct getter: Quiet Mode script.
     */
    public String getQuietModeScript() {
        String q = trimOrNull(quietModeScript);
        if (q != null) return q;
        return trimOrNull(quitModeScript); // legacy
    }

    /**
     * ✅ Correct setter: Quiet Mode script.
     */
    public void setQuietModeScript(String v) {
        String t = trimOrNull(v);
        this.quietModeScript = t;
        this.quitModeScript = t; // keep legacy in sync
    }

    /**
     * ⚠ Legacy typo support (backward compatibility).
     * Prefer getQuietModeScript()/setQuietModeScript().
     */
    @Deprecated
    public String getQuitModeScript() {
        return getQuietModeScript();
    }

    /**
     * ⚠ Legacy typo support (backward compatibility).
     * Prefer getQuietModeScript()/setQuietModeScript().
     */
    @Deprecated
    public void setQuitModeScript(String v) {
        setQuietModeScript(v);
    }

    public String getScanAndFixScript() {
        return scanAndFixScript;
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    public void setScanAndFixScript(String v) {
        this.scanAndFixScript = trimOrNull(v);
    }
}
