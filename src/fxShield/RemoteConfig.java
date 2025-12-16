package fxShield;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable-style RemoteConfig with builder, validation helpers, and safe defaults.
 * Backward compatible getters; adds convenience methods.
 */
public final class RemoteConfig implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    // App state
    private String appStatus;        // e.g. "online", "maintenance"
    private String latestVersion;    // e.g. "1.0.3"
    private String minVersion;       // e.g. "1.0.0"

    // Update info
    private String downloadUrl;      // EXE download URL
    private String updateMessage;    // Message shown to the user
    private boolean forceUpdate;     // Force update flag

    public RemoteConfig() { }

    public RemoteConfig(
            String appStatus,
            String latestVersion,
            String minVersion,
            String downloadUrl,
            String updateMessage,
            boolean forceUpdate
    ) {
        this.appStatus = trimOrNull(appStatus);
        this.latestVersion = trimOrNull(latestVersion);
        this.minVersion = trimOrNull(minVersion);
        this.downloadUrl = trimOrNull(downloadUrl);
        this.updateMessage = trimOrNull(updateMessage);
        this.forceUpdate = forceUpdate;
    }

    // Copy
    public RemoteConfig(RemoteConfig other) {
        if (other != null) {
            this.appStatus = other.appStatus;
            this.latestVersion = other.latestVersion;
            this.minVersion = other.minVersion;
            this.downloadUrl = other.downloadUrl;
            this.updateMessage = other.updateMessage;
            this.forceUpdate = other.forceUpdate;
        }
    }

    // Builder
    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private String appStatus;
        private String latestVersion;
        private String minVersion;
        private String downloadUrl;
        private String updateMessage;
        private boolean forceUpdate;

        public Builder appStatus(String v) { this.appStatus = v; return this; }
        public Builder latestVersion(String v) { this.latestVersion = v; return this; }
        public Builder minVersion(String v) { this.minVersion = v; return this; }
        public Builder downloadUrl(String v) { this.downloadUrl = v; return this; }
        public Builder updateMessage(String v) { this.updateMessage = v; return this; }
        public Builder forceUpdate(boolean v) { this.forceUpdate = v; return this; }

        public RemoteConfig build() {
            return new RemoteConfig(appStatus, latestVersion, minVersion, downloadUrl, updateMessage, forceUpdate);
        }
    }

    // Getters & Setters (for compatibility with existing code)
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

    // Convenience
    public Optional<String> appStatusOpt() { return Optional.ofNullable(appStatus); }
    public Optional<String> latestVersionOpt() { return Optional.ofNullable(latestVersion); }
    public Optional<String> minVersionOpt() { return Optional.ofNullable(minVersion); }
    public Optional<String> downloadUrlOpt() { return Optional.ofNullable(downloadUrl); }
    public Optional<String> updateMessageOpt() { return Optional.ofNullable(updateMessage); }

    public boolean isMaintenance() {
        return appStatus != null && appStatus.equalsIgnoreCase("maintenance");
    }

    public boolean isOnline() {
        return appStatus == null || appStatus.equalsIgnoreCase("online");
    }

    // Very simple semantic: if latestVersion != null && minVersion != null, a clientVersion lower than minVersion requires update.
    // Note: proper semver compare is out-of-scope; this naive compare splits by dots and compares numerically.
    public boolean requiresUpdate(String clientVersion) {
        if (clientVersion == null || minVersion == null) return false;
        return compareVersion(clientVersion, minVersion) < 0 || forceUpdate;
    }

    public boolean hasNewerVersionThan(String clientVersion) {
        if (clientVersion == null || latestVersion == null) return false;
        return compareVersion(latestVersion, clientVersion) > 0;
    }

    // Helpers
    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // Naive dotted-number comparison: "1.10.2" vs "1.9.9"
    private static int compareVersion(String a, String b) {
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int ai = (i < as.length) ? parseIntSafe(as[i]) : 0;
            int bi = (i < bs.length) ? parseIntSafe(bs[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9-]", "")); } catch (Exception e) { return 0; }
    }

    @Override
    public String toString() {
        return "RemoteConfig{" +
                "appStatus='" + appStatus + '\'' +
                ", latestVersion='" + latestVersion + '\'' +
                ", minVersion='" + minVersion + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                ", updateMessage='" + updateMessage + '\'' +
                ", forceUpdate=" + forceUpdate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RemoteConfig that)) return false;
        return forceUpdate == that.forceUpdate &&
                Objects.equals(appStatus, that.appStatus) &&
                Objects.equals(latestVersion, that.latestVersion) &&
                Objects.equals(minVersion, that.minVersion) &&
                Objects.equals(downloadUrl, that.downloadUrl) &&
                Objects.equals(updateMessage, that.updateMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appStatus, latestVersion, minVersion, downloadUrl, updateMessage, forceUpdate);
    }
}