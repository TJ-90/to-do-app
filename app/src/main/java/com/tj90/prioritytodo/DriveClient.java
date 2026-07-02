package com.tj90.prioritytodo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class DriveClient {
    private static final String FILE_NAME = "priority-todo.json";
    private static final String BOUNDARY = "ptdo_boundary_7f3a";

    private final String accessToken;

    DriveClient(String accessToken) {
        this.accessToken = accessToken;
    }

    String findFileId() throws IOException {
        String q = URLEncoder.encode("name='" + FILE_NAME + "' and trashed=false", "UTF-8");
        String url = "https://www.googleapis.com/drive/v3/files"
                + "?spaces=appDataFolder&fields=files(id,name)&q=" + q;
        HttpURLConnection conn = open(url, "GET");
        String body = readBody(conn);
        int start = body.indexOf("\"id\"");
        if (start < 0) {
            return null;
        }
        int colon = body.indexOf(':', start);
        int firstQuote = body.indexOf('"', colon + 1);
        int lastQuote = body.indexOf('"', firstQuote + 1);
        return body.substring(firstQuote + 1, lastQuote);
    }

    String download(String fileId) throws IOException {
        String url = "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media";
        HttpURLConnection conn = open(url, "GET");
        return readBody(conn);
    }

    String create(String json) throws IOException {
        String url = "https://www.googleapis.com/upload/drive/v3/files"
                + "?uploadType=multipart&fields=id";
        HttpURLConnection conn = open(url, "POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + BOUNDARY);
        String meta = "{\"name\":\"" + FILE_NAME + "\",\"parents\":[\"appDataFolder\"]}";
        String payload = "--" + BOUNDARY + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                + meta + "\r\n"
                + "--" + BOUNDARY + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                + json + "\r\n"
                + "--" + BOUNDARY + "--";
        writeBody(conn, payload);
        return readBody(conn);
    }

    void update(String fileId, String json) throws IOException {
        String url = "https://www.googleapis.com/upload/drive/v3/files/"
                + fileId + "?uploadType=media";
        HttpURLConnection conn = open(url, "PATCH");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        writeBody(conn, json);
        readBody(conn);
    }

    private HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        if ("PATCH".equals(method)) {
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            conn.setRequestMethod("POST");
        } else {
            conn.setRequestMethod(method);
        }
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        return conn;
    }

    private void writeBody(HttpURLConnection conn, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) {
            if (code >= 400) {
                throw new IOException("Drive HTTP " + code);
            }
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        if (code >= 400) {
            throw new IOException("Drive HTTP " + code + ": " + sb);
        }
        return sb.toString();
    }
}
