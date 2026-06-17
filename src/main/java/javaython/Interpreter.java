package javaython;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.StringJoiner;

class Interpreter {
    private final Environment environment = new Environment();
    private final Scanner input;
    private final PrintStream output;

    Interpreter(InputStream input, PrintStream output) {
        this.input = new Scanner(input);
        this.output = output;
    }

    void interpret(List<Stmt> statements) {
        // Parserが作ったASTの文を、上から順に実行する。
        for (Stmt statement : statements) {
            execute(statement);
        }
    }

    private void execute(Stmt statement) {
        // 文の種類ごとに実行方法を振り分ける。
        switch (statement) {
            case Stmt.Assign stmt -> environment.assign(stmt.name().lexeme(), evaluate(stmt.value()));
            case Stmt.AugAssign stmt -> executeAugAssign(stmt);
            case Stmt.Expression stmt -> evaluate(stmt.expression());
            case Stmt.For stmt -> executeFor(stmt);
            case Stmt.If stmt -> executeIf(stmt);
            case Stmt.Print stmt -> executePrint(stmt);
            case Stmt.UnpackAssign stmt -> executeUnpackAssign(stmt);
            case Stmt.While stmt -> {
                while (evaluate(stmt.condition()).isTruthy()) {
                    executeBlock(stmt.body());
                }
            }
        }
    }

    private void executeAugAssign(Stmt.AugAssign stmt) {
        // 複合代入は「現在値 op 右辺」を計算して同じ変数へ戻す。
        PyValue left = environment.get(stmt.name());
        PyValue right = evaluate(stmt.value());
        PyValue value = evaluateAugmentedOperator(stmt.operator(), left, right);
        environment.assign(stmt.name().lexeme(), value);
    }

    private void executeUnpackAssign(Stmt.UnpackAssign stmt) {
        List<PyValue> values = iterateValues(evaluate(stmt.value()));
        if (values.size() != stmt.names().size()) {
            throw new JavaythonException("Cannot unpack " + values.size() + " value(s) into " + stmt.names().size() + " variable(s).");
        }
        for (int i = 0; i < stmt.names().size(); i++) {
            environment.assign(stmt.names().get(i).lexeme(), values.get(i));
        }
    }

    private void executeBlock(List<Stmt> statements) {
        for (Stmt statement : statements) {
            execute(statement);
        }
    }

    private void executeIf(Stmt.If stmt) {
        // if/elifは最初にtruthyになった枝だけを実行する。
        for (Stmt.Branch branch : stmt.branches()) {
            if (evaluate(branch.condition()).isTruthy()) {
                executeBlock(branch.body());
                return;
            }
        }
        executeBlock(stmt.elseBranch());
    }

    private void executeFor(Stmt.For stmt) {
        PyValue iterable = evaluate(stmt.iterable());
        for (PyValue value : iterateValues(iterable)) {
            environment.assign(stmt.name().lexeme(), value);
            executeBlock(stmt.body());
        }
    }

    private void executePrint(Stmt.Print stmt) {
        StringJoiner joiner = new StringJoiner(" ");
        for (Expr value : stmt.values()) {
            joiner.add(evaluate(value).display());
        }
        output.println(joiner);
    }

    private PyValue evaluate(Expr expr) {
        // 式は必ずPyValueに評価される。
        return switch (expr) {
            case Expr.Binary binary -> evaluateBinary(binary);
            case Expr.Call call -> evaluateCall(call);
            case Expr.DictLiteral dictLiteral -> evaluateDictLiteral(dictLiteral);
            case Expr.Grouping grouping -> evaluate(grouping.expression());
            case Expr.Index index -> evaluateIndex(index);
            case Expr.ListComprehension listComprehension -> evaluateListComprehension(listComprehension);
            case Expr.ListLiteral listLiteral -> evaluateListLiteral(listLiteral);
            case Expr.Literal literal -> literal.value();
            case Expr.MethodCall methodCall -> evaluateMethodCall(methodCall);
            case Expr.TupleLiteral tupleLiteral -> evaluateTupleLiteral(tupleLiteral);
            case Expr.Unary unary -> evaluateUnary(unary);
            case Expr.Variable variable -> environment.get(variable.name());
        };
    }

