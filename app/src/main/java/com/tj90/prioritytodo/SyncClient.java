package com.tj90.prioritytodo;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class SyncClient {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private SyncClient() { }

    static String normalizeBaseUrl(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Enter a sync server URL.");
        }
        try {
            URI uri = new URI(raw);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Use an http:// or https:// server URL.");
            }
            String normalized = uri.normalize().toString();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Enter a valid sync server URL.");
        }
    }

    static SyncState sync(String baseUrl, String accessClientId,
                          String accessClientSecret, String requestJson) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                normalizeBaseUrl(baseUrl) + "/api/sync").openConnection();
        connection.setRequestMethod("POST");
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        if (accessClientId != null && !accessClientId.trim().isEmpty()
                && accessClientSecret != null && !accessClientSecret.trim().isEmpty()) {
            connection.setRequestProperty("CF-Access-Client-Id", accessClientId.trim());
            connection.setRequestProperty("CF-Access-Client-Secret", accessClientSecret.trim());
        }
        byte[] body = requestJson.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readUtf8(stream);
            if (status < 200 || status >= 300) {
                throw new IOException("Sync server returned HTTP " + status);
            }
            return parseResponse(response);
        } finally {
            connection.disconnect();
        }
    }

    static boolean responseMatchesRequest(long requestedRevision, long currentRevision,
                                          String requestedBaseUrl, String currentBaseUrl) {
        return requestedRevision == currentRevision
                && requestedBaseUrl != null
                && requestedBaseUrl.equals(currentBaseUrl);
    }

    static SyncState parseResponse(String response) throws Exception {
        JSONObject json = new JSONObject(response);
        if (json.optJSONArray("tasks") == null
                || json.optJSONArray("taskTombstones") == null
                || json.optJSONArray("categories") == null) {
            throw new IOException("Sync response is missing required state arrays");
        }
        return SyncState.fromJson(json);
    }

    private static String readUtf8(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("Sync response is too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
