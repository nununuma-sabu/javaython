package javaython;

sealed interface PyValue permits PyInt, PyFloat, PyStr, PyBool, PyNone {
    // ifやwhileの条件で使う真偽値判定。
    boolean isTruthy();

    // printやstr()で使う表示用文字列。
    String display();
}

record PyInt(long value) implements PyValue {
    @Override
    public boolean isTruthy() {
        return value != 0;
    }

    @Override
    public String display() {
        return Long.toString(value);
    }
}

record PyFloat(double value) implements PyValue {
    @Override
    public boolean isTruthy() {
        return value != 0.0;
    }

    @Override
    public String display() {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.toString(value);
        }
        String text = Double.toString(value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) + ".0" : text;
    }
}

record PyStr(String value) implements PyValue {
    @Override
    public boolean isTruthy() {
        return !value.isEmpty();
    }

    @Override
    public String display() {
        return value;
    }
}

record PyBool(boolean value) implements PyValue {
    @Override
    public boolean isTruthy() {
        return value;
    }

    @Override
    public String display() {
        return value ? "True" : "False";
    }
}

final class PyNone implements PyValue {
    static final PyNone INSTANCE = new PyNone();

    private PyNone() {
    }

    @Override
    public boolean isTruthy() {
        return false;
    }

    @Override
    public String display() {
        return "None";
    }
}
