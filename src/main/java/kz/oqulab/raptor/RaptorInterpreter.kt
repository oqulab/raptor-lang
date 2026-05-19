package kz.oqulab.raptor

import jdk.dynalink.linker.support.Guards.isInstance
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kz.oqulab.raptor.paradigms.ClassInstance
import kz.oqulab.raptor.utls.*
import kz.oqulab.raptor.utls.RaptorJson.mJson
import kotlin.collections.set

abstract class RaptorInterpreter(
    open val log: (JsonElement, Boolean) -> Unit = { _, _ -> },
    open val readInput: suspend () -> String = { "" },
    open val executionContext: ExecutionContext? = null
) {
    protected var currentScope: Scope = Scope.empty()   // теперь один current, а не список
    protected var returnEncountered = false
    protected var breakEncountered = false
    protected val variables = mutableMapOf<String, Any?>()

    // === АБСТРАКЦИИ (реализуются в наследниках) ===
    // getValue теперь можно сделать protected default-реализацией
    protected open fun getValue(key: String): Any? = currentScope.get(key)
    protected fun getDeclaredType(key: String): TypeNode? {
        return currentScope.getDeclaredType(key)
    }
    abstract fun setValue(key: String, value: Any?, newVar: Boolean = false, isMutable: Boolean = true)
    abstract fun findFunction(name: String): MethodNode?
    abstract fun findClass(name: String): ClassNode?

    // === ЕДИНЫЙ executeNode (эталон из ClassInstance) ===
    suspend fun executeNode(node: ASTNode?): Any? {
        return try {
            when (node) {
                is AssignmentNode -> {
                    if (node.target is IndexAccessNode) {
                        val target = executeNode(node.target.target)
                        val index = executeNode(node.target.index)
                        val value = executeNode(node.value)
                        when (target) {
                            is MutableList<*> -> (target as MutableList<Any?>)[(index as? JsonPrimitive)?.intOrNull ?: error("Index must be an integer")] = value
                            is MutableMap<*, *> -> (target as MutableMap<Any?, Any?>)[index] = value
                            else -> error("Assignment not supported on this type")
                        }
                    } else {
                        if (node.target is MemberAccessNode) executeMemberAssignment(node)
                        else executeAssignmentNode(node)
                    }
                }
                is IndexAccessNode -> {
                    val target = executeNode(node.target)
                    val index = executeNode(node.index)
                    when (target) {
                        is MutableList<*> -> (target as MutableList<Any?>)[(index as? JsonPrimitive)?.intOrNull ?: error("Index must be an integer")]
                        is MutableMap<*, *> -> target[index]
                        else -> error("Indexing not supported on this type")
                    }
                }
                is ListNode -> {
                    val target = node.elements.map { executeNode(it) }

                    target
                }
                is MapNode -> node.pairs.mapKeys { executeNode(it.key) }.mapValues { executeNode(it.value) }
                is ReturnNode -> {
                    returnEncountered = true
                    executeNode(node.value)
                }
                is BreakStatementNode -> {
                    breakEncountered = true
                    null
                }
                is ConcatenationNode -> evaluateConcatenationNode(node)
                is BinaryExpressionNode -> evaluateBinaryExpression(node)
                is VariableNode -> getValue(node.name)
                is NewVarNode -> executeNewVarNode(node)
                is LiteralNode -> node.value
                is CharNode -> node.value
                is IfNode -> executeIfNode(node)
                is WhenNode -> executeWhenNode(node)
                is BlockStatementNode -> {
                    var lastResult: Any? = null
                    node.statements.forEach { lastResult = executeNode(it) }
                    lastResult
                }
                is CompoundAssignmentNode -> executeCompoundAssignment(node)
                is MethodCallNode -> executeFunctionCallNode(node)
                is TryNode -> executeTryNode(node)
                is ThrowStatementNode -> executeThrowStatementNode(node)
                is WhileNode -> executeWhileNode(node)
                is ForLoopNode -> executeForLoop(node)
                is ForRangeNode -> executeForRangeNode(node)
                is ForInNode -> executeForInNode(node)
                is UnaryExpressionNode -> executeUnaryExpression(node)
                is GroupingNode -> executeNode(node.expression)
                is MethodNode -> null
                is MemberAccessNode -> {
                    val instance = executeNode(node.instance)
                    when (instance) {
                        is ClassInstance -> instance.getMember(node.memberName) ?: throw Exception("Unresolved reference '${node.memberName}'.")
                        else -> handlePrimitiveMemberAccess(instance, node.memberName)
                    }
                }
                else -> {
                    println("Unsupported node type: ${node?.let { mJson.encodeToString(it) }}")
                    null
                }
            }
        } catch (e: InterpreterException) {
            throw e
        } catch (e: RaptorException) {
            throw e
        } catch (e: Exception) {
            val (line, col) = getPosition(node)
            throw InterpreterException(
                message = e.message ?: e.toString(),
                line = line,
                column = col,
                cause = e
            )
        }
    }

    protected fun newVarStack() {
        currentScope = Scope.child(currentScope)   // создаём дочерний скоуп
    }

    protected fun removeLastVarStack() {
        currentScope.clear()
        currentScope = currentScope.parent ?: Scope.empty()
    }

    suspend fun executeIfNode(node: IfNode): Any? {
        val conditionResult = node.condition?.let { evaluateCondition(it) } ?: false

        when {
            conditionResult -> return executeBlock(node.thenBranch)
            node.branches.isNotEmpty() -> {
                node.branches.forEach {
                    if(it.condition?.let { evaluateCondition(it) } == true) {
                        return executeBlock(it.thenBranch)
                    }
                }
            }
            else -> {}
        }


        return if(node.elseBranch != null) executeBlock(node.elseBranch) else null
    }
    suspend fun executeWhenNode(node: WhenNode): Any? {
        val conditionValue = executeNode(node.condition)
        for (branch in node.branches) {
            val patternValue = executeNode(branch.pattern)
            val mainCondition = if(conditionValue != null) conditionValue == patternValue else patternValue == JsonPrimitive(true)
            if (mainCondition) {
                return executeNode(branch.result)
            }
        }

        return if(node.elseBranch != null) executeBlock(node.elseBranch) else null
    }

    suspend fun evaluateCondition(condition: ASTNode): Boolean {
        return when (val result = executeNode(condition)) {
            is JsonPrimitive -> {
                when {
                    result.isString -> result.contentOrNull != null
                    result.isInt() -> result.int != 0
                    result.isDouble() -> result.double != 0.0
                    result.isBoolean() -> result.boolean
                    else -> false
                }
            }
            else -> false
        }
    }

    private suspend fun executeBlock(node: ASTNode?): Any? {
        newVarStack()
        val result = when (node) {
            is BlockStatementNode -> {
                var lastResult: Any? = null
                for (statement in node.statements) {
                    lastResult = executeNode(statement)
                }
                lastResult
            }
            else -> executeNode(node)
        }
        removeLastVarStack()

        return result
    }

    private suspend fun executeFunBlock(node: ASTNode?): Any? {
        return when (node) {
            is BlockStatementNode -> {
                var lastResult: Any? = null
                for (statement in node.statements) {
                    lastResult = executeNode(statement)
                    if (returnEncountered) {
                        returnEncountered = false  // Сброс флага для будущего использования
                        break  // Прерывание выполнения блока при обнаружении return
                    }
                }
                lastResult
            }
            else -> executeNode(node)
        }
    }

    suspend fun executeFunctionCallNode(node: MethodCallNode, arguments: List<Any> = listOf()): Any? {
        var result: Any? = null

        val classNode = findClass(node.name)
        val function = findFunction(node.name)

        when {
            node.instance != null -> {
                val instance = executeNode(node.instance)
                println("instance: $instance, ${instance is JsonPrimitive}")
                when(instance) {
                    is ClassInstance -> {
                        val evaluatedArguments = node.arguments.mapNotNull {
                            executeNode(it)
                        }

                        // Связывание аргументов с параметрами функции

                        result = instance.runMethod(node.copy(instance = null), evaluatedArguments)
                    }
                    is MutableList<*> -> {
                        instance as MutableList<Any?>
                        when(node.name) {
                            "add" -> {
                                if(node.arguments.size == 2) {
                                    val index = executeNode(node.arguments[0])
                                    val value = executeNode(node.arguments[1])
                                    instance.add((index as? JsonPrimitive)?.intOrNull ?: error("Index must be an integer"), value)
                                } else if(node.arguments.size == 1) {
                                    val value = executeNode(node.arguments[0])
                                    instance.add(value)
                                }
                            }
                            "removeAt" -> {
                                if(node.arguments.size == 1) {
                                    val index = executeNode(node.arguments[0])
                                    instance.removeAt((index as? JsonPrimitive)?.intOrNull ?: error("Index must be an integer"))
                                }
                            }
                            "remove" -> {
                                if(node.arguments.size == 1) {
                                    val value = executeNode(node.arguments[0])
                                    instance.remove(value)
                                }
                            }
                            "indexOf" -> {
                                if(node.arguments.size == 1) {
                                    val value = executeNode(node.arguments[0])
                                    result = JsonPrimitive(instance.indexOf(value))
                                }
                            }
                            "contains" -> {
                                if(node.arguments.size == 1) {
                                    result = JsonPrimitive(instance.contains(executeNode(node.arguments[0])))
                                }
                            }
                            "isEmpty" -> {
                                result = JsonPrimitive(instance.isEmpty())
                            }
                            "isNotEmpty" -> {
                                result = JsonPrimitive(instance.isNotEmpty())
                            }
                            // NEW
                            "toString" -> {
                                result = JsonPrimitive(instance.toString())
                            }
                            "joinToString" -> {
                                val separator = if(node.arguments.size == 1) {
                                    (executeNode(node.arguments[0]) as? JsonPrimitive)?.getString() ?: ", "
                                } else ", "
                                result = JsonPrimitive(instance.joinToString(separator))
                            }
                            "reversed" -> {
                                result = instance.reversed()
                            }
                            else -> {}
                        }

                        JsonPrimitive(instance.size)
                    }
                    is MutableMap<*, *> -> {
                        instance as MutableMap<Any?, Any?>

                        when(node.name) {
                            "put" -> {
                                if(node.arguments.size == 2) {
                                    val index = executeNode(node.arguments[0])
                                    val value = executeNode(node.arguments[1])
                                    instance[index] = value
                                }
                            }
                            "remove" -> {
                                if(node.arguments.size == 1) {
                                    val value = executeNode(node.arguments[0])
                                    instance.remove(value)
                                }
                            }
                            "containsKey" -> {
                                if(node.arguments.size == 1) {
                                    result = JsonPrimitive(instance.containsKey(executeNode(node.arguments[0])))
                                }
                            }
                            "containsValue" -> {
                                if(node.arguments.size == 1) {
                                    result = JsonPrimitive(instance.containsValue(executeNode(node.arguments[0])))
                                }
                            }
                            "isEmpty" -> {
                                result = JsonPrimitive(instance.isEmpty())
                            }
                            "isNotEmpty" -> {
                                result = JsonPrimitive(instance.isNotEmpty())
                            }
                            "toString" -> {
                                result = JsonPrimitive(instance.toString())
                            }
                            else -> {}
                        }
                        JsonPrimitive(instance.size)
                    }
                    is Char -> {
                        when (node.name) {
                            "isChar" -> {
                                result = JsonPrimitive(false)
                            }
                        }
                    }
                    is JsonPrimitive -> {
                        when (node.name) {
                            "toInt" -> {
                                if (instance.isString) {
                                    val str = instance.getString().trim()
                                    try {
                                        result = JsonPrimitive(str.toInt())
                                    } catch (e: Exception) {
                                        throw InterpreterException(
                                            message = "Невозможно преобразовать '$str' в Int",
                                            line = node.line,
                                            column = node.column
                                        )
                                    }
                                } else if (instance.isDouble()) {
                                    result = JsonPrimitive(instance.jsonPrimitive.double.toInt())
                                } else if (instance.isInt()) {
                                    result = JsonPrimitive(instance.jsonPrimitive.int)
                                } else {
                                    throw Exception("Type mismatch: cannot convert '$instance' to 'Int' using 'toInt()'. This method is only available for 'String' or 'Double'.")
                                }
                            }
                            "toDouble" -> {
                                if (instance.isString) {
                                    val str = instance.getString().trim()
                                    try {
                                        result = JsonPrimitive(str.toDouble())
                                    } catch (e: Exception) {
                                        throw InterpreterException(
                                            message = "Cannot convert '$str' to Double",
                                            line = node.line,
                                            column = node.column
                                        )
                                    }
                                } else if (instance.isNumber()) {
                                    result = JsonPrimitive(instance.jsonPrimitive.double)
                                } else {
                                    throw Exception("Type mismatch: cannot convert '$instance' to 'Double' using 'toDouble()'. This method is only available for 'String' or 'Int'.")
                                }
                            }
                            "toBoolean" -> {
                                if (instance.isString) {
                                    val str = instance.getString().trim().lowercase()
                                    result = JsonPrimitive(str == "true")
                                } else {
                                    throw Exception("Type mismatch: cannot convert '$instance' to 'Boolean' using 'toBoolean()'. This method is only available for 'String'.")
                                }
                            }
                            "toString" -> {
                                result = JsonPrimitive(instance.getString())
                            }
                            "isInt" -> {
                                result = JsonPrimitive(instance.isInt())
                            }
                            "isChar" -> {
                                result = JsonPrimitive(false)
                            }
                            "isDouble" -> {
                                result = JsonPrimitive(instance.isDouble())
                            }
                            "isBoolean" -> {
                                result = JsonPrimitive(instance.isBoolean())
                            }
                            "isString" -> {
                                result = JsonPrimitive(instance.isString())
                            }
                            "isNull" -> {
                                result = JsonPrimitive(instance.isNull())
                            }
                            else -> {
                                if (instance.isString) {
                                    val value = instance.getString()
                                    when(node.name) {
                                        "trim" -> {
                                            result = JsonPrimitive(value.trim())
                                        }
                                        "trimStart" -> {
                                            result = JsonPrimitive(value.trimStart())
                                        }
                                        "trimEnd" -> {
                                            result = JsonPrimitive(value.trimEnd())
                                        }
                                        "contains" -> {
                                            if(node.arguments.isNotEmpty()) {
                                                val other = (executeNode(node.arguments[0]) as? JsonPrimitive)?.getString()
                                                val ignoreCase = (node.arguments.getOrNull(1)?.let { executeNode(it) as? JsonPrimitive })?.booleanOrNull ?: false
                                                if(other != null) {
                                                    result = JsonPrimitive(value.contains(other, ignoreCase))
                                                } else throw IllegalArgumentException("The 'other' string parameter cannot be null in contains() method.")
                                            }
                                        }
                                        "isEmpty" -> {
                                            result = JsonPrimitive(value.isEmpty())
                                        }
                                        "isNotEmpty" -> {
                                            result = JsonPrimitive(value.isNotEmpty())
                                        }
                                        "isBlack" -> {
                                            result = JsonPrimitive(value.isBlank())
                                        }
                                        "isNotBlack" -> {
                                            result = JsonPrimitive(value.isNotBlank())
                                        }
                                        "replace" -> {
                                            if(node.arguments.size == 2) {
                                                val old = (executeNode(node.arguments[0]) as? JsonPrimitive)?.getString()
                                                val new = (executeNode(node.arguments[1]) as? JsonPrimitive)?.getString()
                                                if(old != null && new != null) {
                                                    println("replace:2: old=$old, new=$new, ignoreCase=false, ins=${value}, rep=${value.replace(old, new)}")
                                                    result = JsonPrimitive(value.replace(old, new))
                                                } else throw IllegalArgumentException("Both 'old' and 'new' parameters must be non-null in replace() method. 'old' was ${old?.let { "not null" } ?: "null"}, 'new' was ${new?.let { "not null" } ?: "null"}.")
                                            } else if(node.arguments.size == 3) {
                                                val old = (executeNode(node.arguments[0]) as? JsonPrimitive)?.getString()
                                                val new = (executeNode(node.arguments[1]) as? JsonPrimitive)?.getString()
                                                val ignoreCase = (executeNode(node.arguments[2]) as? JsonPrimitive)?.booleanOrNull ?: false
                                                println("replace:3: old=$old, new=$new, ignoreCase=$ignoreCase")
                                                if(old != null && new != null) {
                                                    result = JsonPrimitive(value.replace(old, new, ignoreCase))
                                                } else throw IllegalArgumentException("Both 'old' and 'new' parameters must be non-null in replace() method. 'old' was ${old?.let { "not null" } ?: "null"}, 'new' was ${new?.let { "not null" } ?: "null"}.")
                                            }
                                        }
                                        "substr" -> {
                                            if(node.arguments.size == 1) {
                                                val start = (executeNode(node.arguments[0]) as? JsonPrimitive)?.intOrNull ?: -1
                                                if (start < 0 || start >= value.length) {
                                                    throw IllegalArgumentException("The 'start' parameter in substr() must be within the range of the string. 'start' was $start, but expected a value between 0 and ${value.length - 1}.")
                                                } else {
                                                    result = JsonPrimitive(value.substring(start))
                                                }
                                            } else if(node.arguments.size == 2) {
                                                val start = (executeNode(node.arguments[0]) as? JsonPrimitive)?.intOrNull ?: -1
                                                val length = (executeNode(node.arguments[0]) as? JsonPrimitive)?.intOrNull ?: -1
                                                if (start < 0 || length < 0 || start + length > value.length) {
                                                    throw IllegalArgumentException("Invalid 'start' or 'length' parameters in substr(). 'start' should be >= 0 and < string length, 'length' should be >= 0, and 'start + length' should be <= string length. Provided: start = $start, length = $length, string length = ${value.length}.")
                                                } else {
                                                    result = JsonPrimitive(value.substring(start))
                                                }
                                            }
                                        }
                                        "substring" -> {
                                            if(node.arguments.size == 1) {
                                                val startIndex = (executeNode(node.arguments[0]) as? JsonPrimitive)?.intOrNull ?: -1
                                                if (startIndex < 0 || startIndex > value.length) {
                                                    throw IllegalArgumentException("The 'startIndex' parameter in substring() must be within the range of the string's length. Provided 'startIndex' was $startIndex, but it must be between 0 and ${value.length}.")
                                                } else {
                                                    println("substring:1: startIndex=$startIndex, value=$value, substring=${value.substring(startIndex)}")
                                                    result = JsonPrimitive(value.substring(startIndex))
                                                }
                                            } else if(node.arguments.size == 2) {
                                                val startIndex = (executeNode(node.arguments[0]) as? JsonPrimitive)?.intOrNull ?: -1
                                                val endIndex = (executeNode(node.arguments[1]) as? JsonPrimitive)?.intOrNull ?: -1
                                                if (startIndex < 0 || endIndex > value.length || startIndex > endIndex) {
                                                    throw IllegalArgumentException("Invalid 'startIndex' or 'endIndex' parameters in substring(). 'startIndex' should be >= 0, 'endIndex' should be <= string length, and 'startIndex' should be <= 'endIndex'. Provided: startIndex = $startIndex, endIndex = $endIndex, string length = ${value.length}.")
                                                } else {
                                                    println("substring:2: startIndex=$startIndex,endIndex=$endIndex, value=$value, substring=${value.substring(startIndex, endIndex)}")
                                                    result = JsonPrimitive(value.substring(startIndex, endIndex))
                                                }
                                            }
                                        }
                                        "reversed" -> {
                                            result = JsonPrimitive(value.reversed())
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
            classNode != null -> {
                val evaluatedArguments = node.arguments.mapNotNull {
                    val value = executeNode(it)
                    value
                }
                val instance = ClassInstance(
                    classNode,
                    "init",
                    evaluatedArguments = evaluatedArguments,
                    parent = null, // будет переопределено в наследнике
                    executionContext = executionContext
                ) { d, n -> log(d, n) }
                instance.init()
                result = instance
            }
            function != null -> {
                val evaluatedArguments = arguments.ifEmpty {
                    node.arguments.mapNotNull {
                        val value = executeNode(it)
                        value
                    }
                }
                newVarStack()
                function.parameters.forEachIndexed { index, parameter ->
                    val value = evaluatedArguments.getOrNull(index)


                    currentScope.set(parameter.name, value ?: executeNode(parameter.defaultValue))
                }
                try {
                    result = executeFunBlock(function.body)
                } finally {
                    removeLastVarStack()
                }
            }
            else -> {
                val evaluatedArguments = node.arguments.mapNotNull {
                    executeNode(it)?.toJson()
                }

                when (node.name) {
                    "println", "zolbasypsygar" -> {
                        if (evaluatedArguments.size == 1) log(evaluatedArguments[0], true)
                        else log(JsonPrimitive(evaluatedArguments.joinToString { it.getString() }), true)
                    }
                    "print", "basypsygar" -> {
                        if (evaluatedArguments.size == 1) log(evaluatedArguments[0], false)
                        else log(JsonPrimitive(evaluatedArguments.joinToString { it.getString() }), false)
                    }
                    "readln", "oqy" -> {
                        val input = executionContext?.readInput?.invoke() ?: ""
                        result = JsonPrimitive(input)
                    }
                    // === НОВЫЕ ФУНКЦИИ ===
                    "listOf" -> {
                        result = node.arguments.map { executeNode(it) }.toMutableList()
                    }
                    "emptyList" -> {
                        result = mutableListOf<Any?>()
                    }
                    "mapOf" -> {
                        val args = node.arguments.map { executeNode(it) }
                        if (args.size % 2 != 0) {
                            throw InterpreterException(
                                message = "mapOf() ожидает чётное количество аргументов (ключ-значение)",
                                line = node.line,
                                column = node.column
                            )
                        }
                        val map = mutableMapOf<Any?, Any?>()
                        for (i in args.indices step 2) {
                            map[args[i]] = args[i + 1]
                        }
                        result = map
                    }
                    "emptyMap" -> {
                        result = mutableMapOf<Any?, Any?>()
                    }
                }
            }
        }

        return result
    }

    suspend fun executeTryNode(node: TryNode): Any? {
        try {
            executeNode(node.tryBlock)
        } catch (e: Throwable) {
            handleCatchBlocks(e, node.catchBlocks)
        } finally {
            node.finallyBlock?.let { executeNode(it) }
        }
        return null // Блоки try-catch обычно не возвращают значение
    }

    suspend fun executeThrowStatementNode(node: ThrowStatementNode): Any? {
        val result = executeNode(node.expression)
        if(result !is ClassInstance) throw InterpreterException(
            "Type mismatch: inferred type is '${result?.getValueType()}', but 'Throwable' was expected.",
            line = node.line,
            column = node.column)
        throw RaptorException(result)
    }

    suspend fun handleCatchBlocks(exception: Throwable, catchBlocks: List<CatchNode>) {
        var handled = false
        for (catchBlock in catchBlocks) {
            if (exception is RaptorException && isExceptionMatch(exception, catchBlock.exceptionType.value)) {
                newVarStack()

                setValue(catchBlock.exceptionVar.value, exception.classInstance, false)
                executeNode(catchBlock.block)
                handled = true

                removeLastVarStack()
                break
            }
        }

        if (!handled) {
            when (exception) {
                is Error -> throw exception
                is RuntimeException -> throw exception
                is Exception -> throw exception
                else -> throw RuntimeException(exception)
            }
        }
    }

    private fun isExceptionMatch(exception: Throwable, exceptionType: String): Boolean {
        // Базовые типы — ловят всё
//        if (exceptionType == "Any") return true
//        if (exceptionType == "Exception" || exceptionType == "Throwable") return true

        // 1. RaptorException — несёт classNode с Raptor-классом исключения
        if (exception is RaptorException && exception.classInstance != null) {
            val thrownClass = exception.classInstance
            // Прямое совпадение имени класса
            if (thrownClass.classNode.name == exceptionType) return true
            // Проверка цепочки наследования
            for (parent in thrownClass.classNode.inheritances) {
                if (parent.name == exceptionType) return true
            }
        }

//        // 2. InterpreterException — ошибки рантайма интерпретатора
//        if (exception is InterpreterException) {
//            if (exceptionType == "InterpreterException" || exceptionType == "RuntimeException") return true
//        }
//
//        // 3. Стандартные ошибки JVM
//        if (exception is RuntimeException && exceptionType == "RuntimeException") return true

        return false
    }

    suspend fun executeWhileNode(node: WhileNode): Any? {
        newVarStack()
        var c = 0
        while (evaluateCondition(node.condition)) {
//        while (evaluateCondition(node.condition)) {
            executeNode(node.body)
            if(breakEncountered) {
                breakEncountered = false
                break
            }
            c++
        }
        removeLastVarStack()
        return null // Циклы while обычно не возвращают значение
    }
    private suspend fun executeForLoop(node: ForLoopNode) {
        newVarStack()
        // Инициализация цикла
        executeNode(node.initializer)

        var c = 0
        while (evaluateCondition(node.condition) && c < 10000) {
            // Выполнение тела цикла
            for (statement in node.body) {
                executeNode(statement)

                // Проверка наличия флага return
                if (returnEncountered) {
                    returnEncountered = false
                    return  // Прерывание цикла при обнаружении return
                }
                if(breakEncountered) {
                    breakEncountered = false
                    return
                }
            }

            // Выполнение инкремента
            executeNode(node.increment)
            c++
        }
        removeLastVarStack()
    }

    private suspend fun executeForInNode(node: ForInNode) {
        newVarStack()
        val iterable = executeNode(node.iterable)
        if(iterable is JsonPrimitive && iterable.isString) {
            val iterableValue = iterable.getString()

            for (element in iterableValue) {
                // Set the loop variable to the current element
                node.variable as VariableNode
                setValue(node.variable.name, element.toString())

                // Execute the loop body
                for (statement in node.body) {
                    executeNode(statement)

                    // Проверка наличия флага return
                    if (returnEncountered) {
                        returnEncountered = false
                        return  // Прерывание цикла при обнаружении return
                    }
                    if(breakEncountered) {
                        breakEncountered = false
                        return
                    }
                }
            }
        } else {
            val iterableValue = iterable as? Iterable<*>
                ?: throw RuntimeException("The right-hand side of 'in' must be an iterable object")

            for (element in iterableValue) {
                // Set the loop variable to the current element
                node.variable as VariableNode
                setValue(node.variable.name, element)

                // Execute the loop body
                for (statement in node.body) {
                    executeNode(statement)

                    // Проверка наличия флага return
                    if (returnEncountered) {
                        returnEncountered = false
                        return  // Прерывание цикла при обнаружении return
                    }
                    if(breakEncountered) {
                        breakEncountered = false
                        return
                    }
                }
            }
        }
        removeLastVarStack()
    }
    private suspend fun executeForRangeNode(node: ForRangeNode) {
        newVarStack()
        val startValue = executeNode(node.range.start) as? JsonElement
            ?: throw RuntimeException("Range start value must be a number")
        val endValue = executeNode(node.range.end) as? JsonElement
            ?: throw RuntimeException("Range end value must be a number")

        if(!startValue.isInt()) throw RuntimeException("Range start value must be a number")
        if(!endValue.isInt()) throw RuntimeException("Range end value must be a number")

        val a1 = startValue.jsonPrimitive.intOrNull ?: 0
        val b1 = endValue.jsonPrimitive.intOrNull ?: 0
        val range = if (node.range.inclusive) {
            a1..b1
        } else {
            a1 until b1
        }

        for (i in range) {
            // Set the loop variable to the current value
            node.variable as VariableNode
            setValue(node.variable.name, JsonPrimitive(i))

            // Execute the loop body
            for (statement in node.body) {
                executeNode(statement)

                // Проверка наличия флага return
                if (returnEncountered) {
                    returnEncountered = false
                    return  // Прерывание цикла при обнаружении return
                }
                if(breakEncountered) {
                    breakEncountered = false
                    return
                }
            }
        }
        removeLastVarStack()
    }

    suspend fun executeUnaryExpression(node: UnaryExpressionNode): Any {
        if(node.operand is MemberAccessNode) {
            val target = node.operand
            val targetInstance = (executeNode(target.instance) as? ClassInstance)
                ?: throw RuntimeException("Target instance not found for increment/decrement")

            val currentValue = targetInstance.getIntValue(target.memberName) as? JsonPrimitive
                ?: throw RuntimeException("Current value is not a number for increment/decrement")

            val newValue = when (node.token.value) {
                "-" -> negate(currentValue)
                "++" -> incrementDecrement(currentValue, 1, node.isPrefix)
                "--" -> incrementDecrement(currentValue, -1, node.isPrefix)
                "!" -> if (currentValue.isBoolean()) JsonPrimitive(!currentValue.boolean) else throw IllegalArgumentException("Logical negation applied to non-boolean type")
                else -> throw UnsupportedOperationException("Unsupported unary operator: ${node.token.value}")
            }
            targetInstance.setValue(target.memberName, newValue, false)
            return newValue
        } else {
            val operand = executeNode(node.operand) ?: throw IllegalArgumentException("UnaryExpression '${node.token.value}' applied to null type")
            val mo = node.operand
            if(mo is VariableNode) {
                val newValue = when (node.token.value) {
                    "-" -> {
                        if (operand !is JsonPrimitive) {
                            throw IllegalArgumentException("UnaryExpression '${node.token.value}' applied to non-Primitive type, operand=${operand.getValueType()}")
                        }
                        negate(operand)
                    }
                    "++" -> incrementDecrement(operand, 1, node.isPrefix)
                    "--" -> incrementDecrement(operand, -1, node.isPrefix)
                    "!" -> {
                        if (operand !is JsonPrimitive) {
                            throw IllegalArgumentException("UnaryExpression '${node.token.value}' applied to non-Primitive type, operand=${operand.getValueType()}")
                        }
                        if (operand.isBoolean()) JsonPrimitive(!operand.boolean) else throw IllegalArgumentException("Logical negation applied to non-boolean type")
                    }
                    else -> throw UnsupportedOperationException("Unsupported unary operator: ${node.token.value}")
                }
                setValue(mo.name, newValue, false)
                return if (node.isPrefix) newValue else operand
            } else  if(mo is LiteralNode) {
                if (operand !is JsonPrimitive) {
                    throw IllegalArgumentException("UnaryExpression '${node.token.value}' applied to non-Primitive type, operand=${operand?.getValueType()}")
                }
                return when (node.token.value) {
                    "-" -> negate(operand)
                    "++" -> incrementDecrement(operand, 1, node.isPrefix)
                    "--" -> incrementDecrement(operand, -1, node.isPrefix)
                    "!" -> if (operand.isBoolean()) JsonPrimitive(!operand.boolean) else throw IllegalArgumentException("Logical negation applied to non-boolean type")
                    else -> throw UnsupportedOperationException("Unsupported unary operator: ${node.token.value}")
                }
            }
        }

        return JsonPrimitive(true)
    }

    private suspend fun evaluateConcatenationNode(node: ConcatenationNode): JsonElement {
        val stringBuilder = StringBuilder()
        node.parts.forEach { part ->
            val value = executeNode(part)
            val partValue = if(value is JsonElement) value.getString() else value?.toString()

            partValue?.let {
                stringBuilder.append(it)
            }
        }
        return JsonPrimitive(stringBuilder.toString())
    }

    private fun negate(operand: JsonPrimitive): JsonPrimitive {
        return JsonPrimitive(
            when {
                operand.isInt() -> -operand.int
                operand.isDouble() -> -operand.double
                else -> throw IllegalArgumentException("Unsupported numeric type for increment/decrement")
            }
        )
    }

    private fun incrementDecrement(operand: Any?, delta: Int, isPrefix: Boolean): Any {
        if(operand is Char) return operand+delta
        if (operand !is JsonPrimitive) {
            throw IllegalArgumentException("Increment/Decrement applied to non-numeric type")
        }

        val updatedValue = JsonPrimitive(
            when {
                operand.isInt() -> operand.int + delta
                operand.isDouble() -> operand.double + delta
                else -> throw IllegalArgumentException("Unsupported numeric type for increment/decrement")
            }
        )

        // Возвращаемое значение зависит от того, является ли операция предварительной
//        return if (isPrefix) updatedValue else operand
        return updatedValue
    }

    private suspend fun evaluateBinaryExpression(node: BinaryExpressionNode): Any {
        val left = executeNode(node.left)
        val right = executeNode(node.right)

        return when (node.token.value) {
            // Логические операторы — отдельная обработка!
            "&&", "||" -> evaluateLogicalOperator(left, right, node.token)

            // Сравнения
            "==", "!=", "<", ">", "<=", ">=" -> evaluateComparisonOperator(left, right, node.token)

            // Арифметика
            "+", "-", "*", "/", "%" -> evaluateArithmeticOperator(left, right, node.token)

            else -> throw UnsupportedOperationException("Unsupported binary operator: ${node.token}")
        }
    }
    /**
     * Правильная обработка логических операторов && и ||
     */
    private fun evaluateLogicalOperator(a: Any?, b: Any?, operator: Token): JsonElement {
        val aBool = when (a) {
            is Boolean -> a
            is JsonPrimitive -> a.booleanOrNull ?: (a.intOrNull != 0) ?: false
            else -> false
        }

        val bBool = when (b) {
            is Boolean -> b
            is JsonPrimitive -> b.booleanOrNull ?: (b.intOrNull != 0) ?: false
            else -> false
        }

        val result = when (operator.value) {
            "&&" -> aBool && bBool
            "||" -> aBool || bBool
            else -> false
        }

        return JsonPrimitive(result)
    }
    private fun evaluateBinaryExpression(left: Any?, right: Any?, operator: Token): Any? {
        // Convert operands to JsonPrimitives if they aren't already
        val leftPrimitive = left as? JsonPrimitive ?: JsonPrimitive(left.toString())
        val rightPrimitive = right as? JsonPrimitive ?: JsonPrimitive(right.toString())

        return when (operator.type) {
            // Arithmetic operators
            TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT -> evaluateArithmeticOperator(leftPrimitive, rightPrimitive, operator)

            // Comparison operators
            TokenType.EQUAL_EQUAL,
            TokenType.BANG_EQUAL,
            TokenType.LESS,
            TokenType.GREATER,
            TokenType.LESS_EQUAL,
            TokenType.GREATER_EQUAL,
            TokenType.AND_AND,
            TokenType.OR_OR -> evaluateComparisonOperator(leftPrimitive, rightPrimitive, operator)

            else -> throw UnsupportedOperationException("Unsupported operator: ${operator.value}")
        }
    }

    private fun evaluateArithmeticOperator(a: Any?, b: Any?, operator: Token): Any {
        return when {
            a is JsonElement && b is JsonElement -> {
                val aType = a.getValueType()
                val bType = b.getValueType()

                when {
                    // 1. Оба числа → арифметика
                    aType.isNumber() && bType.isNumber() -> {
                        performArithmetic(a, b, operator)
                    }

                    // 2. Хотя бы один — строка → конкатенация
                    a.isString() || b.isString() -> {
                        val aStr = a.getString()
                        val bStr = b.getString()
                        JsonPrimitive(aStr + bStr)
                    }

                    // 3. Булевы
                    a.isBoolean() && b.isBoolean() -> {
                        val a1 = a.jsonPrimitive.booleanOrNull ?: false
                        val b1 = b.jsonPrimitive.booleanOrNull ?: false
                        JsonPrimitive(a1.and(b1))
                    }

                    // 4. Всё остальное (включая null) — ошибка
                    else -> {
                        throw InterpreterException(
                            message = "Operator '${operator.value}' is not supported for types: $aType and $bType",
                            line = operator.line,
                            column = operator.column
                        )
                    }
                }
            }

            a is VariableNode && b is JsonElement -> {
                getJsonValue(a.name)?.let { performArithmetic(it, b, operator) } ?: JsonNull
            }

            a is JsonElement && b is VariableNode -> {
                getJsonValue(b.name)?.let { performArithmetic(a, it, operator) } ?: JsonNull
            }

            (a != null && b != null) && (a is Char || b is Char) -> {
                handleCharArithmetic(a, b, operator)
            }

            else -> {
                val a1 = (a as? JsonPrimitive)?.getString() ?: ""
                val b2 = (b as? JsonPrimitive)?.getString() ?: ""
                JsonPrimitive("$a1$b2")
            }
        }
    }

    /**
     * Единая, правильная арифметика для всех чисел.
     * Используется и в evaluateArithmeticOperator, и в executeArith.
     */
    private fun performArithmetic(
        a: JsonElement,
        b: JsonElement,
        operator: Token,
        node: ASTNode? = null   // для позиции ошибки
    ): JsonElement {
        val aIsInt = a.isInt()
        val bIsInt = b.isInt()
        val bothInt = aIsInt && bIsInt

        return when (operator.value) {
            // + - *
            "+", "-", "*" -> {
                if (bothInt) {
                    val a1 = a.jsonPrimitive.int
                    val b1 = b.jsonPrimitive.int
                    val result = when (operator.value) {
                        "+" -> a1 + b1
                        "-" -> a1 - b1
                        "*" -> a1 * b1
                        else -> 0
                    }
                    JsonPrimitive(result)
                } else {
                    val a1 = a.jsonPrimitive.doubleOrNull ?: 0.0
                    val b1 = b.jsonPrimitive.doubleOrNull ?: 0.0
                    val result = when (operator.value) {
                        "+" -> a1 + b1
                        "-" -> a1 - b1
                        "*" -> a1 * b1
                        else -> 0.0
                    }
                    JsonPrimitive(result)
                }
            }

            // Деление
            "/" -> {
                if (bothInt) {
                    val a1 = a.jsonPrimitive.int
                    val b1 = b.jsonPrimitive.int
                    if (b1 == 0) {
                        throw Exception("Division by zero")
                    }
                    JsonPrimitive(a1 / b1)
                } else {
                    val a1 = a.jsonPrimitive.doubleOrNull ?: 0.0
                    val b1 = b.jsonPrimitive.doubleOrNull ?: 0.0
                    if (b1 == 0.0) {
                        throw Exception("Division by zero")
                    }
                    JsonPrimitive(a1 / b1)
                }
            }

            // Остаток от деления
            "%" -> {
                if (bothInt) {
                    val a1 = a.jsonPrimitive.int
                    val b1 = b.jsonPrimitive.int
                    if (b1 == 0) {
                        throw InterpreterException(
                            message = "Division by zero (modulo)",
                            line = node?.line ?: -1,
                            column = node?.column ?: -1
                        )
                    }
                    JsonPrimitive(a1 % b1)
                } else {
                    val a1 = a.jsonPrimitive.doubleOrNull ?: 0.0
                    val b1 = b.jsonPrimitive.doubleOrNull ?: 0.0
                    JsonPrimitive(a1 % b1)
                }
            }

            else -> JsonPrimitive(0)
        }
    }

    /**
     * Обработка арифметики с Char
     * 'A' + 3  → 'D'
     * 'D' - 'A' → 3
     */
    private fun handleCharArithmetic(
        a: Any,
        b: Any,
        operator: Token
    ): Any {
        return when (operator.value) {
            "+" -> {
                when {
                    a is Char && b is JsonElement -> {
                        if(b.isString()) {
                            JsonPrimitive(a+b.getString())
                        } else if (b.isInt()) {
                            a+b.jsonPrimitive.int
                        }
                        else throw Exception("Type mismatch: inferred type is String or Int but ${b.getValueType()} was expected")
                    }
                    a is JsonElement && b is Char -> {
                        if(a.isString()) {
                            JsonPrimitive(a.getString()+b)
                        } else throw Exception("Type mismatch: inferred type is ${a.getValueType()} but String was expected")
                    }
                    else -> throw Exception("Unsupported binary operator: '${operator.value}', for type '${a.getValueType()}' and '${b.getValueType()}'")
                }
            }
            "-" -> {
                when {
                    a is Char && b is JsonElement && b.isInt() -> {
                        a-b.jsonPrimitive.int
                    }
                    a is Char && b is Char -> {
                        JsonPrimitive(a-b)
                    }
                    else -> throw Exception("Unsupported binary operator: '${operator.value}', for type '${a.getValueType()}' and '${b.getValueType()}'")
                }
            }
            else -> throw Exception("Unsupported binary operator: '${operator.value}', for type '${a.getValueType()}' and '${b.getValueType()}'")
        }
    }

    /**
     * Проверяет совместимость объявленного типа и реального значения
     * Поддерживает List<T>, Map<K,V> и Char
     */
    protected fun checkTypeCompatibility(
        declaredType: TypeNode,
        actualValue: Any?,
        varName: String,
        node: ASTNode? = null
    ) {
        if (actualValue == null) return // null всегда можно присвоить (пока)

        val actualType = getActualType(actualValue)

        val compatible = when (declaredType) {
            is TypeNode.Simple -> {
                when (declaredType.name) {
                    "Any", "Any?" -> true
                    "Char" -> actualValue is Char || (actualValue is JsonPrimitive && actualValue.isString && actualValue.getString().length == 1)
                    else -> declaredType.name.equals(actualType.toPrettyString(), ignoreCase = true)
                }
            }
            is TypeNode.ListType -> {
                actualValue is MutableList<*> &&
                        (actualValue.isEmpty() || isElementCompatible(declaredType.elementType, actualValue.first()))
            }
            is TypeNode.MapType -> {
                actualValue is MutableMap<*, *> &&
                        (actualValue.isEmpty() ||
                                isElementCompatible(declaredType.keyType, actualValue.keys.first()) &&
                                isElementCompatible(declaredType.valueType, actualValue.values.first()))
            }
            else -> true
        }

        if (!compatible) {
            throw InterpreterException(
                message = "Type mismatch: expected ${declaredType.toPrettyString()}, but got ${actualType.toPrettyString()} for '$varName'",
                line = node?.line ?: -1,
                column = node?.column ?: -1
            )
        }
    }

    /** Вспомогательная функция для определения реального типа значения */
    private fun getActualType(value: Any?): TypeNode {
        return when (value) {
            is Char -> TypeNode.CHAR
            is JsonPrimitive -> when {
                value.isInt() -> TypeNode.INT
                value.isDouble() -> TypeNode.DOUBLE
                value.isBoolean() -> TypeNode.BOOLEAN
                value.isString -> TypeNode.STRING
                else -> TypeNode.ANY
            }
            is MutableList<*> -> TypeNode.ListType(TypeNode.ANY)
            is MutableMap<*, *> -> TypeNode.MapType(TypeNode.ANY, TypeNode.ANY)
            else -> TypeNode.ANY
        }
    }

    private fun isElementCompatible(expected: TypeNode, actualElement: Any?): Boolean {
        // Простая проверка для начала (можно сделать строже)
        return true
    }

    private fun evaluateComparisonOperator(a: Any?, b: Any?, operator: Token): JsonElement {
        return JsonPrimitive(
            when {
                a is JsonElement && b is JsonElement -> {
                    val aType = a.getValueType()
                    val bType = b.getValueType()
                    val li = listOf(a, b)
                    when {
                        aType.isNumber() && bType.isNumber() -> {
                            val a1 = a.jsonPrimitive.doubleOrNull ?: 0.0
                            val b1 = b.jsonPrimitive.doubleOrNull ?: 0.0
                            when (operator.value) {
                                "==" -> a == b
                                "!=" -> a != b
                                "<" -> safeCompare(a1, b1) < 0
                                ">" -> safeCompare(a1, b1) > 0
                                "<=" -> safeCompare(a1, b1) <= 0
                                ">=" -> safeCompare(a1, b1) >= 0
                                else -> false
                            }
                        }
                        li.all { it.isBoolean() } -> {
                            val a1 = a.jsonPrimitive.booleanOrNull ?: false
                            val b1 = b.jsonPrimitive.booleanOrNull ?: false

                            when (operator.value) {
                                "&&" -> a1.and(b1)
                                "||" -> a1.or(b1)
                                else -> false
                            }
                        }
                        else -> {
                            val a1 = a.getString()
                            val b1 = b.getString()

                            when (operator.value) {
                                "==" -> a == b
                                "!=" -> a != b
                                "<" -> safeCompare(a1.length, b1.length) < 0
                                ">" -> safeCompare(a1.length, b1.length) > 0
                                "<=" -> safeCompare(a1.length, b1.length) <= 0
                                ">=" -> safeCompare(a1.length, b1.length) >= 0
                                else -> false
                            }
                        }
                    }
                }
                (a is Char && b is Char) -> {
                    when (operator.value) {
                        "==" -> a == b
                        "!=" -> a != b
                        "<" -> a < b
                        ">" -> a > b
                        "<=" -> a <= b
                        ">=" -> a >= b
                        else -> false
                    }
                }
                else -> false
            }
        )
    }

    private fun safeCompare(left: Comparable<*>, right: Comparable<*>): Int {
        // В этом методе необходимо гарантировать, что оба значения имеют совместимые типы
        // Это может потребовать дополнительной логики для обработки разных типов данных
        // Например, для сравнения числовых типов, вы можете привести их к общему типу

        return when {
            left is Number && right is Number -> compareValues(left.toDouble(), right.toDouble())
            left is String && right is Number -> compareValues(getJsonValue(left)?.asNumber(), right.toDouble())
            left is Number && right is String -> compareValues(left.toDouble(), getJsonValue(right)?.asNumber())
            left is String && right is String -> compareValues(getJsonValue(left)?.asNumber(), getJsonValue(right)?.asNumber())
            // Добавьте здесь дополнительные проверки для других типов
            else -> -1
        }
    }

    private suspend fun executeNewVarNode(node: NewVarNode): Any? {
        val value = executeNode(node.initializer)

        // Проверка типа при объявлении
        if (node.declaredType != null) {
            checkTypeCompatibility(node.declaredType, value, node.name, node)
        }

        val isMutable = !node.isVal()   // val = immutable, var = mutable

        setValue(
            key = node.name,
            value = value,
            newVar = true,
            isMutable = isMutable          // ← новый параметр
        )

        return value
    }

    private suspend fun executeAssignmentNode(node: AssignmentNode): Any? {
        val value = executeNode(node.value)
        // Assuming that the target is a VariableNode, which contains the variable's name
        if (node.target is VariableNode) {
            val variableName = node.target.name
            setValue(variableName, value, false)
        } else {
            // Handle other types of assignment targets (e.g., MemberAccessNode for object properties)
        }
        return value
    }
    // New: Method to execute assignments for MemberAccessNode
    private suspend fun executeMemberAssignment(node: AssignmentNode): Any? {
        val target = node.target as MemberAccessNode
        val value = executeNode(node.value)

        // Get the target instance and update its field
        val targetInstance = (target.instance?.let{ executeNode(it) } as? ClassInstance)
            ?: throw RuntimeException("Target instance not found for member assignment")
        targetInstance.fields[target.memberName] = value

        return value
    }

    // New: Method to handle compound assignments
    private suspend fun executeCompoundAssignment(node: CompoundAssignmentNode): Any? {
        // First, evaluate the current value of the target.
        val targetValue = executeNode(node.target)
        // Then, evaluate the value to be added, subtracted, etc.
        val value = executeNode(node.value)

        // Perform the operation based on the operator.
        val result = when (node.operator) {
            TokenType.PLUS_EQUAL -> evaluateBinaryExpression(targetValue, value,
                Token("+", TokenType.PLUS)
            )
            TokenType.MINUS_EQUAL -> evaluateBinaryExpression(targetValue, value,
                Token("-", TokenType.MINUS)
            )
            TokenType.STAR_EQUAL -> evaluateBinaryExpression(targetValue, value,
                Token("*", TokenType.STAR)
            )
            TokenType.SLASH_EQUAL -> evaluateBinaryExpression(targetValue, value,
                Token("/", TokenType.SLASH)
            )
            TokenType.PERCENT_EQUAL -> evaluateBinaryExpression(targetValue, value,
                Token("%", TokenType.PERCENT)
            )
            // Add more cases for other compound assignment operators
            else -> throw UnsupportedOperationException("Unsupported compound assignment operator: ${node.operator}")
        }

        // The target should be a VariableNode if it's a simple assignment
        // or a MemberAccessNode if it's an assignment to a property of an object.
        when (node.target) {
            is VariableNode -> {
                // If the target is a VariableNode, we simply update the variable with the new value.
                setValue(node.target.name, result)
            }
            is MemberAccessNode -> {
                // If the target is a MemberAccessNode, we need to update the property of an object.
                // Assuming MemberAccessNode has 'instance' and 'memberName'.
                val instance = (node.target.instance as? ClassInstance)
                    ?: throw IllegalStateException("Target instance is not a ClassInstance.")
                instance.setValue(node.target.memberName, result)
            }
            is IndexAccessNode -> {
                val target = executeNode(node.target.target)
                val index = executeNode(node.target.index)
                when (target) {
                    is MutableList<*> -> (target as MutableList<Any?>)[(index as? JsonPrimitive)?.intOrNull ?: error("Index must be an integer")] = result
                    is MutableMap<*, *> -> (target as MutableMap<Any?, Any?>)[index] = result
                    else -> error("Assignment not supported on this type")
                }
            }
            else -> throw IllegalStateException("Unsupported assignment target: ${node.target}")
        }

        return result
    }

    fun getJsonValue(key: String) = getValue(key) as? JsonElement

    fun getIntValue(key: String): JsonElement? {
        val value = getValue(key) as? JsonElement
        return if(value?.isNumber() == true) value else null
    }

    protected fun handlePrimitiveMemberAccess(instance: Any?, memberName: String): Any? {
        // Полная оригинальная логика length, size, keys, values, trim, replace, substring и т.д.
        return when (instance) {
            is JsonPrimitive -> when (memberName) {
                "length" -> if (instance.isString) JsonPrimitive(instance.getString().length) else null
                else -> instance
            }
            is Char -> when (memberName) {
                "code" -> JsonPrimitive(instance.code)
                else -> null
            }
            is MutableList<*> -> when (memberName) {
                "size" -> JsonPrimitive(instance.size)
                else -> null
            }
            is MutableMap<*, *> -> when (memberName) {
                "size", "length" -> JsonPrimitive(instance.size)
                "keys" -> instance.keys.toMutableList()
                "values" -> instance.values.toMutableList()
                else -> null
            }
            else -> instance
        }
    }

    // getAllVariables для REPL
    fun getAllVariables(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        result.putAll(currentScope.toMap())
        result.putAll(variables)
        return result
    }
    open fun resetState() {
        currentScope = Scope.empty()
    }
    fun getVariableType(value: Any?): String {
        return when {
            value !is JsonElement -> "Object"
            value is JsonNull -> "Null"
            value.isInt() -> "Int"
            value.isDouble() -> "Double"
            value.isBoolean() -> "Boolean"
            value.isString() -> "String"
            else -> "Object"
        }
    }

    protected fun getPosition(node: ASTNode?): Pair<Int, Int> {
        return node?.let { it.line to it.column } ?: (-1 to -1)
    }

    companion object {
        val RAPTOR_KEYWORDS = listOf(
            "class", "fun", "val", "var", "if", "else", "while", "when", "for", "return",
            "break", "continue", "try", "catch", "finally", "in", "is", "false", "true",
            "null", "public", "private", "override", "until", "throw", "enum",
            "constructor", "interface", "abstract", "import",
            // Kazakh equivalents
            "сынып", "әдіс", "мән", "айны", "егер", "басқаша", "әзірге", "қашан", "үшін",
            "қайтар", "тоқтат", "жалғастыр", "байқа", "ұстау", "ақыры", "ішінде", "болса",
            "жалған", "шын", "жоқ", "жалпы", "жеке", "басыпөту", "дейін", "лақтыр", "тізім",
            "құрылысшы", "байланыс", "жоба", "енгіз",

            // System functions
            "println", "zolbasypsygar", "print", "basypsygar", "readln", "oqy", "listOf", "emptyList",
            "mapOf", "emptyMap", "add", "removeAt", "remove", "indexOf", "contains", "isEmpty", "toString",
            "joinToString", "reversed", "put", "containsKey", "containsValue", "toInt", "toDouble", "toBoolean",
            "isInt", "isChar", "isDouble", "isBoolean", "isString", "isNull", "trim", "trimStart", "trimEnd",
            "isNotEmpty", "isBlack", "isNotBlack", "replace", "substr", "substring"
        )
    }
}