package com.codelens.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// stands in for the python ai service; records requests, no llm calls
public final class FakeAiService {

    public record Request(String method, String path, String body) {
    }

    private static final Pattern PROJECT = Pattern.compile("/projects/(\\d+)/(index|ask|report)");

    public final List<Request> requests = new CopyOnWriteArrayList<>();
    public volatile boolean failLlm;

    private final HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Integer> chunks = new ConcurrentHashMap<>();

    private FakeAiService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public static FakeAiService start() {
        try {
            return new FakeAiService();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        requests.add(new Request(method, path, body));

        if (path.equals("/health")) {
            send(ex, 200, "{\"status\":\"ok\",\"model\":\"fake-gemma\"}");
            return;
        }
        Matcher m = PROJECT.matcher(path);
        if (!m.matches()) {
            send(ex, 404, "{\"detail\":\"not found\"}");
            return;
        }
        String project = m.group(1);
        switch (m.group(2) + ":" + method) {
            case "index:POST" -> {
                JsonNode n = json.readTree(body);
                int count = n.path("reset").asBoolean() ? 0 : chunks.getOrDefault(project, 0);
                count += n.path("chunks").size();
                chunks.put(project, count);
                send(ex, 200, "{\"added\":" + n.path("chunks").size() + ",\"skipped\":0,\"removed\":0,\"total\":" + count + "}");
            }
            case "index:GET" -> send(ex, 200, "{\"projectId\":" + project + ",\"chunks\":" + chunks.getOrDefault(project, 0)
                    + ",\"files\":1,\"embedder\":\"fake\"}");
            case "index:DELETE" -> {
                chunks.remove(project);
                ex.sendResponseHeaders(204, -1);
                ex.close();
            }
            case "ask:POST" -> {
                if (failLlm) {
                    send(ex, 503, "{\"detail\":\"llm unavailable: quota exceeded\"}");
                    return;
                }
                send(ex, 200, """
                        {"answer":"UserServiceImpl implements it [1]","sources":[{"ref":1,
                        "path":"src/main/java/com/acme/service/UserServiceImpl.java","label":"UserServiceImpl",
                        "qualifiedName":"com.acme.service.UserServiceImpl","startLine":8,"endLine":40,"score":0.91}],
                        "model":"fake-gemma","cached":false,"generatedBy":"llm"}""");
            }
            case "report:POST" -> send(ex, 200, """
                    {"summary":"## Summary\\nChanging this is risky [1]","sources":[{"ref":1,
                    "path":"src/main/java/com/acme/repo/UserRepository.java","label":"UserRepository#findByName(String)",
                    "qualifiedName":"com.acme.repo.UserRepository#findByName(String)","startLine":9,"endLine":9,"score":1.2}],
                    "model":"fake-gemma","cached":false,"generatedBy":"llm"}""");
            default -> send(ex, 405, "{\"detail\":\"method not allowed\"}");
        }
    }

    public List<Request> find(String method, String path) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().equals(path)).toList();
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (var out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