    private PyValue evaluateDictLiteral(Expr.DictLiteral dictLiteral) {
        List<PyDictEntry> entries = new ArrayList<>();
        for (Expr.DictEntry entry : dictLiteral.entries()) {
            putDictEntry(entries, evaluate(entry.key()), evaluate(entry.value()));
        }
        return new PyDict(entries);
    }

    private PyValue evaluateListLiteral(Expr.ListLiteral listLiteral) {
        List<PyValue> values = new ArrayList<>();
        for (Expr element : listLiteral.elements()) {
            values.add(evaluate(element));
        }
        return new PyList(values);
    }

    private PyValue evaluateListComprehension(Expr.ListComprehension listComprehension) {
        List<PyValue> results = new ArrayList<>();
        for (PyValue value : iterateValues(evaluate(listComprehension.iterable()))) {
            environment.assign(listComprehension.variable().lexeme(), value);
            if (listComprehension.condition() == null || evaluate(listComprehension.condition()).isTruthy()) {
                results.add(evaluate(listComprehension.element()));
            }
        }
        return new PyList(results);
    }

    private PyValue evaluateTupleLiteral(Expr.TupleLiteral tupleLiteral) {
        List<PyValue> values = new ArrayList<>();
        for (Expr element : tupleLiteral.elements()) {
            values.add(evaluate(element));
        }
        return new PyTuple(values);
    }

    private PyValue evaluateIndex(Expr.Index index) {
        PyValue receiver = evaluate(index.receiver());
        if (receiver instanceof PyList list) {
            return list.values().get(normalizeIndex(index.bracket(), list.values().size(), evaluate(index.index())));
        }
        if (receiver instanceof PyTuple tuple) {
            return tuple.values().get(normalizeIndex(index.bracket(), tuple.values().size(), evaluate(index.index())));
        }
        if (receiver instanceof PyDict dict) {
            PyValue key = evaluate(index.index());
            int entryIndex = dictIndexOf(dict.entries(), key);
            if (entryIndex < 0) {
                throw typeError(index.bracket(), "Dict key not found.");
            }
            return dict.entries().get(entryIndex).value();
        }
        throw typeError(index.bracket(), "Only lists, tuples, and dicts can be indexed.");
    }

    private PyValue evaluateCall(Expr.Call call) {
        if (call.callee().lexeme().equals("map")) {
            return evaluateMap(call);
        }

        List<PyValue> args = call.arguments().stream().map(this::evaluate).toList();
        // MVPの組み込み関数。ユーザー定義関数はまだ扱わない。
        return switch (call.callee().lexeme()) {
            case "input" -> {
                requireArity(call, args, 0);
                yield input.hasNextLine() ? new PyStr(input.nextLine()) : new PyStr("");
            }
            case "int" -> {
                requireArity(call, args, 1);
                yield toInt(args.get(0));
            }
            case "float" -> {
                requireArity(call, args, 1);
                yield toFloat(args.get(0));
            }
            case "str" -> {
                requireArity(call, args, 1);
                yield new PyStr(args.get(0).display());
            }
            case "bool" -> {
                requireArity(call, args, 1);
                yield new PyBool(args.get(0).isTruthy());
            }
            case "len" -> {
                requireArity(call, args, 1);
                yield lengthOf(call.callee(), args.get(0));
            }
            case "list" -> {
                requireArity(call, args, 1);
                yield new PyList(iterateValues(args.get(0)));
            }
            case "range" -> {
                yield toRange(call.callee(), args);
            }
            default -> throw new JavaythonException("Unknown function '" + call.callee().lexeme() + "'.");
        };
    }

    private PyValue evaluateMap(Expr.Call call) {
        if (call.arguments().size() != 2) {
            throw new JavaythonException("Function 'map' expects 2 argument(s).");
        }
        if (!(call.arguments().get(0) instanceof Expr.Variable function)) {
            throw new JavaythonException("map currently expects a built-in function name as the first argument.");
        }

        PyValue iterable = evaluate(call.arguments().get(1));
        List<PyValue> results = new ArrayList<>();
        for (PyValue value : iterateValues(iterable)) {
            results.add(applyBuiltinFunction(function.name(), value));
        }
        return new PyList(results);
    }

    private PyValue applyBuiltinFunction(Token function, PyValue value) {
        return switch (function.lexeme()) {
            case "int" -> toInt(value);
            case "float" -> toFloat(value);
            case "str" -> new PyStr(value.display());
            case "bool" -> new PyBool(value.isTruthy());
            default -> throw new JavaythonException("map does not support function '" + function.lexeme() + "'.");
        };
    }

