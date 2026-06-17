#!/usr/bin/env bash
set -euo pipefail

rm -rf out
mkdir -p out/main out/test

javac -d out/main $(find src/main/java -name '*.java' | sort)
javac -cp out/main -d out/test $(find src/test/java -name '*.java' | sort)

java -cp out/main:out/test javaython.InterpreterTest

printf 'Numa\n12\n' | java -cp out/main javaython.Main examples/mvp.jy
java -cp out/main javaython.Main examples/operators.jy
java -cp out/main javaython.Main examples/lists.jy
java -cp out/main javaython.Main examples/tuples_dicts.jy
printf '2 3\n10 20 30\n' | java -cp out/main javaython.Main examples/atcoder_input.jy
