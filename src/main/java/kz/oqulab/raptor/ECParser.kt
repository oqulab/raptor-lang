package kz.oqulab.raptor

import kz.oqulab.raptor.utls.toJson
import kotlinx.serialization.json.*
import kotlin.collections.mutableListOf
class ECParser(_tokens: List<Token> = listOf()) {
    private var tokens: List<Token> = _tokens
    private var current = 0

    fun parse(_tokens: List<Token>): Map<String, ClassNode> {
        tokens = _tokens
        current = 0
        return parse()
    }

    fun parseNodes(_tokens: List<Token>): List<ASTNode> {
        tokens = _tokens
        current = 0
        val nodes = mutableListOf<ASTNode>()
        while (!isAtEnd()) {
            parseStatement()?.let { nodes.add(it) }
        }
        return nodes
    }

    fun parse(): Map<String, ClassNode> {
        val statements = mutableMapOf<String, ClassNode>()

        val methods = mutableMapOf<String, MethodNode>()
        val methodsCall = mutableListOf<MethodCallNode>()
        val fields = mutableMapOf<String, NewVarNode>()
        val params = mutableListOf<ParamNode>()

        while (!isAtEnd()) {
            val s = parseStatement()
            s?.let {
                when(s) {
                    is MethodNode -> {
                        methods[s.name] = s
                    }
                    is MethodCallNode -> {
                        methodsCall.add(s)
                    }
                    is NewVarNode -> {
                        fields[s.name] = s
                    }
                    is ParamNode -> {
                        params.add(s)
                    }
                    is ClassNode -> {
                        statements[s.name] = s
                    }
                    else -> {

                    }
                }

            }
        }

        statements["MainRn"] = ClassNode("MainRn", methods, methodsCall, fields, params, token = previous())
        return statements
    }

    private fun parseVariableDeclaration(addValues: Boolean = true): NewVarNode {
        val varType = previous()
        val nameToken = consume(TokenType.IDENTIFIER, "Expected variable name")
        val name = nameToken.value

        var declaredType: TypeNode? = null
        if (match(TokenType.COLON)) {
            declaredType = parseTypeNode()
        }

        var initializer: ASTNode? = null
        if (addValues) {
            consume(TokenType.EQUAL, "Ожидается '=' после имени переменной")
            initializer = parseExpression()
        }

        return NewVarNode(
            name = name,
            declaredType = declaredType,
            initializer = initializer,
            token = nameToken,
            varType = varType
        )
    }

    private fun parseBlock3(): BlockStatementNode? {
        val s = parseBlock()

        return if(s.isEmpty()) null else BlockStatementNode(s, previous())
    }

    private fun parseBlock(): List<ASTNode> {
        if(match(TokenType.EQUAL)) {
            return listOf(parseReturnStatement())
        }
        val statements = mutableListOf<ASTNode>()
        val isBraceOpen = match(TokenType.BRACE_OPEN) // , "Expected '{' to start block."
        while (!check(TokenType.BRACE_CLOSE) && !isAtEnd()) {
//            println("parseBlock: peek=${peek().value}")
            parseStatement()?.let {
                statements.add(it)
            }
        }
        if(isBraceOpen) match(TokenType.BRACE_CLOSE) // , "Expected '}' after block."
        return statements
    }
    //    fun parseMemberAccessOrFunctionCall(left: ASTNode): ASTNode {
//        var member: ASTNode = left
//        while (match(TokenType.DOT)) {
//            val memberName = consume(TokenType.IDENTIFIER, "Expected identifier after '.'")
//            if (match(TokenType.PARENTHESIS_OPEN)) {
//                val arguments = parseArguments()
//                member = FunctionCallNode(memberName.value, arguments, instance = member)
//            } else {
//                while (match(TokenType.DOT)) {
//                    val nextMemberName = consume(TokenType.IDENTIFIER, "Expected identifier after '.'")
//                    member = MemberAccessNode(member, nextMemberName.value)
//                }
//            }
//        }
//        return member
//    }
    private fun parseMemberAccessOrFunctionCall(left: ASTNode): ASTNode {
        val name = consume(TokenType.IDENTIFIER, "Expected identifier after '.'")
        var expression: ASTNode = left
//        println("parserMember:RUN name=${name.value}, peek=${peek().value}, previos=${previous().value}, expression=$expression")
        while (true) {
            when {
                match(TokenType.PARENTHESIS_OPEN) -> {
                    val arguments = parseArguments()
                    val ma = expression as? MemberAccessNode
                    expression = if(ma != null) {
                        MethodCallNode(ma.memberName, arguments, ma.instance ?: expression, previous())
                    } else {
                        MethodCallNode(name.value, arguments, expression, name)
                    }
                    break // Function call ends the chain
                }
                match(TokenType.EQUAL) -> {
                    if(expression is VariableNode) {
                        expression = MemberAccessNode(expression, name.value, name)
                    }
                    val value = parseExpression()
                    expression = AssignmentNode(expression, value, name)
                    break // Assignment ends the chain
                }
                match(TokenType.PLUS_EQUAL, TokenType.MINUS_EQUAL, TokenType.STAR_EQUAL, TokenType.SLASH_EQUAL) -> {
                    val token = previous()
                    if(expression is VariableNode) {
                        expression = MemberAccessNode(expression, name.value, previous())
                    }
                    val value = parseExpression()
                    expression = CompoundAssignmentNode(expression, token, value)
                    break // Compound assignment ends the chain
                }
                match(TokenType.PLUS_PLUS, TokenType.MINUS_MINUS) -> {
                    if(expression is VariableNode) {
                        expression = MemberAccessNode(expression, name.value, previous())
                    }
//                    val operator = previous().type
//                    IncrementDecrementNode(expression, operator, isPostfix = true)

                    expression = UnaryExpressionNode(previous(), expression, isPrefix = true)
                    break // Increment/Decrement ends the chain
                }
                check(TokenType.DOT) -> {
//                    println("parserMember:DOT-ST, peek=${peek().value}, previos=${previous().value}, expression=$expression")
//                    val nextName = consume(TokenType.IDENTIFIER, "Expected identifier after '.'")
                    if (match(TokenType.PARENTHESIS_OPEN)) {
                        val nextArguments = parseArguments()
                        expression = MethodCallNode(name.value, nextArguments, instance = expression, previous())
                    } else {
                        expression = MemberAccessNode(expression, name.value, previous())
//                        expression = MemberAccessNode(expression, nextName.value)
                        while (match(TokenType.DOT)) {
//                            println("parserMember:EX-DOT, peek=${peek().value}, previos=${previous().value}, expression=$expression")
                            val nextMemberName = consume(TokenType.IDENTIFIER, "Expected identifier after '.'")
                            expression = MemberAccessNode(expression, nextMemberName.value, previous())
                        }
                    }
                    // Continue the loop for chained member access
                }
                else -> {
                    expression = MemberAccessNode(expression, name.value, previous())
                    break
                }
            }
        }

//    println("parserMember:END name=${name.value}, peek=${peek().value}, previos=${previous().value}, expression=$expression")
        return expression
    }

