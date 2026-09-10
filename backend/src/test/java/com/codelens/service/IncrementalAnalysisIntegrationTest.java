package com.codelens.service;

import com.codelens.domain.RunMode;
import com.codelens.domain.RunStatus;
import com.codelens.support.IntegrationTest;
import com.codelens.support.SampleProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncrementalAnalysisIntegrationTest extends IntegrationTest {

    @Autowired
    ProjectService projects;
    @Autowired
    AnalysisService analysis;
    @Autowired
    JdbcTemplate jdbc;

    @TempDir
    Path tmp;

    @Test
    void incrementalRunMatchesFullAnalysisOfSameTree() throws IOException {
        Path repo = copySample();
        long project = projects.createLocal("inc", repo.toString()).getId();
        analysis.runNow(project, RunMode.FULL);
        long userId = entityId(project, SampleProject.USER);

        // change one file, add one, delete one
        Path impl = repo.resolve("src/main/java/com/acme/service/UserServiceImpl.java");
        Files.writeString(impl, Files.readString(impl).replace("    private void validate(User user) {", """
                    public int count() {
                        return repo.findByName("all").size();
                    }

                    private void validate(User user) {"""));
        Files.writeString(repo.resolve("src/main/java/com/acme/service/AuditService.java"), """
                package com.acme.service;

                public class AuditService {
                    private final UserService users;

                    public AuditService(UserService users) {
                        this.users = users;
                    }

                    public void audit(Long id) {
                        users.find(id);
                    }
                }
                """);
        Files.delete(repo.resolve("src/main/java/com/acme/domain/UserDto.java"));

        var run = analysis.runNow(project, RunMode.INCREMENTAL);

        assertThat(run.getStatus()).isEqualTo(RunStatus.SUCCESS);
        assertThat(run.getMode()).isEqualTo(RunMode.INCREMENTAL);
        assertThat(run.getFilesParsed()).isEqualTo(2);
        assertThat(run.getFilesDeleted()).isEqualTo(1);
        assertThat(entityId(project, SampleProject.USER)).isEqualTo(userId);

        long reference = projects.createLocal("reference", repo.toString()).getId();
        analysis.runNow(reference, RunMode.FULL);

        assertThat(entities(project)).containsExactlyInAnyOrderElementsOf(entities(reference));
        assertThat(edges(project)).containsExactlyInAnyOrderElementsOf(edges(reference));
        assertThat(count("entity_metric", project)).isEqualTo(count("entity_metric", reference));
        assertThat(entities(project)).contains(SampleProject.IMPL + "#count()", "com.acme.service.AuditService#audit(Long)");
        assertThat(entities(project)).noneMatch(qn -> qn.startsWith(SampleProject.DTO));
    }

    @Test
    void unchangedTreeIsANoOp() throws IOException {
        long project = projects.createLocal("noop", copySample().toString()).getId();
        analysis.runNow(project, RunMode.FULL);
        long userId = entityId(project, SampleProject.USER);

        var run = analysis.runNow(project, RunMode.INCREMENTAL);

        assertThat(run.getStatus()).isEqualTo(RunStatus.SUCCESS);
        assertThat(run.getFilesParsed()).isZero();
        assertThat(entityId(project, SampleProject.USER)).isEqualTo(userId);
        assertThat(run.getEntities()).isEqualTo(SampleProject.facts().entities().size());
    }

    @Test
    void incrementalWithoutHistoryFallsBackToFull() throws IOException {
        long project = projects.createLocal("first", copySample().toString()).getId();
        var run = analysis.runNow(project, RunMode.INCREMENTAL);
        assertThat(run.getStatus()).isEqualTo(RunStatus.SUCCESS);
        assertThat(run.getMode()).isEqualTo(RunMode.FULL);
    }

    private Path copySample() throws IOException {
        Path repo = Files.createTempDirectory(tmp, "repo");
        FileSystemUtils.copyRecursively(SampleProject.root(), repo);
        return repo;
    }

    private long entityId(long project, String qn) {
        return jdbc.queryForObject("select id from code_entity where project_id = ? and qualified_name = ?",
                Long.class, project, qn);
    }

    private List<String> entities(long project) {
        return jdbc.queryForList("select qualified_name from code_entity where project_id = ?", String.class, project);
    }

    private List<String> edges(long project) {
        return jdbc.queryForList("""
                select s.qualified_name || ' -' || d.type || '(' || d.weight || ')-> ' || t.qualified_name
                from dependency_edge d
                join code_entity s on s.id = d.source_id
                join code_entity t on t.id = d.target_id
                where d.project_id = ?""", String.class, project);
    }

    private int count(String table, long project) {
        return jdbc.queryForObject("select count(*) from " + table + " where project_id = ?", Integer.class, project);
    }
}
