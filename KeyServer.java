package fertdlc.keys;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public final class KeyServer {
    private static final int MAX_REQUEST_BYTES = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, KeyRecord> KEYS = new ConcurrentHashMap<>();
    private static Path dataFile;
    private static String adminToken;

    private KeyServer() {
    }

    public static void main(String[] args) throws Exception {
        // Токен берем из переменной окружения, если нет - ставим дефолтный (для теста)
        adminToken = System.getenv().getOrDefault("FERTDLC_ADMIN_TOKEN", "SuperSecretDefaultAdminToken1234567890!");
        if (adminToken.length() < 32) {
            throw new IllegalStateException("Set FERTDLC_ADMIN_TOKEN to a random secret of at least 32 characters.");
        }
        
        dataFile = Path.of(System.getenv().getOrDefault("FERTDLC_KEYS_FILE", "keys.db")).toAbsolutePath();
        loadKeys();

        // Render требует, чтобы сервер слушал 0.0.0.0, а порт берем из окружения (Render сам его подставит)
        String host = System.getenv().getOrDefault("FERTDLC_BIND_HOST", "0.0.0.0");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080")); // Render использует PORT
        
        InetAddress bindAddress = InetAddress.getByName(host);
        
        // Создаем основной сервер
        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.createContext("/validate", KeyServer::validateKey);
        server.createContext("/admin/issue", exchange -> admin(exchange, KeyServer::issueKey));
        server.createContext("/admin/issue-admin", exchange -> admin(exchange, KeyServer::issueAdminKey));
        server.createContext("/admin/revoke", exchange -> admin(exchange, KeyServer::revokeKey));
        server.createContext("/admin/set-role", exchange -> admin(exchange, KeyServer::setKeyRole));
        server.createContext("/admin/list", exchange -> admin(exchange, KeyServer::listKeys));
        server.createContext("/manage/issue", exchange -> manage(exchange, KeyServer::issueKey));
        server.createContext("/manage/issue-admin", exchange -> manage(exchange, KeyServer::issueAdminKey));
        server.createContext("/manage/revoke", exchange -> manage(exchange, KeyServer::revokeKey));
        server.createContext("/manage/set-role", exchange -> manage(exchange, KeyServer::setKeyRole));
        server.createContext("/manage/list", exchange -> manage(exchange, KeyServer::listKeys));
        server.setExecutor(Executors.newFixedThreadPool(8));

        // Валидационный шлюз (оставляем для совместимости)
        int validationPort = Integer.parseInt(System.getenv().getOrDefault("FERTDLC_VALIDATION_GATEWAY_PORT", "8081"));
        if (validationPort != port) {
            HttpServer validationGateway = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), validationPort), 0);
            validationGateway.createContext("/", exchange -> {
                if ("/validate".equals(exchange.getRequestURI().getPath()) && exchange.getRequestURI().getRawQuery() == null) {
                    validateKey(exchange);
                } else {
                    respond(exchange, 404, "not found");
                }
            });
            validationGateway.setExecutor(Executors.newFixedThreadPool(4));
            validationGateway.start();
            System.out.println("Validation gateway on 127.0.0.1:" + validationPort);
        }

        server.start();
        System.out.println("Key server listening on " + host + ":" + port);
        System.out.println("Key database: " + dataFile);
    }

    private static void validateKey(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "method not allowed");
            return;
        }
        String key;
        try {
            key = readBody(exchange).trim();
        } catch (IllegalArgumentException exception) {
            respond(exchange, 413, "request too large");
            return;
        }
        String hash = hash(key);
        KeyRecord record = KEYS.get(hash);
        if (record == null || !record.active()) {
            respond(exchange, 200, "invalid");
        } else {
            respond(exchange, 200, record.administrator() ? "valid:admin" : "valid:user");
        }
    }

    private static void admin(HttpExchange exchange, AdminAction action) throws IOException {
        if (!authorized(exchange)) {
            respond(exchange, 401, "unauthorized");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "method not allowed");
            return;
        }
        try {
            action.run(exchange);
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, exception.getMessage());
        } catch (Exception exception) {
            respond(exchange, 500, "internal error");
        }
    }

    private static void issueKey(HttpExchange exchange) throws Exception {
        issueKey(exchange, false);
    }

    private static void issueAdminKey(HttpExchange exchange) throws Exception {
        issueKey(exchange, true);
    }

    private static void issueKey(HttpExchange exchange, boolean administrator) throws Exception {
        byte[] random = new byte[24];
        RANDOM.nextBytes(random);
        String key = "FDLC-" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String hash = hash(key);
        synchronized (KEYS) {
            KEYS.put(hash, new KeyRecord(System.currentTimeMillis(), true, administrator));
            saveKeys();
        }
        respond(exchange, 201, key);
    }

    private static void revokeKey(HttpExchange exchange) throws Exception {
        String key = readBody(exchange).trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key is required");
        }
        String hash = hash(key);
        synchronized (KEYS) {
            KeyRecord current = KEYS.get(hash);
            if (current == null) {
                respond(exchange, 404, "key not found");
                return;
            }
            KEYS.put(hash, new KeyRecord(current.createdAt(), false, current.administrator()));
            saveKeys();
        }
        respond(exchange, 200, "revoked");
    }

    private static void setKeyRole(HttpExchange exchange) throws Exception {
        String[] fields = readBody(exchange).split("\\t", 2);
        if (fields.length != 2 || fields[0].isBlank()) {
            throw new IllegalArgumentException("expected key and role separated by a tab");
        }
        String role = fields[1].trim();
        if (!"admin".equalsIgnoreCase(role) && !"user".equalsIgnoreCase(role)) {
            throw new IllegalArgumentException("role must be admin or user");
        }
        String hash = hash(fields[0].trim());
        synchronized (KEYS) {
            KeyRecord current = KEYS.get(hash);
            if (current == null) {
                respond(exchange, 404, "key not found");
                return;
            }
            KEYS.put(hash, new KeyRecord(current.createdAt(), current.active(), "admin".equalsIgnoreCase(role)));
            saveKeys();
        }
        respond(exchange, 200, "role updated");
    }

    private static void listKeys(HttpExchange exchange) throws IOException {
        List<String> rows = new ArrayList<>();
        KEYS.forEach((hash, record) -> rows.add(hash.substring(0, 12) + "\t" + record.createdAt() + "\t" + (record.active() ? "active" : "revoked") + "\t" + (record.administrator() ? "admin" : "user")));
        rows.sort(String::compareTo);
        respond(exchange, 200, String.join("\n", rows));
    }

    private static void manage(HttpExchange exchange, AdminAction action) throws IOException {
        if (!authorizedWithAdminKey(exchange)) {
            respond(exchange, 403, "admin key required");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "method not allowed");
            return;
        }
        try {
            action.run(exchange);
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, exception.getMessage());
        } catch (Exception exception) {
            respond(exchange, 500, "internal error");
        }
    }

    private static boolean authorizedWithAdminKey(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        try {
            KeyRecord record = KEYS.get(hash(header.substring(7).trim()));
            return record != null && record.active() && record.administrator();
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        return MessageDigest.isEqual(
            adminToken.getBytes(StandardCharsets.UTF_8),
            header.substring(7).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("request too large");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static String hash(String key) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private static void loadKeys() throws IOException {
        if (!Files.exists(dataFile)) {
            return;
        }
        for (String line : Files.readAllLines(dataFile, StandardCharsets.UTF_8)) {
            String[] fields = line.split("\\t", -1);
            if (fields.length >= 3 && fields[0].matches("[0-9a-f]{64}")) {
                boolean administrator = fields.length >= 4 && "admin".equalsIgnoreCase(fields[3]);
                KEYS.put(fields[0], new KeyRecord(Long.parseLong(fields[1]), Boolean.parseBoolean(fields[2]), administrator));
            }
        }
    }

    private static void saveKeys() throws IOException {
        Path parent = dataFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        List<String> lines = KEYS.entrySet().stream()
            .map(entry -> entry.getKey() + "\t" + entry.getValue().createdAt() + "\t" + entry.getValue().active() + "\t" + (entry.getValue().administrator() ? "admin" : "user"))
            .sorted()
            .toList();
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @FunctionalInterface
    private interface AdminAction {
        void run(HttpExchange exchange) throws Exception;
    }

    private record KeyRecord(long createdAt, boolean active, boolean administrator) {
    }
}