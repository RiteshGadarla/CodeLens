package com.codelens.graph;

import com.codelens.domain.EdgeType;
import com.codelens.domain.EntityKind;
import com.codelens.graph.GraphTraversal.Direction;
import com.codelens.graph.GraphTraversal.Hop;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.codelens.domain.EdgeType.*;
import static com.codelens.domain.EntityKind.*;
import static org.assertj.core.api.Assertions.assertThat;

class GraphTraversalTest {

    // A#m calls I#x, Impl#x overrides I#x, B#n calls A#m and I#x, endpoint routes to B#n
    private final DependencyGraph g = DependencyGraph.of(List.of(
            node(1, "a.A", CLASS, null), node(2, "a.A#m()", METHOD, 1L),
            node(3, "a.B", CLASS, null), node(4, "a.B#n()", METHOD, 3L),
            node(5, "a.I", INTERFACE, null), node(6, "a.I#x()", METHOD, 5L),
            node(7, "a.Impl", CLASS, null), node(8, "a.Impl#x()", METHOD, 7L),
            node(9, "GET /x", ENDPOINT, 3L)
    ), List.of(
            edge(2, 6, CALLS), edge(8, 6, OVERRIDES), edge(4, 2, CALLS), edge(4, 6, CALLS),
            edge(9, 4, ROUTES_TO), edge(7, 5, IMPLEMENTS)
    ));

    @Test
    void upstreamFollowsDispatchToCallersOfInterface() {
        var hops = GraphTraversal.bfs(g, List.of(idx(8)), Direction.UPSTREAM, 10, true);
        var depths = depthsById(hops);
        assertThat(depths).containsEntry(6L, 0).containsEntry(2L, 1).containsEntry(4L, 1).containsEntry(9L, 2);
        assertThat(hops.get(idx(6)).dispatch()).isTrue();
    }

    @Test
    void upstreamWithoutDispatchStopsAtImplementation() {
        var hops = GraphTraversal.bfs(g, List.of(idx(8)), Direction.UPSTREAM, 10, false);
        assertThat(hops).hasSize(1);
    }

    @Test
    void siblingImplementationsAreNotAffected() {
        var withSibling = DependencyGraph.of(List.of(
                node(1, "a.I#x()", METHOD, null), node(2, "a.A#x()", METHOD, null),
                node(3, "a.B#x()", METHOD, null), node(4, "a.C#call()", METHOD, null)
        ), List.of(edge(2, 1, OVERRIDES), edge(3, 1, OVERRIDES), edge(4, 1, CALLS)));
        var hops = GraphTraversal.bfs(withSibling, List.of(withSibling.indexOf(2)), Direction.UPSTREAM, 10, true);
        assertThat(depthsById(hops, withSibling)).containsOnlyKeys(2L, 1L, 4L);
    }

    @Test
    void downstreamDepthIsShortestHopCount() {
        var depths = depthsById(GraphTraversal.bfs(g, List.of(idx(9)), Direction.DOWNSTREAM, 10, false));
        assertThat(depths).containsEntry(4L, 1).containsEntry(2L, 2).containsEntry(6L, 2);
    }

    @Test
    void depthLimitIsRespected() {
        var depths = depthsById(GraphTraversal.bfs(g, List.of(idx(9)), Direction.DOWNSTREAM, 1, false));
        assertThat(depths).containsOnlyKeys(9L, 4L);
    }

    @Test
    void findsShortestPath() {
        var path = GraphTraversal.shortestPath(g, idx(9), idx(6), Direction.DOWNSTREAM, 10).orElseThrow();
        assertThat(path).extracting(h -> g.node(h.node()).id()).containsExactly(9L, 4L, 6L);
        assertThat(path.get(2).edge()).isEqualTo(CALLS);
        assertThat(GraphTraversal.shortestPath(g, idx(6), idx(9), Direction.DOWNSTREAM, 10)).isEmpty();
    }

    @Test
    void enumeratesBoundedPaths() {
        var paths = GraphTraversal.allPaths(g, idx(9), idx(6), Direction.DOWNSTREAM, 10, 10);
        assertThat(paths).hasSize(2);
        assertThat(GraphTraversal.allPaths(g, idx(9), idx(6), Direction.DOWNSTREAM, 10, 1)).hasSize(1);
        assertThat(GraphTraversal.allPaths(g, idx(9), idx(6), Direction.DOWNSTREAM, 2, 10)).hasSize(1);
    }

    @Test
    void rollsMemberEdgesUpToTypes() {
        var types = GraphRollup.types(g);
        assertThat(types.size()).isEqualTo(4);
        assertThat(types.edges()).extracting(e -> e.sourceId() + "->" + e.targetId() + ":" + e.type())
                .containsExactlyInAnyOrder("1->5:CALLS", "3->1:CALLS", "3->5:CALLS", "7->5:OVERRIDES", "7->5:IMPLEMENTS");
    }

    @Test
    void resolvesOwnersAndLabels() {
        assertThat(g.ownerType(idx(9))).isEqualTo(idx(3));
        assertThat(g.node(idx(8)).label()).isEqualTo("Impl#x()");
        assertThat(g.node(idx(9)).label()).isEqualTo("GET /x");
        assertThat(GraphNode.labelOf("com.acme.Scope.None", CLASS, "None")).isEqualTo("Scope.None");
        assertThat(GraphNode.labelOf("com.acme.Svc#<init>(Repo,int)", CONSTRUCTOR, "Svc")).isEqualTo("Svc(Repo,int)");
        assertThat(g.members(idx(3))).containsExactlyInAnyOrder(idx(4), idx(9));
    }

    private int idx(long id) {
        return g.indexOf(id);
    }

    private Map<Long, Integer> depthsById(Map<Integer, Hop> hops) {
        return depthsById(hops, g);
    }

    private static Map<Long, Integer> depthsById(Map<Integer, Hop> hops, DependencyGraph graph) {
        return hops.values().stream().collect(Collectors.toMap(h -> graph.node(h.node()).id(), Hop::depth));
    }

    static GraphNode node(long id, String qn, EntityKind kind, Long parent) {
        return new GraphNode(id, qn, qn, kind, parent, null, "F.java", "root", "a", null, null, null, 1,
                kind == INTERFACE);
    }

    static GraphEdge edge(long s, long t, EdgeType type) {
        return new GraphEdge(s, t, type, 1);
    }
}