    private PyValue evaluateMethodCall(Expr.MethodCall call) {
        PyValue receiver = evaluate(call.receiver());
        List<PyValue> args = call.arguments().stream().map(this::evaluate).toList();
        if (receiver instanceof PyStr pyStr) {
            return evaluateStringMethod(call.method(), pyStr, args);
        }
        if (receiver instanceof PyList list) {
            return evaluateListMethod(call.method(), list, args);
        }
        if (receiver instanceof PyTuple tuple) {
            return evaluateTupleMethod(call.method(), tuple, args);
        }
        if (receiver instanceof PyDict dict) {
            return evaluateDictMethod(call.method(), dict, args);
        }
        throw typeError(call.method(), "Only lists, tuples, and dicts have methods currently.");
    }

    private PyValue evaluateStringMethod(Token method, PyStr string, List<PyValue> args) {
        return switch (method.lexeme()) {
            case "split" -> {
                if (args.size() > 1) {
                    throw arityError(method, "0 or 1", args.size());
                }
                String[] parts;
                if (args.isEmpty()) {
                    String trimmed = string.value().trim();
                    parts = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
                } else if (args.get(0) instanceof PyStr separator) {
                    parts = string.value().split(java.util.regex.Pattern.quote(separator.value()), -1);
                } else {
                    throw typeError(method, "split separator must be a str.");
                }

                List<PyValue> values = new ArrayList<>();
                for (String part : parts) {
                    values.add(new PyStr(part));
                }
                yield new PyList(values);
            }
            default -> throw typeError(method, "Unknown str method '" + method.lexeme() + "'.");
        };
    }

    private PyValue evaluateListMethod(Token method, PyList list, List<PyValue> args) {
        return switch (method.lexeme()) {
            case "append" -> {
                requireArity(method, args, 1);
                list.values().add(args.get(0));
                yield PyNone.INSTANCE;
            }
            case "pop" -> {
                if (args.size() > 1) {
                    throw arityError(method, "0 or 1", args.size());
                }
                if (list.values().isEmpty()) {
                    throw typeError(method, "Cannot pop from an empty list.");
                }
                int index = args.isEmpty() ? list.values().size() - 1 : normalizeIndex(method, list.values().size(), args.get(0));
                yield list.values().remove(index);
            }
            case "clear" -> {
                requireArity(method, args, 0);
                list.values().clear();
                yield PyNone.INSTANCE;
            }
            case "remove" -> {
                requireArity(method, args, 1);
                int index = indexOf(list.values(), args.get(0));
                if (index < 0) {
                    throw typeError(method, "Value is not in list.");
                }
                list.values().remove(index);
                yield PyNone.INSTANCE;
            }
            case "insert" -> {
                requireArity(method, args, 2);
                int index = normalizeInsertIndex(method, list.values().size(), args.get(0));
                list.values().add(index, args.get(1));
                yield PyNone.INSTANCE;
            }
            case "extend" -> {
                requireArity(method, args, 1);
                if (!(args.get(0) instanceof PyList other)) {
                    throw typeError(method, "extend expects a list.");
                }
                list.values().addAll(other.values());
                yield PyNone.INSTANCE;
            }
            case "count" -> {
                requireArity(method, args, 1);
                long count = 0;
                for (PyValue value : list.values()) {
                    if (equalsValue(value, args.get(0))) {
                        count++;
                    }
                }
                yield new PyInt(count);
            }
            case "index" -> {
                requireArity(method, args, 1);
                int index = indexOf(list.values(), args.get(0));
                if (index < 0) {
                    throw typeError(method, "Value is not in list.");
                }
                yield new PyInt(index);
            }
            default -> throw typeError(method, "Unknown list method '" + method.lexeme() + "'.");
        };
    }

    private PyValue evaluateTupleMethod(Token method, PyTuple tuple, List<PyValue> args) {
        return switch (method.lexeme()) {
            case "count" -> {
                requireArity(method, args, 1);
                long count = 0;
                for (PyValue value : tuple.values()) {
                    if (equalsValue(value, args.get(0))) {
                        count++;
                    }
                }
                yield new PyInt(count);
            }
            case "index" -> {
                requireArity(method, args, 1);
                int index = indexOf(tuple.values(), args.get(0));
                if (index < 0) {
                    throw typeError(method, "Value is not in tuple.");
                }
                yield new PyInt(index);
            }
            default -> throw typeError(method, "Unknown tuple method '" + method.lexeme() + "'.");
        };
    }

