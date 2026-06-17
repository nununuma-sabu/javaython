package javaython;

import java.math.BigInteger;
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
        if (check(TokenType.IDENTIFIER) && checkNext(TokenType.COMMA)) {
            return unpackAssignment();
        }
        if (check(TokenType.IDENTIFIER) && isAugmentedAssignment(checkNext())) {
            return augmentedAssignment();
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

    private Stmt unpackAssignment() {
        List<Token> names = new ArrayList<>();
        names.add(consume(TokenType.IDENTIFIER, "Expected variable name."));
        while (match(TokenType.COMMA)) {
            names.add(consume(TokenType.IDENTIFIER, "Expected variable name after ','."));
        }
        consume(TokenType.EQUAL, "Expected '=' after assignment targets.");
        Expr value = expression();
        consume(TokenType.NEWLINE, "Expected a newline after assignment.");
        return new Stmt.UnpackAssign(names, value);
    }

    private Stmt augmentedAssignment() {
        Token name = consume(TokenType.IDENTIFIER, "Expected variable name.");
        Token operator = advance();
        Expr value = expression();
        consume(TokenType.NEWLINE, "Expected a newline after augmented assignment.");
        return new Stmt.AugAssign(name, operator, value);
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
        Expr expr = not();
        while (match(TokenType.AND)) {
            Token operator = previous();
            Expr right = not();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr not() {
        // Pythonに近く、notはandより強く、比較演算より弱い優先順位にする。
        if (match(TokenType.NOT)) {
            Token operator = previous();
            Expr right = not();
            return new Expr.Unary(operator, right);
        }
        return equality();
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
        Expr expr = bitwiseOr();
        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token operator = previous();
            Expr right = bitwiseOr();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr bitwiseOr() {
        // ビット演算はPythonの優先順位に合わせて |, ^, &, shift の順に分けて読む。
        Expr expr = bitwiseXor();
        while (match(TokenType.PIPE)) {
            Token operator = previous();
            Expr right = bitwiseXor();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr bitwiseXor() {
        Expr expr = bitwiseAnd();
        while (match(TokenType.CARET)) {
            Token operator = previous();
            Expr right = bitwiseAnd();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr bitwiseAnd() {
        Expr expr = shift();
        while (match(TokenType.AMPERSAND)) {
            Token operator = previous();
            Expr right = shift();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr shift() {
        // シフト演算は加減算より弱く、ビットANDより強い。
        Expr expr = term();
        while (match(TokenType.LEFT_SHIFT, TokenType.RIGHT_SHIFT)) {
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
        while (match(TokenType.SLASH, TokenType.FLOOR_SLASH, TokenType.PERCENT, TokenType.STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr unary() {
        if (match(TokenType.PLUS, TokenType.MINUS, TokenType.TILDE)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return call();
    }

    private Expr call() {
        Expr expr = primary();
        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                if (!(expr instanceof Expr.Variable variable)) {
                    throw error(previous(), "Only named functions can be called.");
                }
                expr = new Expr.Call(variable.name(), finishArguments(TokenType.RIGHT_PAREN, "Expected ')' after arguments."));
            } else if (match(TokenType.DOT)) {
                Token method = consume(TokenType.IDENTIFIER, "Expected method name after '.'.");
                consume(TokenType.LEFT_PAREN, "Expected '(' after method name.");
                expr = new Expr.MethodCall(expr, method, finishArguments(TokenType.RIGHT_PAREN, "Expected ')' after method arguments."));
            } else if (match(TokenType.LEFT_BRACKET)) {
                Expr index = expression();
                Token bracket = consume(TokenType.RIGHT_BRACKET, "Expected ']' after index.");
                expr = new Expr.Index(expr, index, bracket);
            } else {
                break;
            }
        }
        return expr;
    }

    private List<Expr> finishArguments(TokenType terminator, String message) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(terminator)) {
            do {
                arguments.add(expression());
            } while (match(TokenType.COMMA));
        }
        consume(terminator, message);
        return arguments;
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
            if (literal instanceof BigInteger value) {
                return new Expr.Literal(new PyInt(value));
            }
            return new Expr.Literal(new PyFloat((Double) literal));
        }
        if (match(TokenType.STRING)) {
            return new Expr.Literal(new PyStr((String) previous().literal()));
        }
        if (match(TokenType.LEFT_BRACKET)) {
            List<Expr> elements = new ArrayList<>();
            if (!check(TokenType.RIGHT_BRACKET)) {
                Expr first = expression();
                if (match(TokenType.FOR)) {
                    Token variable = consume(TokenType.IDENTIFIER, "Expected loop variable name in list comprehension.");
                    consume(TokenType.IN, "Expected 'in' after loop variable in list comprehension.");
                    Expr iterable = expression();
                    Expr condition = null;
                    if (match(TokenType.IF)) {
                        condition = expression();
                    }
                    consume(TokenType.RIGHT_BRACKET, "Expected ']' after list comprehension.");
                    return new Expr.ListComprehension(first, variable, iterable, condition);
                }

                elements.add(first);
                while (match(TokenType.COMMA)) {
                    if (check(TokenType.RIGHT_BRACKET)) {
                        break;
                    }
                    elements.add(expression());
                }
            }
            consume(TokenType.RIGHT_BRACKET, "Expected ']' after list literal.");
            return new Expr.ListLiteral(elements);
        }
        if (match(TokenType.LEFT_BRACE)) {
            return dictLiteral();
        }
        if (match(TokenType.IDENTIFIER)) {
            return new Expr.Variable(previous());
        }
        if (match(TokenType.LEFT_PAREN)) {
            return parenthesized();
        }
        throw error(peek(), "Expected expression.");
    }

    private Expr dictLiteral() {
        List<Expr.DictEntry> entries = new ArrayList<>();
        if (!check(TokenType.RIGHT_BRACE)) {
            do {
                Expr key = expression();
                consume(TokenType.COLON, "Expected ':' between dict key and value.");
                Expr value = expression();
                entries.add(new Expr.DictEntry(key, value));
            } while (match(TokenType.COMMA) && !check(TokenType.RIGHT_BRACE));
        }
        consume(TokenType.RIGHT_BRACE, "Expected '}' after dict literal.");
        return new Expr.DictLiteral(entries);
    }

    private Expr parenthesized() {
        if (match(TokenType.RIGHT_PAREN)) {
            return new Expr.TupleLiteral(List.of());
        }

        Expr first = expression();
        if (!match(TokenType.COMMA)) {
            consume(TokenType.RIGHT_PAREN, "Expected ')' after expression.");
            return new Expr.Grouping(first);
        }

        List<Expr> elements = new ArrayList<>();
        elements.add(first);
        while (!check(TokenType.RIGHT_PAREN)) {
            elements.add(expression());
            if (!match(TokenType.COMMA)) {
                break;
            }
        }
        consume(TokenType.RIGHT_PAREN, "Expected ')' after tuple literal.");
        return new Expr.TupleLiteral(elements);
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

    private TokenType checkNext() {
        return current + 1 < tokens.size() ? tokens.get(current + 1).type() : TokenType.EOF;
    }

    private boolean isAugmentedAssignment(TokenType type) {
        return switch (type) {
            case PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL, FLOOR_SLASH_EQUAL, PERCENT_EQUAL,
                    AMPERSAND_EQUAL, PIPE_EQUAL, CARET_EQUAL, LEFT_SHIFT_EQUAL, RIGHT_SHIFT_EQUAL -> true;
            default -> false;
        };
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
