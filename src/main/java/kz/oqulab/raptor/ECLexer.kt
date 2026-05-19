package kz.oqulab.raptor

import kz.oqulab.raptor.utls.toBpValueType

class ECLexer(var source: String = "") {
    companion object {
//        val mainTokens = mutableListOf<Token>()
//
//        fun updateTokens(text: String) {
//            mainTokens.clear()
//            mainTokens.addAll(ECLexer().lex(text))
//        }
        fun isAlpha(c: Char): Boolean {
            return c in 'a'..'z' || c in 'A'..'Z' || c == '_' || isKazakhAlpha(c)
        }
        fun isKazakhAlpha(c: Char): Boolean {
            return c in 'а'..'я' || c in 'А'..'Я' ||
                    c in setOf('Ә', 'ә', 'І', 'і', 'Ң', 'ң', 'Ғ', 'ғ', 'Ү', 'ү', 'Ұ', 'ұ', 'Қ', 'қ', 'Ө', 'ө', 'Һ', 'һ')
        }
        fun isDigit(c: Char): Boolean {
            return c in '0'..'9'
        }
        fun isAlphaNumeric(c: Char): Boolean {
            return isAlpha(c) || isDigit(c)
        }
        fun isAlphaNumericOrSpace(c: Char): Boolean {
            return isAlpha(c) || isDigit(c)
        }
        fun isSpace(c: Char) = c == ' '
    }
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var column = 0
    private var line = 1
    private var level = 1

    fun reInit(text: String) {
        source = text
        tokens.clear()
        start = 0
        current = 0
        column = 0
        line = 1
        level = 1
    }

    fun lex(full: Boolean = false): List<Token> {
        while (!isAtEnd()) {
            start = current
            lexToken(full)
        }

        tokens.add(Token("", TokenType.EOF, line = line, level = level))
        return tokens
    }

    fun lex(text: String, full: Boolean = false): List<Token> {
        reInit(text)
        while (!isAtEnd()) {
            start = current
            lexToken(full)
        }

        tokens.add(Token("", TokenType.EOF, line = line, level = level))
        return tokens
    }

    private fun isAtEnd(): Boolean = current >= source.length

