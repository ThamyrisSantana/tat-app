package br.vendas.app.ui.receivables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.vendas.app.data.SalesRepository
import br.vendas.app.domain.Money
import br.vendas.app.domain.OpenInstallment
import br.vendas.app.domain.ReceivableMonth
import br.vendas.app.domain.Receivables
import br.vendas.app.domain.Reports
import br.vendas.app.domain.today
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.EmptyBox
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.formatCompact
import br.vendas.app.ui.common.formatShort
import br.vendas.app.ui.common.longName
import br.vendas.app.ui.common.shortName
import br.vendas.app.ui.design.AppIcons
import br.vendas.app.ui.design.Avatar
import br.vendas.app.ui.design.Bar
import br.vendas.app.ui.design.BarChart
import br.vendas.app.ui.design.KpiTile
import br.vendas.app.ui.design.ListRow
import br.vendas.app.ui.design.Panel
import br.vendas.app.ui.design.RowDivider
import br.vendas.app.ui.design.ScreenHeader
import br.vendas.app.ui.design.SectionHeader
import br.vendas.app.ui.design.StatusPill
import br.vendas.app.ui.design.TileStyle
import br.vendas.app.ui.design.Tone
import br.vendas.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class ReceivablesData(
    val summary: Receivables,
    val overdue: List<OpenInstallment>,
    /** Each coming month with its installments. */
    val months: List<Pair<ReceivableMonth, List<OpenInstallment>>>,
)

@HiltViewModel
class ReceivablesViewModel @Inject constructor(repository: SalesRepository) : ViewModel() {
    val loader = AutoLoader(viewModelScope, repository.changes) {
        val snapshot = repository.snapshot()
        val today = today()
        val summary = Reports.receivables(snapshot.purchases, today)
        val open = snapshot.openInstallmentsDueUntil(LocalDate.MAX)
        val byMonth = open.filterNot { it.installment.isOverdue(today) }.groupBy { YearMonth.from(it.installment.dueDate) }
        ReceivablesData(
            summary = summary,
            overdue = open.filter { it.installment.isOverdue(today) },
            months = summary.months.map { it to byMonth[it.month].orEmpty() },
        )
    }
}

@Composable
fun ReceivablesScreen(onOpenCustomer: (String) -> Unit, viewModel: ReceivablesViewModel = hiltViewModel()) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<String?>(YearMonth.from(today()).toString()) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("A receber", subtitle = "Parcelas em aberto, mês a mês")
        LoadStateContent(state, onRetry = viewModel.loader::retry) { data ->
            val summary = data.summary
            val thisMonth = summary.months.firstOrNull { it.month == YearMonth.from(today()) }?.amount ?: Money.ZERO
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    KpiTile(
                        label = "Total a receber",
                        value = summary.totalOpen.format(),
                        caption = "${summary.months.sumOf { it.installmentCount } + summary.overdueCount} parcelas em aberto",
                        style = TileStyle.DARK,
                        icon = AppIcons.Wallet,
                        modifier = Modifier.padding(0.dp),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KpiTile(
                            label = "Atrasado",
                            value = summary.overdue.format(),
                            caption = "${summary.overdueCount} parcelas",
                            style = if (summary.overdue > Money.ZERO) TileStyle.DANGER else TileStyle.LIGHT,
                            modifier = Modifier.weight(1f),
                        )
                        KpiTile(
                            label = "Este mês",
                            value = thisMonth.format(),
                            caption = "a vencer",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (summary.months.isNotEmpty()) {
                    item {
                        Panel {
                            SectionHeader("Previsão de recebimento", subtitle = "Próximos meses")
                            val shown = summary.months.take(6)
                            BarChart(
                                bars = shown.mapIndexed { index, month ->
                                    Bar(month.month.shortName(), month.amount.cents, month.amount.formatCompact(), highlighted = index == 0)
                                },
                                onBarClick = { expanded = shown[it].month.toString() },
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }
                if (data.overdue.isNotEmpty()) {
                    item {
                        InstallmentsPanel(
                            title = "Atrasadas",
                            subtitle = "${summary.overdueCount} parcelas · ${summary.overdue.format()}",
                            installments = data.overdue,
                            overdue = true,
                            onOpenCustomer = onOpenCustomer,
                        )
                    }
                }
                if (data.months.isEmpty() && data.overdue.isEmpty()) {
                    item { Panel { EmptyBox("Nenhuma parcela em aberto.") } }
                }
                items(data.months, key = { it.first.month.toString() }) { (month, installments) ->
                    val key = month.month.toString()
                    MonthPanel(
                        month = month,
                        installments = installments,
                        expanded = expanded == key,
                        onToggle = { expanded = if (expanded == key) null else key },
                        onOpenCustomer = onOpenCustomer,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthPanel(
    month: ReceivableMonth,
    installments: List<OpenInstallment>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenCustomer: (String) -> Unit,
) {
    Panel(contentPadding = PaddingValues(vertical = 4.dp)) {
        ListRow(
            title = month.month.longName(),
            subtitle = "${month.installmentCount} parcelas",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(month.amount.format(), style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.ink)
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Recolher" else "Expandir",
                        tint = AppTheme.colors.faint,
                    )
                }
            },
            onClick = onToggle,
        )
        if (expanded) {
            installments.forEach { item ->
                RowDivider(Modifier.padding(horizontal = 16.dp))
                InstallmentLine(item, overdue = false, onOpenCustomer)
            }
        }
    }
}

@Composable
private fun InstallmentsPanel(
    title: String,
    subtitle: String,
    installments: List<OpenInstallment>,
    overdue: Boolean,
    onOpenCustomer: (String) -> Unit,
) {
    Panel(contentPadding = PaddingValues(vertical = 16.dp)) {
        SectionHeader(title, subtitle = subtitle, modifier = Modifier.padding(horizontal = 20.dp))
        installments.forEachIndexed { index, item ->
            if (index > 0) RowDivider(Modifier.padding(horizontal = 16.dp))
            InstallmentLine(item, overdue, onOpenCustomer)
        }
    }
}

@Composable
private fun InstallmentLine(item: OpenInstallment, overdue: Boolean, onOpenCustomer: (String) -> Unit) {
    ListRow(
        title = item.customerName,
        subtitle = "Parcela ${item.installment.number}/${item.installmentCount} · vence ${item.installment.dueDate.formatShort()}",
        leading = { Avatar(item.customerName, size = 36.dp, tone = if (overdue) Tone.DANGER else null) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    item.installment.remaining.format(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (overdue) AppTheme.colors.dangerText else AppTheme.colors.ink,
                )
                when {
                    overdue -> StatusPill("Atrasada", Tone.DANGER)
                    item.installment.isPartiallyPaid -> StatusPill("Parcial", Tone.WARNING)
                }
            }
        },
        onClick = { onOpenCustomer(item.customerId) },
    )
}