    private PyValue evaluateDictMethod(Token method, PyDict dict, List<PyValue> args) {
        return switch (method.lexeme()) {
            case "keys" -> {
                requireArity(method, args, 0);
                List<PyValue> values = new ArrayList<>();
                for (PyDictEntry entry : dict.entries()) {
                    values.add(entry.key());
                }
                yield new PyList(values);
            }
            case "values" -> {
                requireArity(method, args, 0);
                List<PyValue> values = new ArrayList<>();
                for (PyDictEntry entry : dict.entries()) {
                    values.add(entry.value());
                }
                yield new PyList(values);
            }
            case "items" -> {
                requireArity(method, args, 0);
                List<PyValue> values = new ArrayList<>();
                for (PyDictEntry entry : dict.entries()) {
                    values.add(new PyTuple(List.of(entry.key(), entry.value())));
                }
                yield new PyList(values);
            }
            case "get" -> {
                if (args.isEmpty() || args.size() > 2) {
                    throw arityError(method, "1 or 2", args.size());
                }
                int index = dictIndexOf(dict.entries(), args.get(0));
                yield index >= 0 ? dict.entries().get(index).value() : (args.size() == 2 ? args.get(1) : PyNone.INSTANCE);
            }
            case "pop" -> {
                if (args.isEmpty() || args.size() > 2) {
                    throw arityError(method, "1 or 2", args.size());
                }
                int index = dictIndexOf(dict.entries(), args.get(0));
                if (index >= 0) {
                    yield dict.entries().remove(index).value();
                }
                if (args.size() == 2) {
                    yield args.get(1);
                }
                throw typeError(method, "Dict key not found.");
            }
            case "clear" -> {
                requireArity(method, args, 0);
                dict.entries().clear();
                yield PyNone.INSTANCE;
            }
            case "update" -> {
                requireArity(method, args, 1);
                if (!(args.get(0) instanceof PyDict other)) {
                    throw typeError(method, "update expects a dict.");
                }
                for (PyDictEntry entry : other.entries()) {
                    putDictEntry(dict.entries(), entry.key(), entry.value());
                }
                yield PyNone.INSTANCE;
            }
            default -> throw typeError(method, "Unknown dict method '" + method.lexeme() + "'.");
        };
    }

    private PyValue evaluateUnary(Expr.Unary unary) {
        PyValue right = evaluate(unary.right());
        return switch (unary.operator().type()) {
            case MINUS -> {
                if (right instanceof PyInt value) {
                    yield new PyInt(-value.value());
                }
                if (right instanceof PyFloat value) {
                    yield new PyFloat(-value.value());
                }
                throw typeError(unary.operator(), "Unary '-' expects a number.");
            }
            case PLUS -> {
                if (right instanceof PyInt || right instanceof PyFloat) {
                    yield right;
                }
                throw typeError(unary.operator(), "Unary '+' expects a number.");
            }
            case NOT -> new PyBool(!right.isTruthy());
            case TILDE -> new PyInt(~asLong(unary.operator(), right));
            default -> throw new IllegalStateException("Unexpected unary operator: " + unary.operator().type());
        };
    }

