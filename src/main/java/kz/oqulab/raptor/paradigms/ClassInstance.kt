package kz.oqulab.raptor.paradigms

import kz.oqulab.raptor.utls.RaptorJson.mJson
import kz.oqulab.raptor.utls.asNumber
import kz.oqulab.raptor.utls.getString
import kz.oqulab.raptor.utls.getValueType
import kz.oqulab.raptor.utls.isBoolean
import kz.oqulab.raptor.utls.isDouble
import kz.oqulab.raptor.utls.isInt
import kz.oqulab.raptor.utls.isNumber
import kz.oqulab.raptor.utls.toJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kz.oqulab.raptor.ASTNode
import kz.oqulab.raptor.AssignmentNode
import kz.oqulab.raptor.BinaryExpressionNode
import kz.oqulab.raptor.BlockStatementNode
import kz.oqulab.raptor.BreakStatementNode
import kz.oqulab.raptor.CatchNode
import kz.oqulab.raptor.ClassNode
import kz.oqulab.raptor.CompoundAssignmentNode
import kz.oqulab.raptor.ConcatenationNode
import kz.oqulab.raptor.ExecutionContext
import kz.oqulab.raptor.ForInNode
import kz.oqulab.raptor.ForLoopNode
import kz.oqulab.raptor.ForRangeNode
import kz.oqulab.raptor.GroupingNode
import kz.oqulab.raptor.IfNode
import kz.oqulab.raptor.IndexAccessNode
import kz.oqulab.raptor.InterpreterException
import kz.oqulab.raptor.ListNode
import kz.oqulab.raptor.LiteralNode
import kz.oqulab.raptor.MapNode
import kz.oqulab.raptor.MemberAccessNode
import kz.oqulab.raptor.MethodCallNode
import kz.oqulab.raptor.MethodNode
import kz.oqulab.raptor.NewVarNode
import kz.oqulab.raptor.RaptorInterpreter
import kz.oqulab.raptor.ReturnNode
import kz.oqulab.raptor.Token
import kz.oqulab.raptor.TokenType
import kz.oqulab.raptor.TryNode
import kz.oqulab.raptor.UnaryExpressionNode
import kz.oqulab.raptor.VariableNode
import kz.oqulab.raptor.WhenNode
import kz.oqulab.raptor.WhileNode
import kotlin.collections.get
import kotlin.text.iterator

