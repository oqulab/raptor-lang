package kz.oqulab.raptor
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class Token(
    @SerialName("value")
    val value: String = "",
    @SerialName("type")
    val type: TokenType = TokenType.UNKNOWN,
    @SerialName("line")
    val line: Int = -1,
    @SerialName("column")
    val column: Int = -1,
    @SerialName("level")
    val level: Int = -1,
) {
}

//class - сынып
//fun - функция
//val (immutable variable) - мәнді (өзгермейтін айнымалы)
//var (mutable variable) - айнымалы
//return - қайтару
//break - тоқтату
//import - импорттау
//
//for - үшін
//while - әзірге
//if - егер
//else - басқаша
//when - қашан
//try - байқап көру
//catch - ұстау
//finally - ақырында
//throw - лақтыру
//enum - тізім
//struct - құрылым
//interface - интерфейс
//const (constant) - тұрақты
//public - жалпы
//private - жеке
//raptor - бұл сөздің бағдарламалау контекстінде тікелей баламасы жоқ, сондықтан оны өзгертпеу немесе контекстіне сай жаңа атау таңдау керек
//async - асинхрон
//await - күту
//do - орындау
//in - ішінде


@Serializable
enum class TokenType(private val jsonName: String) {
    @SerialName("if")
    IF("if"),
    @SerialName("while")
    WHILE("while"),
    @SerialName("for")
    FOR("for"),
    @SerialName("fun")
    FUN("fun"),
    @SerialName("var")
    VAR("var"),
    @SerialName("return")
    RETURN("return"),
    @SerialName("identifier")
    IDENTIFIER("identifier"),
    @SerialName("class")
    CLASS("class"),
    @SerialName("break")
    BREAK("break"),
    @SerialName("continue")
    CONTINUE("continue"),
    @SerialName("until")
    UNTIL("until"),
    @SerialName("when")
    WHEN("when"),
    @SerialName("try")
    TRY("try"),
    @SerialName("throw")
    THROW("throw"),
    @SerialName("entity/enums")
    ENUM("entity/enums"),
    @SerialName("struct")
    STRUCT("struct"),
    @SerialName("interface")
    INTERFACE("interface"),
    @SerialName("const")
    CONST("const"),
    @SerialName("async")
    ASYNC("async"),
    @SerialName("await")
    AWAIT("await"),
    @SerialName("do")
    DO("do"),
    @SerialName("import")
    IMPORT("import"),
    @SerialName("else")
    ELSE("else"),
    @SerialName("in")
    IN("in"),
    @SerialName("is")
    IS("is"),
    @SerialName("false")
    FALSE("false"),
    @SerialName("true")
    TRUE("true"),
    @SerialName("null")
    NULL("null"),
    @SerialName("semicolon")
    SEMICOLON("semicolon"),
    @SerialName("equal")
    EQUAL("equal"),
    @SerialName("catch")
    CATCH("catch"),
    @SerialName("finally")
    FINALLY("finally"),
    @SerialName("default")
    DEFAULT("default"),
    @SerialName("case")
    CASE("case"),
    @SerialName("this")
    THIS("this"),
    @SerialName("super")
    SUPER("super"),
    @SerialName("val")
    VAL("val"),
    @SerialName("and")
    AND("and"),
    @SerialName("or")
    OR("or"),
    @SerialName("private")
    PRIVATE("private"),
    @SerialName("public")
    PUBLIC("public"),
    @SerialName("override")
    OVERRIDE("override"),
    @SerialName("abstract")
    ABSTRACT("abstract"),
    @SerialName("constructor")
    CONSTRUCTOR("constructor"),
    @SerialName("none")
    NONE("none"),
    @SerialName("operator")
    OPERATOR("operator"),
    @SerialName("character_start")
    CHARACTER_START("character_start"),
    @SerialName("character_end")
    CHARACTER_END("character_end"),
    @SerialName("parenthesis_open")
    PARENTHESIS_OPEN("parenthesis_open"),
    @SerialName("colon")
    COLON("colon"),
    @SerialName("comma")
    COMMA("comma"),
    @SerialName("brace_open")
    BRACE_OPEN("brace_open"),
    @SerialName("parenthesis_close")
    PARENTHESIS_CLOSE("parenthesis_close"),
    @SerialName("brace_close")
    BRACE_CLOSE("brace_close"),
    @SerialName("greater")
    GREATER("greater"),
    @SerialName("less")
    LESS("less"),
    @SerialName("greater_equal")
    GREATER_EQUAL("greater_equal"),
    @SerialName("less_equal")
    LESS_EQUAL("less_equal"),
    @SerialName("plus")
    PLUS("plus"),
    @SerialName("plus_plus")
    PLUS_PLUS("plus_plus"),
    @SerialName("minus")
    MINUS("minus"),
    @SerialName("minus_minus")
    MINUS_MINUS("minus_minus"),
    @SerialName("star,")
    STAR("star,"),
    @SerialName("slash")
    SLASH("slash"),
    @SerialName("percent")
    PERCENT("percent"),
    @SerialName("percent_equal")
    PERCENT_EQUAL("percent"),
    @SerialName("bang")
    BANG("bang"),
    @SerialName("dot")
    DOT("dot"),
    @SerialName("dot_dot")
    DOT_DOT("dot_dot"),
    @SerialName("eof")
    EOF("eof"),
    @SerialName("tilde")
    TILDE("tilde"),
    @SerialName("backquote")
    BACKQUOTE("backquote"),
    @SerialName("backslash")
    BACKSLASH("backslash"),
    @SerialName("question_mark")
    QUESTION_MARK("question_mark"),
    @SerialName("equal_equal")
    EQUAL_EQUAL("equal_equal"),
    @SerialName("bang_equal")
    BANG_EQUAL("bang_equal"),
    @SerialName("number")
    NUMBER("number"),
    @SerialName("string")
    STRING("string"),
    @SerialName("char")
    CHAR("string"),
    @SerialName("interpolation_start")
    INTERPOLATION_START("interpolation_start"),
    @SerialName("interpolation_end")
    INTERPOLATION_END("interpolation_end"),
    @SerialName("string_end")
    STRING_END("string_end"),
    @SerialName("string_start")
    STRING_START("string_start"),
    @SerialName("and_and")
    AND_AND("and_and"),
    @SerialName("or_or")
    OR_OR("or_or"),
    @SerialName("caret")
    CARET("caret"),
    @SerialName("plus_equal")
    PLUS_EQUAL("plus_equal"),
    @SerialName("minus_equal")
    MINUS_EQUAL("minus_equal"),
    @SerialName("star_equal")
    STAR_EQUAL("star_equal"),
    @SerialName("slash_equal")
    SLASH_EQUAL("slash_equal"),
    @SerialName("bracket_open")
    BRACKET_OPEN("bracket_open"),
    @SerialName("bracket_close")
    BRACKET_CLOSE("bracket_close"),
    @SerialName("dollar")
    DOLLAR("dollar"),
    @SerialName("ampersand")
    AMPERSAND("ampersand"),
    @SerialName("int")
    INT("int"),
    @SerialName("double")
    DOUBLE("double"),
    @SerialName("boolean")
    BOOLEAN("boolean"),
    @SerialName("new_line")
    NEW_LINE("new_line"),
    @SerialName("white_space")
    WHITE_SPACE("white_space"),
    @SerialName("rapter")
    RAPTOR("rapter"),
    @SerialName("comment_text")
    COMMENT_TEXT("comment_text"),
    @SerialName("comment_start")
    COMMENT_START("comment_start"),
    @SerialName("comment_end")
    COMMENT_END("comment_end"),
    @SerialName("arrow")
    ARROW("arrow"),
    @SerialName("hash")
    HASH("hash"),
    @SerialName("at")
    AT("at"),
    @SerialName("unknown")
    UNKNOWN("unknown");
    // Add other specific types as needed


