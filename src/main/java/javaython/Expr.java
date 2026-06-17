package javaython;

import java.util.List;

sealed interface Expr permits Expr.Binary, Expr.Call, Expr.Grouping, Expr.Index, Expr.ListComprehension, Expr.ListLiteral,
        Expr.Literal, Expr.MethodCall, Expr.Unary, Expr.Variable {
    // 二項演算: left + right, left == right など。
    record Binary(Expr left, Token operator, Expr right) implements Expr {
    }

    // 組み込み関数呼び出し: input(), int(x), range(n) など。
    record Call(Token callee, List<Expr> arguments) implements Expr {
    }

    // 括弧で囲まれた式。
    record Grouping(Expr expression) implements Expr {
    }

    // 添字参照: values[0] など。
    record Index(Expr receiver, Expr index, Token bracket) implements Expr {
    }

    // リストリテラル: [] や [1, 2, 3]。
    record ListLiteral(List<Expr> elements) implements Expr {
    }

    // リスト内包表記: [value * 2 for value in values if value > 0]。
    record ListComprehension(Expr element, Token variable, Expr iterable, Expr condition) implements Expr {
    }

    // 数値、文字列、真偽値などのリテラル。
    record Literal(PyValue value) implements Expr {
    }

    // メソッド呼び出し: values.append(1) など。
    record MethodCall(Expr receiver, Token method, List<Expr> arguments) implements Expr {
    }

    // 単項演算: -x, not x など。
    record Unary(Token operator, Expr right) implements Expr {
    }

    // 変数参照。
    record Variable(Token name) implements Expr {
    }
}
