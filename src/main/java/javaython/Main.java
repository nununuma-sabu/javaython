package javaython;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // まずは「1ファイルを読んで実行する」だけのシンプルなCLI。
        if (args.length != 1) {
            System.err.println("Usage: javaython <file.jy>");
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
        List<Token> tokens = new Lexer(source).scanTokens();
        List<Stmt> statements = new Parser(tokens).parse();
        new Interpreter(System.in, System.out).interpret(statements);
    }
}
