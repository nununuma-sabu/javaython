# javaython

Javaで実装するPython風の小さなインタプリタです。

## 現在のMVP

- 標準入力: `input()`
- 標準出力: `print(...)`
- 型: `int`, `float`, `str`, `bool`
- リスト: `[]`, `[1, 2]`, 添字参照、`len(...)`
- リスト内包表記: `[value * 2 for value in values]`, `[value for value in values if value > 0]`
- リストメソッド: `append`, `pop`, `clear`, `remove`, `insert`, `extend`, `count`, `index`
- タプル: `()`, `(1,)`, `(1, 2)`, 添字参照、`count`, `index`
- 辞書: `{}`, `{"key": value}`, 添字参照、`get`, `keys`, `values`, `items`, `pop`, `clear`, `update`
- 変数代入
- 複合代入: `+=`, `-=`, `*=`, `/=`, `//=`, `%=`, `&=`, `|=`, `^=`, `<<=`, `>>=`
- 数値演算: `+`, `-`, `*`, `/`, `//`, `%`
- 比較演算: `==`, `!=`, `<`, `<=`, `>`, `>=`
- 論理演算: `and`, `or`, `not`
- ビット演算: `&`, `|`, `^`, `~`, `<<`, `>>`
- `if`, `elif`, `else`
- `while`
- `for name in range(n)`
- `range(stop)`, `range(start, stop)`, `range(start, stop, step)`

## 実行方法

```sh
javac -d out $(find src/main/java -name '*.java')
java -cp out javaython.Main examples/mvp.jy
```

引数なしで起動すると対話モードになります。

```sh
java -cp out javaython.Main
```

```text
>>> values = [1, 2, 3]
>>> print([value * 2 for value in values])
[2, 4, 6]
>>> exit()
```

`if` や `while` などのブロックは、空行で入力を確定します。

```text
>>> i = 0
>>> while i < 3:
...     print(i)
...     i += 1
...
0
1
2
```

入力例:

```text
Numa
12
```

出力例:

```text
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
```

## 構文例

```python
n = int(input())

if n > 10:
    print("big")
elif n == 10:
    print("ten")
else:
    print("small")

i = 0
while i < 3:
    print(i)
    i = i + 1

for j in range(3):
    print(j)

print(5 // 2)
print(-5 % 2)
print(True and not False)
print((5 & 3) | 8)

x = 5
x //= 2
print(x)

values = []
values.append(1)
values.append(2)
print(values[0])
print(len(values))
print([value * 2 for value in values])
print([i for i in range(2, 9, 3)])

pair = (1, "two")
scores = {"alice": 10, "bob": 8}
print(pair[1])
print(scores["alice"])
```

## 実装構成

```text
source code
  -> Lexer / Tokenizer
  -> Parser
  -> AST
  -> Interpreter
```

現時点ではASTを直接実行するtree-walk interpreterです。

## テスト

```sh
bash scripts/test.sh
```

`push` と `pull_request` では GitHub Actions の `CI` workflow が同じテストを実行します。
