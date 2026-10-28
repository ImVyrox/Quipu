# Diseño técnico — Finanzas personales (fase 1)

Estado:
- **Fase 1**: diseño y lógica de dominio pura con pruebas.
- **Fase 2**: ingresos, gastos, saldo y dinero declarado, implementados (Room v4, pantalla **Dinero**).
- **Fase 3**: objetivos de ahorro, implementados (Room v5).
- **Pendiente**: pagos recurrentes y sus recordatorios.

## Principios

1. **Un único libro de movimientos reales.** Todo dinero que entra, sale o se aparta queda en un `Movement`. El disponible, los totales y el historial se calculan a partir de ese libro, como los saldos de préstamos se calculan a partir de los registros.
2. **Lo previsto no es dinero.** Los ingresos por horas o fijos y los pagos recurrentes pendientes se calculan a partir de reglas. Nunca se guardan como movimiento ni cambian el disponible hasta que el usuario confirma que ocurrieron.
3. **Cada operación conserva su moneda.** Los totales se agrupan por código de moneda (`CurrencyAmounts`). No se suman monedas distintas y no hay tasas de cambio. Se reutilizan `Money.parseToMinor`, `Money.format` y `AppCurrencies`. El ajuste de moneda actual pasa a ser la **moneda predeterminada para operaciones nuevas**.
4. **Historial auditable.** Los traspasos al ahorro son movimientos propios: una aportación y un retiro, nunca un saldo editable. El saldo de un objetivo es la suma de sus traspasos.
5. **Sin duplicados por construcción.** Los vencimientos mensuales no se guardan, se generan a partir de la regla. El pago de un mes es el único gasto con `(recurringPaymentId, recurringPeriod)`, y lo garantiza un índice único en la base de datos.

## Lógica implementada (paquete `domain/`, sin Android)

| Archivo | Contenido |
|---|---|
| `PersonalFinance.kt` | `MovementType`, `Movement`, `IncomeKind`, `IncomeSource`, `SavingsGoal`, `RecurringPayment`, `CurrencyAmounts` |
| `Ledger.kt` | Disponible por moneda, saldo y progreso de objetivos, total ahorrado, totales por mes o rango, historial, validación de traspasos y eliminación de copias por id |
| `IncomePlanner.kt` | Previsión mensual (fijo / por horas / variable), previsto frente a recibido, `parseHoursToMinutes`, ejemplo de 18 BOB/h × 42 h = 756 BOB |
| `RecurringPlanner.kt` | Vencimientos mensuales (día 31 → último día del mes), estado PAID/PENDING/OVERDUE, `confirm` idempotente, total pendiente del mes y recordatorios (`remindersBetween`, `nextReminderAfter`) |
| `FinanceValidation.kt` | Formularios de movimiento, fuente de ingresos, objetivo y pago recurrente |
| `Motivation.kt` | Mensaje motivacional determinista según objetivos y mes actual |

Pruebas: `IncomePlannerTest`, `LedgerTest`, `RecurringPlannerTest`, `MotivationTest`.

## Reglas de negocio

### Ingresos
- **FIXED**: importe mensual. **HOURLY**: tarifa en centavos × minutos previstos al mes. Las horas se guardan en minutos para admitir fracciones (42,5 h = 2550 min) y el importe se redondea al centavo, mitad hacia arriba. **VARIABLE**: sin previsión.
- Ejemplo inicial: `IncomePlanner.EXAMPLE_HOURLY` = 18,00 BOB/h × 42 h = **756,00 BOB previstos**. La tarifa y las horas se pueden editar.
- *Recibido* = movimientos `INCOME` vinculados a la fuente, en el mes y en su moneda. *Pendiente* = previsto − recibido, nunca negativo.
- La previsión solo aparece como «previsto». El disponible lo calcula `Ledger.available`, que únicamente lee movimientos.

### Gastos
- Importe y fecha obligatorios. Descripción opcional, de hasta 300 caracteres (`Limits.NOTE_MAX`).
- Un gasto confirmado es un `Movement` con un id. Los cálculos aplican `Ledger.distinct`, así que el mismo id cuenta una sola vez aunque llegue repetido. Para evitar el doble guardado al pulsar dos veces, la UI deshabilitará «Guardar» mientras se guarda (fase de UI).