    // Method to parse a string literal, including interpolations
    fun parseStringLiteral(): ASTNode {
        val parts = mutableListOf<ASTNode>()

        consume(TokenType.STRING_START, "Expect 'string_start'.")
        do {
            if (check(TokenType.STRING)) {
                // Add the string part to the parts list
                val stringToken = advance()

                parts.add(LiteralNode(JsonPrimitive(stringToken.value), stringToken))
            }

            if (match(TokenType.INTERPOLATION_START)) {
                // Parse the interpolated expression and add it to the parts list
                parts.add(parseExpression())
                consume(TokenType.INTERPOLATION_END, "Expect '}' after interpolated expression.")
            }
        } while (!check(TokenType.STRING_END) && !isAtEnd())

        // Consume the STRING_END token
        consume(TokenType.STRING_END, "Expect end of string after string literal.")

//        println("parseStringLiteral: parts: $parts")
        // If there's only one part and it's a string, return it directly
        if (parts.size == 1 && parts[0] is LiteralNode) {
            return parts[0]
        }


        // Otherwise, return a concatenation of all parts
        return ConcatenationNode(parts, previous())
    }
    fun parseCharLiteral(): ASTNode {
        consume(TokenType.CHARACTER_START, "Expect 'character_start'.")
        val stringToken = advance()
        val part = CharNode(stringToken.value.get(0), stringToken)
        consume(TokenType.CHARACTER_END, "Expect end of character after Char literal.")

        return part
    }

