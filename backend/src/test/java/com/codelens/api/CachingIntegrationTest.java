package com.codelens.api;

import com.codelens.domain.RunMode;
import com.codelens.service.AnalysisService;
import com.codelens.service.ImpactService;
import com.codelens.service.ProjectService;
import com.codelens.support.IntegrationTest;
import com.codelens.support.SampleProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "spring.cache.type=redis")
class CachingIntegrationTest extends IntegrationTest {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @Autowired
    ImpactService impact;
    @Autowired
    ProjectService projects;
    @Autowired
    AnalysisService analysis;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void impactIsCachedPerGraphVersion() {
        long project = projects.createLocal("cache", SampleProject.root().toString()).getId();
        analysis.runNow(project, RunMode.FULL);
        String pattern = "codelens:impact::" + project + ":*";

        var first = impact.forEntity(project, userId(project), 10, false);
        assertThat(redis.keys(pattern)).hasSize(1);
        assertThat(impact.forEntity(project, userId(project), 10, false)).isEqualTo(first);
        assertThat(redis.keys(pattern)).hasSize(1);

        // new analysis -> new version in the key
        analysis.runNow(project, RunMode.FULL);
        var second = impact.forEntity(project, userId(project), 10, false);
        assertThat(redis.keys(pattern)).hasSize(2);
        assertThat(second.transitiveDependents()).isEqualTo(first.transitiveDependents());
    }

    private long userId(long project) {
        return jdbc.queryForObject("select id from code_entity where project_id = ? and qualified_name = ?",
                Long.class, project, SampleProject.USER);
    }
}