### Ahorros
- `SAVING_DEPOSIT` resta del disponible y suma al objetivo. `SAVING_WITHDRAWAL` hace lo contrario. Ninguno de los dos es ingreso ni gasto externo (`PeriodTotals.income/expense` los excluyen y van en `savedNet`).
- Una aportación no puede superar el disponible de esa moneda y un retiro no puede superar lo ahorrado. El traspaso y el objetivo deben ir en la misma moneda (`Ledger.validateTransfer`).

### Política contable del ahorro (fase 3)
- **Total = disponible + ahorrado**, por moneda (`Ledger.overview`).
- **Aportación**: disponible −x, objetivo +x, total igual. No puede dejar el disponible en negativo y solo se admite en objetivos **activos**.
- **Retiro**: objetivo −x, disponible +x, total igual. No puede superar lo ahorrado en ese objetivo. Se admite en objetivos en pausa o completados (para usar el dinero).
- **Ahorro inicial** (`SAVING_OPENING`): dinero que ya estaba ahorrado fuera de Quipu. El usuario lo registra explícitamente, como mucho una vez por objetivo. Suma al objetivo y al total, pero no es ingreso ni sale del disponible.
- **Saldos negativos**: el disponible nunca queda en negativo por ahorrar; sí puede quedar en negativo por gastos reales, porque refleja la realidad. Lo ahorrado en un objetivo nunca es negativo: tampoco al borrar movimientos (`Ledger.validateRemoval`).
- **Eliminar un objetivo** registra un retiro de cierre por lo ahorrado (`Ledger.closingWithdrawal`). Su historial se conserva y queda de solo lectura.
- **Completar** es una decisión del usuario: se puede completar sin llegar a la meta, y llegar a la meta no completa el objetivo de forma automática. Se pueden aportar importes por encima de la meta; el porcentaje se queda en 100 %.
- **Sin errores de redondeo**: todo en centavos (`Long`), y el porcentaje se redondea hacia abajo (99,99 % se muestra como 99 %).
- **Sin duplicados**: cada movimiento cuenta una vez por id. Además, el repositorio valida en una transacción con los datos guardados, así que dos registros seguidos no pueden saltarse los límites.
- **Dinero real declarado**: se compara con el total (disponible + ahorros).

### Pagos recurrentes y suscripciones
- Vencimiento mensual en `dayOfMonth` (1–31). Si el mes es más corto, vence su último día. Solo hay vencimientos entre `startDate` y `endDate`.
- `confirm` devuelve `Created` (un gasto nuevo), `AlreadyPaid` (si el mes ya estaba pagado no se crea otro) o `NotDue`.
- Los pendientes se muestran aparte (`pendingTotal`) y no reducen el disponible.
- Un pago inactivo deja de generar pendientes, pero conserva su historial de pagos.

### Recordatorios y mensajes
- Avisos de pagos recurrentes sin pagar, `leadDays` antes a una hora del día (igual que las deudas). Como mucho uno por pago en cada ventana. Usa claves `recurring-<id>-<yyyy-MM>`.
- `Motivation.pick` elige, por prioridad: objetivo cumplido → hito de 75/50/25 % → ahorro este mes → ingresos mayores que gastos en alguna moneda → mensaje genérico que rota cada día. Nunca suma monedas distintas.

## Modelo de datos

**Room v4 (fase 2, aplicado)**: `movements`, `income_sources` y `declared_balances`. Además de lo propuesto, `movements` tiene:
- `category`: categoría del gasto.
- `pending`: por cobrar o por pagar; no cuenta en el saldo.
- `workEpochDay` y `workedMinutes`: horas anotadas con el cobro.
- `period`: mes saldado; sirve para pagos recurrentes y para ingresos fijos.

Hay índices únicos (`incomeSourceId`, `period`) y (`recurringPaymentId`, `period`). `declared_balances` guarda el dinero real que declara el usuario; no es un movimiento.

**Room v5 (fase 3, aplicado)**: `savings_goals`, con `name`, `description`, `targetMinor`, `currencyCode`, `createdEpochDay`, `deadlineEpochDay`, `status` (ACTIVE, PAUSED o COMPLETED) y `completedEpochDay`. Lo acumulado no se guarda: sale de `movements`.

**Pendiente**: `recurring_payments`, según la propuesta de abajo.

