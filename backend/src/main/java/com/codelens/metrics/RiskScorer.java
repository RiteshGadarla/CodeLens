package com.codelens.metrics;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

// weighted, log-normalised against project maxima; 0..100
public final class RiskScorer {

    public static final double W_DEPENDENTS = 0.30;
    public static final double W_DEPTH = 0.15;
    public static final double W_COUPLING = 0.20;
    public static final double W_COMPLEXITY = 0.20;
    public static final double W_EXPOSED = 0.15;

    public record Scale(int dependents, int depth, int coupling, int complexity) {

        public static Scale of(Collection<NodeMetrics> metrics) {
            int dep = 0, depth = 0, coupling = 0, cx = 0;
            for (NodeMetrics m : metrics) {
                dep = Math.max(dep, m.dependents());
                depth = Math.max(depth, m.depth());
                coupling = Math.max(coupling, m.fanIn() + m.fanOut());
                cx = Math.max(cx, m.complexity());
            }
            return new Scale(dep, depth, coupling, cx);
        }
    }

    public record Breakdown(double score, String level, Map<String, Double> factors) {
    }

    private RiskScorer() {
    }

    public static Breakdown score(int dependents, int depth, int coupling, int complexity, boolean exposed, Scale s) {
        var f = new LinkedHashMap<String, Double>();
        f.put("dependents", norm(dependents, s.dependents()));
        f.put("depth", norm(depth, s.depth()));
        f.put("coupling", norm(coupling, s.coupling()));
        f.put("complexity", norm(complexity, s.complexity()));
        f.put("exposed", exposed ? 1.0 : 0.0);

        double raw = W_DEPENDENTS * f.get("dependents")
                + W_DEPTH * f.get("depth")
                + W_COUPLING * f.get("coupling")
                + W_COMPLEXITY * f.get("complexity")
                + W_EXPOSED * f.get("exposed");
        double score = Math.round(Math.min(1.0, raw) * 1000) / 10.0;
        return new Breakdown(score, level(score), f);
    }

    public static String level(double score) {
        return score >= 60 ? "HIGH" : score >= 30 ? "MEDIUM" : "LOW";
    }

    private static double norm(int value, int max) {
        if (max <= 0 || value <= 0) return 0.0;
        double v = Math.min(1.0, Math.log1p(value) / Math.log1p(max));
        return Math.round(v * 1000) / 1000.0;
    }
}
