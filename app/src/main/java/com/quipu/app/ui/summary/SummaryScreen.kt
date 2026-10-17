@file:OptIn(ExperimentalMaterial3Api::class)

package com.quipu.app.ui.summary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quipu.app.domain.AppCurrencies
import com.quipu.app.domain.AppCurrency
import com.quipu.app.domain.Money
import com.quipu.app.domain.PersonBalance
import com.quipu.app.ui.components.EmptyState
import com.quipu.app.ui.components.FormSheet
import com.quipu.app.ui.components.SectionHeader
import com.quipu.app.ui.theme.QuipuColors

@Composable
fun SummaryScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: SummaryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val summary = state.summary
    val currency = state.currency
    val extra = QuipuColors.extra
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resumen") },
                actions = {
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        if (!state.loaded) {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {}
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    val netColor = when {
                        summary.net > 0 -> extra.positive
                        summary.net < 0 -> extra.negative
                        else -> scheme.onSurface
                    }
                    SummaryCard {
                        Text(
                            "Balance neto",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            text = signed(summary.net, currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = netColor,
                        )
                        Text(
                            "Lo que me deben menos lo que debo (solo movimientos pendientes)",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard(modifier = Modifier.weight(1f)) {
                            Text("Me deben", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                            Text(
                                Money.format(summary.totalOwedToMe, currency),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = extra.positive,
                            )
                        }
                        SummaryCard(modifier = Modifier.weight(1f)) {
                            Text("Debo", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                            Text(
                                Money.format(summary.totalIOwe, currency),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = extra.negative,
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard(modifier = Modifier.weight(1f)) {
                            Text("Personas", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                            Text(
                                summary.peopleCount.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "con saldo pendiente",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        SummaryCard(modifier = Modifier.weight(1f)) {
                            Text("Registros", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                            Text(
                                summary.pendingCount.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "pendientes",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item { SectionHeader("Por persona") }
                if (summary.perPerson.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Sin movimientos pendientes",
                            message = "Cuando registres préstamos o deudas en Finanzas, aquí verás cuánto te debe o le debes a cada persona.",
                            modifier = Modifier.heightIn(min = 160.dp),
                        )
                    }
                } else {
                    items(summary.perPerson, key = { it.personKey }) { person ->
                        PersonRow(person = person, currency = currency)
                    }
                }
            }
        }
    }

    if (settingsOpen) {
        SettingsSheet(
            currency = currency,
            demoCount = state.demoCount,
            onCurrency = viewModel::setCurrency,
            onLoadDemo = viewModel::loadDemoData,
            onRemoveDemo = viewModel::removeDemoData,
            onDismiss = { settingsOpen = false },
        )
    }
}

private fun signed(minor: Long, currency: AppCurrency): String =
    if (minor > 0) "+" + Money.format(minor, currency) else Money.format(minor, currency)

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun PersonRow(person: PersonBalance, currency: AppCurrency) {
    val extra = QuipuColors.extra
    val scheme = MaterialTheme.colorScheme

    val detail = buildList {
        if (person.owedToMe > 0) add("Te debe " + Money.format(person.owedToMe, currency))
        if (person.iOwe > 0) add("Le debes " + Money.format(person.iOwe, currency))
    }.joinToString("  ·  ")

    val netColor = when {
        person.net > 0 -> extra.positive
        person.net < 0 -> extra.negative
        else -> scheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = person.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = signed(person.net, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = netColor,
                )
                Text(
                    text = "neto",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsSheet(
    currency: AppCurrency,
    demoCount: Int,
    onCurrency: (AppCurrency) -> Unit,
    onLoadDemo: () -> Unit,
    onRemoveDemo: () -> Unit,
    onDismiss: () -> Unit,
) {
    FormSheet(title = "Ajustes", onDismiss = onDismiss) {
        Text("Moneda", style = MaterialTheme.typography.titleSmall)
        Text(
            "Cambiar la moneda solo modifica el símbolo con que se muestran los importes. No convierte los montos guardados.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            AppCurrencies.all.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.RadioButton) { onCurrency(option) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option.code == currency.code, onClick = null)
                    Text(
                        text = "${option.code} · ${option.name} (${option.symbol})",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }

        HorizontalDivider()

        Text("Datos de demostración", style = MaterialTheme.typography.titleSmall)
        Text(
            if (demoCount == 0) {
                "Carga unos pocos ejemplos para ver cómo funciona la app. Se pueden eliminar en un toque sin afectar tus datos reales."
            } else {
                "Hay $demoCount registros de ejemplo en la app. Eliminarlos no afecta tus datos reales."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (demoCount == 0) {
            OutlinedButton(
                onClick = onLoadDemo,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Cargar datos de demostración") }
        } else {
            Button(
                onClick = onRemoveDemo,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Eliminar datos de demostración") }
        }

        Text(
            "Quipu guarda todo solo en este teléfono y no usa Internet. Si desinstalas la app, los datos se pierden.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