```
income_sources
  id INTEGER PK AUTOINCREMENT, name TEXT NOT NULL, kind TEXT NOT NULL  -- FIXED|VARIABLE|HOURLY
  currencyCode TEXT NOT NULL, fixedAmountMinor INTEGER, hourlyRateMinor INTEGER,
  plannedMinutesPerMonth INTEGER, active INTEGER NOT NULL DEFAULT 1, isDemo INTEGER NOT NULL DEFAULT 0

savings_goals
  id INTEGER PK AUTOINCREMENT, name TEXT NOT NULL, currencyCode TEXT NOT NULL,
  targetMinor INTEGER NOT NULL, deadlineEpochDay INTEGER, archived INTEGER NOT NULL DEFAULT 0,
  isDemo INTEGER NOT NULL DEFAULT 0

recurring_payments
  id INTEGER PK AUTOINCREMENT, name TEXT NOT NULL, amountMinor INTEGER NOT NULL,
  currencyCode TEXT NOT NULL, dayOfMonth INTEGER NOT NULL, startEpochDay INTEGER NOT NULL,
  endEpochDay INTEGER, isSubscription INTEGER NOT NULL DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 1, isDemo INTEGER NOT NULL DEFAULT 0

movements
  id INTEGER PK AUTOINCREMENT, type TEXT NOT NULL,       -- INCOME|EXPENSE|SAVING_DEPOSIT|SAVING_WITHDRAWAL
  amountMinor INTEGER NOT NULL, currencyCode TEXT NOT NULL, epochDay INTEGER NOT NULL,
  description TEXT NOT NULL DEFAULT '', incomeSourceId INTEGER, goalId INTEGER,
  recurringPaymentId INTEGER, recurringPeriod TEXT,     -- "2026-10"
  createdAtEpochSecond INTEGER NOT NULL, isDemo INTEGER NOT NULL DEFAULT 0
  INDEX (epochDay), INDEX (goalId), INDEX (incomeSourceId)
  UNIQUE INDEX (recurringPaymentId, recurringPeriod)    -- un pago por mes; SQLite admite varios NULL
```

- **Relaciones lógicas, sin `FOREIGN KEY`**, como hoy entre `debts` y `debt_payments`. El borrado lo gestiona el repositorio en una transacción: borrar una fuente, un objetivo o un pago recurrente **no borra** sus movimientos. Un objetivo con traspasos se **archiva** y no se borra, para no perder historial.
- Enums guardados por `name` con los `Converters` existentes. Los importes son `Long` en centavos y los días `epochDay`, con la misma convención que las tablas actuales.
- **Migración 3 → 4**: solo `CREATE TABLE IF NOT EXISTS` y `CREATE INDEX`, sin tocar `tasks`, `debts` ni `debt_payments`. Se exportará `app/schemas/.../4.json`.

### Capa de datos propuesta
- `IncomeSourceDao`, `SavingsGoalDao`, `RecurringPaymentDao` y `MovementDao`, con `observeAll(): Flow`, `getAll()`, `getById`, `insert`, `update`, `deleteDemo`, y en `MovementDao` `findRecurring(paymentId, period)`.
- `PersonalFinanceRepository` (o una sección nueva en `QuipuRepository`, para mantener un único repositorio como hoy):
  - `observeLedger(): Flow<LedgerSnapshot>` combina fuentes, objetivos, pagos y movimientos.
  - `addMovement(draft)` valida con `FinanceValidators`.
  - `transferToGoal/withdrawFromGoal` usa `withTransaction` y vuelve a ejecutar `Ledger.validateTransfer` con los datos de la base.
  - `confirmRecurring(paymentId, period, date)` usa `withTransaction`, aplica `RecurringPlanner.confirm` y lo respalda con el índice único.
  - `removeDemoData` se amplía a las tablas nuevas.
- Casos de uso: se mantiene el patrón actual (objetos puros en `domain/` llamados desde el ViewModel), sin capa extra de interactors.
- Recordatorios: en la fase correspondiente se añadirán `ReminderKind.RECURRING` y su ajuste a `ReminderSettings`/`SettingsStore`, y `ReminderPlanner` llamará a `RecurringPlanner.remindersBetween`/`nextReminderAfter`. Se seguirá usando una sola alarma.

## Decisiones tomadas (revisión de la fase 1)
1. **Préstamos y deudas**: se mantienen **separados** del disponible. Cobrar o pagar un préstamo no crea movimientos.
2. **Saldo inicial**: **no** se crea un movimiento de saldo inicial. El saldo calculado sale solo de los movimientos.
3. **Mensaje «objetivo cumplido»**: se limitará a los 7 días siguientes al último traspaso (fase de ahorros).
4. **Horas trabajadas**: se anotan **con el cobro**. Cada ingreso de una fuente por horas lleva sus horas y la fecha del trabajo. No hay un registro de horas aparte.
