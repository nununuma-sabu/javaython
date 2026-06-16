package javaython;

import java.util.List;

sealed interface Expr permits Expr.Binary, Expr.Call, Expr.Grouping, Expr.Literal, Expr.Unary, Expr.Variable {
    // 二項演算: left + right, left == right など。
    record Binary(Expr left, Token operator, Expr right) implements Expr {
    }

    // 組み込み関数呼び出し: input(), int(x), range(n) など。
    record Call(Token callee, List<Expr> arguments) implements Expr {
    }

    // 括弧で囲まれた式。
    record Grouping(Expr expression) implements Expr {
    }

    // 数値、文字列、真偽値などのリテラル。
    record Literal(PyValue value) implements Expr {
    }

    // 単項演算: -x, not x など。
    record Unary(Token operator, Expr right) implements Expr {
    }

    // 変数参照。
    record Variable(Token name) implements Expr {
    }
}
