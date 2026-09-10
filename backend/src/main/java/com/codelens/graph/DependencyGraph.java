package com.codelens.graph;

import com.codelens.domain.EdgeType;
import com.codelens.domain.Stereotype;

import java.util.*;

// immutable, index-based adjacency
public final class DependencyGraph {

    public record Adj(int node, EdgeType type, int weight) {
    }

    private final List<GraphNode> nodes;
    private final Map<Long, Integer> index;
    private final Adj[][] out;
    private final Adj[][] in;
    private final int[] parent;
    private final int[][] members;
    private final int edgeCount;

    private DependencyGraph(List<GraphNode> nodes, Map<Long, Integer> index, Adj[][] out, Adj[][] in,
                            int[] parent, int[][] members, int edgeCount) {
        this.nodes = nodes;
        this.index = index;
        this.out = out;
        this.in = in;
        this.parent = parent;
        this.members = members;
        this.edgeCount = edgeCount;
    }

    public static DependencyGraph of(Collection<GraphNode> nodeList, Collection<GraphEdge> edgeList) {
        var nodes = List.copyOf(nodeList);
        int n = nodes.size();
        var index = new HashMap<Long, Integer>(n * 2);
        for (int i = 0; i < n; i++) index.put(nodes.get(i).id(), i);

        List<List<Adj>> out = lists(n);
        List<List<Adj>> in = lists(n);
        int count = 0;
        for (GraphEdge e : edgeList) {
            Integer s = index.get(e.sourceId());
            Integer t = index.get(e.targetId());
            if (s == null || t == null || s.equals(t)) continue;
            out.get(s).add(new Adj(t, e.type(), e.weight()));
            in.get(t).add(new Adj(s, e.type(), e.weight()));
            count++;
        }

        int[] parent = new int[n];
        List<List<Integer>> members = lists(n);
        for (int i = 0; i < n; i++) {
            Long p = nodes.get(i).parentId();
            Integer pi = p == null ? null : index.get(p);
            parent[i] = pi == null ? -1 : pi;
            if (pi != null) members.get(pi).add(i);
        }

        return new DependencyGraph(nodes, index,
                out.stream().map(l -> l.toArray(Adj[]::new)).toArray(Adj[][]::new),
                in.stream().map(l -> l.toArray(Adj[]::new)).toArray(Adj[][]::new),
                parent,
                members.stream().map(l -> l.stream().mapToInt(Integer::intValue).toArray()).toArray(int[][]::new),
                count);
    }

    public int size() {
        return nodes.size();
    }

    public int edgeCount() {
        return edgeCount;
    }

    public List<GraphNode> nodes() {
        return nodes;
    }

    public GraphNode node(int i) {
        return nodes.get(i);
    }

    public int indexOf(long id) {
        return index.getOrDefault(id, -1);
    }

    public Optional<GraphNode> find(long id) {
        int i = indexOf(id);
        return i < 0 ? Optional.empty() : Optional.of(nodes.get(i));
    }

    // arrays are shared, do not modify
    public Adj[] out(int i) {
        return out[i];
    }

    public Adj[] in(int i) {
        return in[i];
    }

    public int parent(int i) {
        return parent[i];
    }

    public int[] members(int i) {
        return members[i];
    }

    // nearest enclosing type, itself if a type
    public int ownerType(int i) {
        for (int j = i; j >= 0; j = parent[j]) {
            if (nodes.get(j).kind().isType()) return j;
        }
        return -1;
    }

    public boolean isTest(int i) {
        int owner = ownerType(i);
        return owner >= 0 && nodes.get(owner).stereotype() == Stereotype.TEST;
    }

    public List<GraphEdge> edges() {
        var list = new ArrayList<GraphEdge>(edgeCount);
        for (int s = 0; s < out.length; s++) {
            for (Adj a : out[s]) {
                list.add(new GraphEdge(nodes.get(s).id(), nodes.get(a.node()).id(), a.type(), a.weight()));
            }
        }
        return list;
    }

    private static <T> List<List<T>> lists(int n) {
        var l = new ArrayList<List<T>>(n);
        for (int i = 0; i < n; i++) l.add(new ArrayList<>());
        return l;
    }
}
