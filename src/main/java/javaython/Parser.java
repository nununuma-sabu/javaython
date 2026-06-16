package javaython;

import java.util.ArrayList;
import java.util.List;

class Parser {
    private final List<Token> tokens;
    private int current;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            // トップレベルの余分な空行は読み飛ばす。
            skipNewlines();
            if (!isAtEnd()) {
                statements.add(statement());
            }
        }
        return statements;
    }

    private Stmt statement() {
        // 文の先頭トークンを見て、どの構文として読むかを決める。
        if (match(TokenType.IF)) {
            return ifStatement();
        }
        if (match(TokenType.WHILE)) {
            return whileStatement();
        }
        if (match(TokenType.FOR)) {
            return forStatement();
        }
        if (check(TokenType.IDENTIFIER) && checkNext(TokenType.EQUAL)) {
            return assignment();
        }
        if (check(TokenType.IDENTIFIER) && peek().lexeme().equals("print") && checkNext(TokenType.LEFT_PAREN)) {
            // MVPではprintを通常の関数ではなく、専用の文として扱う。
            return printStatement();
        }
        Stmt stmt = new Stmt.Expression(expression());
        consume(TokenType.NEWLINE, "Expected a newline after expression.");
        return stmt;
    }

    private Stmt assignment() {
        Token name = consume(TokenType.IDENTIFIER, "Expected variable name.");
        consume(TokenType.EQUAL, "Expected '=' after variable name.");
        Expr value = expression();
        consume(TokenType.NEWLINE, "Expected a newline after assignment.");
        return new Stmt.Assign(name, value);
    }

    private Stmt printStatement() {
        advance();
        consume(TokenType.LEFT_PAREN, "Expected '(' after print.");
        List<Expr> values = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                values.add(expression());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RIGHT_PAREN, "Expected ')' after print arguments.");
        consume(TokenType.NEWLINE, "Expected a newline after print call.");
        return new Stmt.Print(values);
    }

    private Stmt ifStatement() {
        List<Stmt.Branch> branches = new ArrayList<>();
        Expr condition = expression();
        branches.add(new Stmt.Branch(condition, blockAfterColon()));

        // ifとelifは同じBranchとして保持し、実行時に上から順に評価する。
        while (match(TokenType.ELIF)) {
            Expr elifCondition = expression();
            branches.add(new Stmt.Branch(elifCondition, blockAfterColon()));
        }

        List<Stmt> elseBranch = List.of();
        if (match(TokenType.ELSE)) {
            elseBranch = blockAfterColon();
        }
        return new Stmt.If(branches, elseBranch);
    }

    private Stmt whileStatement() {
        Expr condition = expression();
        return new Stmt.While(condition, blockAfterColon());
    }

    private Stmt forStatement() {
        Token name = consume(TokenType.IDENTIFIER, "Expected loop variable name after 'for'.");
        consume(TokenType.IN, "Expected 'in' after loop variable.");
        Expr iterable = expression();
        return new Stmt.For(name, iterable, blockAfterColon());
    }

    private List<Stmt> blockAfterColon() {
        // Python風に「: 改行 INDENT 文... DEDENT」を1つのブロックとして読む。
        consume(TokenType.COLON, "Expected ':' before block.");
        consume(TokenType.NEWLINE, "Expected a newline before block.");
        consume(TokenType.INDENT, "Expected an indented block.");
        List<Stmt> statements = new ArrayList<>();
        while (!check(TokenType.DEDENT) && !isAtEnd()) {
            skipNewlines();
            if (!check(TokenType.DEDENT) && !isAtEnd()) {
                statements.add(statement());
            }
        }
        consume(TokenType.DEDENT, "Expected end of indented block.");
        return statements;
    }

    private Expr expression() {
        // 優先順位の一番低い演算子から順に下位のパーサへ委譲する。
        return or();
    }

    private Expr or() {
        Expr expr = and();
        while (match(TokenType.OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr and() {
        Expr expr = equality();
        while (match(TokenType.AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();
        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr term() {
        Expr expr = factor();
        while (match(TokenType.MINUS, TokenType.PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary();
        while (match(TokenType.SLASH, TokenType.STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr unary() {
        if (match(TokenType.NOT, TokenType.MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return call();
    }

    private Expr call() {
        Expr expr = primary();
        if (expr instanceof Expr.Variable variable && match(TokenType.LEFT_PAREN)) {
            // 現時点では「名前(...)」形式の呼び出しだけ対応する。
            List<Expr> arguments = new ArrayList<>();
            if (!check(TokenType.RIGHT_PAREN)) {
                do {
                    arguments.add(expression());
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RIGHT_PAREN, "Expected ')' after arguments.");
            return new Expr.Call(variable.name(), arguments);
        }
        return expr;
    }

    private Expr primary() {
        // リテラル、変数、括弧式など、式の最小単位を読む。
        if (match(TokenType.FALSE)) {
            return new Expr.Literal(new PyBool(false));
        }
        if (match(TokenType.TRUE)) {
            return new Expr.Literal(new PyBool(true));
        }
        if (match(TokenType.NUMBER)) {
            Object literal = previous().literal();
            if (literal instanceof Long value) {
                return new Expr.Literal(new PyInt(value));
            }
            return new Expr.Literal(new PyFloat((Double) literal));
        }
        if (match(TokenType.STRING)) {
            return new Expr.Literal(new PyStr((String) previous().literal()));
        }
        if (match(TokenType.IDENTIFIER)) {
            return new Expr.Variable(previous());
        }
        if (match(TokenType.LEFT_PAREN)) {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expected ')' after expression.");
            return new Expr.Grouping(expr);
        }
        throw error(peek(), "Expected expression.");
    }

    private void skipNewlines() {
        while (match(TokenType.NEWLINE)) {
        }
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type() == type;
    }

    private boolean checkNext(TokenType type) {
        return current + 1 < tokens.size() && tokens.get(current + 1).type() == type;
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private JavaythonException error(Token token, String message) {
        return new JavaythonException("[line " + token.line() + ", column " + token.column() + "] " + message);
    }
}
