package kz.oqulab.raptor

import kz.oqulab.raptor.paradigms.ClassInstance

class InterpreterException(
    message: String,
    val line: Int = -1,
    val column: Int = -1,
    cause: Throwable? = null
) : RuntimeException(
    if (line >= -1) "$message (line $line, column $column)" else message,
    cause
)

class RaptorException(
    val classInstance: ClassInstance? = null
) : Exception("HaHa", null)