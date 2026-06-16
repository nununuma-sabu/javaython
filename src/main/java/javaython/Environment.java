package javaython;

import java.util.HashMap;
import java.util.Map;

class Environment {
    // MVPではスコープを1つだけ持つ。関数を入れるなら親環境を追加する。
    private final Map<String, PyValue> values = new HashMap<>();

    PyValue get(Token name) {
        if (values.containsKey(name.lexeme())) {
            return values.get(name.lexeme());
        }
        throw new JavaythonException(errorAt(name, "Undefined variable '" + name.lexeme() + "'."));
    }

    void assign(String name, PyValue value) {
        values.put(name, value);
    }

    private String errorAt(Token token, String message) {
        return "[line " + token.line() + ", column " + token.column() + "] " + message;
    }
}