    fun isIndentifier() = this == IDENTIFIER
    override fun toString() = jsonName
}


@Serializable
data class Position(val line: Int, val column: Int)

@Serializable
sealed class ASTNode {
    /**
     * Токен, из которого был создан этот узел.
     * Используется для точного указания позиции ошибки в IDE.
     */
    @Transient
    open val token: Token = Token()

    // Удобные свойства (чтобы не писать token?.line каждый раз)
    val line: Int get() = token.line
    val column: Int get() = token.column
}

@Serializable
data class ConcatenationNode(
    val parts: List<ASTNode>,
    @Transient
    override val token: Token = Token()
) : ASTNode()
@Serializable
data class StringLiteralNode(
    val value: String,
    @Transient
    override val token: Token = Token()) : ASTNode()
@Serializable
data class InterpolationNode(
    val expression: ASTNode,
    @Transient
    override val token: Token = Token()) : ASTNode()
@Serializable
data class BinaryOperation(
    val left: ASTNode,
    val operator: Token,
    val right: ASTNode
) : ASTNode()

@Serializable
data class NumberLiteral(
    @Transient
    override val token: Token = Token()
) : ASTNode()

// Example of how to add a new node type
@Serializable
data class SuspendFunctionDeclaration(
    val name: String,
    val body: ASTNode
) : ASTNode()

