package com.bravo68web.mclivetracker;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
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
    private long seed;

    public void setSeed(long seed) {
        this.seed = seed;
    }

    private static final Pattern TILE_PATTERN = Pattern.compile("^/api/tile/([0-9]+)/([0-9]+)/([0-9]+)\\.png$");

    public WebServerManager(int port) {
        this.port = port;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            server.createContext("/", new StaticHandler("web/dist"));
            server.createContext("/api/players", new PlayersHandler());
            server.createContext("/events", new EventsHandler());
            server.createContext("/api/tile", new TileHandler());
            server.createContext("/api/seed", new SeedHandler());
            server.start();
            System.out.println("[mc-live-tracker] Web server started on http://localhost:" + port);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start web server", e);
        }
    }

    public void stop() {
        try {
            if (server != null)
                server.stop(0);
        } finally {
            for (SseClient c : sseClients) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
            sseClients.clear();
            if (executor != null)
                executor.shutdownNow();
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

    private class StaticHandler implements HttpHandler {
        private final String resourceRoot;
        private final java.nio.file.Path fileSystemRoot;

        public StaticHandler(String resourceRoot) {
            this.resourceRoot = resourceRoot;
            String devPath = System.getProperty("mclivetracker.dev.web-root");
            this.fileSystemRoot = devPath != null ? java.nio.file.Paths.get(devPath) : null;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            // Security check to prevent directory traversal
            if (path.contains("..")) {
                sendNotFound(exchange);
                return;
            }

            String contentType = determineContentType(path);

            // Try filesystem first if in dev mode
            if (fileSystemRoot != null) {
                // Remove leading slash for resolve
                String relPath = path.startsWith("/") ? path.substring(1) : path;
                java.nio.file.Path file = fileSystemRoot.resolve(relPath).normalize();

                // Ensure we are still within root
                if (!file.startsWith(fileSystemRoot)) {
                    sendNotFound(exchange);
                    return;
                }

                if (java.nio.file.Files.exists(file) && !java.nio.file.Files.isDirectory(file)) {
                    byte[] bytes = java.nio.file.Files.readAllBytes(file);
                    // Disable caching in dev environment
                    exchange.getResponseHeaders().add("Cache-Control", "no-cache, no-store, must-revalidate");
                    sendResponse(exchange, bytes, contentType);
                    return;
                }
            } else {
                // System.out.println("[Dev] fileSystemRoot is null, using Classpath for: " +
                // path);
            }

            // Fallback to classpath
            // resourceRoot is like "web/dist"
            // path is like "/index.html"
            String resourcePath = resourceRoot + path;
            InputStream is = WebServerManager.class.getClassLoader().getResourceAsStream(resourcePath);
            if (is != null) {
                byte[] bytes = readAll(is);
                sendResponse(exchange, bytes, contentType);
                return;
            }

            sendNotFound(exchange);
        }

        private String determineContentType(String path) {
            if (path.endsWith(".html"))
                return "text/html; charset=utf-8";
            if (path.endsWith(".js"))
                return "application/javascript; charset=utf-8";
            if (path.endsWith(".css"))
                return "text/css; charset=utf-8";
            if (path.endsWith(".png"))
                return "image/png";
            if (path.endsWith(".svg"))
                return "image/svg+xml";
            if (path.endsWith(".json"))
                return "application/json; charset=utf-8";
            return "application/octet-stream";
        }

        private void sendResponse(HttpExchange exchange, byte[] bytes, String contentType) throws IOException {
            Headers h = exchange.getResponseHeaders();
            h.add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
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

    private void sendNotFound(HttpExchange exchange) throws IOException {
        byte[] msg = "Not Found".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(404, msg.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(msg);
        }
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        byte[] msg = "Method Not Allowed".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(405, msg.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(msg);
        }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) != -1)
            baos.write(buf, 0, r);
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
            if (!open)
                return false;
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
            if (!open)
                return;
            open = false;
            try {
                os.close();
            } catch (IOException ignored) {
            }
            exchange.close();
        }
    }

    private class SeedHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            Map<String, Object> response = new HashMap<>();
            response.put("seed", seed);
            byte[] bytes = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
            Headers h = exchange.getResponseHeaders();
            h.add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
