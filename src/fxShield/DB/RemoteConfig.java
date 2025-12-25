package fxShield.DB;

import java.io.Serial;
import java.io.Serializable;

public final class RemoteConfig implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    private String appStatus;
    private String latestVersion;
    private String minVersion;

    private String downloadUrl;
    private String updateMessage;
    private boolean forceUpdate;

    // Scripts (PowerShell text)
    private String FreeRam_Script;
    private String OptimizeDisk_Script;
    private String OptimizeNetwork_Script;
    private String PerformanceMode_Script;
    private String BalancedMode_Script;
    private String QuitMode_Script;
    private String ScanAndFix_Script;

    public RemoteConfig() {}

    public String getAppStatus() { return appStatus; }
    public void setAppStatus(String appStatus) { this.appStatus = trimOrNull(appStatus); }

    public String getLatestVersion() { return latestVersion; }
    public void setLatestVersion(String latestVersion) { this.latestVersion = trimOrNull(latestVersion); }

    public String getMinVersion() { return minVersion; }
    public void setMinVersion(String minVersion) { this.minVersion = trimOrNull(minVersion); }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = trimOrNull(downloadUrl); }

    public String getUpdateMessage() { return updateMessage; }
    public void setUpdateMessage(String updateMessage) { this.updateMessage = trimOrNull(updateMessage); }

    public boolean isForceUpdate() { return forceUpdate; }
    public void setForceUpdate(boolean forceUpdate) { this.forceUpdate = forceUpdate; }

    public boolean isMaintenance() {
        return appStatus != null && appStatus.equalsIgnoreCase("maintenance");
    }

    public boolean isOnline() {
        return appStatus == null || appStatus.equalsIgnoreCase("online");
    }

    public String getFreeRam_Script() { return FreeRam_Script; }
    public void setFreeRam_Script(String v) { this.FreeRam_Script = trimOrNull(v); }

    public String getOptimizeDisk_Script() { return OptimizeDisk_Script; }
    public void setOptimizeDisk_Script(String v) { this.OptimizeDisk_Script = trimOrNull(v); }

    public String getOptimizeNetwork_Script() { return OptimizeNetwork_Script; }
    public void setOptimizeNetwork_Script(String v) { this.OptimizeNetwork_Script = trimOrNull(v); }

    public String getPerformanceMode_Script() { return PerformanceMode_Script; }
    public void setPerformanceMode_Script(String v) { this.PerformanceMode_Script = trimOrNull(v); }

    public String getBalancedMode_Script() { return BalancedMode_Script; }
    public void setBalancedMode_Script(String v) { this.BalancedMode_Script = trimOrNull(v); }

    public String getQuitMode_Script() { return QuitMode_Script; }
    public void setQuitMode_Script(String v) { this.QuitMode_Script = trimOrNull(v); }

    public String getScanAndFix_Script() { return ScanAndFix_Script; }
    public void setScanAndFix_Script(String v) { this.ScanAndFix_Script = trimOrNull(v); }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