@Serializable
data class MethodCallNode(
    @SerialName("name")
    val name: String,
    @SerialName("arguments")
    val arguments: List<ASTNode>,
    @SerialName("instance")
    val instance: ASTNode? = null,
    @Transient
    override val token: Token = Token()
) : ASTNode()


@Serializable
data class NoneASTNode(
    @SerialName("name")
    val name: String = "",
    @Transient
    override val token: Token = Token()) : ASTNode()


@Serializable
data class FunctionModifiers(
    @SerialName("is_private")
    val isPrivate: Boolean = false,
    @SerialName("is_override")
    val isOverride: Boolean = false,
    @SerialName("is_rapter")
    val isRapter: Boolean = false,
    @Transient
    val token: Token = Token()
)
@Serializable
data class MethodNode(
    @SerialName("name")
    val _name: String? = null,
    @SerialName("parameters")
    val _parameters: List<ParamNode>? = null,
    @SerialName("body")
    val _body: BlockStatementNode? = null,
    val returnType: TypeNode? = null,
    @SerialName("modifiers")
    val modifiers: FunctionModifiers? = null,
    @Transient
    override val token: Token = Token()
) : ASTNode() {
    val name get() = _name ?: ""
    val body get() =  _body ?: BlockStatementNode()
    val parameters get() =  _parameters ?: listOf()

}

@Serializable
data class ParameterNode(
    val name: String,
    @SerialName("t")
    val type: String
) : ASTNode()

@Serializable
data class BinaryASTNode(val left: ASTNode, val operator: Token, val right: ASTNode) : ASTNode()

@Serializable
data class IfNode(
    val condition: ASTNode? = null,
    val thenBranch: ASTNode? = null,
    val elseBranch: ASTNode? = null,
    val _branches: List<IfNode>? = null,
    @Transient
    override val token: Token = Token()
) : ASTNode() {
    val branches get() = _branches ?: listOf()
}
@Serializable
data class WhenNode(
    val condition: ASTNode? = null,
    val _branches: List<WhenBranch>? = null,
    val elseBranch: ASTNode? = null,
    @Transient
    override val token: Token = Token()
) : ASTNode() {

    val branches get() = _branches ?: listOf()
}

