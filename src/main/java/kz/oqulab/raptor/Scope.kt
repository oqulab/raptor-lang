package kz.oqulab.raptor

/**
 * Информация о переменной в скоупе
 */
data class VariableInfo(
    val value: Any?,
    val declaredType: TypeNode? = null,
    val isMutable: Boolean = true   // true = var, false = val
)

class Scope(val parent: Scope? = null) {
    private val variables = mutableMapOf<String, VariableInfo>()

    fun size() = variables.size
    fun get(key: String): Any? {
        return variables[key]?.value ?: parent?.get(key)
    }

    fun getDeclaredType(key: String): TypeNode? {
        return variables[key]?.declaredType ?: parent?.getDeclaredType(key)
    }

    /** Возвращает true, если переменная объявлена как val */
    fun isImmutable(key: String): Boolean {
        return variables[key]?.isMutable == false || parent?.isImmutable(key) == true
    }

    fun set(key: String, value: Any?, declaredType: TypeNode? = null, isMutable: Boolean = true) {
        variables[key] = VariableInfo(
            value = value,
            declaredType = declaredType ?: getDeclaredType(key),
            isMutable = isMutable
        )
    }

    fun containsKey(key: String): Boolean {
        return variables.containsKey(key) || (parent?.containsKey(key) == true)
    }

    fun assign(key: String, value: Any?, declaredType: TypeNode? = null, isMutable: Boolean = true) {
        if (variables.containsKey(key)) {
            // Переменная объявлена именно в ЭТОМ скоупе — обновляем здесь
            val oldInfo = variables[key]!!
            variables[key] = oldInfo.copy(value = value,
                declaredType = declaredType ?: getDeclaredType(key),
                isMutable = isMutable)

        } else if (parent != null) {
            // Переменной здесь нет — идём искать в родителя
            parent.assign(key, value)

        } else {
            variables[key] = VariableInfo(
                value = value,
                declaredType = declaredType ?: getDeclaredType(key),
                isMutable = isMutable
            )
            // Дошли до самого верхнего скоупа и не нашли
//            throw InterpreterException("Cannot assign to undeclared variable '$key'")
        }
    }

    fun clear() = variables.clear()

    fun toMap(): Map<String, Any?> = variables.mapValues { it.value.value }

    companion object {
        fun empty() = Scope()
        fun child(parent: Scope) = Scope(parent)
    }
}