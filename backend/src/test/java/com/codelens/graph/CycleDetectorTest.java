package com.codelens.graph;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.codelens.domain.EdgeType.USES_TYPE;
import static com.codelens.domain.EntityKind.CLASS;
import static com.codelens.graph.GraphTraversalTest.edge;
import static com.codelens.graph.GraphTraversalTest.node;
import static org.assertj.core.api.Assertions.assertThat;

class CycleDetectorTest {

    // 1 -> 2 -> 3 -> 1, 4 -> 1, 3 -> 5
    private final DependencyGraph g = DependencyGraph.of(
            List.of(node(1, "a.A", CLASS, null), node(2, "a.B", CLASS, null), node(3, "a.C", CLASS, null),
                    node(4, "a.D", CLASS, null), node(5, "a.E", CLASS, null)),
            List.of(edge(1, 2, USES_TYPE), edge(2, 3, USES_TYPE), edge(3, 1, USES_TYPE),
                    edge(4, 1, USES_TYPE), edge(3, 5, USES_TYPE)));

    @Test
    void findsStronglyConnectedCycle() {
        var cycles = CycleDetector.cycles(g);
        assertThat(cycles).hasSize(1);
        assertThat(Arrays.stream(cycles.get(0)).mapToObj(i -> g.node(i).id()).toList())
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void computesDepthOverCondensation() {
        int[] depth = CycleDetector.depth(g, CycleDetector.components(g));
        assertThat(depth[g.indexOf(5)]).isZero();
        assertThat(depth[g.indexOf(1)]).isEqualTo(1);
        assertThat(depth[g.indexOf(3)]).isEqualTo(1);
        assertThat(depth[g.indexOf(4)]).isEqualTo(2);
    }

    @Test
    void dagHasNoCycles() {
        var dag = DependencyGraph.of(
                List.of(node(1, "a.A", CLASS, null), node(2, "a.B", CLASS, null)),
                List.of(edge(1, 2, USES_TYPE)));
        assertThat(CycleDetector.cycles(dag)).isEmpty();
    }

    @Test
    void nestedTypesDoNotFormCyclesWithTheirOuterType() {
        var g = DependencyGraph.of(List.of(
                node(1, "a.Scope", com.codelens.domain.EntityKind.INTERFACE, null),
                node(2, "a.Scope.None", CLASS, 1L),
                node(3, "a.Scope#NONE", com.codelens.domain.EntityKind.FIELD, 1L)
        ), List.of(edge(2, 1, com.codelens.domain.EdgeType.IMPLEMENTS), edge(3, 2, USES_TYPE)));

        var types = GraphRollup.types(g);
        assertThat(types.edgeCount()).isZero();
        assertThat(CycleDetector.cycles(types)).isEmpty();
    }
}
