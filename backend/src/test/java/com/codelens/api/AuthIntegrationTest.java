package com.codelens.api;

import com.codelens.service.ProjectService;
import com.codelens.support.IntegrationTest;
import com.codelens.support.SampleProject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Autowired
    ObjectMapper json;
    @Autowired
    ProjectService projects;

    @Test
    void registerLoginAndMe() throws Exception {
        MockMvc anon = anonymous();
        String email = "Ada-" + UUID.randomUUID() + "@Example.com";
        var body = Map.of("name", "Ada", "email", email, "password", "analytical-engine");

        anon.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.email").value(email.toLowerCase()));
        anon.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());

        var login = Map.of("email", email.toUpperCase(), "password", "analytical-engine");
        var res = anon.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(json.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        String token = json.readTree(res.getResponse().getContentAsString()).get("token").asText();

        mvcAs(token).perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada"));
    }

    @Test
    void rejectsBadCredentialsAndMissingTokens() throws Exception {
        MockMvc anon = anonymous();
        signUp("grace");
        anon.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever1\"}"))
                .andExpect(status().isUnauthorized());
        anon.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        anon.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mvcAs("not.a.jwt").perform(get("/api/projects")).andExpect(status().isUnauthorized());
        anon.perform(get("/api/config")).andExpect(jsonPath("$.allowLocalPaths").value(true));
    }

    @Test
    void projectsAreVisibleOnlyToTheirOwner() throws Exception {
        var alice = signUp("alice");
        var bob = signUp("bob");
        long id = projects.createLocal(alice.user().getId(), "alice-only", SampleProject.root().toString()).getId();

        mvcAs(alice.token()).perform(get("/api/projects/{id}", id)).andExpect(status().isOk());
        mvcAs(alice.token()).perform(get("/api/projects")).andExpect(jsonPath("$[*].name", hasItem("alice-only")));

        MockMvc asBob = mvcAs(bob.token());
        asBob.perform(get("/api/projects")).andExpect(jsonPath("$[*].name", not(hasItem("alice-only"))));
        asBob.perform(get("/api/projects/{id}", id)).andExpect(status().isNotFound());
        asBob.perform(get("/api/projects/{id}/overview", id)).andExpect(status().isNotFound());
        asBob.perform(post("/api/projects/{id}/analyze", id)).andExpect(status().isNotFound());
        asBob.perform(get("/api/dashboard")).andExpect(jsonPath("$.totals.projects").value(0));
    }

    private MockMvc anonymous() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
}
