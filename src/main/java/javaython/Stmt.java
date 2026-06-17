package javaython;

import java.util.List;

sealed interface Stmt permits Stmt.Assign, Stmt.AugAssign, Stmt.Expression, Stmt.For, Stmt.If, Stmt.Print, Stmt.While {
    // 変数代入: name = value
    record Assign(Token name, Expr value) implements Stmt {
    }

    // 複合代入: name += value, name //= value など。
    record AugAssign(Token name, Token operator, Expr value) implements Stmt {
    }

    // 値を捨てる式文。関数呼び出しだけの行などで使う。
    record Expression(Expr expression) implements Stmt {
    }

    // for name in range(n): ...
    record For(Token name, Expr iterable, List<Stmt> body) implements Stmt {
    }

    // if/elif/else。elifはbranchesの2個目以降として保持する。
    record If(List<Branch> branches, List<Stmt> elseBranch) implements Stmt {
    }

    // print専用文。複数引数はスペース区切りで出力する。
    record Print(List<Expr> values) implements Stmt {
    }

    // while condition: ...
    record While(Expr condition, List<Stmt> body) implements Stmt {
    }

    // ifまたはelifの条件と本体。
    record Branch(Expr condition, List<Stmt> body) {
    }
}
