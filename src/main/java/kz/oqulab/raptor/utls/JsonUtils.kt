package kz.oqulab.raptor.utls

import kz.oqulab.raptor.BpValueType
import kz.oqulab.raptor.Token
import kz.oqulab.raptor.paradigms.ClassInstance
import kz.oqulab.raptor.utls.RaptorJson.mJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

fun JsonElement.isNull(): Boolean {
    return this is JsonNull
}
fun JsonElement.isString(): Boolean {
    return this is JsonPrimitive && this.isString
}

fun JsonElement.isInt(): Boolean {
    if (this !is JsonPrimitive) return false
    // Главное исправление: строка "123" НЕ считается Int
    return this.intOrNull != null && !this.isString
}

fun JsonElement.isDouble(): Boolean {
    if (this !is JsonPrimitive) return false
    return (this.doubleOrNull != null || this.floatOrNull != null) && !this.isString
}

fun JsonElement.isBoolean(): Boolean {
    if (this !is JsonPrimitive) return false
    return this.booleanOrNull != null && !this.isString
}

fun JsonElement.isNumber(): Boolean {
    return isInt() || isDouble()
}

//fun JsonElement.isNull(): Boolean {
//    return this is JsonNull || (this is JsonPrimitive && this.isNull)
//}

fun JsonElement.isJsonArray(): Boolean {
    return this is JsonArray
}

fun test() {
    val jsonElement: JsonElement = JsonPrimitive("hello")

    if (jsonElement.isString()) {
        // It's a string
        val stringValue = jsonElement.jsonPrimitive.content
        println("String value: $stringValue")
    } else if (jsonElement.isInt()) {
        // It's an int
        val intValue = jsonElement.jsonPrimitive.int
        println("Int value: $intValue")
    } else if (jsonElement.isDouble()) {
        // It's a double
        val doubleValue = jsonElement.jsonPrimitive.double
        println("Double value: $doubleValue")
    } else if (jsonElement.isBoolean()) {
        // It's a boolean
        val booleanValue = jsonElement.jsonPrimitive.boolean
        println("Boolean value: $booleanValue")
    } else if (jsonElement.isJsonArray()) {
        // It's a JSON array
        val jsonArray = jsonElement as JsonArray
        println("JSON Array: $jsonArray")
    }
}

fun String.toBpValueType(): BpValueType {
    // Attempt to parse as Boolean
    toBooleanStrictOrNull()?.let { return BpValueType.BOOLEAN }

    // Attempt to parse as Int
    toIntOrNull()?.let { return BpValueType.INT }

    // Attempt to parse as Double
    toDoubleOrNull()?.let { return BpValueType.DOUBLE }

    // Attempt to parse as JsonArray or JsonObject
    runCatching { Json.parseToJsonElement(this) }
        .getOrNull()?.let { return BpValueType.STRING }

    // Check for array types
    if (startsWith("[") && endsWith("]")) {
        val arrayElements = substring(1, length - 1).split(",").map { it.trim() }
        when {
            arrayElements.all { it.toIntOrNull() != null } ->
                return BpValueType.INT_ARRAY
            arrayElements.all { it.toDoubleOrNull() != null } ->
                return BpValueType.DOUBLE_ARRAY
            arrayElements.all { it.toBooleanStrictOrNull() != null } ->
                return BpValueType.BOOLEAN_ARRAY
            else -> BpValueType.STRING_ARRAY
        }
    }

    // Default to String
    return BpValueType.STRING
}
fun Any.getValueType(array: Boolean = false): BpValueType {
    try {
        if(this is Char) return BpValueType.CHAR
        this as JsonElement
        val propertyStr = this.toString()
        return when {
            propertyStr.startsWith("{") -> {
                if(array) BpValueType.JSON_ARRAY else BpValueType.JSON
            }
            propertyStr.startsWith("[") -> {
                if(this.jsonArray.isNotEmpty()) {
                    this.jsonArray[0].getValueType(true)
                } else {
                    BpValueType.JSON_ARRAY
                }
            }
            else -> {
                when {
                    isString() -> if(array) BpValueType.STRING_ARRAY else BpValueType.STRING
                    isInt() -> if(array) BpValueType.INT_ARRAY else BpValueType.INT
                    isDouble() -> if(array) BpValueType.DOUBLE_ARRAY else BpValueType.DOUBLE
                    isBoolean() -> if(array) BpValueType.BOOLEAN_ARRAY else BpValueType.BOOLEAN
                    else -> BpValueType.NULL
                }
            }
        }
    } catch (_: Exception) {}

    return BpValueType.NULL
}

