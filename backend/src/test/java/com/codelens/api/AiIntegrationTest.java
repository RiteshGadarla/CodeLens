package com.codelens.api;

import com.codelens.domain.RunMode;
import com.codelens.service.AnalysisService;
import com.codelens.service.ProjectService;
import com.codelens.support.FakeAiService;
import com.codelens.support.IntegrationTest;
import com.codelens.support.SampleProject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static com.codelens.support.SampleProject.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "codelens.ai.enabled=true")
class AiIntegrationTest extends IntegrationTest {

    static final FakeAiService AI = FakeAiService.start();

    @DynamicPropertySource
    static void aiUrl(DynamicPropertyRegistry registry) {
        registry.add("codelens.ai.base-url", AI::url);
    }

    MockMvc mvc;
    Long owner;
    @Autowired
    ObjectMapper json;
    @Autowired
    ProjectService projects;
    @Autowired
    AnalysisService analysis;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void signedIn() {
        var session = signUp("ai");
        owner = session.user().getId();
        mvc = mvcAs(session.token());
    }

    @Test
    void analysisIndexesMethodAndTypeChunks() throws Exception {
        long project = analyzed("ai-index");
        JsonNode body = json.readTree(AI.find("POST", "/projects/" + project + "/index").get(0).body());

        assertThat(body.get("reset").asBoolean()).isTrue();
        var byId = new java.util.HashMap<String, JsonNode>();
        body.get("chunks").forEach(c -> byId.put(c.get("id").asText(), c));
        assertThat(byId).containsKeys(IMPL, IMPL + "#create(UserDto)", CTRL + "#get(Long)");
        assertThat(byId.get(IMPL + "#create(UserDto)").get("text").asText()).contains("validate(user)");
        assertThat(byId.get(IMPL).get("text").asText()).contains("implements UserService", "find(Long id) { ... }");
        assertThat(byId.get(CTRL + "#get(Long)").get("label").asText()).isEqualTo("UserController#get(Long)");
    }

    @Test
    void askSendsGraphFactsAndLinksSources() throws Exception {
        long project = analyzed("ai-ask");

        mvc.perform(post("/api/projects/{id}/ask", project).contentType(APPLICATION_JSON)
                        .content("{\"question\":\"What depends on UserService?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("UserServiceImpl")))
                .andExpect(jsonPath("$.generatedBy").value("llm"))
                .andExpect(jsonPath("$.sources[0].entityId").isNumber())
                .andExpect(jsonPath("$.facts.entities[0].qualifiedName").value(SVC));

        JsonNode sent = json.readTree(AI.find("POST", "/projects/" + project + "/ask").get(0).body());
        assertThat(sent.at("/facts/entities/0/dependents").toString()).contains("UserServiceImpl (implements)");
        assertThat(sent.at("/facts/entities/0/changeImpact/affectedEndpoints").toString()).contains("GET /api/users/{id}");
        assertThat(sent.get("focus").toString()).contains(SVC);
    }

    @Test
    void impactReportIsGeneratedAndStored() throws Exception {
        long project = analyzed("ai-report");
        long entity = entityId(project, REPO + "#findByName(String)");

        mvc.perform(post("/api/projects/{id}/reports/impact/{e}", project, entity))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary", containsString("Summary")))
                .andExpect(jsonPath("$.impact.endpoints[0].entity.label").value("POST /api/users"))
                .andExpect(jsonPath("$.sources[0].entityId").value(entity));

        JsonNode sent = json.readTree(AI.find("POST", "/projects/" + project + "/report").get(0).body());
        assertThat(sent.at("/facts/target/label").asText()).isEqualTo("UserRepository#findByName(String)");
        assertThat(sent.at("/facts/risk/level").asText()).isNotBlank();

        mvc.perform(get("/api/projects/{id}/reports", project))
                .andExpect(jsonPath("$[0].impact.target.label").value("UserRepository#findByName(String)"))
                .andExpect(jsonPath("$[0].model").value("fake-gemma"));
    }

    @Test
    void llmFailureIsServiceUnavailable() throws Exception {
        long project = analyzed("ai-down");
        AI.failLlm = true;
        try {
            mvc.perform(post("/api/projects/{id}/ask", project).contentType(APPLICATION_JSON)
                            .content("{\"question\":\"Explain UserController\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.detail", containsString("quota")));
        } finally {
            AI.failLlm = false;
        }
    }

    @Test
    void validatesQuestion() throws Exception {
        long project = projects.createLocal(owner, "ai-validate", SampleProject.root().toString()).getId();
        mvc.perform(post("/api/projects/{id}/ask", project).contentType(APPLICATION_JSON).content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private long analyzed(String name) throws InterruptedException {
        long project = projects.createLocal(owner, name, SampleProject.root().toString()).getId();
        analysis.runNow(project, RunMode.FULL);
        awaitIndexed(project);
        return project;
    }

    private void awaitIndexed(long project) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            if (!AI.find("POST", "/projects/" + project + "/index").isEmpty()) return;
            Thread.sleep(50);
        }
        List<String> seen = new ArrayList<>();
        AI.requests.forEach(r -> seen.add(r.method() + " " + r.path()));
        throw new AssertionError("index request not sent; saw " + seen);
    }

    private long entityId(long project, String qn) {
        return jdbc.queryForObject("select id from code_entity where project_id = ? and qualified_name = ?",
                Long.class, project, qn);
    }
}
