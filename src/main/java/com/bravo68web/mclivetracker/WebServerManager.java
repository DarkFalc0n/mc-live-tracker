package com.bravo68web.mclivetracker;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebServerManager {
    private static final Gson GSON = new Gson();

    private final int port;
    private HttpServer server;
    private ExecutorService executor;

    private final List<PlayerPosition> latestPositions = new CopyOnWriteArrayList<>();
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();

    private static final Pattern TILE_PATTERN = Pattern.compile("^/api/tile/([0-9]+)/([0-9]+)/([0-9]+)\\.png$");

    public WebServerManager(int port) {
        this.port = port;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            server.createContext("/", new RootHandler());
            server.createContext("/api/players", new PlayersHandler());
            server.createContext("/events", new EventsHandler());
            server.createContext("/api/tile", new TileHandler());
            server.start();
            System.out.println("[mc-live-tracker] Web server started on http://localhost:" + port);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start web server", e);
        }
    }

    public void stop() {
        try {
            if (server != null) server.stop(0);
        } finally {
            for (SseClient c : sseClients) {
                try { c.close(); } catch (Exception ignored) {}
            }
            sseClients.clear();
            if (executor != null) executor.shutdownNow();
        }
    }

    public void updatePlayerPositions(List<PlayerPosition> positions) {
        latestPositions.clear();
        latestPositions.addAll(positions);
        Map<String, Object> payload = new HashMap<>();
        payload.put("timestamp", Instant.now().toEpochMilli());
        payload.put("players", positions);
        broadcastSse(GSON.toJson(payload));
    }

    private void broadcastSse(String data) {
        for (SseClient client : sseClients) {
            if (!client.send(data)) {
                sseClients.remove(client);
            }
        }
    }

    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            if (path.equals("/") || path.equals("/index.html")) {
                sendResource(exchange, "web/index.html", "text/html; charset=utf-8");
                return;
            }
            if (path.equals("/app.js")) {
                sendResource(exchange, "web/app.js", "application/javascript; charset=utf-8");
                return;
            }
            if (path.equals("/styles.css")) {
                sendResource(exchange, "web/styles.css", "text/css; charset=utf-8");
                return;
            }
            sendNotFound(exchange);
        }
    }

    private class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            byte[] bytes = GSON.toJson(latestPositions).getBytes(StandardCharsets.UTF_8);
            Headers h = exchange.getResponseHeaders();
            h.add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            Headers h = exchange.getResponseHeaders();
            h.add("Content-Type", "text/event-stream");
            h.add("Cache-Control", "no-cache");
            h.add("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);
            SseClient client = new SseClient(exchange);
            sseClients.add(client);
            // send initial state
            Map<String, Object> payload = new HashMap<>();
            payload.put("timestamp", Instant.now().toEpochMilli());
            payload.put("players", latestPositions);
            client.send(GSON.toJson(payload));
        }
    }

    private class TileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            Matcher m = TILE_PATTERN.matcher(exchange.getRequestURI().getPath());
            if (!m.matches()) {
                sendNotFound(exchange);
                return;
            }
            int z = Integer.parseInt(m.group(1));
            int x = Integer.parseInt(m.group(2));
            int y = Integer.parseInt(m.group(3));
            byte[] png = TileRenderer.renderPlaceholderPng(z, x, y);
            Headers h = exchange.getResponseHeaders();
            h.add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, png.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(png);
            }
        }
    }

    private void sendResource(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
        InputStream is = WebServerManager.class.getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            sendNotFound(exchange);
            return;
        }
        byte[] bytes = readAll(is);
        Headers h = exchange.getResponseHeaders();
        h.add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendNotFound(HttpExchange exchange) throws IOException {
        byte[] msg = "Not Found".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(404, msg.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(msg); }
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        byte[] msg = "Method Not Allowed".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(405, msg.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(msg); }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
        return baos.toByteArray();
    }

    private static class SseClient {
        private final HttpExchange exchange;
        private final OutputStream os;
        private boolean open = true;
        SseClient(HttpExchange exchange) throws IOException {
            this.exchange = exchange;
            this.os = exchange.getResponseBody();
        }
        boolean send(String json) {
            if (!open) return false;
            try {
                String msg = "data: " + json + "\n\n";
                os.write(msg.getBytes(StandardCharsets.UTF_8));
                os.flush();
                return true;
            } catch (IOException e) {
                close();
                return false;
            }
        }
        void close() {
            if (!open) return;
            open = false;
            try { os.close(); } catch (IOException ignored) {}
            exchange.close();
        }
    }
}
