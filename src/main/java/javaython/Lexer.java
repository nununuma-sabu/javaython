package javaython;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Lexer {
    // 予約語は識別子として読み取った後に、この表で専用トークンへ変換する。
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("elif", TokenType.ELIF);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("while", TokenType.WHILE);
        KEYWORDS.put("for", TokenType.FOR);
        KEYWORDS.put("in", TokenType.IN);
        KEYWORDS.put("True", TokenType.TRUE);
        KEYWORDS.put("False", TokenType.FALSE);
        KEYWORDS.put("and", TokenType.AND);
        KEYWORDS.put("or", TokenType.OR);
        KEYWORDS.put("not", TokenType.NOT);
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private final Deque<Integer> indentStack = new ArrayDeque<>();
    private int start;
    private int current;
    private int line = 1;
    private int lineStart = 0;
    private boolean atLineStart = true;

    Lexer(String source) {
        this.source = source.replace("\r\n", "\n").replace('\r', '\n');
        indentStack.push(0);
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        // Parser側を単純にするため、ファイル末尾にも改行とDEDENTを補っておく。
        if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).type() != TokenType.NEWLINE) {
            addToken(TokenType.NEWLINE);
        }
        while (indentStack.size() > 1) {
            indentStack.pop();
            tokens.add(new Token(TokenType.DEDENT, "", null, line, 1));
        }
        tokens.add(new Token(TokenType.EOF, "", null, line, 1));
        return tokens;
    }

    private void scanToken() {
        // Python風のブロック構文を扱うため、行頭だけインデント量を確認する。
        if (atLineStart) {
            scanIndentation();
            if (isAtEnd()) {
                return;
            }
            start = current;
        }

        char c = advance();
        switch (c) {
            case '(' -> addToken(TokenType.LEFT_PAREN);
            case ')' -> addToken(TokenType.RIGHT_PAREN);
            case '+' -> addToken(match('=') ? TokenType.PLUS_EQUAL : TokenType.PLUS);
            case '-' -> addToken(match('=') ? TokenType.MINUS_EQUAL : TokenType.MINUS);
            case '*' -> addToken(match('=') ? TokenType.STAR_EQUAL : TokenType.STAR);
            case '/' -> {
                if (match('/')) {
                    addToken(match('=') ? TokenType.FLOOR_SLASH_EQUAL : TokenType.FLOOR_SLASH);
                } else {
                    addToken(match('=') ? TokenType.SLASH_EQUAL : TokenType.SLASH);
                }
            }
            case '%' -> addToken(match('=') ? TokenType.PERCENT_EQUAL : TokenType.PERCENT);
            case '&' -> addToken(match('=') ? TokenType.AMPERSAND_EQUAL : TokenType.AMPERSAND);
            case '|' -> addToken(match('=') ? TokenType.PIPE_EQUAL : TokenType.PIPE);
            case '^' -> addToken(match('=') ? TokenType.CARET_EQUAL : TokenType.CARET);
            case '~' -> addToken(TokenType.TILDE);
            case ':' -> addToken(TokenType.COLON);
            case ',' -> addToken(TokenType.COMMA);
            case '=' -> addToken(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
            case '!' -> {
                if (match('=')) {
                    addToken(TokenType.BANG_EQUAL);
                } else {
                    throw error("Unexpected character '!'. Use 'not' or '!='.");
                }
            }
            case '>' -> {
                if (match('=')) {
                    addToken(TokenType.GREATER_EQUAL);
                } else {
                    if (match('>')) {
                        addToken(match('=') ? TokenType.RIGHT_SHIFT_EQUAL : TokenType.RIGHT_SHIFT);
                    } else {
                        addToken(TokenType.GREATER);
                    }
                }
            }
            case '<' -> {
                if (match('=')) {
                    addToken(TokenType.LESS_EQUAL);
                } else {
                    if (match('<')) {
                        addToken(match('=') ? TokenType.LEFT_SHIFT_EQUAL : TokenType.LEFT_SHIFT);
                    } else {
                        addToken(TokenType.LESS);
                    }
                }
            }
            case ' ', '\t' -> {
            }
            case '#' -> {
                while (peek() != '\n' && !isAtEnd()) {
                    advance();
                }
            }
            case '\n' -> newline();
            case '"', '\'' -> string(c);
            default -> {
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    throw error("Unexpected character '" + c + "'.");
                }
            }
        }
    }

    private void scanIndentation() {
        int indent = 0;
        int indentColumn = 1;
        while (!isAtEnd()) {
            char c = peek();
            if (c == ' ') {
                indent++;
                indentColumn++;
                advance();
            } else if (c == '\t') {
                throw new JavaythonException("[line " + line + ", column " + indentColumn + "] Tabs are not supported for indentation.");
            } else {
                break;
            }
        }

        if (peek() == '\n' || peek() == '#') {
            // 空行やコメントだけの行は、ブロック構造に影響させない。
            atLineStart = false;
            return;
        }

        int previous = indentStack.peek();
        if (indent > previous) {
            // インデントが深くなったら、新しいブロック開始としてINDENTを出す。
            indentStack.push(indent);
            tokens.add(new Token(TokenType.INDENT, "", null, line, 1));
        } else {
            while (indent < indentStack.peek()) {
                // インデントが浅くなった分だけ、ブロック終了のDEDENTを出す。
                indentStack.pop();
                tokens.add(new Token(TokenType.DEDENT, "", null, line, 1));
            }
            if (indent != indentStack.peek()) {
                throw new JavaythonException("[line " + line + ", column 1] Inconsistent indentation.");
            }
        }
        atLineStart = false;
    }

    private void newline() {
        addToken(TokenType.NEWLINE);
        line++;
        lineStart = current;
        atLineStart = true;
    }

    private void string(char quote) {
        StringBuilder value = new StringBuilder();
        while (peek() != quote && !isAtEnd()) {
            if (peek() == '\n') {
                throw error("Unterminated string.");
            }
            if (peek() == '\\') {
                // MVPでは最低限のエスケープシーケンスだけ扱う。
                advance();
                if (isAtEnd()) {
                    throw error("Unterminated string.");
                }
                char escaped = advance();
                value.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case '"', '\'', '\\' -> escaped;
                    default -> escaped;
                });
            } else {
                value.append(advance());
            }
        }
        if (isAtEnd()) {
            throw error("Unterminated string.");
        }
        advance();
        addToken(TokenType.STRING, value.toString());
    }

    private void number() {
        while (isDigit(peek())) {
            advance();
        }

        boolean isFloat = false;
        // 小数点の後に数字が続く場合だけfloatとして扱う。
        if (peek() == '.' && isDigit(peekNext())) {
            isFloat = true;
            advance();
            while (isDigit(peek())) {
                advance();
            }
        }

        String text = source.substring(start, current);
        if (isFloat) {
            addToken(TokenType.NUMBER, Double.parseDouble(text));
        } else {
            addToken(TokenType.NUMBER, Long.parseLong(text));
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) {
            advance();
        }
        String text = source.substring(start, current);
        addToken(KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER));
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) {
            return false;
        }
        current++;
        return true;
    }

    private char advance() {
        return source.charAt(current++);
    }

    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    private char peekNext() {
        return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        tokens.add(new Token(type, source.substring(start, current), literal, line, start - lineStart + 1));
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private JavaythonException error(String message) {
        return new JavaythonException("[line " + line + ", column " + (start - lineStart + 1) + "] " + message);
    }
}
