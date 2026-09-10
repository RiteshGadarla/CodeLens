package com.codelens.service;

import com.codelens.domain.ProjectStatus;
import com.codelens.domain.RunMode;
import com.codelens.domain.RunStatus;
import com.codelens.repository.AnalysisRunRepository;
import com.codelens.support.IntegrationTest;
import com.codelens.support.SampleProject;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.FileSystemUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisServiceIntegrationTest extends IntegrationTest {

    @Autowired
    ProjectService projects;
    @Autowired
    AnalysisService analysis;
    @Autowired
    GraphService graphs;
    @Autowired
    AnalysisRunRepository runs;
    @Autowired
    JdbcTemplate jdbc;

    private final int entities = SampleProject.facts().entities().size();
    private final int edges = SampleProject.facts().edges().size();

    @Test
    void fullAnalysisPersistsFactsGraphAndMetrics() {
        var project = projects.createLocal("sample", SampleProject.root().toString());
        var run = analysis.runNow(project.getId(), RunMode.FULL);

        assertThat(run.getStatus()).isEqualTo(RunStatus.SUCCESS);
        assertThat(run.getEntities()).isEqualTo(entities);
        assertThat(run.getFilesTotal()).isEqualTo(7);
        assertThat(projects.get(project.getId()).getStatus()).isEqualTo(ProjectStatus.READY);

        assertThat(count("source_file", project.getId())).isEqualTo(7);
        assertThat(count("code_entity", project.getId())).isEqualTo(entities);
        assertThat(count("dependency_edge", project.getId())).isEqualTo(edges);
        assertThat(count("entity_metric", project.getId())).isEqualTo(entities);
        assertThat(count("module_metric", project.getId())).isPositive();

        var graph = graphs.graph(project.getId());
        assertThat(graph.size()).isEqualTo(entities);
        assertThat(graph.edgeCount()).isEqualTo(edges);
    }

    @Test
    void reanalysisReplacesData() {
        var project = projects.createLocal("again", SampleProject.root().toString());
        analysis.runNow(project.getId(), RunMode.FULL);
        analysis.runNow(project.getId(), RunMode.FULL);

        assertThat(count("code_entity", project.getId())).isEqualTo(entities);
        assertThat(runs.findTop20ByProjectIdOrderByStartedAtDesc(project.getId())).hasSize(2);
        assertThat(graphs.graph(project.getId()).size()).isEqualTo(entities);
    }

    @Test
    void analyzesUploadedZip() throws IOException {
        var project = projects.createUpload("", "sample.zip", new ByteArrayInputStream(zip(SampleProject.root(), "sample-main/")));
        assertThat(project.getName()).isEqualTo("sample");
        assertThat(project.getLocalPath()).endsWith("sample-main");

        var run = analysis.runNow(project.getId(), RunMode.FULL);
        assertThat(run.getStatus()).isEqualTo(RunStatus.SUCCESS);
        assertThat(count("code_entity", project.getId())).isEqualTo(entities);
    }

    @Test
    void clonesAndAnalyzesGitRepository(@TempDir Path tmp) throws Exception {
        Path origin = tmp.resolve("origin");
        FileSystemUtils.copyRecursively(SampleProject.root(), origin);
        try (Git git = Git.init().setDirectory(origin.toFile()).setInitialBranch("main").call()) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setAuthor("t", "t@t").setCommitter("t", "t@t").setSign(false).call();
        }

        var project = projects.createGit(null, origin.toUri().toString(), "main");
        assertThat(project.getName()).isEqualTo("origin");

        var run = analysis.runNow(project.getId(), RunMode.FULL);
        assertThat(run.getStatus()).isEqualTo(RunStatus.SUCCESS);
        assertThat(run.getCommitHash()).hasSize(40);
        assertThat(count("code_entity", project.getId())).isEqualTo(entities);

        // second run pulls instead of cloning
        assertThat(analysis.runNow(project.getId(), RunMode.FULL).getStatus()).isEqualTo(RunStatus.SUCCESS);
    }

    @Test
    void failureMarksRunAndProject(@TempDir Path tmp) throws IOException {
        Path dir = Files.createDirectories(tmp.resolve("gone"));
        var project = projects.createLocal("gone", dir.toString());
        Files.delete(dir);

        var run = analysis.runNow(project.getId(), RunMode.FULL);
        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(run.getError()).contains("not found");
        assertThat(projects.get(project.getId()).getStatus()).isEqualTo(ProjectStatus.FAILED);
    }

    @Test
    void validatesSources() {
        assertThatThrownBy(() -> projects.createLocal("x", "/definitely/not/here"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> projects.createGit("x", "ftp://example.com/repo", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ProjectService.repoName("git@github.com:acme/shop.git")).isEqualTo("shop");
    }

    @Test
    void deleteCascadesProjectData() {
        var project = projects.createLocal("delete-me", SampleProject.root().toString());
        analysis.runNow(project.getId(), RunMode.FULL);
        projects.delete(project.getId());
        assertThat(count("code_entity", project.getId())).isZero();
        assertThat(count("analysis_run", project.getId())).isZero();
    }

    private int count(String table, long projectId) {
        return jdbc.queryForObject("select count(*) from " + table + " where project_id = ?", Integer.class, projectId);
    }

    private static byte[] zip(Path root, String prefix) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ZipOutputStream(bytes); var files = Files.walk(root)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new ZipEntry(prefix + root.relativize(p).toString().replace('\\', '/')));
                out.write(Files.readAllBytes(p));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
