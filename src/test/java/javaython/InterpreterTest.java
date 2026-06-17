package javaython;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class InterpreterTest {
    private static int passed;

    public static void main(String[] args) {
        test("input, if, while, for", InterpreterTest::testMvpFlow);
        test("atcoder input", InterpreterTest::testAtCoderInput);
        test("operators", InterpreterTest::testOperators);
        test("big integers", InterpreterTest::testBigIntegers);
        test("range", InterpreterTest::testRange);
        test("augmented assignment", InterpreterTest::testAugmentedAssignment);
        test("lists", InterpreterTest::testLists);
        test("tuples and dicts", InterpreterTest::testTuplesAndDicts);
        test("repl", InterpreterTest::testRepl);
        test("runtime errors", InterpreterTest::testRuntimeErrors);
        System.out.println("Passed " + passed + " tests.");
    }

    private static void testMvpFlow() {
        String source = """
                name = input()
                n = int(input())

                print("hello", name)
                print("n + 2 =", n + 2)
                print("n / 2 =", n / 2)

                if n > 10:
                    print("big")
                elif n == 10:
                    print("ten")
                else:
                    print("small")

                i = 0
                while i < 3:
                    print("while", i)
                    i = i + 1

                for j in range(3):
                    print("for", j)
                """;

        assertOutput(source, "Numa\n12\n", """
                hello Numa
                n + 2 = 14
                n / 2 = 6.0
                big
                while 0
                while 1
                while 2
                for 0
                for 1
                for 2
                """);
    }

    private static void testOperators() {
        String source = """
                print("floor", 5 // 2)
                print("floor negative", -5 // 2)
                print("floor negative divisor", 5 // -2)
                print("mod", 5 % 2)
                print("mod negative", -5 % 2)
                print("mod negative divisor", 5 % -2)
                print("logic", True and not False)
                print("logic precedence", not 1 == 1)
                print("logic short", 0 or "fallback")
                print("bit and", 5 & 3)
                print("bit or", 5 | 2)
                print("bit xor", 5 ^ 3)
                print("bit not", ~5)
                print("shift left", 3 << 2)
                print("shift right", 16 >> 2)
                """;

        assertOutput(source, "", """
                floor 2
                floor negative -3
                floor negative divisor -3
                mod 1
                mod negative 1
                mod negative divisor -1
                logic True
                logic precedence False
                logic short fallback
                bit and 1
                bit or 7
                bit xor 6
                bit not -6
                shift left 12
                shift right 4
                """);
    }

    private static void testAtCoderInput() {
        String source = """
                a, b = map(int, input().split())
                S = list(map(int, input().split()))
                words = input().split()

                print("a", a)
                print("b", b)
                print("S", S)
                print("words", words)
                print("sum", a + b + S[0])
                """;

        assertOutput(source, "2 3\n10 20 30\nhello javaython\n", """
                a 2
                b 3
                S [10, 20, 30]
                words ['hello', 'javaython']
                sum 15
                """);
    }

    private static void testBigIntegers() {
        String source = """
                big = 9223372036854775808123456789
                print("big", big)
                print("add", big + 11)
                print("mul", 12345678901234567890 * 10)
                print("floor", -9223372036854775809 // 2)
                print("mod", -9223372036854775809 % 2)
                print("shift", 1 << 100)
                a, b = map(int, input().split())
                print("input add", a + b)
                """;

        assertOutput(source, "9223372036854775808 2\n", """
                big 9223372036854775808123456789
                add 9223372036854775808123456800
                mul 123456789012345678900
                floor -4611686018427387905
                mod 1
                shift 1267650600228229401496703205376
                input add 9223372036854775810
                """);
    }

    private static void testAugmentedAssignment() {
        String source = """
                x = 5
                x += 3
                print("plus equal", x)
                x -= 2
                print("minus equal", x)
                x *= 4
                print("star equal", x)
                x /= 3
                print("slash equal", x)
                x = 5
                x //= 2
                print("floor equal", x)
                x %= 3
                print("mod equal", x)
                x = 6
                x &= 3
                print("and equal", x)
                x ^= 7
                print("xor equal", x)
                x <<= 4
                print("left shift equal", x)
                x |= 3
                print("or equal", x)
                x >>= 1
                print("right shift equal", x)
                """;

        assertOutput(source, "", """
                plus equal 8
                minus equal 6
                star equal 24
                slash equal 8.0
                floor equal 2
                mod equal 2
                and equal 2
                xor equal 5
                left shift equal 80
                or equal 83
                right shift equal 41
                """);
    }

    private static void testRange() {
        String source = """
                print("range stop", [i for i in range(4)])
                print("range start stop", [i for i in range(2, 6)])
                print("range step", [i for i in range(2, 9, 3)])
                print("range negative", [i for i in range(5, 0, -2)])
                print("range empty", [i for i in range(5, 0)])
                total = 0
                for i in range(1, 4):
                    total += i
                print("range total", total)
                """;

        assertOutput(source, "", """
                range stop [0, 1, 2, 3]
                range start stop [2, 3, 4, 5]
                range step [2, 5, 8]
                range negative [5, 3, 1]
                range empty []
                range total 6
                """);
    }

    private static void testLists() {
        String source = """
                values = []
                values.append(1)
                values.append(2)
                values.append(3)
                print("values", values)
                print("len", len(values))
                print("first", values[0])
                print("last", values[-1])

                values.insert(1, 9)
                print("insert", values)
                print("pop", values.pop())
                print("after pop", values)
                values.remove(9)
                print("after remove", values)
                values.extend([4, 5])
                print("after extend", values)
                print("count", values.count(2))
                print("index", values.index(4))

                total = 0
                for value in values:
                    total += value
                print("total", total)

                evens = [value for value in values if value % 2 == 0]
                doubled = [value * 2 for value in values]
                from_range = [i + 1 for i in range(3)]
                print("evens", evens)
                print("doubled", doubled)
                print("from range", from_range)

                values.clear()
                print("clear", values)
                """;

        assertOutput(source, "", """
                values [1, 2, 3]
                len 3
                first 1
                last 3
                insert [1, 9, 2, 3]
                pop 3
                after pop [1, 9, 2]
                after remove [1, 2]
                after extend [1, 2, 4, 5]
                count 1
                index 2
                total 12
                evens [2, 4]
                doubled [2, 4, 8, 10]
                from range [1, 2, 3]
                clear []
                """);
    }

    private static void testRuntimeErrors() {
        assertErrorContains("x += 1\n", "Undefined variable 'x'.");
        assertErrorContains("print(1 // 0)\n", "Division by zero.");
        assertErrorContains("print(1 << -1)\n", "Negative shift count.");
        assertErrorContains("print(1.5 & 1)\n", "Expected an int.");
        assertErrorContains("print([][0])\n", "List index out of range.");
        assertErrorContains("values = []\nvalues.pop()\n", "Cannot pop from an empty list.");
        assertErrorContains("print((1,)[2])\n", "index out of range");
        assertErrorContains("print({\"a\": 1}[\"b\"])\n", "Dict key not found.");
        assertErrorContains("print([i for i in range(1, 5, 0)])\n", "range step cannot be zero.");
        assertErrorContains("a, b = [1]\n", "Cannot unpack 1 value(s) into 2 variable(s).");
        assertErrorContains("print(map(sum, [1]))\n", "map does not support function 'sum'.");
    }

    private static void testRepl() {
        String input = """
                x = 1
                x += 2
                print(x)
                while x < 5:
                    print(x)
                    x += 1

                print([i for i in range(3)])
                exit()
                """;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        Main.runRepl(
                inputStream,
                new PrintStream(outputStream, true, StandardCharsets.UTF_8),
                new PrintStream(errorStream, true, StandardCharsets.UTF_8)
        );

        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertContains(output, "Javaython REPL. Type exit() or quit() to exit.");
        assertContains(output, "3\n");
        assertContains(output, "4\n");
        assertContains(output, "[0, 1, 2]\n");
        assertEquals("", errorStream.toString(StandardCharsets.UTF_8));
    }

    private static void testTuplesAndDicts() {
        String source = """
                empty_tuple = ()
                single = (1,)
                pair = (1, "two")
                more = pair + (3,)
                print("empty tuple", empty_tuple)
                print("single tuple", single)
                print("pair", pair)
                print("more", more)
                print("tuple len", len(more))
                print("tuple first", more[0])
                print("tuple last", more[-1])
                print("tuple count", (1, 2, 1).count(1))
                print("tuple index", more.index("two"))

                scores = {}
                scores.update({"alice": 10, "bob": 8})
                print("scores", scores)
                print("alice", scores["alice"])
                print("missing", scores.get("carol", 0))
                print("keys", scores.keys())
                print("values", scores.values())
                print("items", scores.items())

                total = 0
                for name in scores:
                    total += scores[name]
                print("dict total", total)

                print("popped", scores.pop("bob"))
                print("after pop", scores)
                scores.clear()
                print("after clear", scores)
                """;

        assertOutput(source, "", """
                empty tuple ()
                single tuple (1,)
                pair (1, 'two')
                more (1, 'two', 3)
                tuple len 3
                tuple first 1
                tuple last 3
                tuple count 2
                tuple index 1
                scores {'alice': 10, 'bob': 8}
                alice 10
                missing 0
                keys ['alice', 'bob']
                values [10, 8]
                items [('alice', 10), ('bob', 8)]
                dict total 18
                popped 8
                after pop {'alice': 10}
                after clear {}
                """);
    }

    private static void assertOutput(String source, String input, String expected) {
        String actual = run(source, input);
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected:\n" + expected + "\nActual:\n" + actual);
        }
    }

    private static void assertContains(String actual, String expectedPart) {
        if (!actual.contains(expectedPart)) {
            throw new AssertionError("Expected output to contain:\n" + expectedPart + "\nActual:\n" + actual);
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected:\n" + expected + "\nActual:\n" + actual);
        }
    }

    private static void assertErrorContains(String source, String expectedMessage) {
        try {
            run(source, "");
            throw new AssertionError("Expected error containing: " + expectedMessage);
        } catch (JavaythonException error) {
            if (!error.getMessage().contains(expectedMessage)) {
                throw new AssertionError("Expected error containing '" + expectedMessage + "' but got: " + error.getMessage());
            }
        }
    }

    private static String run(String source, String input) {
        List<Token> tokens = new Lexer(source).scanTokens();
        List<Stmt> statements = new Parser(tokens).parse();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream, true, StandardCharsets.UTF_8);
        new Interpreter(inputStream, printStream).interpret(statements);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private static void test(String name, Runnable runnable) {
        try {
            runnable.run();
            passed++;
            System.out.println("[PASS] " + name);
        } catch (RuntimeException | AssertionError error) {
            System.err.println("[FAIL] " + name);
            throw error;
        }
    }
}
