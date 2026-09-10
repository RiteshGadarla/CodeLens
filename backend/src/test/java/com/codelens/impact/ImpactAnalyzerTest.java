package com.codelens.impact;

import com.codelens.common.NotFoundException;
import com.codelens.domain.EdgeType;
import com.codelens.graph.DependencyGraph;
import com.codelens.graph.EntityRef;
import com.codelens.graph.PathStep;
import com.codelens.metrics.MetricsCalculator;
import com.codelens.metrics.RiskScorer;
import com.codelens.support.SampleProject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.codelens.support.SampleProject.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImpactAnalyzerTest {

    private static final String FIND_BY_NAME = REPO + "#findByName(String)";

    private static ExecutorService pool;
    private static DependencyGraph g;
    private static RiskScorer.Scale scale;

    private final ImpactAnalyzer analyzer = new ImpactAnalyzer();

    @BeforeAll
    static void setup() {
        pool = Executors.newFixedThreadPool(4);
        g = SampleProject.graph();
        scale = new MetricsCalculator().compute(g, pool).scale();
    }

    @AfterAll
    static void shutdown() {
        pool.shutdownNow();
    }

    @Test
    void repositoryChangeReachesEndpointThroughInterfaceDispatch() {
        var r = analyzer.analyze(g, id(FIND_BY_NAME), ImpactOptions.defaults(), scale);

        assertThat(labels(r.affected())).containsExactly(
                "UserServiceImpl#create(UserDto)", "UserController#create(UserDto)", "POST /api/users");
        assertThat(r.directDependents()).isEqualTo(1);
        assertThat(r.transitiveDependents()).isEqualTo(3);
        assertThat(r.maxDepth()).isEqualTo(3);
        assertThat(labels(r.endpoints())).containsExactly("POST /api/users");
        assertThat(r.affectedTypes()).containsOnlyKeys("CONTROLLER", "SERVICE");
        assertThat(r.tests()).extracting(EntityRef::label).containsExactly("UserServiceImplTest");
        assertThat(r.modules()).containsExactly("root");
        assertThat(r.risk().factors()).containsEntry("exposed", 1.0);
        assertThat(r.risk().score()).isBetween(0.0, 100.0);
    }

    @Test
    void pathShowsDispatchStep() {
        var r = analyzer.analyze(g, id(FIND_BY_NAME), ImpactOptions.defaults(), scale);
        var path = r.endpoints().get(0).path();

        assertThat(path).extracting(PathStep::label).containsExactly(
                "UserRepository#findByName(String)", "UserServiceImpl#create(UserDto)",
                "UserService#create(UserDto)", "UserController#create(UserDto)", "POST /api/users");
        assertThat(path.get(2).dispatch()).isTrue();
        assertThat(path.get(4).edge()).isEqualTo(EdgeType.ROUTES_TO);
    }

    @Test
    void includeTestsCountsTestCallers() {
        var r = analyzer.analyze(g, id(FIND_BY_NAME), new ImpactOptions(10, true, 500), scale);
        assertThat(labels(r.affected())).contains("UserServiceImplTest#createsUser()");
        assertThat(r.transitiveDependents()).isEqualTo(4);
    }

    @Test
    void depthLimitStopsTraversal() {
        var r = analyzer.analyze(g, id(FIND_BY_NAME), new ImpactOptions(1, false, 500), scale);
        assertThat(labels(r.affected())).containsExactly("UserServiceImpl#create(UserDto)");
    }

    @Test
    void classImpactSeedsAllMembers() {
        var r = analyzer.analyze(g, id(USER), ImpactOptions.defaults(), scale);
        assertThat(r.seeds()).isEqualTo(6);
        assertThat(labels(r.endpoints())).containsExactlyInAnyOrder("GET /api/users/{id}", "POST /api/users");
        assertThat(r.affectedTypes().get("REPOSITORY")).extracting(EntityRef::label).contains("UserRepository");
    }

    @Test
    void fileImpactUsesEveryEntityInFile() {
        var r = analyzer.analyzeFile(g, "src/main/java/com/acme/domain/UserDto.java", ImpactOptions.defaults(), scale);
        assertThat(r.target().label()).isEqualTo("UserDto");
        assertThat(r.seeds()).isEqualTo(3);
        assertThat(r.endpoints()).isNotEmpty();
    }

    @Test
    void truncatesLargeResults() {
        var r = analyzer.analyze(g, id(USER), new ImpactOptions(10, false, 2), scale);
        assertThat(r.affected()).hasSize(2);
        assertThat(r.truncated()).isTrue();
        assertThat(r.transitiveDependents()).isGreaterThan(2);
    }

    @Test
    void unknownEntityFails() {
        assertThatThrownBy(() -> analyzer.analyze(g, 999_999L, ImpactOptions.defaults(), scale))
                .isInstanceOf(NotFoundException.class);
    }

    private static List<String> labels(List<ImpactResult.Affected> list) {
        return list.stream().map(a -> a.entity().label()).toList();
    }
}