fun String.toJsonElementByType(): JsonElement {
    // Attempt to parse as Boolean
    toBooleanStrictOrNull()?.let { return JsonPrimitive(it) }

    // Attempt to parse as Int
    toIntOrNull()?.let { return JsonPrimitive(it) }

    // Attempt to parse as Double
    toDoubleOrNull()?.let { return JsonPrimitive(it) }

    // Attempt to parse as JsonArray or JsonObject
    runCatching { Json.parseToJsonElement(this) }
        .getOrNull()?.let { return it }

    // Check for array types
    if (startsWith("[") && endsWith("]")) {
        val arrayElements = substring(1, length - 1).split(",").map { it.trim() }
        when {
            arrayElements.all { it.toIntOrNull() != null } ->
                return JsonArray(arrayElements.map { JsonPrimitive(it.toInt()) })
            arrayElements.all { it.toDoubleOrNull() != null } ->
                return JsonArray(arrayElements.map { JsonPrimitive(it.toDouble()) })
            arrayElements.all { it.toBooleanStrictOrNull() != null } ->
                return JsonArray(arrayElements.map { JsonPrimitive(it.toBooleanStrict()) })
            else -> return JsonArray(arrayElements.map { JsonPrimitive(it) })
        }
    }

    // Default to String
    return JsonPrimitive(this)
}


fun String.isValueString() = this.startsWith("\"")

fun String.removeVal() = this.replace("val", "").replace("var", "").trim()

fun String.isVal() = (this.contains("val ") || this.contains("var ")) || this.contains(" = ") || this.contains("=")


fun JsonElement.getString(): String {
    return when (this) {
        is JsonPrimitive -> {
            when {
                isString -> content
                isInt() -> int.toString()
                isDouble() -> double.toString()
                isBoolean() -> boolean.toString()
                else -> toString()
            }
        }
        else -> mJson.encodeToString(this)
    }
}

fun JsonElement.asNumber(): Double {
    return when {
        this.isInt() -> this.jsonPrimitive.int.toDouble()
        this.isDouble() -> this.jsonPrimitive.double
        else -> 0.0
    }
}


fun Token.toJson() = mJson.encodeToString(this)

fun Any.toJson(): JsonElement {
    return when (this) {
        is JsonElement -> this
        is ClassInstance -> this.toToJsonElement()
        is MutableList<*> -> mJson.encodeToJsonElement(this.map { it?.toJson() })
        is MutableMap<*, *> -> {
            val newMap = this.mapKeys { it.key?.toJson()?.getString() }.mapValues { it.value?.toJson() }
            val a = mJson.encodeToJsonElement(newMap)
            a
        }
        else -> JsonPrimitive(this.toString())
    }
}


/**
private fun lexExpression() {
    // Continue lexing tokens until we reach the end of the interpolated expression
    while (!isAtEnd() && peek() != '}') {
        when {
            isAlpha(peek()) -> lexIdentifierOrKeyword()
            isDigit(peek()) -> lexNumber()
            peek() == ')' || peek() == '(' -> { // Example for handling parentheses
                advance()
                addToken(if (peek() == '(') TokenType.PARENTHESIS_OPEN else TokenType.PARENTHESIS_CLOSE)
            }
            // ... handle other operators and punctuation specific to your language
            else -> {
                advance() // For any character that is not part of an expression, just consume it
            }
        }
    }

    // At the end of the expression, look for the closing brace '}' and consume it
    if (peek() == '}') {
        advance() // Consume the closing brace
    } else {
        // If the closing brace is not found, throw an error indicating the end of the interpolation was expected
        throw LexicalException("Expected '}' at the end of the interpolation.", line, current)
    }
}
private fun lexString() {
    val value = StringBuilder()
    println("lexString:START, peek=${peek()}")

    // Start after the opening quote, which has already been consumed
    while (!isAtEnd() && peek() != '"') {
        when (val c = peek()) {
            '\n' -> {
                line++
                value.append(advance())
            }
            '\\' -> { // Handle escape sequences
                advance() // Consume the backslash
                val escaped = when (peek()) {
                    'n' -> '\n'
                    't' -> '\t'
                    // ... other escape sequences ...
                    else -> peek()
                }
                value.append(escaped)
                advance() // Consume the character after the backslash
            }
            '$' -> { // Handle string interpolation
                // If interpolation, add the current string part and start the interpolation
                if (peekNext() == '{') {
                    if (value.isNotEmpty()) {
                        addToken(TokenType.STRING, value.toString())
                        value.clear()
                    }
                    advance() // Consume the dollar sign
                    advance() // Consume the opening brace
                    addToken(TokenType.INTERPOLATION_START, "{")
                    println("lexExpression:START, peek=${peek()}")
                    lexExpression() // Implement this method based on your language's rules
                    addToken(TokenType.INTERPOLATION_END, "}")
                } else {
                    value.append(c)
                    advance()
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
    advance()
    addToken(TokenType.STRING, value.toString())

    // Additionally, add a STRING_END token if your design requires it.
    addToken(TokenType.STRING_END, "")
}*/