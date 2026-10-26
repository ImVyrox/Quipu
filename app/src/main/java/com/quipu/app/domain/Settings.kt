package com.quipu.app.domain

/** Tema claro/oscuro: seguir al sistema o forzar uno. */
enum class ThemeMode(val label: String) {
    SYSTEM("Igual que el sistema"),
    LIGHT("Claro"),
    DARK("Oscuro"),
}

/** Color principal de la interfaz. TEAL es el original de Quipu. */
enum class AccentColor(val label: String) {
    TEAL("Verde azulado"),
    BLUE("Azul"),
    PURPLE("Morado"),
    ORANGE("Naranja"),
    PINK("Rosa"),
}

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.TEAL,
    /** Colores del fondo de pantalla (Material You, Android 12+). Si se activa, ignora [accent]. */
    val dynamicColor: Boolean = false,
)

/**
 * Configuración de recordatorios. Los minutos del día van de 0 (00:00) a 1439 (23:59).
 */
data class ReminderSettings(
    /** Interruptor general: si está apagado no se programa ningún aviso. */
    val enabled: Boolean = true,
    /** Tareas normales: aviso [taskLeadMinutes] antes de su fecha y hora. */
    val tasksEnabled: Boolean = true,
    val taskLeadMinutes: Int = 30,
    /** Tareas diarias: aviso a esta hora si todavía no se completaron ese día. */
    val dailyEnabled: Boolean = true,
    val dailyMinuteOfDay: Int = 20 * 60,
    /** Préstamos y deudas pendientes: aviso [debtLeadDays] días antes del vencimiento, a esta hora. */
    val debtsEnabled: Boolean = true,
    val debtLeadDays: Int = 1,
    val debtMinuteOfDay: Int = 9 * 60,
)

object ReminderOptions {
    val taskLeadMinutes = listOf(0, 5, 10, 15, 30, 60, 120, 180, 24 * 60)
    val debtLeadDays = listOf(0, 1, 2, 3, 7)

    fun taskLeadLabel(minutes: Int): String = when {
        minutes == 0 -> "A la hora exacta"
        minutes % (24 * 60) == 0 -> plural(minutes / (24 * 60), "día", "días") + " antes"
        minutes % 60 == 0 -> plural(minutes / 60, "hora", "horas") + " antes"
        else -> "$minutes minutos antes"
    }

    fun debtLeadLabel(days: Int): String = when (days) {
        0 -> "El mismo día del vencimiento"
        else -> plural(days, "día", "días") + " antes"
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"
}
