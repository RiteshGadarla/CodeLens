package com.codelens.graph;

import com.codelens.graph.DependencyGraph.Adj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CycleDetector {

    private CycleDetector() {
    }

    // iterative Tarjan; components come out in reverse topological order
    public static List<int[]> components(DependencyGraph g) {
        int n = g.size();
        int[] index = new int[n];
        int[] low = new int[n];
        boolean[] onStack = new boolean[n];
        int[] stack = new int[n];
        int[] callNode = new int[n];
        int[] callEdge = new int[n];
        Arrays.fill(index, -1);
        int counter = 0;
        int sp = 0;
        var result = new ArrayList<int[]>();

        for (int root = 0; root < n; root++) {
            if (index[root] != -1) continue;
            int csp = 0;
            callNode[csp] = root;
            callEdge[csp++] = 0;
            index[root] = low[root] = counter++;
            stack[sp++] = root;
            onStack[root] = true;

            while (csp > 0) {
                int v = callNode[csp - 1];
                Adj[] adj = g.out(v);
                if (callEdge[csp - 1] < adj.length) {
                    int w = adj[callEdge[csp - 1]++].node();
                    if (index[w] == -1) {
                        index[w] = low[w] = counter++;
                        stack[sp++] = w;
                        onStack[w] = true;
                        callNode[csp] = w;
                        callEdge[csp++] = 0;
                    } else if (onStack[w]) {
                        low[v] = Math.min(low[v], index[w]);
                    }
                } else {
                    csp--;
                    if (csp > 0) {
                        int p = callNode[csp - 1];
                        low[p] = Math.min(low[p], low[v]);
                    }
                    if (low[v] == index[v]) {
                        int end = sp;
                        int w;
                        do {
                            w = stack[--sp];
                            onStack[w] = false;
                        } while (w != v);
                        result.add(Arrays.copyOfRange(stack, sp, end));
                    }
                }
            }
        }
        return result;
    }

    public static List<int[]> cycles(DependencyGraph g) {
        return components(g).stream().filter(c -> c.length > 1).toList();
    }

    // longest dependency chain below each node, cycles collapsed
    public static int[] depth(DependencyGraph g, List<int[]> components) {
        int[] comp = new int[g.size()];
        for (int c = 0; c < components.size(); c++) {
            for (int v : components.get(c)) comp[v] = c;
        }
        int[] compDepth = new int[components.size()];
        for (int c = 0; c < components.size(); c++) {
            int best = 0;
            for (int v : components.get(c)) {
                for (Adj a : g.out(v)) {
                    int d = comp[a.node()];
                    if (d != c) best = Math.max(best, compDepth[d] + 1);
                }
            }
            compDepth[c] = best;
        }
        int[] depth = new int[g.size()];
        for (int v = 0; v < g.size(); v++) depth[v] = compDepth[comp[v]];
        return depth;
    }
}
