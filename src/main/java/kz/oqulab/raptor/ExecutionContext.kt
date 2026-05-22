package kz.oqulab.raptor

import kz.oqulab.raptor.paradigms.ClassInstance
import kz.oqulab.raptor.utls.RaptorJson.mJson
import kz.oqulab.raptor.utls.asNumber
import kz.oqulab.raptor.utls.getString
import kz.oqulab.raptor.utls.isBoolean
import kz.oqulab.raptor.utls.isDouble
import kz.oqulab.raptor.utls.isInt
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kz.oqulab.raptor.utls.isString
import kotlin.time.measureTime

class ExecutionContext(
    override val log: (JsonElement, Boolean) -> Unit = { _, _ -> },
    override val readInput: suspend () -> String = { "" }
) : RaptorInterpreter(log = log, readInput = readInput) {

    var classes: Map<String, ClassNode> = mutableMapOf()
    val functions = mutableListOf<MethodNode>()


    override fun getValue(key: String): Any? = super.getValue(key) ?: variables[key]
    override fun setValue(key: String, value: Any?, newVar: Boolean, isMutable: Boolean) {
        if (value == null) return

        println("RAPTOR:EC:setValue key=$key, value=$value, newVar=$newVar")
        if (!newVar && currentScope.isImmutable(key)) {
            throw InterpreterException(
                message = "Val cannot be reassigned: '$key' is immutable",
                line = -1,
                column = -1
            )
        }

        val declaredType = if (newVar) null else getDeclaredType(key)
        if (declaredType != null) {
            checkTypeCompatibility(declaredType, value, key)
        }

        if (newVar) {
            currentScope.set(key, value, declaredType, isMutable)
        } else {
            currentScope.assign(key, value)
        }
    }

    override fun findFunction(name: String): MethodNode? {
        classes.values.forEach { it.methods[name]?.let { return it } }
        return functions.find { it.name == name }
    }

    override fun findClass(name: String): ClassNode? = classes[name]

    // run(), runRepl(), initClass() — теперь используют полный executeNode
    suspend fun run(): Pair<List<JsonElement>, Long> {
        var result: List<JsonElement> = listOf()
        val workTime = measureTime {
            try {
                result = initClass("MainRn", "main")
            } catch (e: Exception) {
                result = listOf(JsonPrimitive("Error: $e"))
                log(JsonPrimitive("[system] error: $e"), true)
            }
        }
        log(JsonPrimitive("[system] execution time: $workTime ms"), true)

        functions.clear()
        super.resetState()
        variables.clear()
        returnEncountered = false

        return Pair(result, workTime.inWholeMilliseconds)
    }

    suspend fun runRepl(source: String): JsonElement? {
        val tokens = ECLexer(source).lex()
        val parser = ECParser()
        val nodes = parser.parseNodes(tokens)
        var lastResult: Any? = null
        for (node in nodes) {
            val isSilent = node is NewVarNode || node is AssignmentNode || node is CompoundAssignmentNode || node is MethodNode || node is ClassNode
            val result = executeNode(node)
            if (!isSilent) lastResult = result
        }
        return when (lastResult) {
            is JsonElement -> lastResult
            is ClassInstance -> lastResult.toToJsonElement()
            null -> null
            else -> JsonPrimitive(lastResult.toString())
        }
    }

    suspend fun initClass(className: String, startMethod: String = ""): List<JsonElement> {
        val result = mutableListOf<JsonElement>()
        classes[className]?.let { classNode ->
            val instance = ClassInstance(
                classNode,
                startMethod,
                executionContext = this
            ) { data, newLine ->
                log(data, newLine)
                val text = data.getString()
                result += JsonPrimitive(text + if (newLine) "\n" else "")
            }
            instance.init()
        }
        return result
    }


    override fun resetState() {
        super.resetState()
        classes = mutableMapOf()
        functions.clear()
        variables.clear()
        returnEncountered = false
    }
}