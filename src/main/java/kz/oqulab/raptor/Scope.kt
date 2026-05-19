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
        val info = VariableInfo(
            value = value,
            declaredType = declaredType ?: getDeclaredType(key),
            isMutable = isMutable
        )
        variables[key] = info
    }

    fun containsKey(key: String): Boolean {
        return variables.containsKey(key) || (parent?.containsKey(key) == true)
    }

    fun clear() = variables.clear()

    fun toMap(): Map<String, Any?> = variables.mapValues { it.value.value }

    companion object {
        fun empty() = Scope()
        fun child(parent: Scope) = Scope(parent)
    }
}