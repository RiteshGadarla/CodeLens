package com.codelens.parser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;

// McCabe: 1 + decision points
final class ComplexityCalculator {

    private ComplexityCalculator() {
    }

    static int of(Node body) {
        if (body == null) return 1;
        int[] c = {1};
        body.walk(n -> {
            if (n instanceof IfStmt || n instanceof ForStmt || n instanceof ForEachStmt
                    || n instanceof WhileStmt || n instanceof DoStmt
                    || n instanceof CatchClause || n instanceof ConditionalExpr) {
                c[0]++;
            } else if (n instanceof SwitchEntry e) {
                c[0] += e.getLabels().size();
            } else if (n instanceof BinaryExpr b
                    && (b.getOperator() == BinaryExpr.Operator.AND || b.getOperator() == BinaryExpr.Operator.OR)) {
                c[0]++;
            }
        });
        return c[0];
    }
}