    private fun lexToken(full: Boolean = false) {
        val char = advance()
        when (char) {
            // Handling brackets
            '[' -> addToken(TokenType.BRACKET_OPEN)
            ']' -> addToken(TokenType.BRACKET_CLOSE)
            '(' -> addToken(TokenType.PARENTHESIS_OPEN)
            ')' -> addToken(TokenType.PARENTHESIS_CLOSE)
            '{' -> {
                level++
                addToken(TokenType.BRACE_OPEN)
            }
            '}' -> {
                level--
                addToken(TokenType.BRACE_CLOSE)
            }
            ',' -> addToken(TokenType.COMMA)
            '.' -> addToken(
                when {
                    match('.') -> TokenType.DOT_DOT
                    else -> TokenType.DOT
                }
            )
            // Handling compound assignment operators
            '-' -> addToken(
                when {
                    match('=') -> TokenType.MINUS_EQUAL
                    match('-') -> TokenType.MINUS_MINUS
                    match('>') -> TokenType.ARROW // Check for '->' and add ARROW token
                    else -> TokenType.MINUS
                }
            )
            '+' -> addToken(if (match('=')) TokenType.PLUS_EQUAL else if (match('+')) TokenType.PLUS_PLUS else TokenType.PLUS)
            ';' -> addToken(TokenType.SEMICOLON)
            '*' -> addToken(if (match('=')) TokenType.STAR_EQUAL else TokenType.STAR)

            '!' -> addToken(if (match('=')) TokenType.BANG_EQUAL else TokenType.BANG)
            '=' -> addToken(if (match('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
            '<' -> addToken(if (match('=')) TokenType.LESS_EQUAL else TokenType.LESS)
            '>' -> addToken(if (match('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)

            // Additional operators
            '&' -> addToken(if (match('&')) TokenType.AND_AND else TokenType.AND)
            '|' -> addToken(if (match('|')) TokenType.OR_OR else TokenType.OR)
            '%' -> addToken(if (match('=')) TokenType.PERCENT_EQUAL else TokenType.PERCENT)
            '^' -> addToken(TokenType.CARET)
            '~' -> addToken(TokenType.TILDE)
            '`' -> addToken(TokenType.BACKQUOTE)
            '\\' -> addToken(TokenType.BACKSLASH)

            '0' -> if (peek() == 'x' || peek() == 'X') {
                lexHexadecimalNumber()
            } else {
                lexNumber()
            }
            '\'' -> {
                lexCharacterLiteral()
            }
            '/' -> {
                if (match('/')) {
                    // Handle single-line comment
                    val start = current
                    while (peek() != '\n' && !isAtEnd()) advance()

                    if(full) {
                        val value = source.substring(start, current)
                        addToken(TokenType.COMMENT_START, "//$value")
                    }
//                    if(full) {
//                        addToken(TokenType.COMMENT_TEXT)
//                        addToken(TokenType.COMMENT_END, "")
//                    }
                } else if (match('*')) {
                    // Handle multi-line comment
                    lexMultilineComment()
                } else {
                    addToken(if (match('=')) TokenType.SLASH_EQUAL else TokenType.SLASH)
                }
            }

            '\r', '\t' -> {
                if(full) addToken(TokenType.WHITE_SPACE)
            } // Ignore whitespace
            ' ' -> {
                if(full) addToken(TokenType.WHITE_SPACE, " ")
            } // Ignore whitespace
            '\n' -> {
                if(full) addToken(TokenType.NEW_LINE)
                line++
                column = 0
            }

            '"' -> lexString(full)

            // Handling boolean literals
            't' -> if (matchNext("rue")) addToken(TokenType.TRUE) else lexIdentifierOrKeyword()
            'f' -> if (matchNext("alse")) addToken(TokenType.FALSE) else lexIdentifierOrKeyword()

            // Additional punctuation marks
            ':' -> addToken(TokenType.COLON)
            '?' -> addToken(TokenType.QUESTION_MARK)



            // Handling string interpolation or templating characters (if applicable)
            '$' -> addToken(TokenType.DOLLAR)
            '@' -> addToken(TokenType.AT)
            '#' -> addToken(TokenType.HASH)
            else -> {
                when {
                    isDigit(char) -> lexNumber()
                    isAlpha(char) -> lexIdentifierOrKeyword()
                    else -> addToken(TokenType.UNKNOWN, char.toString())
                }
            }
        }
    }

    private fun lexHexadecimalNumber() {
        // Assume current position is at 'x' in '0x'
        advance() // Consume 'x'

        while (isHexDigit(peek())) advance()

        val value = source.substring(start, current)
        try {
            val number = value.toLong(16) // Convert the hexadecimal string to a number
            addToken(TokenType.NUMBER, number.toString())
        } catch (e: NumberFormatException) {
            throw LexicalException("Invalid hexadecimal number.", line, current)
        }
    }

    private fun isHexDigit(c: Char): Boolean {
        return c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    }

    private fun lexMultilineComment() {
        while (!(peek() == '*' && peekNext() == '/') && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }
        // Consume the closing '*/'
        if (!isAtEnd()) {
            advance()  // Consume '*'
            advance()  // Consume '/'
        }
        // Optionally add a token for the comment or ignore it
    }

    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]

    private fun peekNext(): Char = if (current + 1 >= source.length) '\u0000' else source[current + 1]

    private fun advance(): Char {
        current++
        column++
        return source[current - 1]
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false
        current++
        column++
        return true
    }

    private fun addToken(type: TokenType, value: String? = null) {
        val text = value ?: source.substring(start, current)
        tokens.add(Token(type = type, value = text, line = line, level = level, column = column))
    }

    private fun lexCharacterLiteral() {
        addToken(TokenType.CHARACTER_START, "\'")
        var value = advance() // Get the character inside the quotes

        if (value == '\\') {
            // Handle escape sequences like '\n', '\t', etc.
            value = when (advance()) {
                'n' -> '\n'
                't' -> '\t'
                // ... other escape sequences ...
                else -> value
            }
        }

        addToken(TokenType.CHAR, value.toString())
        if (peek() == '\'') {
            advance() // Consume the closing quote
            addToken(TokenType.CHARACTER_END, "\'")
        }
    }

    private fun lexString(full: Boolean = false) {
        val value = StringBuilder()
        addToken(TokenType.STRING_START, "\"")

        while (!isAtEnd() && peek() != '"' && peek() != '\n') {
            when (val c = peek()) {
//                '\\' -> { // Handle escape sequences
//                    advance() // Consume the backslash
//                    val escapedChar = when (peek()) {
//                        'n' -> '\n'
//                        't' -> '\t'
//                        '"' -> '"'
//                        '\\' -> '\\'
//                        // ... other escape sequences ...
//                        else -> peek() // If it's not a recognized escape sequence, take the character as-is
//                    }
//                    value.append(escapedChar)
//                    advance() // Consume the character after the backslash
//                }
                '$' -> {
                    if (peekNext() == '{') {
                        // If we have "${", it's the start of an interpolated expression
                        if (value.isNotEmpty()) {
                            addToken(TokenType.STRING, value.toString())
                            value.clear()
                        }
                        advance() // Consume the dollar sign
                        advance() // Consume the opening braced
                        addToken(TokenType.INTERPOLATION_START, "\${")
                        lexExpression(full)
                        addToken(TokenType.INTERPOLATION_END, "}")
                    } else if(isAlpha(peekNext())) {
                        if (value.isNotEmpty()) {
                            addToken(TokenType.STRING, value.toString())
                            value.clear()
                        }
                        // It's just a dollar sign followed by an identifier
                        advance() // Consume the dollar sign
                        val identifier = lexIdentifier()
                        addToken(TokenType.INTERPOLATION_START, "\$")
                        addToken(TokenType.IDENTIFIER, identifier)
                        addToken(TokenType.INTERPOLATION_END, "")
                    } else {
                        value.append(c)
                        advance() // Consume the character
                    }
                }
                else -> {
                    value.append(c)
                    advance() // Consume the character
                }
            }
        }

        if (isAtEnd()) {
            throw LexicalException("Unterminated string.", line, current)
        }

        // Consume the closing quote and emit the final STRING token
        val isQuote = peek() == '"'
        val isNewLine = peek() == '\n'
        if(!isNewLine) advance()
        if (value.isNotEmpty()) {
            addToken(TokenType.STRING, value.toString())
        }

        // Additionally, add a STRING_END token if your design requires it.
        addToken(TokenType.STRING_END, if(isQuote) "\"" else "")
    }

    private fun lexUntilInterpolationEnd() {
        // This method should lex tokens until it finds the end of the interpolation
        // This might involve lexing a whole expression, depending on your language's syntax
        // For simplicity, let's assume it just finds the closing brace
        while (!isAtEnd() && peek() != '}') {
            // You might call lexToken(), or handle the characters within the interpolation
            // depending on how complex your interpolations can be
            advance()
        }
        if (!isAtEnd()) {
            advance() // Consume the closing brace
        }
    }
    private fun lexExpression(full: Boolean = false) {
        // Continue lexing tokens until we reach the end of the interpolated expression
        while (!isAtEnd() && peek() != '}') {
            start = current
            lexToken(full)
//            when {
////                isAlpha(peek()) -> lexIdentifierOrKeyword()
//                isAlpha(peek()) -> {
//                    println("text: ${source.substring(start, current)}, start=$start, current=$current")
//
//                    val identifier = lexIdentifier()
//                    addToken(TokenType.IDENTIFIER, identifier)
//                }
//                isDigit(peek()) -> lexNumber()
//                peek() == ')' || peek() == '(' -> { // Example for handling parentheses
//                    advance()
//                    addToken(if (peek() == '(') TokenType.PARENTHESIS_OPEN else TokenType.PARENTHESIS_CLOSE)
//                }
//                // ... handle other operators and punctuation specific to your language
//                else -> {
//                    advance() // For any character that is not part of an expression, just consume it
//                }
//            }
        }

        // At the end of the expression, look for the closing brace '}' and consume it
        if (peek() == '}') {
            advance() // Consume the closing brace
        } else {
            // If the closing brace is not found, throw an error indicating the end of the interpolation was expected
            throw LexicalException("Expected '}' at the end of the interpolation.", line, current)
        }
    }

    private fun lexIdentifier(): String {
        // You may need to implement this method to handle identifiers following the dollar sign in interpolation
        // This could be a simple lexeme extraction or more complex if your language allows expressions in interpolation
        val start = current
        while (isAlphaNumeric(peek())) advance()
        return translateKazakhTextToEnglish(source.substring(start, current))
    }

    private fun lexString2() {
        val value = StringBuilder()
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            value.append(advance())
        }

        if (isAtEnd()) {
            throw LexicalException("Unterminated string.", line, current)
        }

        // Consume the closing quote.
        advance()

        addToken(TokenType.STRING, value.toString())
    }
    private fun lexNumber() {
        while (isDigit(peek())) advance()

        // Look for a fractional part.
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance()

            while (isDigit(peek())) advance()
        }

        val text = source.substring(start, current)
        val tokenType = when(text.toBpValueType()) {
            BpValueType.INT -> TokenType.INT
            BpValueType.DOUBLE -> TokenType.DOUBLE
            else -> TokenType.NUMBER
        }
        addToken(tokenType, text)
    }
    private fun translateKazakhToEnglish(c: Char): String {
        val kazakhToEnglishMap = mapOf(
            'Ә' to "A", 'ә' to "a",
            'І' to "I", 'і' to "i",
            'Ң' to "N", 'ң' to "n",
            'Ғ' to "G", 'ғ' to "g",
            'Ү' to "U", 'ү' to "u",
            'Ұ' to "U", 'ұ' to "u",
            'Қ' to "Q", 'қ' to "q",
            'Ө' to "O", 'ө' to "o",
            'Һ' to "H", 'һ' to "h",
            // Add mappings for other characters
            // Uppercase
            'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G",
            'Д' to "D", 'Е' to "E", 'Ё' to "Y", 'Ж' to "Z",
            'З' to "Z", 'И' to "I", 'Й' to "Y", 'К' to "K",
            'Л' to "L", 'М' to "M", 'Н' to "N", 'О' to "O",
            'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T",
            'У' to "U", 'Ф' to "F", 'Х' to "K", 'Ц' to "T",
            'Ч' to "C", 'Ш' to "S", 'Щ' to "c", 'Ъ' to "",
            'Ы' to "Y", 'Ь' to "", 'Э' to "E", 'Ю' to "U",
            'Я' to "Y",
            // Lowercase
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g",
            'д' to "d", 'е' to "e", 'ё' to "y", 'ж' to "z",
            'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
            'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
            'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "k", 'ц' to "t",
            'ч' to "c", 'ш' to "s", 'щ' to "c", 'ъ' to "b",
            'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "u",
            'я' to "y"
        )
        return kazakhToEnglishMap[c] ?: c.toString()
    }

    fun translateKazakhTextToEnglish(text: String): String {
        return text //.map { translateKazakhToEnglish(it) }.joinToString("")
    }

    private fun lexIdentifierOrKeyword() {
        while (isAlphaNumeric(peek())) advance()

        val text = source.substring(start, current)
        val type = when (text) {
            "class", "сынып" -> TokenType.CLASS
            "fun", "әдіс" -> TokenType.FUN
            "val", "мән" -> TokenType.VAL
            "var", "айны" -> TokenType.VAR
            "if", "егер" -> TokenType.IF
            "else", "басқаша" -> TokenType.ELSE
            "while", "әзірге" -> TokenType.WHILE
            "when", "қашан" -> TokenType.WHEN
            "for", "үшін" -> TokenType.FOR
            "return", "қайтар" -> TokenType.RETURN
            "break", "тоқтат" -> TokenType.BREAK
            "continue", "жалғастыр" -> TokenType.CONTINUE
            "try", "байқа" -> TokenType.TRY
            "catch", "ұстау" -> TokenType.CATCH
            "finally", "ақыры" -> TokenType.FINALLY
            "in", "ішінде" -> TokenType.IN
            "is", "болса" -> TokenType.IS
            "false", "жалған" -> TokenType.FALSE
            "true", "шын" -> TokenType.TRUE
            "null", "жоқ" -> TokenType.NULL
            "public", "жалпы" -> TokenType.PUBLIC
            "private", "жеке" -> TokenType.PRIVATE
            "override", "басыпөту" -> TokenType.OVERRIDE
            "until", "дейін" -> TokenType.UNTIL
            "throw", "лақтыр" -> TokenType.THROW
            "entity/enums", "тізім" -> TokenType.ENUM
            "constructor", "құрылысшы" -> TokenType.CONSTRUCTOR
            "interface", "байланыс" -> TokenType.INTERFACE
            "abstract", "жоба" -> TokenType.ABSTRACT
            "import", "енгіз" -> TokenType.IMPORT

            // TODO: add support
//            "const", "тұрақты" -> TokenType.CONST
//            "none", "ешқандай" -> TokenType.NONE
//            "async", "асинхрон" -> TokenType.ASYNC
//            "await", "күт" -> TokenType.AWAIT
//            "struct", "құрылым" -> TokenType.STRUCT
//            "do", "орында" -> TokenType.DO
//            "default", "әдепкі" -> TokenType.DEFAULT
//            "case", "жағдай" -> TokenType.CASE
//            "and", "және" -> TokenType.AND
//            "or", "немесе" -> TokenType.OR
            else -> TokenType.IDENTIFIER
        }

        addToken(type, if(type.isIndentifier()) translateKazakhTextToEnglish(text) else text)
    }
    private fun matchNext(expected: String): Boolean {
        for (i in expected.indices) {
            if (current + i >= source.length || source[current + i] != expected[i]) {
                return false
            }
        }
        advanceBy(expected.length)
        return true
    }

    private fun advanceBy(n: Int) {
        for (i in 0 until n) {
            advance()
        }
    }
}
