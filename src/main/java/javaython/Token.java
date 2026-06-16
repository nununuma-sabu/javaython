package javaython;

record Token(TokenType type, String lexeme, Object literal, int line, int column) {
    @Override
    public String toString() {
        return type + " " + lexeme + (literal == null ? "" : " " + literal);
    }
}