    private PyValue evaluateBinary(Expr.Binary binary) {
        PyValue left = evaluate(binary.left());
        if (binary.operator().type() == TokenType.OR) {
            // Pythonと同じように、and/orは短絡評価して値そのものを返す。
            return left.isTruthy() ? left : evaluate(binary.right());
        }
        if (binary.operator().type() == TokenType.AND) {
            return left.isTruthy() ? evaluate(binary.right()) : left;
        }
        PyValue right = evaluate(binary.right());
        return switch (binary.operator().type()) {
            case PLUS -> add(binary.operator(), left, right);
            case MINUS -> numeric(binary.operator(), left, right, (a, b) -> a - b, (a, b) -> a - b);
            case STAR -> numeric(binary.operator(), left, right, (a, b) -> a * b, (a, b) -> a * b);
            case SLASH -> divide(binary.operator(), left, right);
            case FLOOR_SLASH -> floorDivide(binary.operator(), left, right);
            case PERCENT -> modulo(binary.operator(), left, right);
            case AMPERSAND -> bitwise(binary.operator(), left, right, (a, b) -> a & b);
            case PIPE -> bitwise(binary.operator(), left, right, (a, b) -> a | b);
            case CARET -> bitwise(binary.operator(), left, right, (a, b) -> a ^ b);
            case LEFT_SHIFT -> shift(binary.operator(), left, right, true);
            case RIGHT_SHIFT -> shift(binary.operator(), left, right, false);
            case GREATER -> compare(binary.operator(), left, right, (a, b) -> a > b);
            case GREATER_EQUAL -> compare(binary.operator(), left, right, (a, b) -> a >= b);
            case LESS -> compare(binary.operator(), left, right, (a, b) -> a < b);
            case LESS_EQUAL -> compare(binary.operator(), left, right, (a, b) -> a <= b);
            case EQUAL_EQUAL -> new PyBool(equalsValue(left, right));
            case BANG_EQUAL -> new PyBool(!equalsValue(left, right));
            default -> throw new IllegalStateException("Unexpected binary operator: " + binary.operator().type());
        };
    }

    private PyValue evaluateAugmentedOperator(Token operator, PyValue left, PyValue right) {
        return switch (operator.type()) {
            case PLUS_EQUAL -> add(operator, left, right);
            case MINUS_EQUAL -> numeric(operator, left, right, (a, b) -> a - b, (a, b) -> a - b);
            case STAR_EQUAL -> numeric(operator, left, right, (a, b) -> a * b, (a, b) -> a * b);
            case SLASH_EQUAL -> divide(operator, left, right);
            case FLOOR_SLASH_EQUAL -> floorDivide(operator, left, right);
            case PERCENT_EQUAL -> modulo(operator, left, right);
            case AMPERSAND_EQUAL -> bitwise(operator, left, right, (a, b) -> a & b);
            case PIPE_EQUAL -> bitwise(operator, left, right, (a, b) -> a | b);
            case CARET_EQUAL -> bitwise(operator, left, right, (a, b) -> a ^ b);
            case LEFT_SHIFT_EQUAL -> shift(operator, left, right, true);
            case RIGHT_SHIFT_EQUAL -> shift(operator, left, right, false);
            default -> throw new IllegalStateException("Unexpected augmented assignment operator: " + operator.type());
        };
    }

    private PyValue add(Token operator, PyValue left, PyValue right) {
        if (left instanceof PyStr l && right instanceof PyStr r) {
            return new PyStr(l.value() + r.value());
        }
        if (left instanceof PyList l && right instanceof PyList r) {
            List<PyValue> values = new ArrayList<>(l.values());
            values.addAll(r.values());
            return new PyList(values);
        }
        if (left instanceof PyTuple l && right instanceof PyTuple r) {
            List<PyValue> values = new ArrayList<>(l.values());
            values.addAll(r.values());
            return new PyTuple(values);
        }
        return numeric(operator, left, right, (a, b) -> a + b, (a, b) -> a + b);
    }

    private PyValue divide(Token operator, PyValue left, PyValue right) {
        double divisor = asDouble(operator, right);
        if (divisor == 0.0) {
            throw typeError(operator, "Division by zero.");
        }
        return new PyFloat(asDouble(operator, left) / divisor);
    }

    private PyValue floorDivide(Token operator, PyValue left, PyValue right) {
        // // は床除算。負数でもPythonと同じく小さい整数方向へ丸める。
        if (left instanceof PyInt l && right instanceof PyInt r) {
            if (r.value() == 0) {
                throw typeError(operator, "Division by zero.");
            }
            return new PyInt(Math.floorDiv(l.value(), r.value()));
        }

        double divisor = asDouble(operator, right);
        if (divisor == 0.0) {
            throw typeError(operator, "Division by zero.");
        }
        return new PyInt((long) Math.floor(asDouble(operator, left) / divisor));
    }

    private PyValue modulo(Token operator, PyValue left, PyValue right) {
        // % は床除算と対応する剰余にするため、負数でも除数と同じ符号になる。
        if (left instanceof PyInt l && right instanceof PyInt r) {
            if (r.value() == 0) {
                throw typeError(operator, "Modulo by zero.");
            }
            return new PyInt(Math.floorMod(l.value(), r.value()));
        }

        double divisor = asDouble(operator, right);
        if (divisor == 0.0) {
            throw typeError(operator, "Modulo by zero.");
        }
        double dividend = asDouble(operator, left);
        return new PyFloat(dividend - Math.floor(dividend / divisor) * divisor);
    }

