package fxShield;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class RemoteConfigService {

    // 🔹 رابط Firestore Document (REST)
    private static final String DEFAULT_CONFIG_URL =
            "https://firestore.googleapis.com/v1/projects/fx-shield-aqu/databases/(default)/documents/fxShield/config";

    private final HttpClient httpClient;
    private final String configUrl;

    public RemoteConfigService() {
        this(DEFAULT_CONFIG_URL);
    }

    public RemoteConfigService(String configUrl) {
        this.configUrl = (configUrl == null || configUrl.isBlank()) ? DEFAULT_CONFIG_URL : configUrl;

        // Timeouts مهمة حتى التطبيق ما يعلق إذا النت بطيء
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public RemoteConfig fetchConfig() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(configUrl))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("Failed to fetch config. HTTP code: " + response.statusCode());
                // أحيانًا Firestore برجع JSON error
                String body = response.body();
                if (body != null && !body.isBlank()) {
                    System.out.println("Response body: " + shrink(body, 300));
                }
                return null;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                System.out.println("Config response body is empty!");
                return null;
            }

            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject fields = safeGetObject(root, "fields");

            if (fields == null) {
                System.out.println("No fields object in Firestore document!");
                return null;
            }

            RemoteConfig config = new RemoteConfig();
            config.setAppStatus(getStringField(fields, "appStatus"));
            config.setLatestVersion(getStringField(fields, "latestVersion"));
            config.setMinVersion(getStringField(fields, "minVersion"));
            config.setDownloadUrl(getStringField(fields, "downloadUrl"));
            config.setUpdateMessage(getStringField(fields, "updateMessage"));
            config.setForceUpdate(getBooleanField(fields, "forceUpdate"));

            return config;

        } catch (InterruptedException e) {
            // مهم جدًا: رجّع الـ interrupt flag
            Thread.currentThread().interrupt();
            System.out.println("fetchConfig interrupted.");
            return null;

        } catch (IOException e) {
            System.out.println("fetchConfig IO error: " + e.getMessage());
            return null;

        } catch (Exception e) {
            System.out.println("fetchConfig unexpected error: " + e.getMessage());
            return null;
        }
    }

    private String getStringField(JsonObject fields, String name) {
        JsonObject obj = safeGetObject(fields, name);
        if (obj == null) return null;

        // Firestore string field: { "stringValue": "..." }
        JsonElement el = obj.get("stringValue");
        if (el != null && !el.isJsonNull()) {
            return el.getAsString();
        }
        return null;
    }

    private boolean getBooleanField(JsonObject fields, String name) {
        JsonObject obj = safeGetObject(fields, name);
        if (obj == null) return false;

        // Firestore boolean field: { "booleanValue": true/false }
        JsonElement el = obj.get("booleanValue");
        if (el != null && !el.isJsonNull()) {
            return el.getAsBoolean();
        }
        return false;
    }

    private JsonObject safeGetObject(JsonObject parent, String key) {
        if (parent == null || key == null) return null;
        JsonElement el = parent.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonObject()) return null;
        return el.getAsJsonObject();
    }

    private String shrink(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}