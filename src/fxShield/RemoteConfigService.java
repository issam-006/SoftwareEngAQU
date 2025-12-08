package fxShield;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RemoteConfigService {

    // 🔹 حطي هنا رابط الفايرستور تبعك
    private static final String CONFIG_URL =
            "https://firestore.googleapis.com/v1/projects/fx-shield-aqu/databases/(default)/documents/fxShield/config";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public RemoteConfig fetchConfig() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CONFIG_URL))
                .GET()
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("Failed to fetch config. HTTP code: " + response.statusCode());
                return null;
            }

            String body = response.body();
            // System.out.println("Config JSON: " + body); // للتجربة

            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject fields = root.getAsJsonObject("fields");

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

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getStringField(JsonObject fields, String name) {
        if (!fields.has(name)) return null;
        JsonObject obj = fields.getAsJsonObject(name);
        // Firestore string field يكون على شكل { "stringValue": "..." }
        if (obj.has("stringValue")) {
            return obj.get("stringValue").getAsString();
        }
        return null;
    }

    private boolean getBooleanField(JsonObject fields, String name) {
        if (!fields.has(name)) return false;
        JsonObject obj = fields.getAsJsonObject(name);
        if (obj.has("booleanValue")) {
            return obj.get("booleanValue").getAsBoolean();
        }
        return false;
    }
}

