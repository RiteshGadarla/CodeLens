package com.codelens.api;

import com.codelens.domain.RunMode;
import com.codelens.service.AnalysisService;
import com.codelens.support.IntegrationTest;
import com.codelens.support.SampleProject;
import com.codelens.support.Zips;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static com.codelens.support.SampleProject.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiIntegrationTest extends IntegrationTest {

    private static Long projectId;
    private static String token;

    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    AnalysisService analysis;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void analyzedProject() throws Exception {
        if (token == null) token = signUp("api").token();
        mvc = mvcAs(token);
        if (projectId != null) return;
        var body = Map.of("name", "api-sample", "sourceType", "LOCAL", "path", SampleProject.root().toString(),
                "analyze", false);
        var res = mvc.perform(post("/api/projects").contentType(APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        projectId = json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
        analysis.runNow(projectId, RunMode.FULL);
    }

    @Test
    void projectShowsLatestRun() throws Exception {
        mvc.perform(get("/api/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.latestRun.status").value("SUCCESS"))
                .andExpect(jsonPath("$.latestRun.filesTotal").value(7));
        mvc.perform(get("/api/projects"))
                .andExpect(jsonPath("$[*].name", hasItem("api-sample")));
    }

    @Test
    void searchRanksExactNameFirst() throws Exception {
        mvc.perform(get("/api/projects/{id}/entities/search", projectId).param("q", "UserService"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("UserService"))
                .andExpect(jsonPath("$[0].kind").value("INTERFACE"));
        mvc.perform(get("/api/projects/{id}/entities/search", projectId).param("q", "find").param("kinds", "METHOD"))
                .andExpect(jsonPath("$[*].kind", everyItem(is("METHOD"))));
    }

    @Test
    void entityDetailHasMembersRelationsAndSource() throws Exception {
        long svc = entityId(SVC);
        mvc.perform(get("/api/projects/{id}/entities/{e}", projectId, svc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[*].name", hasItems("find", "create")))
                .andExpect(jsonPath("$.dependents[?(@.type == 'IMPLEMENTS')].entity.label", hasItem("UserServiceImpl")))
                .andExpect(jsonPath("$.metrics.riskLevel").exists());
        mvc.perform(get("/api/projects/{id}/entities/{e}/source", projectId, svc))
                .andExpect(jsonPath("$.code", containsString("public interface UserService")));
    }

    @Test
    void typeGraphExcludesTestsUnlessAsked() throws Exception {
        mvc.perform(get("/api/projects/{id}/graph", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(6))
                .andExpect(jsonPath("$.edges.length()", greaterThan(0)));
        mvc.perform(get("/api/projects/{id}/graph", projectId).param("includeTests", "true"))
                .andExpect(jsonPath("$.nodes.length()").value(7));
    }

    @Test
    void focusOnMemberCentersItsType() throws Exception {
        mvc.perform(get("/api/projects/{id}/graph", projectId)
                        .param("focus", String.valueOf(entityId(CTRL + "#get(Long)"))).param("depth", "1"))
                .andExpect(jsonPath("$.nodes[?(@.focus == true)].label", contains("UserController")))
                .andExpect(jsonPath("$.nodes[*].label", hasItem("UserService")));
    }

    @Test
    void findsPathsInBothDirections() throws Exception {
        mvc.perform(get("/api/projects/{id}/graph/path", projectId)
                        .param("from", String.valueOf(entityId(CTRL))).param("to", String.valueOf(entityId(USER))))
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.direction").value("DOWNSTREAM"))
                .andExpect(jsonPath("$.paths[0][0].label").value("UserController"));
        mvc.perform(get("/api/projects/{id}/graph/path", projectId)
                        .param("from", String.valueOf(entityId(USER))).param("to", String.valueOf(entityId(CTRL))))
                .andExpect(jsonPath("$.direction").value("UPSTREAM"));
    }

    @Test
    void impactForEntityAndFile() throws Exception {
        mvc.perform(get("/api/projects/{id}/impact/{e}", projectId, entityId(REPO + "#findByName(String)")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoints[0].entity.label").value("POST /api/users"))
                .andExpect(jsonPath("$.risk.level").exists());
        mvc.perform(get("/api/projects/{id}/impact/file", projectId)
                        .param("path", "src/main/java/com/acme/domain/UserDto.java"))
                .andExpect(jsonPath("$.target.label").value("UserDto"));
    }

    @Test
    void overviewHotspotsAndModules() throws Exception {
        mvc.perform(get("/api/projects/{id}/overview", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.types").value(7))
                .andExpect(jsonPath("$.stats.endpoints").value(2))
                .andExpect(jsonPath("$.stats.files").value(7))
                .andExpect(jsonPath("$.topRisks.length()", greaterThan(0)));
        mvc.perform(get("/api/projects/{id}/metrics/hotspots", projectId).param("scope", "METHOD").param("limit", "3"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].entity.kind", anyOf(is("METHOD"), is("CONSTRUCTOR"))));
        mvc.perform(get("/api/projects/{id}/metrics/modules", projectId).param("level", "PACKAGE"))
                .andExpect(jsonPath("$.items[*].name", hasItem("com.acme.domain")));
    }

    @Test
    void dashboardAggregatesOwnedProjects() throws Exception {
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects[?(@.name == 'api-sample')].types", contains(7)))
                .andExpect(jsonPath("$.projects[?(@.name == 'api-sample')].endpoints", contains(2)))
                .andExpect(jsonPath("$.totals.files", greaterThanOrEqualTo(7)))
                .andExpect(jsonPath("$.activity.length()").value(14))
                .andExpect(jsonPath("$.hotspots[0].projectName").exists());
    }

    @Test
    void overviewHasKpis() throws Exception {
        mvc.perform(get("/api/projects/{id}/overview", projectId))
                .andExpect(jsonPath("$.kpis.maxRisk", greaterThan(0.0)))
                .andExpect(jsonPath("$.kpis.complexity.length()").value(5))
                .andExpect(jsonPath("$.kpis.avgComplexity", greaterThanOrEqualTo(1.0)));
    }

    @Test
    void errorsAreProblemDetails() throws Exception {
        mvc.perform(get("/api/projects/{id}", 987654321))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("project not found")));
        mvc.perform(post("/api/projects").contentType(APPLICATION_JSON).content("{\"sourceType\":\"LOCAL\",\"path\":\"/nope\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/projects/{id}/impact/{e}", projectId, 987654321))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadAnalyzeAndDelete() throws Exception {
        var file = new MockMultipartFile("file", "sample.zip", "application/zip", Zips.of(SampleProject.root(), "sample/"));
        var res = mvc.perform(multipart("/api/projects/upload").file(file).param("analyze", "false"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = json.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/projects/{id}/analyze", id).param("mode", "FULL"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"));
        awaitIdle(id);
        mvc.perform(get("/api/projects/{id}/runs", id)).andExpect(jsonPath("$[0].status").value("SUCCESS"));

        mvc.perform(delete("/api/projects/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/projects/{id}", id)).andExpect(status().isNotFound());
    }

    private void awaitIdle(long id) throws Exception {
        for (int i = 0; i < 300; i++) {
            var body = mvc.perform(get("/api/projects/{id}", id)).andReturn().getResponse().getContentAsString();
            if (!json.readTree(body).get("analyzing").asBoolean()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("analysis did not finish");
    }

    private long entityId(String qn) {
        return jdbc.queryForObject("select id from code_entity where project_id = ? and qualified_name = ?",
                Long.class, projectId, qn);
    }
}