    private PyValue bitwise(Token operator, PyValue left, PyValue right, LongBinary longOp) {
        // ビット演算はMVPではint同士だけを受け付ける。
        return new PyInt(longOp.apply(asLong(operator, left), asLong(operator, right)));
    }

    private PyValue shift(Token operator, PyValue left, PyValue right, boolean leftShift) {
        // Javaのシフトは大きすぎる桁数を丸めるため、先に明示的に弾く。
        long amount = asLong(operator, right);
        if (amount < 0) {
            throw typeError(operator, "Negative shift count.");
        }
        if (amount > Integer.MAX_VALUE) {
            throw typeError(operator, "Shift count is too large.");
        }
        long value = asLong(operator, left);
        return new PyInt(leftShift ? value << amount : value >> amount);
    }

    private PyValue numeric(Token operator, PyValue left, PyValue right, LongBinary longOp, DoubleBinary doubleOp) {
        if (left instanceof PyInt l && right instanceof PyInt r) {
            // 両方intなら結果もintに保ち、片方でもfloatならfloatへ寄せる。
            return new PyInt(longOp.apply(l.value(), r.value()));
        }
        return new PyFloat(doubleOp.apply(asDouble(operator, left), asDouble(operator, right)));
    }

    private PyBool compare(Token operator, PyValue left, PyValue right, DoublePredicate predicate) {
        return new PyBool(predicate.test(asDouble(operator, left), asDouble(operator, right)));
    }

    private double asDouble(Token operator, PyValue value) {
        if (value instanceof PyInt pyInt) {
            return pyInt.value();
        }
        if (value instanceof PyFloat pyFloat) {
            return pyFloat.value();
        }
        throw typeError(operator, "Expected a number.");
    }

    private long asLong(Token operator, PyValue value) {
        if (value instanceof PyInt pyInt) {
            return pyInt.value();
        }
        throw typeError(operator, "Expected an int.");
    }

    private PyInt lengthOf(Token token, PyValue value) {
        if (value instanceof PyStr pyStr) {
            return new PyInt(pyStr.value().length());
        }
        if (value instanceof PyList pyList) {
            return new PyInt(pyList.values().size());
        }
        if (value instanceof PyTuple pyTuple) {
            return new PyInt(pyTuple.values().size());
        }
        if (value instanceof PyDict pyDict) {
            return new PyInt(pyDict.entries().size());
        }
        throw typeError(token, "len expects a str, list, tuple, or dict.");
    }

    private int normalizeIndex(Token token, int size, PyValue value) {
        long rawIndex = asLong(token, value);
        long normalized = rawIndex < 0 ? size + rawIndex : rawIndex;
        if (normalized < 0 || normalized >= size) {
            throw typeError(token, "List index out of range.");
        }
        return Math.toIntExact(normalized);
    }

    private int normalizeInsertIndex(Token token, int size, PyValue value) {
        long rawIndex = asLong(token, value);
        long normalized = rawIndex < 0 ? size + rawIndex : rawIndex;
        if (normalized < 0) {
            return 0;
        }
        if (normalized > size) {
            return size;
        }
        return Math.toIntExact(normalized);
    }

    private int indexOf(List<PyValue> values, PyValue target) {
        for (int i = 0; i < values.size(); i++) {
            if (equalsValue(values.get(i), target)) {
                return i;
            }
        }
        return -1;
    }

    private int dictIndexOf(List<PyDictEntry> entries, PyValue key) {
        for (int i = 0; i < entries.size(); i++) {
            if (equalsValue(entries.get(i).key(), key)) {
                return i;
            }
        }
        return -1;
    }

    private void putDictEntry(List<PyDictEntry> entries, PyValue key, PyValue value) {
        int index = dictIndexOf(entries, key);
        PyDictEntry entry = new PyDictEntry(key, value);
        if (index >= 0) {
            entries.set(index, entry);
        } else {
            entries.add(entry);
        }
    }