class ClassInstance(
    val classNode: ClassNode,
    val startMethod: String = "init",
    override val executionContext: ExecutionContext? = null,
    val evaluatedArguments: List<Any> = listOf(),
    val parent: ClassInstance? = null,
    logParam: (JsonElement, Boolean) -> Unit = { _, _ -> }
) : RaptorInterpreter(log = logParam, executionContext = executionContext) {

    val THIS_VALUES = listOf("this", "бұл")
    val SUPER_VALUES = listOf("super", "үстем")
    val fields = mutableMapOf<String, Any?>()
    var classes: Map<String, ClassNode> = emptyMap()
    var classesInstance: MutableMap<String, ClassInstance> = mutableMapOf()
    val functions = mutableListOf<MethodNode>()

    override fun getValue(key: String): Any? {
        if (key in THIS_VALUES) return this
        if (key in SUPER_VALUES) return parent
        return super.getValue(key) ?: fields[key] ?: variables[key] ?: classesInstance[key]
    }

    override fun setValue(key: String, value: Any?, newVar: Boolean, isMutable: Boolean) {
        if (value == null) return

        if (value is ClassInstance) {
            classesInstance[key] = value
            return
        }

        // Проверка immutable (val)
        if (!newVar && currentScope.isImmutable(key)) {
            throw InterpreterException(
                message = "Val cannot be reassigned: '$key' is immutable (declared as val)",
                line = -1, // можно передать node если есть
                column = -1
            )
        }

        // Проверка типа
        val declaredType = if (newVar) null else getDeclaredType(key)
        if (declaredType != null) {
            checkTypeCompatibility(declaredType, value, key)
        }

        if (newVar) {
            currentScope.set(key, value, declaredType, isMutable)
        } else {
            if (currentScope.containsKey(key)) {
                currentScope.set(key, value)
            } else {
                fields[key] = value
            }
        }
    }
    override fun findFunction(name: String): MethodNode? =
        functions.find { it.name == name } ?: classNode.methods[name] ?: parent?.findFunction(name)

    override fun findClass(name: String): ClassNode? =
        classes[name] ?: executionContext?.classes?.get(name) ?: parent?.findClass(name)


    fun toToJsonElement(): JsonElement {
        val values = mutableMapOf<String, JsonElement>()

        (classNode.parameters).forEach { value ->
            variables[value.name]?.let {
                values[value.name] = it.toJson()

            }

            classesInstance[value.name]?.let {
                values[value.name] = it.toToJsonElement()
            }
        }
        classNode.memberVariables.forEach { value ->
            fields[value.key]?.let {
                values[value.key] = it.toJson()
            }
            classesInstance[value.key]?.let {
                values[value.key] = it.toToJsonElement()
            }
        }
        return mJson.encodeToJsonElement(values)
    }
    suspend fun init(): String {
        var result = ""
        // Initialize fields with default values
        classNode.memberVariables.forEach { (name, varNode) ->
            val value = executeNode(varNode.initializer)
            fields[name] = value
        }

        var methodNode = classNode.methods[startMethod]
        var runMethodName = startMethod
        if(methodNode == null && startMethod == "main") {
            methodNode = classNode.methods["basy"]
            if(methodNode != null) {
                runMethodName = "basy"
            }
        }
        methodNode?.let {
            result += executeNode(MethodCallNode(runMethodName, listOf()))
        }

        val newVariableStack = HashMap<String, JsonElement?>()

        // Связывание аргументов с параметрами функции
        classNode.parameters.forEachIndexed { index, parameter ->
            val value = evaluatedArguments.getOrNull(index)
//            newVariableStack[parameter.name] = value ?: executeNode(parameter.defaultValue) as? JsonElement
            (value ?: executeNode(parameter.defaultValue))?.let {
                when(it) {
                    is JsonElement -> {
                        variables[parameter.name] = it
                    }
                    is ClassInstance -> {
                        classesInstance[parameter.name] = it
                    }
                }
            }
//                setValue(parameter.name, value ?: executeNode(parameter.defaultValue) as? JsonElement)
        }

//        variableStack.add(newVariableStack)
//        variables.put(newVariableStack)

        return result
    }

    suspend fun runMethod(methodName: String = "init"): Any? {
        classNode.methods[methodName]?.let {

            val a = runMethod(MethodCallNode(methodName, listOf()))
            return a
        }

        return null
    }

    suspend fun runMethod(callNode: MethodCallNode, arguments: List<Any> = listOf()): Any? {
        val a = executeFunctionCallNode(callNode, arguments)
        return a
    }

    fun getMember(memberName: String): Any? {
        // Check if the member is a field
        fields[memberName]?.let { return it }
        // Check if the member is a field
        getValue(memberName)?.let { return it }

        // Check if the member is a method and return a bound method
//        classNode.methods[memberName]?.let { methodNode ->
//            return BoundMethod(this, methodNode)
//        }

//        throw Exception("No such member: $memberName")
        return null
    }


    // Other properties and methods...

    suspend fun executeMethod(method: MethodNode, instance: ClassInstance? = null): Any? {
        // Temporarily store the current context
        val previousContext = HashMap(variables)

        // Bind parameters to arguments and add them to the context
        method.parameters.forEachIndexed { index, parameter ->
            // Parameters would have been validated before this call
            variables[parameter.name] = instance?.fields?.get(parameter.name)
        }

        // Execute the method body in the new context
        var result: Any? = null
        try {
            result = executeNode(method.body)
        } finally {
            // Restore the previous context after execution
            variables.clear()
            variables.putAll(previousContext)
        }

        return result
    }
    fun setValue(name: String, value: Any?) {
        setValue(name, value, false)
    }

    fun measureTime(callback: () -> Unit): Long {
        val start = System.currentTimeMillis()
        callback()
        val end = System.currentTimeMillis()
        return end - start
    }

//    suspend fun initClass(className: String, startMethod: String = "") {
//        classes[className]?.let { classNode ->
//
////            classNode.runMethod(startMethod) {
////                println("LOGS::// $it")
////                log(it)
////            }
//            initClass(classNode, startMethod)
//        }
//    }

//    suspend fun initClass(classNode: ClassNode, startMethod: String = "") {
//        variableStack.add(HashMap())
//        classNode.memberVariables.filter { it.value.initializer !is MethodNode }.forEach { that ->
//            executeNewVarNode(that.value)
//        }
//        classNode.methods[startMethod]?.let {
//            executeNode(MethodCallNode(startMethod, listOf()))
//        }
//    }
    // init(), runMethod(), toToJsonElement(), getMember(), executeMethod() — **оставлены без изменений**
    // (теперь используют унаследованный executeNode)
    // ... (весь остальной код init(), toToJsonElement() и т.д. — копируй из старого файла)
}