@Serializable
data class WhenBranch(
    val pattern: ASTNode? = null,
    val result: ASTNode? = null,
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class ElseNode(
    val name: String? = null,
    @Transient
    override val token: Token = Token()) : ASTNode() // Represents the 'else' keyword

@Serializable
data class WhileNode(
    val condition: ASTNode, 
    val body: BlockStatementNode,
    @Transient
    override val token: Token = Token()) : ASTNode()

@Serializable
data class NewVarNode(
    @SerialName("name")
    val name: String,
    @SerialName("declared_type")
    val declaredType: TypeNode? = null,
    @SerialName("initializer")
    val initializer: ASTNode? = null,
    @Transient
    override val token: Token = Token(),
    @SerialName("var_type")
    val varType: Token = Token()
) : ASTNode() {
    fun isVal() = varType.type == TokenType.VAL
}


//class ForStatementNode(val variable: String, val iterable: ASTNode, val body: ASTNode) : StatementNode()
@Serializable
data class ForLoopNode(
    @SerialName("initializer")
    val initializer: ASTNode,
    @SerialName("condition")
    val condition: ASTNode,
    @SerialName("increment")
    val increment: ASTNode,
    @SerialName("body")
    val body: List<ASTNode>,
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class ForInNode(
    val variable: ASTNode,
    val iterable: ASTNode,
    val body: List<ASTNode> = listOf(),
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class ForRangeNode(
    val variable: ASTNode,
    val range: RangeNode,
    val body: List<ASTNode> = listOf(),
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class UnaryNode(
    @SerialName("operator")
    val operator: Token,
    @SerialName("right")
    val right: ASTNode
) : ASTNode()

@Serializable
data class RangeNode(
    val start: ASTNode, 
    val end: ASTNode, 
    val inclusive: Boolean,
    @Transient
    override val token: Token = Token()) : ASTNode()


@Serializable
data class ReturnType(
    @SerialName("operator")
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class LiteralNode(
    @SerialName("value")
    val value: JsonElement?,
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class CharNode(
    @SerialName("value")
    val value: Char,
    @Transient
    override val token: Token = Token()
) : ASTNode()
@Serializable
data class VariableNode(
    @SerialName("name")
    val name: String,
    @SerialName("inferredType")
    val inferredType: TypeNode? = null,   // для будущего type inference
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class GroupingNode(
    @SerialName("expression")
    val expression: ASTNode,
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class EmptyNode(val message: String = "") : ASTNode()

class LexicalException(override val message: String, val line: Int = 0, val charPositionInLine: Int = 0) : Exception(message) {
    override fun toString(): String {
        return "Lexical Error at line $line, position $charPositionInLine: $message"
    }
}

@Serializable
data class BreakStatementNode(
    val a: String = "",
    @Transient
    override val token: Token = Token()) : ASTNode()
@Serializable
data class ContinueStatementNode(
    val a: String = "",
    @Transient
    override val token: Token = Token()) : ASTNode()

@Serializable
data class InterfaceNode(
    @SerialName("name")
    val _name: String? = null,
    @SerialName("methods")
    val _methods: MutableMap<String, MethodNode>? = null,
    @SerialName("methods_call")
    val _methodsCall: List<MethodCallNode>? = null,
    @SerialName("member_variables")
    val _memberVariables: Map<String, NewVarNode>? = null,
    @Transient
    override val token: Token = Token()
) : ASTNode() {
    val name get() = _name ?: ""
    val methods get() = _methods ?: mutableMapOf()
    val methodsCall get() =  _methodsCall ?: listOf()
    val memberVariables get() =  _memberVariables ?: mutableMapOf()
}
@Serializable
data class ClassNode(
    @SerialName("name")
    val _name: String? = null,
    @SerialName("methods")
    val _methods: MutableMap<String, MethodNode>? = null,
    @SerialName("methods_call")
    val _methodsCall: List<MethodCallNode>? = null,
    @SerialName("member_variables")
    val _memberVariables: Map<String, NewVarNode>? = null,
    @SerialName("parameters")
    val _parameters: List<ParamNode>? = null,
    @SerialName("inheritances")
    val _inheritances: List<ParamNode>? = null,
    @SerialName("state")
    var parent: ClassNode? = null,
    @Transient
    override val token: Token = Token()
) : ASTNode() {
    val name get() = _name ?: ""
    val tokenType get() = token.type
    val methods get() = _methods ?: mutableMapOf()
    fun addMethod(value: MethodNode) {
        methods[value.name] = value
    }
    val methodsCall get() =  _methodsCall ?: listOf()
    val memberVariables get() =  _memberVariables ?: mutableMapOf()
    val parameters get() =  _parameters ?: listOf()
    val inheritances get() =  _inheritances ?: listOf()
}
@Serializable
data class BasNode(
    @SerialName("name")
    val _name: String? = null,
    @SerialName("methods")
    val _methods: Map<String, MethodNode>? = null,
    @SerialName("methods_call")
    val _methodsCall: List<MethodCallNode>? = null,
    @SerialName("member_variables")
    val _memberVariables: Map<String, NewVarNode>? = null,
    @SerialName("parameters")
    val _parameters: List<ParamNode>? = null,
) : ASTNode() {

    val name get() = _name ?: ""
    val methods get() =  _methods ?: mutableMapOf()
    val methodsCall get() =  _methodsCall ?: listOf()
    val memberVariables get() =  _memberVariables ?: mutableMapOf()
    val parameters get() =  _parameters ?: listOf()
}
@Serializable
data class BlockNode(val statements: List<ASTNode>) : ASTNode()

@Serializable
data class CaseNode(
    @SerialName("expression")
    val expression: ASTNode,
    @SerialName("block")
    val block: ASTNode?,
    @Transient
    override val token: Token = Token()
) : ASTNode()


@Serializable
data class TryNode(
    @SerialName("try")
    val tryBlock: ASTNode?,
    @SerialName("catch")
    val catchBlocks: List<CatchNode>,
    @SerialName("finally")
    val finallyBlock: ASTNode?,
    @Transient
    override val token: Token = Token()) : ASTNode()
@Serializable
data class CatchNode(
    @SerialName("exception_type")
    val exceptionType: Token,
    @SerialName("exception_var")
    val exceptionVar: Token,
    @SerialName("block")
    val block: ASTNode?,
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class ThrowStatementNode(
    @SerialName("expression")
    val expression: ASTNode,
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class DoWhileNode(
    @SerialName("body")
    val body: ASTNode?,
    @SerialName("condition")
    val condition: ASTNode,
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class ImportNode(
    @SerialName("module_name")
    val moduleName: String,
    @Transient
    override val token: Token = Token()) : ASTNode()

@Serializable
data class ThisNode(
    val name: String = "",
    @Transient
    override val token: Token = Token()) : ASTNode()

@Serializable
data class ExpressionStatementNode(val expression: ASTNode) : ASTNode()

@Serializable
data class IdentifierNode(val name: String) : ASTNode()

@Serializable
data class NumberNode(val value: Double) : ASTNode()

@Serializable
data class StringNode(val value: String) : ASTNode()

@Serializable
data class PrintStatementNode(val value: ASTNode) : ASTNode()

@Serializable
data class SemicolonNode(val value: String = "") : ASTNode()

@Serializable
data class BooleanNode(val value: Boolean) : ASTNode()
@Serializable
data class NullNode(val value: String = "") : ASTNode()
@Serializable
data class CommaNode(val value: String = "") : ASTNode()
//@Serializable
//data class AssignmentNode(
//    @SerialName("variable_name")
//    val variableName: String,
//    @SerialName("value")
//    val value: ASTNode
//) : ASTNode()
@Serializable
data class AssignmentNode(
    @SerialName("target")
    val target: ASTNode,
    @SerialName("value")
    val value: ASTNode,
    @Transient
    override val token: Token = Token()
): ASTNode()

@Serializable
data class CompoundAssignmentNode(
    @SerialName("target")
    val target: ASTNode,
    @SerialName("token")
    override val token: Token = Token(),
    @SerialName("value")
    val value: ASTNode
): ASTNode() {
    val operator get() = token.type
}

@Serializable
data class MemberAccessNode(
    @SerialName("instance")
    val _instance: ASTNode? = null,
    @SerialName("member_name")
    val _memberName: String? = null,
    @Transient
    override val token: Token = Token()
) : ASTNode() {
    val instance get() = _instance
    val memberName get() = _memberName ?: ""
}
@Serializable
data class SwitchStatementNode(
    @SerialName("expression")
    val expression: ASTNode,
    @SerialName("cases")
    val cases: List<CaseNode>,
    @SerialName("default_case")
    val defaultCase: ASTNode?,
    @Transient
    override val token: Token = Token()) : ASTNode()

@Serializable
data class ReturnNode(
    @SerialName("value")
    val value: ASTNode?,
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
class BlockStatementNode(
    @SerialName("statements")
    val statements: List<ASTNode> = listOf(),
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class BinaryExpressionNode(
    @SerialName("left")
    val left: ASTNode,
    @SerialName("token")
    override val token: Token = Token(),
    @SerialName("right")
    val right: ASTNode
) : ASTNode()

@Serializable
data class UnaryExpressionNode(
    @SerialName("token")
    override val token: Token = Token(),
    @SerialName("operand")
    val operand: ASTNode, val isPrefix: Boolean = false
) : ASTNode()

@Serializable
data class ParamNode(
    @SerialName("name")
    val name: String,
    @SerialName("declared_type")
    val declaredType: TypeNode = TypeNode.Simple("Any"),   // ← было String
    @SerialName("default_value")
    val defaultValue: ASTNode? = null,
    @Transient
    override val token: Token = Token()
) : ASTNode() {
    val tokenType: TokenType get() = token.type
}

@Serializable
data class ListNode(
    val elements: MutableList<ASTNode>,
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class MapNode(
    val pairs: MutableMap<ASTNode, ASTNode>,
    @Transient
    override val token: Token = Token()
) : ASTNode()

@Serializable
data class IndexAccessNode(
    val target: ASTNode, 
    val index: ASTNode,
    @Transient
    override val token: Token = Token()
) : ASTNode()
/**
 * Представление типов в языке (включая generics)
 */
@Serializable
sealed class TypeNode : ASTNode() {

    @Serializable
    data class Simple(
        val name: String, // "Int", "Double", "String", "Boolean", "Char"
        @Transient
        override val token: Token = Token()
    ) : TypeNode()

    @Serializable
    data class ListType(
        val elementType: TypeNode,
        @Transient
        override val token: Token = Token()
    ) : TypeNode()

    @Serializable
    data class MapType(
        val keyType: TypeNode,
        val valueType: TypeNode,
        @Transient
        override val token: Token = Token()
    ) : TypeNode()

    // Для будущего расширения (nullable, generics и т.д.)
    val isList: Boolean get() = this is ListType
    val isMap: Boolean get() = this is MapType

    fun toPrettyString(): String = when (this) {
        is Simple -> name
        is ListType -> "List<${elementType.toPrettyString()}>"
        is MapType -> "Map<${keyType.toPrettyString()}, ${valueType.toPrettyString()}>"
    }

    companion object {
        val ANY     = Simple("Any")
        val INT     = Simple("Int")
        val DOUBLE  = Simple("Double")
        val STRING  = Simple("String")
        val BOOLEAN = Simple("Boolean")
        val CHAR    = Simple("Char")
    }
}


@Serializable
enum class BpValueType(val symbol: String) {
    NULL("1"),
    INT("2"),
    DOUBLE("3"),
    BOOLEAN("4"),
    STRING("5"),
    JSON("6"),
    INT_ARRAY("7"),
    DOUBLE_ARRAY("8"),
    BOOLEAN_ARRAY("9"),
    STRING_ARRAY("10"),
    JSON_ARRAY("11"),
    CHAR("12"),
    CHAR_ARRAY("13");

    fun isNumber() = this == INT || this == DOUBLE
    fun isInt() = this == INT
    fun isDouble() = this == DOUBLE
    fun int() = symbol.toInt()

    override fun toString(): String {
        return when(this) {
            INT -> "Int"
            DOUBLE -> "Double"
            BOOLEAN -> "Boolean"
            STRING -> "String"
            CHAR -> "Char"
            JSON -> "Object"
            INT_ARRAY -> "List<Int>"
            DOUBLE_ARRAY -> "List<Double>"
            BOOLEAN_ARRAY -> "List<Boolean>"
            STRING_ARRAY -> "List<String>"
            JSON_ARRAY -> "List<Object>"
            CHAR_ARRAY -> "List<Char>"
            else -> "Null"
        }
    }
}