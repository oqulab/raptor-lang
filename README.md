# Raptor Language Interpreter

**A lightweight, embeddable programming language interpreter written in Kotlin.**

Raptor is a Kotlin-like scripting language designed for learning and embedding. It supports both English and Cyrillic (Kazakh/Russian) keywords, making programming accessible regardless of native language.

## Features

- **Full Lexer** — tokenizes source code into 60+ token types
- **Recursive Descent Parser** — builds AST from token stream
- **Tree-Walking Interpreter** — executes AST nodes with scope management
- **OOP Support** — classes, inheritance, methods, member access
- **Exception Handling** — custom exceptions with inheritance-based catch matching
- **Collections** — List, Map with indexing and methods
- **REPL-ready** — `ExecutionContext.runRepl()` for interactive use
- **Zero Android dependencies** — pure Kotlin, runs anywhere JVM runs

## Quick Start

### Gradle (coming soon to Maven Central)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("kz.oqulab:raptor-lang:1.0.0")
}
```

### Usage

```kotlin
import kz.oqulab.raptor.*

// Source code
val source = """
class MainRn {
    fun main() {
        val greeting = "Hello from Raptor!"
        println(greeting)
        
        for (i in 1..5) {
            println("Count: " + i)
        }
    }
}
"""

// Lex
val tokens = ECLexer(source).lex()

// Parse
val parser = ECParser()
val classes = parser.parse(tokens)

// Execute
val context = ExecutionContext(
    log = { json, newLine -> println(json) },
    readInput = { readln() }
)
context.classes = classes
context.run()
```

### REPL Mode

```kotlin
val ctx = ExecutionContext(log = { msg, _ -> println(msg) }, readInput = { readln() })
ctx.runRepl("2 + 2 * 3")  // prints: 8
ctx.runRepl("val name = \"Raptor\"")
ctx.runRepl("println(\"Hello, \" + name)")  // prints: Hello, Raptor
```

## Language Syntax

### Variables
```raptor
val x = 10        // immutable
var y = 20        // mutable
```

### Functions
```raptor
fun add(a: Int, b: Int): Int {
    return a + b
}
```

### Control Flow
```raptor
if (x > 0) {
    println("positive")
} else {
    println("non-positive")
}

for (i in 1..10) {
    println(i)
}

while (x > 0) {
    x = x - 1
}
```

### Classes
```raptor
class Calculator {
    fun add(a: Int, b: Int): Int {
        return a + b
    }
}

val calc = Calculator()
println(calc.add(5, 3))  // 8
```

### Exception Handling
```raptor
try {
    throw Exception("Something went wrong")
} catch (e: Exception) {
    println("Caught: " + e)
} finally {
    println("Done")
}
```

### Bilingual Keywords (EN / KK)
```raptor
// English
if (x > 10) { return "big" }

// Kazakh
егер (x > 10) { қайтар "үлкен" }
```

Supported Kazakh keywords: `сынып` (class), `әдіс` (fun), `мән` (val), `айны` (var), `егер` (if), `басқаша` (else), `әзірге` (while), `қашан` (when), `үшін` (for), `қайтар` (return)

## Architecture

```
raptor/
├── LexerModels.kt         — Token types, AST nodes
├── ECLexer.kt             — Lexer (tokenizer)
├── ECParser.kt            — Parser (AST builder)
├── RaptorInterpreter.kt   — Abstract interpreter (executeNode)
├── ExecutionContext.kt    — Runnable context with I/O hooks
├── Scope.kt               — Variable scope management
├── InterpreterException.kt — Runtime error types
├── RaptorException.kt     — User exception wrapper
└── paradigms/
    └── ClassInstance.kt   — Runtime class instantiation
```

## Use Cases

- **Educational platforms** — teach programming with native-language keywords
- **Game scripting** — embed Raptor as a modding/scripting engine (like Lua)
- **No-code platforms** — compile visual blocks to Raptor AST
- **Mobile IDEs** — power offline code execution (see [OquLab IDE](https://play.google.com/store/apps/details?id=kz.oqulab.app))
- **IoT automation** — lightweight scripting on edge devices

## Build

```bash
./gradlew :raptor:build
```

## Test

```bash
./gradlew :raptor:test
```

## License

MIT — see [LICENSE](LICENSE) file.

## Links

- **OquLab IDE**: [Google Play](https://play.google.com/store/apps/details?id=kz.oqulab.app)
- **Website**: [oqulab.kz](https://oqulab.kz)
- **GitHub**: [github.com/oqulab/raptor-lang](https://github.com/oqulab/raptor-lang)
