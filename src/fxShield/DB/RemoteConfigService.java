package fxShield.DB;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

public final class RemoteConfigService {

    private static final String DEFAULT_CONFIG_URL =
            "https://firestore.googleapis.com/v1/projects/fx-shield-aqu/databases/(default)/documents/fxShield/config";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(6);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_RETRIES = 2;

    private final HttpClient httpClient;
    private final String configUrl;

    private volatile String cachedEtag;
    private volatile RemoteConfig cachedConfig;

    public RemoteConfigService() {
        this(DEFAULT_CONFIG_URL);
    }

    public RemoteConfigService(String configUrl) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), configUrl);
    }

    public RemoteConfigService(HttpClient client, String configUrl) {
        this.httpClient = (client != null) ? client : HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.configUrl = (configUrl == null || configUrl.isBlank()) ? DEFAULT_CONFIG_URL : configUrl;
    }

    public RemoteConfig fetchConfig() {
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(configUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .header("User-Agent", "FxShield/1.0 (RemoteConfigService)")
                .GET();

        String etag = cachedEtag;
        if (etag != null && !etag.isBlank()) rb.header("If-None-Match", etag);

        HttpRequest request = rb.build();

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<byte[]> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (resp.statusCode() == 304 && cachedConfig != null) return cachedConfig;

                if (resp.statusCode() != 200) {
                    if (attempt < MAX_RETRIES && isTransient(resp.statusCode())) {
                        sleepBackoff(attempt);
                        continue;
                    }
                    return null;
                }

                String body = decodeBody(resp);
                if (body == null || body.isBlank()) return null;

                JsonObject root = parseJsonLenient(body);
                JsonObject fields = safeObject(root, "fields");
                if (fields == null) return null;

                RemoteConfig cfg = new RemoteConfig();

                cfg.setAppStatus(getString(fields, "appStatus"));
                cfg.setLatestVersion(getString(fields, "latestVersion"));
                cfg.setMinVersion(getString(fields, "minVersion"));
                cfg.setDownloadUrl(getString(fields, "downloadUrl"));
                cfg.setUpdateMessage(getString(fields, "updateMessage"));
                cfg.setForceUpdate(getBool(fields, "forceUpdate"));

                // Scripts from DB (stringValue)
                cfg.setFreeRam_Script(getString(fields, "FreeRam_Script"));
                cfg.setOptimizeDisk_Script(getString(fields, "OptimizeDisk_Script"));
                cfg.setOptimizeNetwork_Script(getString(fields, "OptimizeNetwork_Script"));
                cfg.setPerformanceMode_Script(getString(fields, "PerformanceMode_Script"));
                cfg.setBalancedMode_Script(getString(fields, "BalancedMode_Script"));
                cfg.setQuitMode_Script(getString(fields, "QuitMode_Script"));
                cfg.setScanAndFix_Script(getString(fields, "ScanAndFix_Script"));

                HttpHeaders h = resp.headers();
                h.firstValue("etag").ifPresent(tag -> this.cachedEtag = tag);
                this.cachedConfig = cfg;

                return cfg;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            } catch (IOException ioe) {
                if (attempt < MAX_RETRIES) {
                    sleepBackoff(attempt);
                    continue;
                }
                return null;
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    private static boolean isTransient(int code) {
        return code == 429 || (code >= 500 && code < 600);
    }

    private static void sleepBackoff(int attempt) {
        long base = 200L;
        long jitter = (long) (Math.random() * 120);
        try { Thread.sleep(base * (attempt + 1) + jitter); }
        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private static JsonObject safeObject(JsonObject parent, String key) {
        if (parent == null || key == null) return null;
        JsonElement el = parent.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonObject()) return null;
        return el.getAsJsonObject();
    }

    private static String getString(JsonObject fields, String name) {
        JsonObject obj = safeObject(fields, name);
        if (obj == null) return null;
        JsonElement s = obj.get("stringValue");
        return (s != null && !s.isJsonNull()) ? s.getAsString() : null;
    }

    private static boolean getBool(JsonObject fields, String name) {
        JsonObject obj = safeObject(fields, name);
        if (obj == null) return false;
        JsonElement b = obj.get("booleanValue");
        return (b != null && !b.isJsonNull()) && b.getAsBoolean();
    }

    private static String decodeBody(HttpResponse<byte[]> resp) {
        byte[] bytes = resp.body();
        if (bytes == null) return "";
        boolean gzip = resp.headers().firstValue("Content-Encoding")
                .map(v -> v.toLowerCase().contains("gzip")).orElse(false);

        if (!gzip && bytes.length >= 2) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            gzip = (b0 == 0x1F && b1 == 0x8B);
        }

        try {
            if (gzip) {
                try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                    return new String(gis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static JsonObject parseJsonLenient(String body) {
        String json = sanitizeJsonBody(body);
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(true);
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return JsonParser.parseString(json).getAsJsonObject();
        }
    }

    private static String sanitizeJsonBody(String s) {
        if (s == null) return "";
        String out = s;
        if (!out.isEmpty() && out.charAt(0) == '\uFEFF') out = out.substring(1);
        out = out.trim();
        if (out.startsWith(")]}'")) {
            int idxObj = out.indexOf('{');
            int idxArr = out.indexOf('[');
            int idx = -1;
            if (idxObj >= 0 && idxArr >= 0) idx = Math.min(idxObj, idxArr);
            else if (idxObj >= 0) idx = idxObj;
            else if (idxArr >= 0) idx = idxArr;
            if (idx > 0) out = out.substring(idx);
        }
        return out;
    }
}
