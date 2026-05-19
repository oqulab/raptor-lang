//package kz.oqulab.raptor
//
//import kz.oqulab.raptor.paradigms.BoundMethod
//import kz.oqulab.raptor.paradigms.ClassInstance
//import kz.oqulab.raptor.utls.RaptorJson.mJson
//import kz.oqulab.raptor.utls.asNumber
//import kz.oqulab.raptor.utls.getString
//import kz.oqulab.raptor.utls.isBoolean
//import kz.oqulab.raptor.utls.isDouble
//import kz.oqulab.raptor.utls.isInt
//import kotlinx.serialization.encodeToString
//import kotlinx.serialization.json.*
//import kotlin.collections.iterator
//
//
//class ExecutionContext2(
//    val log: (JsonElement, Boolean) -> Unit = { _, _ -> },
//    val readInput: suspend () -> String = { "" }
//) {
//    var classes: Map<String, ClassNode> = emptyMap()
//    val functions = mutableListOf<MethodNode>()
//    private val variableStack: MutableList<MutableMap<String, JsonElement?>> = mutableListOf(mutableMapOf())
//    private var returnEncountered = false  // Флаг для отслеживания выполнения return
//    private val variables = mutableMapOf<String, Any?>()
//
//
//    companion object {
//        val RnLexer = ECLexer()
//        val RnParser = ECParser()
//        var tokens: List<Token> = listOf()
//        var data: Map<String, ClassNode> = emptyMap()
//        suspend fun runCode(
//            ec: ExecutionContext,
//            currentText: String,
//            writeLog: (String, Boolean, String) -> Unit = { _, _, _ ->}
//        ): Pair<List<JsonElement>, Long> {
//            var result = listOf<JsonElement>()
//            try {
//                if(currentText.isNotBlank()) {
////                    writeLog("", true, "#FFFFFF")
////                    if(tokens.isEmpty()) {
////                        tokens = RnLexer.lex(currentText)
////                    }
//
////                    if(data.isEmpty()) {
////                        data = RnParser.parse(tokens)
////                    }
//                    ec.classes = RnParser.parse(RnLexer.lex(currentText))
//
//                    return ec.run()
//                }
//            } catch (e: Exception) {
//                println(e.toString())
//                result = listOf(JsonPrimitive(e.toString()))
//                writeLog(e.toString(), false, "#FF0000")
//            }
//
//            return Pair(result, 0L)
//        }
//    }
//    // Other properties and methods...
//
//    suspend fun executeMethod(method: MethodNode, instance: ClassInstance? = null): Any? {
//        // Temporarily store the current context
//        val previousContext = HashMap(variables)
//
//        // Bind parameters to arguments and add them to the context
//        method.parameters.forEachIndexed { index, parameter ->
//            // Parameters would have been validated before this call
//            variables[parameter.name] = instance?.fields?.get(parameter.name)
//        }
//
//        // Execute the method body in the new context
//        var result: Any? = null
//        try {
//            result = executeNode(method.body)
//        } finally {
//            // Restore the previous context after execution
//            variables.clear()
//            variables.putAll(previousContext)
//        }
//
//        return result
//    }
//    fun setValue(name: String, value: Any?) {
//        variables[name] = value
//    }
//
//    suspend fun run(): Pair<List<JsonElement>, Long> {
//        var result: List<JsonElement> = listOf()
//        val workTime = measureTime {
//            try {
//                result = initClass("MainRn", "main")
//            } catch (e: Exception) {
//                result = listOf(JsonPrimitive("Error: $e"))
//                log(JsonPrimitive("[system] error: $e"), true)
//            }
//        }
////        println("EC: run:result=$result,  work_time=$workTime ms")
//        log(JsonPrimitive("[system] execution time: $workTime ms"), true)
//
//        functions.clear()
//        variableStack.clear()
//        variables.clear()
//        returnEncountered = false
//
//        return Pair(result, workTime)
//    }
//
//    suspend fun initClass(className: String, startMethod: String = ""): List<JsonElement> {
//        val result = mutableListOf<JsonElement>()
//        classes[className]?.let { classNode ->
//            val instance =
//                ClassInstance(classNode, startMethod, executionContext = this) { data, newLine ->
//                    log(data, newLine)
//                    result += data
//                }
//            instance.init()
//        }
//
//        return result
//    }
//
//    suspend fun initClass(classNode: ClassNode, startMethod: String = "") {
//        variableStack.add(HashMap())
//        classNode.memberVariables.filter { it.value.initializer !is MethodNode }.forEach { that ->
//            executeNewVarNode(that.value)
//        }
//        classNode.methods[startMethod]?.let {
////            println("ExecutionContext: initClass=$it")
//            executeNode(MethodCallNode(startMethod, listOf()))
//        }
//    }
//    // Main execution logic for different node types
//    suspend fun executeNode(node: ASTNode?): Any? {
//        return when (node) {
//            is ReturnNode -> {
//                returnEncountered = true
//                return executeNode(node.value)  // Выполнить и вернуть значение return
//            }
//
//            is ConcatenationNode -> evaluateConcatenationNode(node)
//            is BinaryExpressionNode -> evaluateBinaryExpression(node)
//            is VariableNode -> getValue(node.name)
//            is NewVarNode -> executeNewVarNode(node)
//            is LiteralNode -> node.value
//            is IfNode -> executeIfNode(node)
//            is BlockStatementNode -> node.statements.forEach { executeNode(it) }
//            is AssignmentNode -> executeAssignmentNode(node)
//            is MethodCallNode -> executeFunctionCallNode(node)
//            is TryNode -> executeTryNode(node)
//            is WhileNode -> executeWhileNode(node)
//            is ForLoopNode -> executeForLoop(node)
//            is UnaryExpressionNode -> executeUnaryExpression(node)
//            is GroupingNode -> executeNode(node.expression)
//            is MethodNode -> {
//                functions.add(node)
//            }
//            // Inside the executeNode method
//            is MemberAccessNode -> {
//                // Assuming that we have an instance of a class or object
//                val instance = executeNode(node.instance) as? ClassInstance
//                    ?: throw RuntimeException("The left-hand side of a dot must be an instance of a class.")
//
//                // If it's a method, bind and call it
//                val member = instance.getMember(node.memberName ?: "")
//                println("EC: MemberAccessNode, member=$member")
//                if (member is BoundMethod) {
//                    // Assuming arguments are provided correctly, call the method
//                    return member.call(emptyList())  // Or with the actual arguments
//                } else {
//                    // It's a field, so just return its value
//                    return member
//                }
//            }
//            else -> {
//                println("Unsupported node type: ${mJson.encodeToString(node)}")
//                throw UnsupportedOperationException("Unsupported node type: ${mJson.encodeToString(node)}")
//            }
//        }
//    }
//
//    suspend fun executeIfNode(node: IfNode): Any? {
//        val conditionResult = node.condition?.let {evaluateCondition(it)} ?: false
//        return when {
//            conditionResult -> executeBlock(node.thenBranch)
//            node.elseBranch != null -> executeBlock(node.elseBranch)
//            else -> null
//        }
//    }
//
//    suspend fun evaluateCondition(condition: ASTNode): Boolean {
//        return when (val result = executeNode(condition)) {
//            is JsonPrimitive -> {
//                when {
//                    result.isString -> result.contentOrNull != null
//                    result.isInt() -> result.int != 0
//                    result.isDouble() -> result.double != 0.0
//                    result.isBoolean() -> result.boolean
//                    else -> false
//                }
//            }
//            else -> false
//        }
//    }
//
//    private suspend fun executeBlock(node: ASTNode?): Any? {
//        return when (node) {
//            is BlockStatementNode -> {
//                var lastResult: Any? = null
//                for (statement in node.statements) {
//                    lastResult = executeNode(statement)
//                }
//                lastResult
//            }
//            else -> executeNode(node)
//        }
//    }
//
//    private suspend fun executeFunBlock(node: ASTNode?): Any? {
//        return when (node) {
//            is BlockStatementNode -> {
//                var lastResult: Any? = null
//                for (statement in node.statements) {
//                    lastResult = executeNode(statement)
//                    if (returnEncountered) {
//                        returnEncountered = false  // Сброс флага для будущего использования
//                        break  // Прерывание выполнения блока при обнаружении return
//                    }
////                    println("ADMIN :://~: executeBlock fun, statement=$statement")
//                }
//                lastResult
//            }
//            else -> executeNode(node)
//        }
//    }
//
//
//    suspend fun executeFunctionCallNode(node: MethodCallNode): Any? {
//        val classNode = classes[node.name]
//        val function = findFunction(node.name)
//        var result: Any? = null
//        val evaluatedArguments = node.arguments.map { executeNode(it) as? JsonElement }
//
//        when {
//            classNode != null -> {
//
////                initClass(classNode, "init")
////                val instance = ClassInstance(classNode, this)
//            }
//            function != null -> {
//                val previousVariableStack = variableStack.last()
//                val newVariableStack = HashMap<String, JsonElement?>()
//
//                // Связывание аргументов с параметрами функции
//                function.parameters.forEachIndexed { index, parameter ->
//                    val value = evaluatedArguments.getOrNull(index)
//                    newVariableStack[parameter.name] = value ?: executeNode(parameter.defaultValue) as? JsonElement
////                setValue(parameter.name, value ?: executeNode(parameter.defaultValue) as? JsonElement)
//                }
//
//                variableStack.add(newVariableStack)
//
////            println("EC:executeFunctionCallNode function=$function, node=$node, newVariableStack=$newVariableStack")
//                try {
//                    result = executeFunBlock(function.body)
//                } finally {
//                    variableStack.removeAt(variableStack.size - 1)
//                    variableStack[variableStack.size - 1] = previousVariableStack
//                }
//            }
//            else -> when(node.name) {
//                "println" -> {
////                    log(evaluatedArguments.mapNotNull { it }.joinToString { it.getString()} + "\n")
////                    println(evaluatedArguments.joinToString { it?.getString() ?: "" })
//                }
//                "print" -> {
////                    print(evaluatedArguments.joinToString { it?.getString() ?: "" })
////                    log(evaluatedArguments.mapNotNull { it }.joinToString { it.getString()})
//                }
//                else -> {}
//            }
//        }
//
//        return result
//    }
//
//    suspend fun executeTryNode(node: TryNode): Any? {
//        try {
//            executeNode(node.tryBlock)
//        } catch (e: Exception) {
//            handleCatchBlocks(e, node.catchBlocks)
//        } finally {
//            node.finallyBlock?.let { executeNode(it) }
//        }
//        return null // Блоки try-catch обычно не возвращают значение
//    }
//
//    suspend fun handleCatchBlocks(exception: Exception, catchBlocks: List<CatchNode>) {
//        var handled = false
//        for (catchBlock in catchBlocks) {
//            if (isExceptionMatch(exception, catchBlock.exceptionType.value)) {
//                // Добавьте исключение в контекст перед выполнением блока catch
//                setValue(catchBlock.exceptionVar.value, JsonPrimitive(exception.toString()))
//                executeNode(catchBlock.block)
//                handled = true
//                break // Исключение обработано; выходим из цикла
//            }
//        }
//
//        if (!handled) {
//            throw exception // Исключение не обработано; повторно бросаем его
//        }
//    }
//
//    private fun isExceptionMatch(exception: Exception, exceptionType: String): Boolean {
//        // Проверьте, соответствует ли исключение указанному типу
//        // Это может быть проверка класса исключения или что-то более сложное
//        return exception::class.simpleName == exceptionType
//    }
//
//    suspend fun executeWhileNode(node: WhileNode): Any? {
//        var c = 0
//        while (evaluateCondition(node.condition) && c < 10) {
////        while (evaluateCondition(node.condition)) {
//            executeNode(node.body)
//            c++
//        }
//        return null // Циклы while обычно не возвращают значение
//    }
//    private suspend fun executeForLoop(node: ForLoopNode) {
//        // Инициализация цикла
//        executeNode(node.initializer)
//
//        var c = 0
//        while (evaluateCondition(node.condition) && c < 10000) {
//            // Выполнение тела цикла
//            for (statement in node.body) {
//                executeNode(statement)
//
//                // Проверка наличия флага return
//                if (returnEncountered) {
//                    returnEncountered = false
//                    return  // Прерывание цикла при обнаружении return
//                }
//            }
//
//            // Выполнение инкремента
//            executeNode(node.increment)
//            c++
//        }
//    }
//
//    suspend fun executeUnaryExpression(node: UnaryExpressionNode): Any {
//        val operand = executeNode(node.operand)
//        if (operand !is JsonPrimitive) {
//            throw IllegalArgumentException("executeUnaryExpression applied to non-JsonPrimitive type")
//        }
//        val mo = node.operand
//        if(mo is VariableNode) {
//            val newValue = when (node.operator.value) {
//                "-" -> negate(operand)
//                "++" -> incrementDecrement(operand, 1, node.isPrefix)
//                "--" -> incrementDecrement(operand, -1, node.isPrefix)
//                "!" -> if (operand.isBoolean()) !operand.boolean else throw IllegalArgumentException("Logical negation applied to non-boolean type")
//                else -> throw UnsupportedOperationException("Unsupported unary operator: ${node.operator}")
//            }
////            println("EC: executeUnaryExpression, newValue=$newValue, mo=$mo, operand=$operand")
//            setValue(mo.name, newValue, false)
//        }
//
//        return true
//    }
//
//    private suspend fun evaluateConcatenationNode(node: ConcatenationNode): JsonElement {
//        val stringBuilder = StringBuilder()
//        node.parts.forEach { part ->
//            val partValue = executeNode(part) as? JsonElement
//
//            partValue?.let {
//                stringBuilder.append(it.getString())
//            }
//        }
//        return JsonPrimitive(stringBuilder.toString())
//    }
//
//    private fun negate(operand: JsonPrimitive): JsonPrimitive {
//        return JsonPrimitive(
//            when {
//                operand.isInt() -> -operand.int
//                operand.isDouble() -> -operand.double
//                else -> throw IllegalArgumentException("Unsupported numeric type for increment/decrement")
//            }
//        )
//    }
//
//    private fun incrementDecrement(operand: Any?, delta: Int, isPrefix: Boolean): Any {
//        if (operand !is JsonPrimitive) {
//            throw IllegalArgumentException("Increment/Decrement applied to non-numeric type")
//        }
//
//        val updatedValue = JsonPrimitive(
//            when {
//                operand.isInt() -> operand.int + delta
//                operand.isDouble() -> operand.double + delta
//                else -> throw IllegalArgumentException("Unsupported numeric type for increment/decrement")
//            }
//        )
//
//        // Возвращаемое значение зависит от того, является ли операция предварительной
//        return if (isPrefix) updatedValue else operand
//    }
//    private suspend fun evaluateBinaryExpression(node: BinaryExpressionNode): Any {
////        println("evaluateBinaryExpression: op=${node.operator.value}, right=${node.right}")
//        val left = executeNode(node.left)
//        val right = executeNode(node.right)
//
//        return when (node.operator.value) {
//            // Логические операторы
//            "&&" -> (left as Boolean) && (right as Boolean)
//            "||" -> (left as Boolean) || (right as Boolean)
//            // Операторы сравнения
//            "==", "!=", "<", ">", "<=", ">=" -> evaluateComparisonOperator(left, right, node.operator)
//            // Арифметические операторы
//            "+", "-", "*", "/", "%" -> {
////                println("evaluateBinaryExpression: op=${node.operator.value}, left=$left, right=$right")
//                evaluateArithmeticOperator(left, right, node.operator)
//            }
//            else -> throw UnsupportedOperationException("Unsupported binary operator: ${node.operator}")
//        }
//    }
//    private fun evaluateArithmeticOperator(a: Any?, b: Any?, operator: Token): JsonElement {
////        println("EC:evaluateArithmeticOperator, aIsVal=${a is VariableNode}, bIsVal=${b is VariableNode}, aIsJson=${a is JsonElement}, bIsJson=${b is JsonElement}, a=$a, b=$b")
//        return when {
//            a is JsonElement && b is JsonElement -> {
//
//                val li = listOf(a, b)
//                when {
//                    li.all { it.isInt() } -> {
//                        val a1 = a.jsonPrimitive.intOrNull ?: 0
//                        val b1 = b.jsonPrimitive.intOrNull ?: 0
//
//                        JsonPrimitive(
//                            when (operator.value) {
//                                "+" -> a1 + b1
//                                "-" -> a1 - b1
//                                "*" -> a1 * b1
//                                "/" -> if(b1 == 0) 0 else a1 / b1
//                                "%" -> a1 % b1
//                                else -> 0
//                            }
//                        )
//                    }
//                    li.all { it.isBoolean() } -> {
//                        val a1 = a.jsonPrimitive.booleanOrNull ?: false
//                        val b1 = b.jsonPrimitive.booleanOrNull ?: false
//                        val result = a1.and(b1)
//
//                        JsonPrimitive(result)
//                    }
//                    li.all { it.isDouble() } -> {
//                        val a1 = a.jsonPrimitive.doubleOrNull ?: 0.0
//                        val b1 = b.jsonPrimitive.doubleOrNull ?: 0.0
//                        JsonPrimitive(
//                            when (operator.value) {
//                                "+" -> a1 + b1
//                                "-" -> a1 - b1
//                                "*" -> a1 * b1
//                                "/" -> a1 / b1
//                                "%" -> a1 % b1
//                                else -> 0
//                            }
//                        )
//                    }
//                    else -> {
////                        println("evaluateArithmeticOperator: ${operator.value}, a=$a, b=$b")
//                        val a1 = a.getString()
//                        val b2 = b.getString()
//                        val result = "$a1$b2"
//
//                        JsonPrimitive(result)
//                    }
//                }
//            }
//            a is VariableNode && b is JsonElement -> {
//                getJsonValue(a.name)?.let {
//                    executeArith(it, b, operator)
//                } ?: JsonNull
//            }
//            a is JsonElement && b is VariableNode -> {
//                getJsonValue(b.name)?.let {
//                    executeArith(a, it, operator)
//                } ?: JsonNull
//            }
//            a is VariableNode && b is VariableNode -> {
//                getJsonValue(a.name)?.let { itA ->
//                    getJsonValue(b.name)?.let { itB ->
//                        executeArith(itA, itB, operator)
//                    } ?: JsonNull
//                } ?: JsonNull
//            }
//            else -> JsonPrimitive("$a$b")
//        }
//    }
//
//    private fun executeArith(a: JsonElement, b: JsonElement, operator: Token): JsonElement {
//        val li = listOf(a, b)
//        return when {
//            li.all { it.isInt() } -> {
//                val a1 = a.jsonPrimitive.intOrNull ?: 0
//                val b1 = b.jsonPrimitive.intOrNull ?: 0
//
//                JsonPrimitive(
//                    when (operator.value) {
//                        "+" -> a1 + b1
//                        "-" -> a1 - b1
//                        "*" -> a1 * b1
//                        "/" -> a1 / b1
//                        "%" -> a1 % b1
//                        else -> 0
//                    }
//                )
//            }
//            li.all { it.isBoolean() } -> {
//                val a1 = a.jsonPrimitive.booleanOrNull ?: false
//                val b1 = b.jsonPrimitive.booleanOrNull ?: false
//                val result = a1.and(b1)
//
//                JsonPrimitive(result)
//            }
//            li.all { it.isDouble() } -> {
//                val a1 = a.jsonPrimitive.doubleOrNull ?: 0.0
//                val b1 = b.jsonPrimitive.doubleOrNull ?: 0.0
//                JsonPrimitive(
//                    when (operator.value) {
//                        "+" -> a1 + b1
//                        "-" -> a1 - b1
//                        "*" -> a1 * b1
//                        "/" -> a1 / b1
//                        "%" -> a1 % b1
//                        else -> 0
//                    }
//                )
//            }
//            else -> {
////                println("evaluateArithmeticOperator: ${operator.value}, a=$a, b=$b")
//                val a1 = a.getString()
//                val b2 = b.getString()
//                val result = "$a1$b2"
//
//                JsonPrimitive(result)
//            }
//        }
//    }
//
//    private suspend fun evaluateComparisonOperator(a: Any?, b: Any?, operator: Token): JsonElement {
//        return JsonPrimitive(
//            when {
//                a is JsonElement && b is JsonElement -> {
//                    val li = listOf(a, b)
//                    when {
//                        li.all { it.isInt() } -> {
//                            val a1 = a.jsonPrimitive.intOrNull ?: 0
//                            val b1 = b.jsonPrimitive.intOrNull ?: 0
//
//                            when (operator.value) {
//                                "==" -> a == b
//                                "!=" -> a != b
//                                "<" -> safeCompare(a1, b1) < 0
//                                ">" -> safeCompare(a1, b1) > 0
//                                "<=" -> safeCompare(a1, b1) <= 0
//                                ">=" -> safeCompare(a1, b1) >= 0
//                                else -> false
//                            }
//                        }
//                        li.all { it.isBoolean() } -> {
//                            val a1 = a.jsonPrimitive.booleanOrNull ?: false
//                            val b1 = b.jsonPrimitive.booleanOrNull ?: false
//
//                            a1.and(b1)
//                        }
//                        li.all { it.isDouble() } -> {
//                            val a1 = a.jsonPrimitive.doubleOrNull ?: 0.0
//                            val b1 = b.jsonPrimitive.doubleOrNull ?: 0.0
//                            when (operator.value) {
//                                "==" -> a == b
//                                "!=" -> a != b
//                                "<" -> safeCompare(a1, b1) < 0
//                                ">" -> safeCompare(a1, b1) > 0
//                                "<=" -> safeCompare(a1, b1) <= 0
//                                ">=" -> safeCompare(a1, b1) >= 0
//                                else -> false
//                            }
//                        }
//                        else -> {
//                            val a1 = a.getString()
//                            val b1 = b.getString()
//
//                            when (operator.value) {
//                                "==" -> a == b
//                                "!=" -> a != b
//                                "<" -> safeCompare(a1.length, b1.length) < 0
//                                ">" -> safeCompare(a1.length, b1.length) > 0
//                                "<=" -> safeCompare(a1.length, b1.length) <= 0
//                                ">=" -> safeCompare(a1.length, b1.length) >= 0
//                                else -> false
//                            }
//                        }
//                    }
//                }
//                else -> false
//            }
//        )
//    }
//
//    private fun safeCompare(left: Comparable<*>, right: Comparable<*>): Int {
//        // В этом методе необходимо гарантировать, что оба значения имеют совместимые типы
//        // Это может потребовать дополнительной логики для обработки разных типов данных
//        // Например, для сравнения числовых типов, вы можете привести их к общему типу
//
//        return when {
//            left is Number && right is Number -> compareValues(left.toDouble(), right.toDouble())
//            left is String && right is Number -> compareValues(getJsonValue(left)?.asNumber(), right.toDouble())
//            left is Number && right is String -> compareValues(left.toDouble(), getJsonValue(right)?.asNumber())
//            left is String && right is String -> compareValues(getJsonValue(left)?.asNumber(), getJsonValue(right)?.asNumber())
//            // Добавьте здесь дополнительные проверки для других типов
//            else -> -1
//        }
//    }
//
//    private suspend fun executeNewVarNode(node: NewVarNode): Any? {
////        val i = node.initializer
////
////        if(i !is MethodNode) {
////        } else {
////            functions.add(i)
////        }
//        val value = executeNode(node.initializer)
////        println("EC: executeNewVarNode, value=$value")
//        setValue(node.name, value, newVar = true)
//        return value
//    }
//
//    private suspend fun executeAssignmentNode(node: AssignmentNode): Any? {
//        val value = executeNode(node.value)
////        println("EC: executeAssignmentNode, value=$value, node=$node")
////        setValue(node.variableName, value)
//        return value
//    }
//
//    fun getJsonValue(key: String) = getValue(key) as? JsonElement
//
//    fun getValue(key: String): Any? {
//        val size = variableStack.size
//        for(i in 0 until size) {
//            variableStack[size - 1 - i][key]?.let {
//                return it
//            }
//        }
////        variableStack.reversed().forEach {
////            if(it[key] != null) return it[key]
////        }
//
//        return variables[key] as? JsonElement
//    }
//
//    // Method to retrieve a variable's value from the context
////    fun getValue(name: String): Any? {
////        return variables[name]
////    }
//    fun setValue(key: String, value: Any?, newVar: Boolean = false) {
//        if(value == null) return
//        val jsonValue = when(value) {
//            is JsonPrimitive -> {
//                value
//            }
//            else -> {
//                JsonPrimitive(value.toString())
//            }
//        }
//        val size = variableStack.size
//        if(newVar) {
//            variableStack.last()[key] = jsonValue
//        } else {
//            for(i in 0 until size) {
//                val index = size - 1 - i
//                if(variableStack[index].containsKey(key)) {
//                    variableStack[index][key] = jsonValue
//                    return
//                }
//            }
//        }
//    }
//
//    private fun findFunction(name: String): MethodNode? {
//        // Пример: ищем функцию среди всех классов и их методов
//        for (classNode in classes) {
//            classNode.value.methods[name]?.let { return it }
//        }
//        return functions.find { it.name == name }
//    }
//}
//
////            classes["Init"]?.memberVariables?.let {
////                variableStack.add(HashMap())
////                it.forEach { that ->
////                    executeNewVarNode(that.value)
////                }
////            }
////            initClass("Calculator", "init")
////            classes["Calculator"]?.let {
////                variableStack.add(HashMap())
////                it.memberVariables.forEach { that ->
////                    executeNewVarNode(that.value)
////                }
////                it.methods["init"]?.let {
////                    println("ExecutionContext: InitMethod=$it")
////                    executeNode(FunctionCallNode("init", listOf()))
////                }
////            }
