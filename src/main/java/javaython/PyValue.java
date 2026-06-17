package javaython;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

sealed interface PyValue permits PyInt, PyFloat, PyStr, PyBool, PyRange, PyList, PyTuple, PyDict, PyNone {
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

record PyRange(long start, long stop, long step) implements PyValue {
    @Override
    public boolean isTruthy() {
        return step > 0 ? start < stop : start > stop;
    }

    @Override
    public String display() {
        if (start == 0 && step == 1) {
            return "range(0, " + stop + ")";
        }
        return "range(" + start + ", " + stop + ", " + step + ")";
    }
}

record PyList(List<PyValue> values) implements PyValue {
    PyList {
        values = new ArrayList<>(values);
    }

    @Override
    public boolean isTruthy() {
        return !values.isEmpty();
    }

    @Override
    public String display() {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (PyValue value : values) {
            joiner.add(PyDisplay.repr(value));
        }
        return joiner.toString();
    }
}

record PyTuple(List<PyValue> values) implements PyValue {
    PyTuple {
        values = List.copyOf(values);
    }

    @Override
    public boolean isTruthy() {
        return !values.isEmpty();
    }

    @Override
    public String display() {
        if (values.isEmpty()) {
            return "()";
        }
        StringJoiner joiner = new StringJoiner(", ", "(", values.size() == 1 ? ",)" : ")");
        for (PyValue value : values) {
            joiner.add(PyDisplay.repr(value));
        }
        return joiner.toString();
    }
}

record PyDict(List<PyDictEntry> entries) implements PyValue {
    PyDict {
        entries = new ArrayList<>(entries);
    }

    @Override
    public boolean isTruthy() {
        return !entries.isEmpty();
    }

    @Override
    public String display() {
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        for (PyDictEntry entry : entries) {
            joiner.add(PyDisplay.repr(entry.key()) + ": " + PyDisplay.repr(entry.value()));
        }
        return joiner.toString();
    }
}

record PyDictEntry(PyValue key, PyValue value) {
}

final class PyDisplay {
    private PyDisplay() {
    }

    static String repr(PyValue value) {
        if (value instanceof PyStr pyStr) {
            return "'" + pyStr.value().replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        return value.display();
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
