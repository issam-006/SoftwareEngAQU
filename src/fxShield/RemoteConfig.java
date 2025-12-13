package fxShield;

/**
 * RemoteConfig
 * -------------
 * Model class for remote configuration fetched from Firebase Firestore.
 * This class is intentionally simple (POJO) to avoid side effects.
 */
public class RemoteConfig {

    // ===== App state =====
    private String appStatus;        // e.g. "online", "maintenance"
    private String latestVersion;    // e.g. "1.0.3"
    private String minVersion;       // e.g. "1.0.0"

    // ===== Update info =====
    private String downloadUrl;      // APK / EXE download URL
    private String updateMessage;    // Message shown to the user
    private boolean forceUpdate;     // Force update flag

    // ===== Constructors =====

    public RemoteConfig() {
        // Default constructor (required for safe deserialization)
    }

    public RemoteConfig(
            String appStatus,
            String latestVersion,
            String minVersion,
            String downloadUrl,
            String updateMessage,
            boolean forceUpdate
    ) {
        this.appStatus = appStatus;
        this.latestVersion = latestVersion;
        this.minVersion = minVersion;
        this.downloadUrl = downloadUrl;
        this.updateMessage = updateMessage;
        this.forceUpdate = forceUpdate;
    }

    // ===== Getters & Setters =====

    public String getAppStatus() {
        return appStatus;
    }

    public void setAppStatus(String appStatus) {
        this.appStatus = appStatus;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public void setMinVersion(String minVersion) {
        this.minVersion = minVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getUpdateMessage() {
        return updateMessage;
    }

    public void setUpdateMessage(String updateMessage) {
        this.updateMessage = updateMessage;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    // ===== Debug helper =====
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
}