package com.quipu.app.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Regla para identificar personas sin duplicados:
 * dos nombres son la misma persona si, tras quitar espacios sobrantes, ignorar
 * mayúsculas y ignorar tildes, son iguales. Ejemplo: "  José  Pérez " == "jose perez".
 *
 * Para distinguir personas con nombres parecidos basta con añadir un detalle
 * al nombre: "Juan P." y "Juan (primo)" generan claves distintas.
 */
object PersonKey {
    private val WHITESPACE = Regex("\\s+")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /** Nombre para mostrar: recortado y con espacios internos simples. */
    fun displayName(name: String): String = name.trim().replace(WHITESPACE, " ")

    /** Clave de comparación. */
    fun keyOf(name: String): String {
        val decomposed = Normalizer.normalize(displayName(name), Normalizer.Form.NFD)
        return decomposed.replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)
    }
}
