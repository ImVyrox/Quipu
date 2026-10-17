package com.quipu.app.domain

import java.math.BigDecimal

/** Moneda mostrada en la app. Cambiarla solo cambia el símbolo; no convierte importes. */
data class AppCurrency(val code: String, val symbol: String, val name: String)

object AppCurrencies {
    val BOB = AppCurrency("BOB", "Bs", "Boliviano")

    val all: List<AppCurrency> = listOf(
        BOB,
        AppCurrency("USD", "US$", "Dólar estadounidense"),
        AppCurrency("EUR", "€", "Euro"),
        AppCurrency("ARS", "AR$", "Peso argentino"),
        AppCurrency("BRL", "R$", "Real brasileño"),
        AppCurrency("CLP", "CL$", "Peso chileno"),
        AppCurrency("COP", "CO$", "Peso colombiano"),
        AppCurrency("MXN", "MX$", "Peso mexicano"),
        AppCurrency("PEN", "S/", "Sol peruano"),
        AppCurrency("PYG", "₲", "Guaraní paraguayo"),
        AppCurrency("UYU", "\$U", "Peso uruguayo"),
    )

    val default: AppCurrency = BOB

    fun byCode(code: String?): AppCurrency = all.firstOrNull { it.code == code } ?: default
}

/** Importes en unidades menores (centavos) y su formato. Independiente del idioma del sistema. */
object Money {
    /** Máximo permitido: 999.999.999,99 */
    const val MAX_MINOR: Long = 99_999_999_999L

    private val AMOUNT_REGEX = Regex("""\d{1,9}([.,]\d{1,2})?""")

    /**
     * Convierte texto como "12", "12.5" o "12,50" a centavos.
     * Devuelve null si el formato no es válido (no admite separador de miles).
     */
    fun parseToMinor(input: String): Long? {
        val text = input.trim()
        if (!AMOUNT_REGEX.matches(text)) return null
        return BigDecimal(text.replace(',', '.')).movePointRight(2).toLong()
    }

    /** "Bs 1.234,50"; los negativos se muestran como "-Bs 1.234,50". */
    fun format(minor: Long, currency: AppCurrency): String {
        val negative = minor < 0
        val abs = if (negative) -minor else minor
        val units = abs / 100
        val cents = (abs % 100).toString().padStart(2, '0')
        val grouped = units.toString().reversed().chunked(3).joinToString(".").reversed()
        return (if (negative) "-" else "") + currency.symbol + " " + grouped + "," + cents
    }

    /** Texto editable para un campo de importe: 1250 -> "12,50". */
    fun toInputText(minor: Long): String {
        val units = minor / 100
        val cents = (minor % 100).toString().padStart(2, '0')
        return "$units,$cents"
    }
}
