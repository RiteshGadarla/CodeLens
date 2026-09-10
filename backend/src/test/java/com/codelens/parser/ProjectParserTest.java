package com.codelens.parser;

import com.codelens.domain.EdgeType;
import com.codelens.domain.EntityKind;
import com.codelens.domain.Stereotype;
import com.codelens.parser.model.AnalysisFacts;
import com.codelens.parser.model.EntityFact;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.codelens.domain.EdgeType.*;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectParserTest {

    private static final String CTRL = "com.acme.api.UserController";
    private static final String SVC = "com.acme.service.UserService";
    private static final String IMPL = "com.acme.service.UserServiceImpl";
    private static final String REPO = "com.acme.repo.UserRepository";
    private static final String USER = "com.acme.domain.User";
    private static final String DTO = "com.acme.domain.UserDto";
    private static final String TEST = "com.acme.service.UserServiceImplTest";

    private static ExecutorService pool;
    private static AnalysisFacts facts;

    @BeforeAll
    static void analyze() throws Exception {
        pool = Executors.newFixedThreadPool(4);
        Path root = Path.of(ProjectParserTest.class.getResource("/sample").toURI());
        try (var files = Files.walk(root)) {
            var paths = files.map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .filter(SourcePaths::isJava)
                    .toList();
            facts = new ProjectParser().analyze(root, paths, pool);
        }
    }

    @AfterAll
    static void shutdown() {
        pool.shutdownNow();
    }

    @Test
    void parsesAllFiles() {
        assertThat(facts.files()).hasSize(7).allSatisfy(f -> assertThat(f.parseError()).isNull());
    }

    @Test
    void detectsStereotypes() {
        assertThat(entity(CTRL).stereotype()).isEqualTo(Stereotype.CONTROLLER);
        assertThat(entity(IMPL).stereotype()).isEqualTo(Stereotype.SERVICE);
        assertThat(entity(REPO).stereotype()).isEqualTo(Stereotype.REPOSITORY);
        assertThat(entity(USER).stereotype()).isEqualTo(Stereotype.ENTITY);
        assertThat(entity(TEST).stereotype()).isEqualTo(Stereotype.TEST);
    }

    @Test
    void detectsEndpoints() {
        var get = entity("endpoint:GET /api/users/{id}@" + CTRL + "#get(Long)");
        assertThat(get.kind()).isEqualTo(EntityKind.ENDPOINT);
        assertThat(get.name()).isEqualTo("GET /api/users/{id}");
        assertThat(edge(get.qualifiedName(), CTRL + "#get(Long)", ROUTES_TO)).isTrue();
        assertThat(entity("endpoint:POST /api/users@" + CTRL + "#create(UserDto)").httpMethod()).isEqualTo("POST");
    }

    @Test
    void resolvesCallsThroughFieldAndParamTypes() {
        assertThat(edge(CTRL + "#get(Long)", SVC + "#find(Long)", CALLS)).isTrue();
        assertThat(edge(IMPL + "#create(UserDto)", REPO + "#findByName(String)", CALLS)).isTrue();
        assertThat(edge(IMPL + "#create(UserDto)", DTO + "#name()", CALLS)).isTrue();
        assertThat(edge(IMPL + "#validate(User)", USER + "#getName()", CALLS)).isTrue();
    }

    @Test
    void resolvesUnqualifiedCallsAndConstructors() {
        assertThat(edge(IMPL + "#create(UserDto)", IMPL + "#validate(User)", CALLS)).isTrue();
        assertThat(edge(IMPL + "#create(UserDto)", USER, CREATES)).isTrue();
        assertThat(edge(IMPL + "#create(UserDto)", USER + "#<init>(String)", CALLS)).isTrue();
    }

    @Test
    void resolvesHierarchy() {
        assertThat(edge(IMPL, SVC, IMPLEMENTS)).isTrue();
        assertThat(edge(IMPL + "#find(Long)", SVC + "#find(Long)", OVERRIDES)).isTrue();
        assertThat(edge(REPO, USER, USES_TYPE)).isTrue();
    }

    @Test
    void resolvesImportsAndTypeUses() {
        assertThat(edge(CTRL, SVC, IMPORTS)).isTrue();
        assertThat(edge(CTRL + "#service", SVC, USES_TYPE)).isTrue();
        assertThat(edge(CTRL + "#<init>(UserService)", SVC, USES_TYPE)).isTrue();
    }

    @Test
    void resolvesTestCallsViaNewAndVar() {
        assertThat(edge(TEST + "#findsUser()", IMPL + "#find(Long)", CALLS)).isTrue();
        assertThat(edge(TEST + "#createsUser()", IMPL + "#create(UserDto)", CALLS)).isTrue();
        assertThat(edge(TEST + "#createsUser()", DTO, CREATES)).isTrue();
    }

    @Test
    void ignoresExternalTypes() {
        assertThat(facts.edges()).noneMatch(e -> e.targetQn().startsWith("java.") || e.targetQn().contains("springframework"));
        assertThat(facts.edges()).noneMatch(e -> e.sourceQn().equals(e.targetQn()));
    }

    @Test
    void computesComplexity() {
        assertThat(entity(IMPL + "#validate(User)").complexity()).isEqualTo(5);
        assertThat(entity(IMPL + "#create(UserDto)").complexity()).isEqualTo(2);
    }

    private static EntityFact entity(String qn) {
        return facts.entities().stream()
                .filter(e -> e.qualifiedName().equals(qn))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing entity " + qn));
    }

    private static boolean edge(String source, String target, EdgeType type) {
        return facts.edges().stream()
                .anyMatch(e -> e.sourceQn().equals(source) && e.targetQn().equals(target) && e.type() == type);
    }
}
