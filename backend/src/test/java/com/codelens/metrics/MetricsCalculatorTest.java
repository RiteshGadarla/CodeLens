package com.codelens.metrics;

import com.codelens.domain.MetricLevel;
import com.codelens.support.SampleProject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.codelens.domain.MetricLevel.MODULE;
import static com.codelens.domain.MetricLevel.PACKAGE;
import static com.codelens.support.SampleProject.*;
import static org.assertj.core.api.Assertions.assertThat;

class MetricsCalculatorTest {

    private static ExecutorService pool;
    private static MetricsResult result;

    @BeforeAll
    static void compute() {
        pool = Executors.newFixedThreadPool(4);
        result = new MetricsCalculator().compute(SampleProject.graph(), pool);
    }

    @AfterAll
    static void shutdown() {
        pool.shutdownNow();
    }

    @Test
    void countsFanInAndTransitiveDependentsWithDispatch() {
        var validate = metric(IMPL + "#validate(User)");
        assertThat(validate.fanIn()).isEqualTo(1);
        // create, UserService#create (dispatch), controller create, POST endpoint, test
        assertThat(validate.dependents()).isEqualTo(5);
        assertThat(validate.exposed()).isTrue();
        assertThat(validate.complexity()).isEqualTo(5);
    }

    @Test
    void interfaceMethodFanInIncludesCallerAndOverride() {
        assertThat(metric(SVC + "#find(Long)").fanIn()).isEqualTo(2);
    }

    @Test
    void computesDependencyDepth() {
        assertThat(metric("endpoint:GET /api/users/{id}@" + CTRL + "#get(Long)").depth()).isEqualTo(3);
        assertThat(metric(USER).depth()).isZero();
    }

    @Test
    void computesPackageCoupling() {
        var domain = module(PACKAGE, "com.acme.domain");
        assertThat(domain.afferent()).isEqualTo(4);
        assertThat(domain.efferent()).isZero();
        assertThat(domain.instability()).isZero();
        assertThat(domain.distance()).isEqualTo(1.0);

        var service = module(PACKAGE, "com.acme.service");
        assertThat(service.entities()).isEqualTo(2);
        assertThat(service.afferent()).isEqualTo(1);
        assertThat(service.efferent()).isEqualTo(3);
        assertThat(service.instability()).isEqualTo(0.75);
        assertThat(service.abstractness()).isEqualTo(0.5);

        assertThat(module(PACKAGE, "com.acme.api").instability()).isEqualTo(1.0);
    }

    @Test
    void computesModuleLevelMetrics() {
        var root = module(MODULE, "root");
        assertThat(root.entities()).isEqualTo(6);
        assertThat(root.abstractness()).isEqualTo(0.333);
    }

    @Test
    void scoresRiskWithinBounds() {
        assertThat(result.nodes()).allSatisfy(m -> assertThat(m.riskScore()).isBetween(0.0, 100.0));
        assertThat(metric(IMPL).riskScore()).isGreaterThan(metric(DTO).riskScore());
        assertThat(result.scale().complexity()).isEqualTo(9);
    }

    @Test
    void sampleHasNoCycles() {
        assertThat(result.cycles()).isEmpty();
    }

    private static NodeMetrics metric(String qn) {
        return result.byEntityId().get(SampleProject.id(qn));
    }

    private static ModuleMetrics module(MetricLevel level, String name) {
        return result.modules().stream()
                .filter(m -> m.level() == level && m.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing module " + name));
    }
}
