package com.codelens.ai;

import com.codelens.common.AiUnavailableException;
import com.codelens.config.CodeLensProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// rest client for the python rag service
@Component
public class AiClient {

    public record Chunk(String id, String path, String kind, String label, String qualifiedName, int startLine,
                        int endLine, String text) {
    }

    public record IndexRequest(List<Chunk> chunks, List<String> removedPaths, boolean reset) {
    }

    public record IndexResult(int added, int skipped, int removed, int total) {
    }

    public record IndexStats(long projectId, int chunks, int files, String embedder) {
    }

    public record Source(int ref, String path, String label, String qualifiedName, int startLine, int endLine,
                         double score) {
    }

    public record AskRequest(String question, Map<String, Object> facts, List<String> focus, int topK) {
    }

    public record AskResult(String answer, List<Source> sources, String model, boolean cached, String generatedBy) {
    }

    public record ReportRequest(Map<String, Object> facts, List<String> focus, int topK) {
    }

    public record ReportResult(String summary, List<Source> sources, String model, boolean cached,
                               String generatedBy) {
    }

    private final RestClient http;
    private final boolean enabled;

    public AiClient(RestClient aiRestClient, CodeLensProperties props) {
        this.http = aiRestClient;
        this.enabled = props.ai().enabled();
    }

    public boolean enabled() {
        return enabled;
    }

    public IndexResult index(long projectId, IndexRequest request) {
        return call(() -> http.post().uri("/projects/{id}/index", projectId).body(request).retrieve()
                .body(IndexResult.class));
    }

    public IndexStats stats(long projectId) {
        return call(() -> http.get().uri("/projects/{id}/index", projectId).retrieve().body(IndexStats.class));
    }

    public void deleteIndex(long projectId) {
        call(() -> http.delete().uri("/projects/{id}/index", projectId).retrieve().toBodilessEntity());
    }

    public AskResult ask(long projectId, AskRequest request) {
        return call(() -> http.post().uri("/projects/{id}/ask", projectId).body(request).retrieve()
                .body(AskResult.class));
    }

    public ReportResult report(long projectId, ReportRequest request) {
        return call(() -> http.post().uri("/projects/{id}/report", projectId).body(request).retrieve()
                .body(ReportResult.class));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> health() {
        return call(() -> http.get().uri("/health").retrieve().body(Map.class));
    }

    private <T> T call(Supplier<T> request) {
        if (!enabled) throw new AiUnavailableException("AI service is disabled");
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            throw new AiUnavailableException("AI service returned " + e.getStatusCode().value() + ": "
                    + (body.length() > 300 ? body.substring(0, 300) : body));
        } catch (RestClientException e) {
            throw new AiUnavailableException("AI service unreachable: " + e.getMessage());
        }
    }
}
