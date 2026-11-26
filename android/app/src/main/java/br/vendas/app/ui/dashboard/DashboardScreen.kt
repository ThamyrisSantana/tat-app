package br.vendas.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.vendas.app.data.SalesRepository
import br.vendas.app.data.SettingsRepository
import br.vendas.app.domain.CustomerBalance
import br.vendas.app.domain.Money
import br.vendas.app.domain.MonthlySales
import br.vendas.app.domain.OpenInstallment
import br.vendas.app.domain.Reports
import br.vendas.app.domain.sum
import br.vendas.app.domain.today
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.EmptyBox
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.formatCompact
import br.vendas.app.ui.common.formatLong
import br.vendas.app.ui.common.formatShort
import br.vendas.app.ui.common.shortName
import br.vendas.app.ui.design.AppIcons
import br.vendas.app.ui.design.Avatar
import br.vendas.app.ui.design.Bar
import br.vendas.app.ui.design.BarChart
import br.vendas.app.ui.design.CircleIconButton
import br.vendas.app.ui.design.KpiTile
import br.vendas.app.ui.design.ListRow
import br.vendas.app.ui.design.OverdueAlert
import br.vendas.app.ui.design.Panel
import br.vendas.app.ui.design.RowDivider
import br.vendas.app.ui.design.ScreenHeader
import br.vendas.app.ui.design.SectionHeader
import br.vendas.app.ui.design.StatusPill
import br.vendas.app.ui.design.TileStyle
import br.vendas.app.ui.design.Tone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class DashboardData(
    val totalOpen: Money,
    val overdueCustomers: List<CustomerBalance>,
    val overdueTotal: Money,
    val soldThisMonth: Money,
    val salesThisMonth: Int,
    val receivedThisMonth: Money,
    val customerCount: Int,
    val lastMonths: List<MonthlySales>,
    val upcoming: List<OpenInstallment>,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SalesRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val settings = settings.settings

    val loader = AutoLoader(viewModelScope, repository.changes) {
        val snapshot = repository.snapshot()
        val today = today()
        val month = YearMonth.from(today)
        val balances = snapshot.customerBalances(today)
        val report = Reports.monthReport(snapshot.purchases, snapshot.payments, snapshot.customerNames, month)
        val overdue = balances.filter { it.isOverdue }.sortedByDescending { it.overdueAmount }
        DashboardData(
            totalOpen = balances.map { it.openAmount }.sum(),
            overdueCustomers = overdue,
            overdueTotal = overdue.map { it.overdueAmount }.sum(),
            soldThisMonth = report.total,
            salesThisMonth = report.purchaseCount,
            receivedThisMonth = report.received,
            customerCount = balances.size,
            lastMonths = Reports.salesByMonth(snapshot.purchases, Reports.monthsUpTo(month, 6)),
            upcoming = snapshot.openInstallmentsDueUntil(today.plusDays(7)).filterNot { it.installment.isOverdue(today) },
        )
    }

    var refreshing by mutableStateOf(false)
        private set

    fun refresh() {
        refreshing = true
        viewModelScope.launch {
            try {
                repository.refresh()
            } catch (e: Exception) {
                // The offline banner already tells the user.
            } finally {
                refreshing = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenCustomer: (String) -> Unit,
    onOpenOverdueCustomers: () -> Unit,
    onOpenReceivables: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = if (settings.name.isBlank()) "Olá!" else "Olá, ${settings.name}",
            subtitle = today().formatLong(),
        ) {
            CircleIconButton(Icons.Filled.Settings, "Ajustes", onOpenSettings)
        }
        LoadStateContent(state, onRetry = viewModel.loader::retry) { data ->
            PullToRefreshBox(isRefreshing = viewModel.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (data.overdueCustomers.isNotEmpty()) {
                        item {
                            OverdueAlert(
                                customerCount = data.overdueCustomers.size,
                                total = data.overdueTotal,
                                names = data.overdueCustomers.map { it.name },
                                onClick = onOpenOverdueCustomers,
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiTile(
                                label = "A receber",
                                value = data.totalOpen.format(),
                                caption = "${data.customerCount} clientes",
                                style = TileStyle.DARK,
                                icon = AppIcons.Wallet,
                                onClick = onOpenReceivables,
                                modifier = Modifier.weight(1f),
                            )
                            KpiTile(
                                label = "Atrasado",
                                value = data.overdueTotal.format(),
                                caption = "${data.overdueCustomers.size} clientes",
                                icon = br.vendas.app.ui.design.AppIcons.Receipt,
                                onClick = onOpenOverdueCustomers,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiTile(
                                label = "Vendido no mês",
                                value = data.soldThisMonth.format(),
                                caption = "${data.salesThisMonth} vendas",
                                icon = AppIcons.BarChart,
                                onClick = onOpenReports,
                                modifier = Modifier.weight(1f),
                            )
                            KpiTile(
                                label = "Recebido no mês",
                                value = data.receivedThisMonth.format(),
                                caption = "pagamentos",
                                icon = AppIcons.Cash,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    item {
                        Panel {
                            SectionHeader("Vendas", subtitle = "Últimos 6 meses")
                            BarChart(
                                bars = data.lastMonths.mapIndexed { index, month ->
                                    Bar(
                                        label = month.month.shortName(),
                                        value = month.total.cents,
                                        valueLabel = month.total.formatCompact(),
                                        highlighted = index == data.lastMonths.lastIndex,
                                    )
                                },
                                onBarClick = { onOpenReports() },
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                    item {
                        Panel(contentPadding = PaddingValues(vertical = 16.dp)) {
                            SectionHeader(
                                "Vencem nos próximos 7 dias",
                                subtitle = "Parcelas em aberto",
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            if (data.upcoming.isEmpty()) {
                                EmptyBox("Nada vence nos próximos dias.")
                            }
                            data.upcoming.forEachIndexed { index, item ->
                                if (index > 0) RowDivider(Modifier.padding(horizontal = 16.dp))
                                ListRow(
                                    title = item.customerName,
                                    subtitle = "Parcela ${item.installment.number}/${item.installmentCount} · vence ${item.installment.dueDate.formatShort()}",
                                    leading = { Avatar(item.customerName) },
                                    trailing = {
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                            Text(item.installment.remaining.format(), style = MaterialTheme.typography.titleSmall)
                                            if (item.installment.isPartiallyPaid) StatusPill("Parcial", Tone.WARNING)
                                        }
                                    },
                                    onClick = { onOpenCustomer(item.customerId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