    private List<PyValue> iterateValues(PyValue iterable) {
        if (iterable instanceof PyList list) {
            return new ArrayList<>(list.values());
        }
        if (iterable instanceof PyTuple tuple) {
            return new ArrayList<>(tuple.values());
        }
        if (iterable instanceof PyDict dict) {
            List<PyValue> values = new ArrayList<>();
            for (PyDictEntry entry : dict.entries()) {
                values.add(entry.key());
            }
            return values;
        }
        if (iterable instanceof PyRange range) {
            List<PyValue> values = new ArrayList<>();
            if (range.step() > 0) {
                for (long i = range.start(); i < range.stop(); i += range.step()) {
                    values.add(new PyInt(i));
                }
            } else {
                for (long i = range.start(); i > range.stop(); i += range.step()) {
                    values.add(new PyInt(i));
                }
            }
            return values;
        }
        if (iterable instanceof PyInt count) {
            List<PyValue> values = new ArrayList<>();
            // 古い内部表現との互換用。range(...)は現在PyRangeを返す。
            for (long i = 0; i < count.value(); i++) {
                values.add(new PyInt(i));
            }
            return values;
        }
        throw new JavaythonException("Expected range, list, tuple, or dict.");
    }

    private PyRange toRange(Token token, List<PyValue> args) {
        if (args.isEmpty() || args.size() > 3) {
            throw arityError(token, "1 to 3", args.size());
        }

        long start;
        long stop;
        long step = 1;
        if (args.size() == 1) {
            start = 0;
            stop = asLong(token, args.get(0));
        } else {
            start = asLong(token, args.get(0));
            stop = asLong(token, args.get(1));
            if (args.size() == 3) {
                step = asLong(token, args.get(2));
            }
        }
        if (step == 0) {
            throw typeError(token, "range step cannot be zero.");
        }
        return new PyRange(start, stop, step);
    }

    private PyInt toInt(PyValue value) {
        if (value instanceof PyInt pyInt) {
            return pyInt;
        }
        if (value instanceof PyFloat pyFloat) {
            return new PyInt((long) pyFloat.value());
        }
        if (value instanceof PyBool pyBool) {
            return new PyInt(pyBool.value() ? 1 : 0);
        }
        if (value instanceof PyStr pyStr) {
            try {
                return new PyInt(Long.parseLong(pyStr.value().trim()));
            } catch (NumberFormatException error) {
                throw new JavaythonException("Cannot convert '" + pyStr.value() + "' to int.");
            }
        }
        throw new JavaythonException("Cannot convert value to int.");
    }

    private PyFloat toFloat(PyValue value) {
        if (value instanceof PyFloat pyFloat) {
            return pyFloat;
        }
        if (value instanceof PyInt pyInt) {
            return new PyFloat(pyInt.value());
        }
        if (value instanceof PyBool pyBool) {
            return new PyFloat(pyBool.value() ? 1.0 : 0.0);
        }
        if (value instanceof PyStr pyStr) {
            try {
                return new PyFloat(Double.parseDouble(pyStr.value().trim()));
            } catch (NumberFormatException error) {
                throw new JavaythonException("Cannot convert '" + pyStr.value() + "' to float.");
            }
        }
        throw new JavaythonException("Cannot convert value to float.");
    }

    private boolean equalsValue(PyValue left, PyValue right) {
        if (left instanceof PyInt l && right instanceof PyFloat r) {
            return l.value() == r.value();
        }
        if (left instanceof PyFloat l && right instanceof PyInt r) {
            return l.value() == r.value();
        }
        return left.equals(right);
    }

    private void requireArity(Expr.Call call, List<PyValue> args, int arity) {
        if (args.size() != arity) {
            throw new JavaythonException("Function '" + call.callee().lexeme() + "' expects " + arity + " argument(s).");
        }
    }

    private void requireArity(Token name, List<PyValue> args, int arity) {
        if (args.size() != arity) {
            throw arityError(name, Integer.toString(arity), args.size());
        }
    }

    private JavaythonException arityError(Token name, String expected, int actual) {
        return new JavaythonException("'" + name.lexeme() + "' expects " + expected + " argument(s), got " + actual + ".");
    }

    private JavaythonException typeError(Token token, String message) {
        return new JavaythonException("[line " + token.line() + ", column " + token.column() + "] " + message);
    }

    @FunctionalInterface
    private interface LongBinary {
        long apply(long left, long right);
    }

    @FunctionalInterface
    private interface DoubleBinary {
        double apply(double left, double right);
    }

    @FunctionalInterface
    private interface DoublePredicate {
        boolean test(double left, double right);
    }
}
