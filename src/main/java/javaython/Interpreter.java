package javaython;

import java.io.InputStream;
import java.io.PrintStream;
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
            case Stmt.Expression stmt -> evaluate(stmt.expression());
            case Stmt.For stmt -> executeFor(stmt);
            case Stmt.If stmt -> executeIf(stmt);
            case Stmt.Print stmt -> executePrint(stmt);
            case Stmt.While stmt -> {
                while (evaluate(stmt.condition()).isTruthy()) {
                    executeBlock(stmt.body());
                }
            }
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
        if (!(iterable instanceof PyInt count)) {
            throw new JavaythonException("for loop currently expects range(n), where n is an int.");
        }
        // range(n)はInterpreter内では「0からn未満まで回す整数」として扱う。
        for (long i = 0; i < count.value(); i++) {
            environment.assign(stmt.name().lexeme(), new PyInt(i));
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
            case Expr.Grouping grouping -> evaluate(grouping.expression());
            case Expr.Literal literal -> literal.value();
            case Expr.Unary unary -> evaluateUnary(unary);
            case Expr.Variable variable -> environment.get(variable.name());
        };
    }

    private PyValue evaluateCall(Expr.Call call) {
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
            case "range" -> {
                requireArity(call, args, 1);
                yield toInt(args.get(0));
            }
            default -> throw new JavaythonException("Unknown function '" + call.callee().lexeme() + "'.");
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
            case NOT -> new PyBool(!right.isTruthy());
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
            case GREATER -> compare(binary.operator(), left, right, (a, b) -> a > b);
            case GREATER_EQUAL -> compare(binary.operator(), left, right, (a, b) -> a >= b);
            case LESS -> compare(binary.operator(), left, right, (a, b) -> a < b);
            case LESS_EQUAL -> compare(binary.operator(), left, right, (a, b) -> a <= b);
            case EQUAL_EQUAL -> new PyBool(equalsValue(left, right));
            case BANG_EQUAL -> new PyBool(!equalsValue(left, right));
            default -> throw new IllegalStateException("Unexpected binary operator: " + binary.operator().type());
        };
    }

    private PyValue add(Token operator, PyValue left, PyValue right) {
        if (left instanceof PyStr l && right instanceof PyStr r) {
            return new PyStr(l.value() + r.value());
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
