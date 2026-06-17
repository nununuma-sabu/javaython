package javaython;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            runRepl(System.in, System.out, System.err);
            return;
        }
        if (args.length != 1) {
            System.err.println("Usage: javaython [file.jy]");
            System.exit(64);
        }

        try {
            run(Files.readString(Path.of(args[0])));
        } catch (IOException error) {
            System.err.println("Could not read file: " + args[0]);
            System.exit(66);
        } catch (JavaythonException error) {
            System.err.println(error.getMessage());
            System.exit(70);
        }
    }

    private static void run(String source) {
        run(source, new Interpreter(System.in, System.out));
    }

    private static void run(String source, Interpreter interpreter) {
        List<Token> tokens = new Lexer(source).scanTokens();
        List<Stmt> statements = new Parser(tokens).parse();
        interpreter.interpret(statements);
    }

    static void runRepl(InputStream input, PrintStream output, PrintStream errorOutput) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        Interpreter interpreter = new Interpreter(input, output);
        StringBuilder buffer = new StringBuilder();
        boolean multiline = false;

        output.println("Javaython REPL. Type exit() or quit() to exit.");
        while (true) {
            try {
                output.print(multiline ? "... " : ">>> ");
                output.flush();
                String line = reader.readLine();
                if (line == null) {
                    output.println();
                    return;
                }

                if (!multiline && isExitCommand(line)) {
                    return;
                }

                if (multiline && line.isBlank()) {
                    runBufferedSource(buffer, interpreter, errorOutput);
                    buffer.setLength(0);
                    multiline = false;
                    continue;
                }

                buffer.append(line).append('\n');
                if (multiline || startsBlock(line)) {
                    multiline = true;
                    continue;
                }

                runBufferedSource(buffer, interpreter, errorOutput);
                buffer.setLength(0);
            } catch (IOException error) {
                errorOutput.println("Could not read input: " + error.getMessage());
                return;
            }
        }
    }

    private static void runBufferedSource(StringBuilder buffer, Interpreter interpreter, PrintStream errorOutput) {
        try {
            run(buffer.toString(), interpreter);
        } catch (JavaythonException error) {
            errorOutput.println(error.getMessage());
        }
    }

    private static boolean startsBlock(String line) {
        String trimmed = line.stripTrailing();
        return trimmed.endsWith(":");
    }

    private static boolean isExitCommand(String line) {
        String trimmed = line.trim();
        return trimmed.equals("exit()") || trimmed.equals("quit()");
    }
}