    private fun parseList(): ListNode {
        val elements = mutableListOf<ASTNode>()
        if (!check(TokenType.BRACKET_CLOSE)) {
            do {
                elements.add(parseExpression())
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.BRACKET_CLOSE, "Expected ']' after list elements")
        return ListNode(elements, previous())
    }

    private fun parseMap(): MapNode {
        val pairs = mutableMapOf<ASTNode, ASTNode>()
        if (!check(TokenType.BRACE_CLOSE)) {
            do {
                val key = parseExpression()
                consume(TokenType.COLON, "Expected ':' after key in map")
                val value = parseExpression()
                pairs[key] = value
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.BRACE_CLOSE, "Expected '}' after map entries")
        return MapNode(pairs, previous())
    }
    /**
     * Парсит тип: Int, Double, String, Boolean, Char,
     * List<Int>, Map<String, List<Boolean>>, List<List<Int>> и т.д.
     */
    private fun parseTypeNode(): TypeNode {
        val typeNameToken = consume(TokenType.IDENTIFIER, "Expected type name after ':'")
        val typeName = typeNameToken.value

        return when {
            typeName.equals("List", ignoreCase = true) -> {
                consume(TokenType.LESS, "Expected '<' after List")
                val elementType = parseTypeNode()
                consume(TokenType.GREATER, "Expected '>' after List type")
                TypeNode.ListType(elementType, previous())
            }

            typeName.equals("Map", ignoreCase = true) -> {
                consume(TokenType.LESS, "Expected '<' after Map")
                val keyType = parseTypeNode()
                consume(TokenType.COMMA, "Expected ',' between key and value type in Map")
                val valueType = parseTypeNode()
                consume(TokenType.GREATER, "Expected '>' after Map type")
                TypeNode.MapType(keyType, valueType, previous())
            }

            else -> {
                val simpleName = when (typeName.lowercase()) {
                    "int" -> "Int"
                    "double" -> "Double"
                    "string" -> "String"
                    "boolean" -> "Boolean"
                    "char" -> "Char"
                    else -> typeName
                }
                TypeNode.Simple(simpleName, typeNameToken)
            }
        }
    }

    private fun parsePrimary(): ASTNode {
        var expr = when {
            match(TokenType.PARENTHESIS_OPEN) -> {
                val expr = parseExpression()
                consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after expression")
                GroupingNode(expr, previous())  // GroupingNode to represent a parenthesized expression
            }
            match(TokenType.FALSE) -> LiteralNode(JsonPrimitive(false), previous())
            match(TokenType.TRUE) -> LiteralNode(JsonPrimitive(true), previous())
            match(TokenType.NULL) -> LiteralNode(JsonNull, previous())
            match(TokenType.NUMBER, TokenType.DOUBLE) -> {
                val value = previous().value.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(previous().value.toDouble())
                LiteralNode(value, previous())
            }
            match(TokenType.INT) -> {
                val value = previous().value.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(previous().value.toInt())
                LiteralNode(value, previous())
            }
            check(TokenType.CHARACTER_START) -> parseCharLiteral()
            check(TokenType.STRING_START) -> parseStringLiteral()

            match(TokenType.IDENTIFIER) -> {
                var identifier: ASTNode = VariableNode(previous().value, token = previous())
                var c = true
                while (c) {
                    identifier = when {
                        match(TokenType.DOT) -> parseMemberAccessOrFunctionCall(identifier)
                        check(TokenType.PARENTHESIS_OPEN) -> parseFunctionCall()

                        match(TokenType.BRACKET_OPEN) -> {
                            val index = parseExpression()
                            consume(TokenType.BRACKET_CLOSE, "Expected ']' after index")
                            IndexAccessNode(identifier, index, previous())
                        }
                        check(TokenType.EQUAL) && identifier is IndexAccessNode -> {
                            consume(TokenType.EQUAL, "Expected '=' after target expression")
                            val value = parseExpression()
                            return AssignmentNode(identifier, value, previous())
                        }

                        check(TokenType.EQUAL) -> {
                            // Handle assignment
                            parseAssignmentStatement()
                        }
                        else -> {
                            c = false
                            identifier
                        }
                    }
                }

                identifier
            }
            // Handling parentheses
            match(TokenType.PARENTHESIS_OPEN) -> {
                val expr = parseExpression()
                GroupingNode(expr, previous())
            }
            check(TokenType.IF, TokenType.FUN, TokenType.WHEN) -> parseStatement() ?: throw Exception("Unexpected token '${previous().value}', line=${previous().line}, column=${previous().column} after identifier: '${previous(2).value}'")

            match(TokenType.BRACKET_OPEN) -> parseList()
            match(TokenType.BRACE_OPEN) -> parseMap()
            match(TokenType.BREAK) -> parseBreakStatement()
            else -> {
                // throw Exception
                NoneASTNode(token = previous())
            }
        }

//        println("parsePrimary: expr=$expr, peek=${peek()}, previous=${previous()}")
        while (true) {
            expr = when {
                match(TokenType.BRACKET_OPEN) -> {
                    val index = parseExpression()
                    consume(TokenType.BRACKET_CLOSE, "Expected ']' after index")
                    IndexAccessNode(expr, index, previous())
                }
                match(TokenType.DOT) -> {
                    val member = consume(TokenType.IDENTIFIER, "Expected member name after '.'")
                    MemberAccessNode(expr, member.value, previous())
                }
                else -> {
//                    println("parsePrimary:return expr=$expr, peek=${peek()}, previous=${previous()}")
                    return expr
                }
            }
        }
    }
    private fun parseArguments(): List<ASTNode> {
        val arguments = mutableListOf<ASTNode>()
        if (!check(TokenType.PARENTHESIS_CLOSE)) {
            do {
                arguments.add(parseExpression())
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after arguments")
        return arguments
    }
    private fun parseAssignment(): AssignmentNode {
        // The target of the assignment could be a variable or a more complex expression like a member access
//        val target = parseExpression()
////        val name = consume(TokenType.IDENTIFIER, "Expected variable name")
////        consume(TokenType.EQUAL, "Expected '=' after variable name")
////        val value = parseExpression()
////        return AssignmentNode(name.value, value)
//        // Ensure we have an equals sign following the target
//        consume(TokenType.EQUAL, "Expected '=' after target expression, peek=${peek()}, prev=${previous()}, target=$target")
//        // Parse the value to be assigned to the target
//        val value = parseExpression()
//        // Return a new AssignmentNode with the parsed target and value
//        return AssignmentNode(target, value)

        val name = consume(TokenType.IDENTIFIER, "Expected variable name")
        consume(TokenType.EQUAL, "Expected '=' after variable name")
        val value = parseExpression()
        return AssignmentNode(VariableNode(name.value, token = name), value, previous())
    }
    private fun parseAssignmentStatement(): AssignmentNode {
        // The target is expected to be a simple variable (identifier), but it's still an ASTNode
        val identifierToken = previous()
        val target = VariableNode(identifierToken.value, token = identifierToken) // Assuming VariableNode is a valid ASTNode for identifiers
        // Ensure we have an equals sign following the target
        consume(TokenType.EQUAL, "Expected '=' after target expression")
        // Parse the value to be assigned to the target
        val value = parseExpression()
        // Return a new AssignmentNode with the parsed target and value
        return AssignmentNode(target, value, previous())
    }
    private fun parseExpression(): ASTNode {
        //println("parseExpression:Start, peek=${peek().value}, previous=${previous().value}")
        var expr = parseComparisonOrLogicalExpression()

        //println("parseExpression: expr=$expr")
        // If the next token is an assignment, handle it as an assignment expression.
        if (expr is VariableNode && match(TokenType.EQUAL)) {
            val value = parseExpression()
            return AssignmentNode(expr, value, previous())
        }
        while (true) {
            when {
                match(TokenType.EQUAL) && expr is VariableNode -> {
                    // Handle assignment
                    expr = parseAssignment()
                }
                match(TokenType.DOT) -> {
                    // Handle member access or function calls
                    expr = parseMemberAccessOrFunctionCall(expr)
                }
                match(
                    TokenType.PLUS_EQUAL,
                    TokenType.MINUS_EQUAL,
                    TokenType.STAR_EQUAL,
                    TokenType.SLASH_EQUAL,
                    TokenType.PERCENT_EQUAL
                ) -> {
                    val token = previous()
                    val value = parseExpression() // Parse the value to be assigned
                    expr = CompoundAssignmentNode(expr, token, value)
                }
                else -> {
                    // Exit the loop if no other expression constructs are matched
                    break
                }
            }
        }
        return expr
    }


    private fun parseArithmeticExpression(): ASTNode {
        // Start by parsing expressions with higher precedence, such as multiplication and division
        var expr = parseMultiplicativeExpression()

        // Continue parsing while there are addition or subtraction operators
        while(match(TokenType.PLUS, TokenType.MINUS, TokenType.PERCENT)) {
            val operator = previous()
            val right = parseMultiplicativeExpression()
            expr = BinaryExpressionNode(expr, operator, right)
        }

        return expr
    }

    private fun parseMultiplicativeExpression(): ASTNode {
        var expr = parseUnaryExpression()

        //println("parseMultiplicativeExpression: expr=$expr, peek=${peek().value}, prev=${previous().value}")
        while (match(TokenType.STAR, TokenType.SLASH)) {
            val operator = previous()
            val right = parseUnaryExpression()
            expr = BinaryExpressionNode(expr, operator, right)
        }

        return expr
    }
    private fun parseComparisonOrLogicalExpression(): ASTNode {
        var expr = parseArithmeticExpression()

        // Сначала все сравнения (выше приоритет)
        while (true) {
            if (match(
                    TokenType.IS,
                    TokenType.EQUAL_EQUAL,
                    TokenType.BANG_EQUAL,
                    TokenType.LESS,
                    TokenType.LESS_EQUAL,
                    TokenType.GREATER,
                    TokenType.GREATER_EQUAL
                )) {
                val operator = previous()
                val right = parseArithmeticExpression()
                expr = BinaryExpressionNode(expr, operator, right)
                continue
            }
            break
        }

        // Потом логические && и || (самый низкий приоритет)
        while (true) {
            if (match(TokenType.AND_AND, TokenType.OR_OR)) {
                val operator = previous()
                val right = parseComparisonOrLogicalExpression() // рекурсия для цепочек
                expr = BinaryExpressionNode(expr, operator, right)
                continue
            }
            break
        }

        return expr
    }

    private fun parseUnaryExpression(): ASTNode {
        // Handling prefix unary operators
        if (match(
                TokenType.BANG,
                TokenType.TILDE,
                TokenType.AMPERSAND,
                TokenType.PLUS_PLUS,
                TokenType.MINUS_MINUS,
                TokenType.PERCENT,
                TokenType.MINUS,
                TokenType.PLUS,
            )) {
            val operator = previous()
//            println("operator: operator=$operator")
            val operand = parseUnaryExpression()  // Recursive call to handle nested unary operators
            return UnaryExpressionNode(operator, operand, isPrefix = true)
        }

        return parsePostfixExpression()
    }

    private fun parseFunctionCall(): MethodCallNode {
        val functionName = previous()
        consume(TokenType.PARENTHESIS_OPEN, "Expected1 '(' after function name")
        val arguments = mutableListOf<ASTNode>()

        if (!check(TokenType.PARENTHESIS_CLOSE)) {
            do {
                arguments.add(parseExpression())
            } while (match(TokenType.COMMA) )
        }

        consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after arguments")
        return MethodCallNode(functionName.value, arguments, token = functionName)
    }
    private fun parseIdentifierStatement(): ASTNode {
        val identifier = previous() // Assumes the identifier token has already been consumed

        return when {
            check(TokenType.EQUAL) -> {
                // Handle assignment
                parseAssignmentStatement()
            }
            check(TokenType.PARENTHESIS_OPEN) -> {
                // Handle function call
                parseFunctionCall()
            }
//            isComparisonOrLogical() -> parseComparisonOrLogicalExpression()
            // Member access or chained function calls: `identifier.otherIdentifier`
            match(TokenType.DOT) -> {
//                println("parseInit: test:DOT1")
                parseMemberAccessOrFunctionCall(VariableNode(identifier.value, token = identifier))
            }
            else -> {
                // If the identifier is not followed by any of the above, it's a simple variable reference
                VariableNode(identifier.value, token = identifier)
            }
        }
    }
    private fun parseWhenStatement(): WhenNode {
        consume(TokenType.PARENTHESIS_OPEN, "Expected '(' after 'when'")
        val condition = parseExpression()
        consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after condition")
        consume(TokenType.BRACE_OPEN, "Expected '{' to start 'when' branches")

        val branches = mutableListOf<WhenBranch>()
        var elseBranch: ASTNode? = null

        while (!match(TokenType.BRACE_CLOSE)) {
            val pattern = if (match(TokenType.ELSE)) ElseNode(token=previous()) else parseExpression()
            consume(TokenType.ARROW, "Expected '->' after pattern")
            val result = if (check(TokenType.BRACE_OPEN)) {
                parseBlock3()
            } else {
                parseExpression()
            }
            if (pattern is ElseNode) {
                elseBranch = result
            } else {
                branches.add(WhenBranch(pattern, result, previous()))
            }
        }

        return WhenNode(condition, branches, elseBranch, previous())
    }
    private fun parseIfStatement(): IfNode {
        consume(TokenType.PARENTHESIS_OPEN, "Expected '(' after 'if'.")
        val condition = parseExpression()
        println("parseIfStatement: condition=$condition, peek=${peek()}")
        consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after condition.")

        val thenBranch = if(check(TokenType.BRACE_OPEN)) parseBlock3() else if(match(TokenType.RETURN)) parseReturnStatement() else parseExpression()
        var elseBranch: ASTNode? = null
        val elseIfBranches = mutableListOf<IfNode>()
        while (check(TokenType.ELSE) && checkNext(TokenType.IF)) {
            match(TokenType.ELSE)
            match(TokenType.IF)
            consume(TokenType.PARENTHESIS_OPEN, "Expected '(' after 'if'.")
            val elseIfCondition = parseExpression()
            consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after condition.")
            val elseIfThenBranch = if(check(TokenType.BRACE_OPEN)) parseBlock3() else if(match(TokenType.RETURN)) parseReturnStatement() else parseExpression()
            elseIfBranches.add(IfNode(elseIfCondition, elseIfThenBranch, token = previous()))
        }
        if (match(TokenType.ELSE)) {
            elseBranch = if(check(TokenType.BRACE_OPEN)) parseBlock3() else if(match(TokenType.RETURN)) parseReturnStatement() else parseExpression()
        }

        return IfNode(condition, thenBranch, elseBranch, elseIfBranches, previous())
    }
    private fun parseType(): String {
        return if (peek().value.lowercase() == "int") {
            "int"
        } else if (peek().value.lowercase() == "double") {
            "double"
        } else if (peek().value.lowercase() == "boolean") {
            "boolean"
        } else if (peek().value.lowercase() == "string") {
            "string"
        } else {
            "null"
        }
    }

    private fun parseParameters(): List<ParamNode> {
        val parameters = mutableListOf<ParamNode>()
        consume(TokenType.PARENTHESIS_OPEN, "Expected '(' after function name")

        if (!check(TokenType.PARENTHESIS_CLOSE)) {
            do {
                var paramToken: Token? = null
                if (check(TokenType.VAL)) {
                    paramToken = consume(TokenType.VAL, "Expected parameter name")
                } else if (check(TokenType.VAR)) {
                    paramToken = consume(TokenType.VAR, "Expected parameter name")
                }

                val paramName = consume(TokenType.IDENTIFIER, "Expected parameter name").value

                var declaredType: TypeNode = TypeNode.ANY
                if (match(TokenType.COLON)) {
                    declaredType = parseTypeNode()
                }

                val defaultValue = if (match(TokenType.EQUAL)) parseExpression() else null

                parameters.add(
                    ParamNode(
                        name = paramName,
                        declaredType = declaredType,
                        defaultValue = defaultValue,
                        token = paramToken ?: Token()
                    )
                )
            } while (match(TokenType.COMMA))
        }

        consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after parameters")
        return parameters
    }
    private fun parseInheritance(): List<ParamNode> {
        val parameters = mutableListOf<ParamNode>()

        if(check(TokenType.COLON)) {
            match(TokenType.COLON)
            if (!check(TokenType.BRACE_CLOSE)) {
                do {
                    val paramName = consume(TokenType.IDENTIFIER, "Expected parameter name").value
                    parameters.add(ParamNode(paramName, token = previous()))
                } while (match(TokenType.COMMA))
            }
        }

        return parameters
    }
    private fun parseFunctionModifiers(): FunctionModifiers {
        var isPrivate = false
        var isOverride = false
        var isRapter = false

        while (checkForFunctionModifier()) {
            when (peek().type) {
                TokenType.PRIVATE -> {
                    consume(TokenType.PRIVATE, "Expected 'private'")
                    isPrivate = true
                }
                TokenType.OVERRIDE -> {
                    consume(TokenType.OVERRIDE, "Expected 'override'")
                    isOverride = true
                }
                TokenType.RAPTOR -> {
                    consume(TokenType.RAPTOR, "Expected 'rapter'")
                    isRapter = true
                }
                else -> {}
            }
        }

        return FunctionModifiers(isPrivate, isOverride, isRapter, previous())
    }

    private fun parseFunctionDeclaration(modifiers: FunctionModifiers? = null): MethodNode {
        val functionName = if (check(TokenType.IDENTIFIER))
            consume(TokenType.IDENTIFIER, "Expected function name").value
        else if (check(TokenType.PARENTHESIS_OPEN)) previous(3).value
        else "anon"

        val params = parseParameters()

        var returnType: TypeNode? = null
        if (match(TokenType.COLON)) {
            returnType = parseTypeNode()
        }

        val body = BlockStatementNode(parseBlock(), previous())

        return MethodNode(
            _name = functionName,
            _parameters = params,
            _body = body,
            returnType = returnType,
            modifiers = modifiers,
            token = previous()
        )
    }

    private fun parseClass(): ClassNode {
        val name = consume(TokenType.IDENTIFIER, "Expected class name").value
        val methods = mutableMapOf<String, MethodNode>()
        val methodsCall = mutableListOf<MethodCallNode>()
        val fields = mutableMapOf<String, NewVarNode>()
        val params = mutableListOf<ParamNode>()

        if (check(TokenType.PARENTHESIS_OPEN)) {
            params.addAll(parseParameters())
        }

//        println("parseClass: params=$params")
        val inheritances = parseInheritance()
        consume(TokenType.BRACE_OPEN, "Expected '{' after class name")
        while (!check(TokenType.BRACE_CLOSE) && !isAtEnd()) {
            when {
                checkForFunctionModifier() -> {
                    val modifiers = parseFunctionModifiers()
                    match(TokenType.FUN) // This should succeed since we've already checked for modifiers
                    val s = parseFunctionDeclaration(modifiers)
                    methods[s.name] = s
                }
                match(TokenType.FUN) -> {
                    val s = parseFunctionDeclaration()
                    methods[s.name] = s
                }
//                (check(TokenType.VAR, TokenType.VAL) && next(2).type == TokenType.FUN) -> {
//                    match(TokenType.VAR, TokenType.VAL)
//                    println("var_fun, peek=${peek()}")
//                    val s = parseFunctionDeclaration()
//                    methods[s.name] = s
//                }
                match(TokenType.VAR, TokenType.VAL) -> {
                    val s = parseVariableDeclaration()
                    fields[s.name] = s
                }
                match(TokenType.IDENTIFIER) -> {
                    methodsCall.add(parseFunctionCall())
                }
                else -> throw Exception("Unexpected token in class body: ${peek().value}, line=${peek().line}, column=${peek().column}")
            }
        }

        consume(TokenType.BRACE_CLOSE, "Expected '}' at the end of class declaration: ${peek().value}, line=${peek().line}, column=${peek().column}")
        return ClassNode(name, methods, methodsCall, fields, params, inheritances, token = previous())
    }

//    private fun parseInterfaceFunctionDeclaration(modifiers: FunctionModifiers? = null): MethodNode {
//        val functionName = if(check(TokenType.IDENTIFIER)) consume(TokenType.IDENTIFIER, "Expected function name").value
//        else if(check(TokenType.PARENTHESIS_OPEN)) previous(3).value else "anon"
//
//        val params = parseParameters()
//        var returnType: ReturnType? = null
//        if(match(TokenType.COLON)) {
//            returnType = ReturnType(consume(TokenType.IDENTIFIER, "Expected function name"))
//        }
//        val body = BlockStatementNode(parseBlock(), previous())
//
//        return MethodNode(functionName, params, body, returnType, modifiers = modifiers, previous())
//    }
    private fun parseInterface(): ClassNode {
        val name = consume(TokenType.IDENTIFIER, "Expected class name").value
        val methods = mutableMapOf<String, MethodNode>()
        val methodsCall = mutableListOf<MethodCallNode>()
        val fields = mutableMapOf<String, NewVarNode>()

        consume(TokenType.BRACE_OPEN, "Expected '{' after class name")
        while (!check(TokenType.BRACE_CLOSE) && !isAtEnd()) {
            when {
                checkForFunctionModifier() -> {
                    val modifiers = parseFunctionModifiers()
                    match(TokenType.FUN) // This should succeed since we've already checked for modifiers
                    val s = parseFunctionDeclaration(modifiers)
                    methods[s.name] = s
                }
                match(TokenType.FUN) -> {
                    val s = parseFunctionDeclaration()
                    methods[s.name] = s
                }
                match(TokenType.VAR, TokenType.VAL) -> {
                    val s = parseVariableDeclaration(false)
                    fields[s.name] = s
                }
                match(TokenType.IDENTIFIER) -> {
                    methodsCall.add(parseFunctionCall())
                }
                else -> throw Exception("Unexpected token in class body: ${peek().value}, line=${peek().line}, column=${peek().column}")
            }
        }

        consume(TokenType.BRACE_CLOSE, "Expected '}' at the end of class declaration: ${peek().value}, line=${peek().line}, column=${peek().column}")
        return ClassNode(name, methods, methodsCall, fields, token = previous())
    }

    private fun parseForLoop(): ASTNode {
        consume(TokenType.PARENTHESIS_OPEN, "Expected '(' after 'for'")

        // Check if it's a traditional for loop or a for-in/for-range loop
        val initializer: ASTNode = parseForInitializer()

        // Determine the type of for loop based on the next tokens
        val isRangeLoop = check(TokenType.IN) && !checkNext(TokenType.IDENTIFIER) && !checkNext(TokenType.STRING_START)
        val isTraditionalLoop = check(TokenType.SEMICOLON)

        val loopNode: ASTNode = when {
            isRangeLoop -> {
                consume(TokenType.IN, "Expected 'in' after loop variable")
                val rangeExpression = parseRangeExpression()
                consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after loop range")
                val body = parseBlock()
                ForRangeNode(initializer, rangeExpression, body, previous())
            }
            isTraditionalLoop -> {
                consume(TokenType.SEMICOLON, "Expected ';' after initializer")
                val condition = parseExpression()
                consume(TokenType.SEMICOLON, "Expected ';' after condition")
                val increment = parseExpression()
                consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after for loop header")
                val body = parseBlock()
                ForLoopNode(initializer, condition, increment, body, previous())
            }
            else -> {
                consume(TokenType.IN, "Expected 'in' after loop variable")
                val iterable = parseExpression()
                consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after iterable expression")
                val body = parseBlock()
                ForInNode(initializer, iterable, body, previous())
            }
        }

        return loopNode
    }

    private fun parseRangeExpression(): RangeNode {
        val start = parseExpression()
        val inclusive = !match(TokenType.UNTIL)
        if(inclusive) match(TokenType.DOT_DOT)
        val end = parseExpression()
        return RangeNode(start, end, inclusive, previous())
    }

    private fun parseWhileStatement(): WhileNode {
        consume(TokenType.PARENTHESIS_OPEN, "Expected '(' after 'while'")

        val condition = parseExpression()  // Parses the loop condition

        consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after condition")

        val body = BlockStatementNode(parseBlock(), previous())

        return WhileNode(condition, body, previous())
    }
    private fun parsePostfixExpression(): ASTNode {
        var expr = parsePrimary()

        while (true) {
            expr = when {
                match(TokenType.DOT) -> {
                    val member = consume(TokenType.IDENTIFIER, "Expected member name after '.'")
                    MemberAccessNode(expr, member.value, previous())
                }
                match(TokenType.PARENTHESIS_OPEN) -> {
                    // This means it's a function call
                    val arguments = parseArguments()
                    val ma = expr as? MemberAccessNode
                    if(ma != null) {
                        MethodCallNode(ma.memberName, arguments, ma.instance ?: expr, previous())
                    } else {
                        MethodCallNode((expr as VariableNode).name, arguments, token = previous())
                    }
                }
                expr is VariableNode && match(TokenType.PLUS_PLUS, TokenType.MINUS_MINUS) -> UnaryExpressionNode(previous(), expr)
                else -> return expr
            }
        }
    }

    private fun parseForInitializer(): ASTNode {
        if (match(TokenType.VAR, TokenType.VAL)) {
            return parseVariableDeclaration()
        } else if (check(TokenType.IDENTIFIER)) {
            val identifier = consume(TokenType.IDENTIFIER, "Expected loop variable")
            return VariableNode(identifier.value, token = identifier)
        }
        throw Exception("Invalid initializer in for loop.")
    }

    private fun parseReturnStatement(): ReturnNode {
        val returnValue: ASTNode? = if (!isAtEnd()) {
            parseStatement() //parseExpression()  // Parse the expression to be returned
        } else {
            null  // No return value or end of line/block reached
        }
        // Note: No need to consume a semicolon
        return ReturnNode(returnValue, previous())
    }

    private fun parseBreakStatement(): BreakStatementNode {
        return BreakStatementNode(token = previous())
    }
    private fun parseContinueStatement(): ContinueStatementNode {
        return ContinueStatementNode(token = previous())
    }
    private fun parseSwitchStatement(): SwitchStatementNode {
        consume(TokenType.PARENTHESIS_OPEN, "Expected '(' after 'switch'.")
        val expression = parseExpression()
        consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after expression.")

        consume(TokenType.BRACE_OPEN, "Expected '{' after switch expression.")
        val cases = mutableListOf<CaseNode>()
        var defaultCase: ASTNode? = null

        while (!check(TokenType.BRACE_CLOSE) && !isAtEnd()) {
            if (match(TokenType.CASE)) {
                val caseExpr = parseExpression()
                consume(TokenType.COLON, "Expected ':' after case expression.")
                val caseBlock = parseBlock3()
                cases.add(CaseNode(caseExpr, caseBlock, previous()))
            } else if (match(TokenType.DEFAULT)) {
                consume(TokenType.COLON, "Expected ':' after 'default'.")
                defaultCase = parseBlock3()
                break  // 'default' should be the last case
            }
        }

        consume(TokenType.BRACE_CLOSE, "Expected '}' after switch cases.")
        return SwitchStatementNode(expression, cases, defaultCase, previous())
    }

    private fun parseTryStatement(): TryNode {
        val tryBlock = parseBlock3()

        val catchBlocks = mutableListOf<CatchNode>()
        while (match(TokenType.CATCH)) {
            consume(TokenType.PARENTHESIS_OPEN, "Expected '(' after 'catch'.")
            val exceptionVar = consume(TokenType.IDENTIFIER, "Expected exception type.")
            consume(TokenType.COLON, "Expected ':' after 'default'.")
            val exceptionType = consume(TokenType.IDENTIFIER, "Expected exception variable.")
            consume(TokenType.PARENTHESIS_CLOSE, "Expected ')' after catch declaration.")
            val catchBlock = parseBlock3()
            catchBlocks.add(CatchNode(exceptionType, exceptionVar, catchBlock, previous()))
        }

        var finallyBlock: ASTNode? = null
        if (match(TokenType.FINALLY)) {
            finallyBlock = parseBlock3()
        }

        return TryNode(tryBlock, catchBlocks, finallyBlock, previous())
    }

    private fun parseThrowStatement(): ThrowStatementNode {
        val expression = parseExpression()
        return ThrowStatementNode(expression, previous())
    }

    private fun parseDoWhileStatement(): DoWhileNode {
        val body = parseBlock3()
        consume(TokenType.WHILE, "Expected 'while' after do block.")
        val condition = parseExpression()
        return DoWhileNode(body, condition, previous())
    }
    private fun parseImportStatement(): ImportNode {
        val moduleName = parseQualifiedName()
        return ImportNode(moduleName, previous())
    }
    private fun parseThisStatement() = ThisNode(token = previous())
    private fun parseQualifiedName(): String {
        val builder = StringBuilder()

        builder.append(consume(TokenType.IDENTIFIER, "Expected identifier."))
        while (match(TokenType.DOT)) {
            builder.append(".")
            builder.append(consume(TokenType.IDENTIFIER, "Expected identifier after '.'."))
        }

        return builder.toString()
    }

    private fun checkForFunctionModifier(): Boolean {
        return peek().type in setOf(TokenType.PRIVATE, TokenType.OVERRIDE, TokenType.RAPTOR)
    }

    private fun parseStatement(): ASTNode? {
        match(TokenType.SEMICOLON)
        return when {
            match(TokenType.IF) -> parseIfStatement()
            match(TokenType.WHEN) -> parseWhenStatement()
            match(TokenType.WHILE) -> parseWhileStatement()
            match(TokenType.FOR) -> parseForLoop()
            // Check for potential modifiers before "fun"
            checkForFunctionModifier() -> {
                val modifiers = parseFunctionModifiers()
                match(TokenType.FUN) // This should succeed since we've already checked for modifiers
                parseFunctionDeclaration(modifiers)
            }
            match(TokenType.FUN) -> parseFunctionDeclaration()
            // Handle variable assignment
            (check(TokenType.IDENTIFIER) && checkNext(TokenType.EQUAL)) -> {
                parseAssignment()
            }
            match(TokenType.VAR, TokenType.VAL) -> parseVariableDeclaration()
            check(TokenType.IDENTIFIER) && checkNext(
                TokenType.PLUS_PLUS,
                TokenType.MINUS_MINUS
            ) -> parsePostfixExpression()
//            match(TokenType.IDENTIFIER) -> parseIdentifierStatement()
            match(TokenType.CLASS) -> parseClass()
            match(TokenType.INTERFACE) -> parseInterface()
//            match(TokenType.CLASS, TokenType.FUN) -> parseBas()
            match(TokenType.COMMA) -> parseIdentifierStatement()
            match(TokenType.CONTINUE) -> parseContinueStatement()
//            match(TokenType.WHEN) -> parseSwitchStatement()
            match(TokenType.TRY) -> parseTryStatement()
            match(TokenType.THROW) -> parseThrowStatement()
            match(TokenType.DO) -> parseDoWhileStatement()
            match(TokenType.IMPORT) -> parseImportStatement()
            match(TokenType.RETURN) -> parseReturnStatement()
//            (isComparisonOrLogical()) -> parseComparisonOrLogicalExpression()
//            check(TokenType.BRACE_OPEN) -> parseBlock3()
            // Other casesd
            else -> {
                val exp = parseExpression()
                if(exp is NoneASTNode)  throw Exception("Unexpected token '${previous().value}', line=${previous().line}, column=${previous().column} after identifier: '${previous(2).value}'")
                exp
            }
        }
    }

    private fun isComparisonOrLogical(): Boolean {
        return peek().type in setOf(
            TokenType.IS,
            TokenType.EQUAL_EQUAL,
            TokenType.BANG_EQUAL,
            TokenType.LESS,
            TokenType.LESS_EQUAL,
            TokenType.GREATER,
            TokenType.GREATER_EQUAL,
            TokenType.AND_AND,
            TokenType.OR_OR
        )
    }
    private fun isStartOfExpression(): Boolean {
        return peek().type in setOf(
            TokenType.NUMBER,
//            TokenType.INT,
//            TokenType.DOUBLE,
//            TokenType.STRING,
//            TokenType.TRUE,
//            TokenType.FALSE,
//            TokenType.NULL,
//            TokenType.PLUS,
//            TokenType.MINUS,
//            TokenType.PERCENT,
//            TokenType.PLUS_PLUS,
//            TokenType.MINUS_MINUS,
//            TokenType.BANG,
////            TokenType.IDENTIFIER,
//            TokenType.TILDE,
//            TokenType.AMPERSAND,
////            TokenType.STAR,
//            TokenType.PARENTHESIS_OPEN // If you handle parenthesized expressions
            // Add other tokens that can start an expression
        )
    }
    private fun consume(type: TokenType, message: String, log: Boolean = false): Token {
//        if(log) {
//            println("consume: type=$type, peek=${peek()}, $message")
//        }
        if (check(type)) return advance()
        throw Exception(message + ", peek=${peek().value}, line=${peek().line}, column=${peek().column}")
    }
    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }
    private fun check(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                return true
            }
        }
        return false
    }
    private fun checkNext(vararg types: TokenType): Boolean {
        for (type in types) {
            if (checkNext(type)) {
                return true
            }
        }
        return false
    }
    private fun match(type: TokenType, default: Int = 1): Boolean {
        if (check(type)) {
//            println("match:true: type=$type")
            advance(default)
            return true
        }
//        println("match:false: type=$type, peek=${peek()}")
        return false
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun checkNext(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return next().type == type
    }
    private fun error(token: Token, message: String): Exception {
        // Handle parsing error
        throw Exception("Error at ${token.value}: $message")
    }
    private fun advance(default: Int = 1): Token {
        if (!isAtEnd()) current += default
        return previous()
    }

    private fun isAtEnd(): Boolean = current >= tokens.size || peek().type == TokenType.EOF

    private fun previous(default: Int = 1): Token = tokens[current - default]

    private fun peek(): Token = tokens[current]
    private fun next(default: Int = 1): Token = tokens[current + default]
}

//fun start() {
//    print("Start program")
//    val r = sum(12, 23)
//    val b = subtract(r, 7)
//
//    val s = r >= b
//
//    if(s) {
//        print("variable is true!")
//    } else {
//        print("variable is false!")
//    }
//
//    var counter = 0
//    loop(counter < 10) {
//        counter++
//        print("loop: counter=counter")
//    }
//
//    print("End program")
//}
