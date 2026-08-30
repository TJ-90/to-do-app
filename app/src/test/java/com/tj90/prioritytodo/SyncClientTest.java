package com.tj90.prioritytodo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class SyncClientTest {
    private static final String EMPTY_STATE =
            "{\"tasks\":[],\"taskTombstones\":[],\"categories\":[]}";

    @Test
    public void syncPostsJsonToApiPathAndDecodesSuccess() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        try (LoopbackServer server = new LoopbackServer(200, EMPTY_STATE, captured)) {
            SyncState response = SyncClient.sync(server.baseUrl(), "{\"client\":\"android\"}");

            assertTrue(response.tasks.isEmpty());
            CapturedRequest request = server.awaitRequest();
            assertEquals("POST /api/sync HTTP/1.1", request.requestLine);
            assertEquals("application/json; charset=utf-8", request.contentType);
            assertEquals("{\"client\":\"android\"}", request.body);
        }
    }

    @Test(expected = IOException.class)
    public void syncRejectsNonSuccessStatus() throws Exception {
        try (LoopbackServer server = new LoopbackServer(503, "unavailable", new AtomicReference<>())) {
            SyncClient.sync(server.baseUrl(), EMPTY_STATE);
        }
    }

    @Test(expected = IOException.class)
    public void syncRejectsSuccessMissingRequiredArrays() throws Exception {
        try (LoopbackServer server = new LoopbackServer(200, "{\"tasks\":[]}", new AtomicReference<>())) {
            SyncClient.sync(server.baseUrl(), EMPTY_STATE);
        }
    }

    private static final class CapturedRequest {
        final String requestLine;
        final String contentType;
        final String body;

        CapturedRequest(String requestLine, String contentType, String body) {
            this.requestLine = requestLine;
            this.contentType = contentType;
            this.body = body;
        }
    }

    private static final class LoopbackServer implements AutoCloseable {
        private final ServerSocket server;
        private final Thread thread;
        private final AtomicReference<CapturedRequest> captured;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        LoopbackServer(int status, String responseBody,
                       AtomicReference<CapturedRequest> captured) throws IOException {
            this.server = new ServerSocket(0, 1);
            this.captured = captured;
            this.thread = new Thread(() -> serve(status, responseBody), "sync-client-test-server");
            this.thread.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }

        CapturedRequest awaitRequest() throws Exception {
            thread.join(3000);
            if (thread.isAlive()) {
                throw new AssertionError("Loopback server did not finish");
            }
            if (failure.get() != null) {
                throw new AssertionError("Loopback server failed", failure.get());
            }
            return captured.get();
        }

        private void serve(int status, String responseBody) {
            try (Socket socket = server.accept()) {
                InputStream input = socket.getInputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.ISO_8859_1));
                String requestLine = reader.readLine();
                int contentLength = 0;
                String contentType = null;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    int separator = line.indexOf(':');
                    if (separator < 0) {
                        continue;
                    }
                    String name = line.substring(0, separator).trim();
                    String value = line.substring(separator + 1).trim();
                    if ("Content-Length".equalsIgnoreCase(name)) {
                        contentLength = Integer.parseInt(value);
                    } else if ("Content-Type".equalsIgnoreCase(name)) {
                        contentType = value;
                    }
                }
                char[] bodyChars = new char[contentLength];
                int offset = 0;
                while (offset < bodyChars.length) {
                    int count = reader.read(bodyChars, offset, bodyChars.length - offset);
                    if (count < 0) {
                        break;
                    }
                    offset += count;
                }
                captured.set(new CapturedRequest(
                        requestLine, contentType, new String(bodyChars, 0, offset)));

                byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
                OutputStream output = socket.getOutputStream();
                output.write(("HTTP/1.1 " + status + " Test\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: " + response.length + "\r\n"
                        + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(response);
                output.flush();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }

        @Override
        public void close() throws Exception {
            server.close();
            thread.join(3000);
            if (failure.get() != null) {
                throw new AssertionError("Loopback server failed", failure.get());
            }
        }
    }
}
