package br.vendas.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.vendas.app.data.SalesRepository
import br.vendas.app.domain.MonthReport
import br.vendas.app.domain.Reports
import br.vendas.app.domain.today
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.EmptyBox
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.formatCompact
import br.vendas.app.ui.common.longName
import br.vendas.app.ui.common.plural
import br.vendas.app.ui.common.shortName
import br.vendas.app.ui.design.AppIcons
import br.vendas.app.ui.design.Avatar
import br.vendas.app.ui.design.Bar
import br.vendas.app.ui.design.BarChart
import br.vendas.app.ui.design.CircleIconButton
import br.vendas.app.ui.design.DonutChart
import br.vendas.app.ui.design.KpiRow
import br.vendas.app.ui.design.KpiTile
import br.vendas.app.ui.design.Legend
import br.vendas.app.ui.design.ListRow
import br.vendas.app.ui.design.Panel
import br.vendas.app.ui.design.RankedBar
import br.vendas.app.ui.design.RowDivider
import br.vendas.app.ui.design.ScreenHeader
import br.vendas.app.ui.design.SectionHeader
import br.vendas.app.ui.design.Slice
import br.vendas.app.ui.design.TileStyle
import br.vendas.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject

private const val CHART_MONTHS = 6

@HiltViewModel
class ReportsViewModel @Inject constructor(repository: SalesRepository) : ViewModel() {
    val loader = AutoLoader(viewModelScope, repository.changes) { repository.snapshot() }

    var month by mutableStateOf(YearMonth.from(today()))
        private set

    val currentMonth: YearMonth get() = YearMonth.from(today())

    fun previous() {
        month = month.minusMonths(1)
    }

    fun next() {
        if (month < currentMonth) month = month.plusMonths(1)
    }

    fun select(month: YearMonth) {
        this.month = month
    }
}

@Composable
fun ReportsScreen(onOpenCustomer: (String) -> Unit, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Relatórios", subtitle = "Vendas e recebimentos")
        LoadStateContent(state, onRetry = viewModel.loader::retry) { snapshot ->
            val month = viewModel.month
            val report = Reports.monthReport(snapshot.purchases, snapshot.payments, snapshot.customerNames, month)
            // The chart ends at the selected month, or at the current one when looking back less than 6 months.
            val chartEnd = maxOf(month, minOf(viewModel.currentMonth, month.plusMonths(CHART_MONTHS - 1L)))
            val chartMonths = Reports.salesByMonth(snapshot.purchases, Reports.monthsUpTo(chartEnd, CHART_MONTHS))

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { MonthSelector(viewModel) }
                item { Kpis(report) }
                item {
                    Panel {
                        SectionHeader("Vendas por mês", subtitle = "Toque em um mês para ver os detalhes")
                        BarChart(
                            bars = chartMonths.map {
                                Bar(it.month.shortName(), it.total.cents, it.total.formatCompact(), highlighted = it.month == month)
                            },
                            onBarClick = { viewModel.select(chartMonths[it].month) },
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                item { PaymentSplit(report) }
                item { TopProducts(report) }
                item { TopCustomers(report, onOpenCustomer) }
            }
        }
    }
}

@Composable
private fun MonthSelector(viewModel: ReportsViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        CircleIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior", viewModel::previous)
        Text(
            viewModel.month.longName(),
            style = MaterialTheme.typography.titleMedium,
            color = AppTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (viewModel.month < viewModel.currentMonth) {
            CircleIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês", viewModel::next)
        } else {
            Spacer(Modifier.width(44.dp))
        }
    }
}

@Composable
private fun Kpis(report: MonthReport) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KpiRow {
            KpiTile(
                label = "Vendido",
                value = report.total.format(),
                caption = plural(report.purchaseCount, "venda", "vendas"),
                style = TileStyle.DARK,
                icon = AppIcons.BarChart,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = "Recebido",
                value = report.received.format(),
                caption = "pagamentos no mês",
                icon = AppIcons.Cash,
                modifier = Modifier.weight(1f),
            )
        }
        KpiRow {
            KpiTile(
                label = "Ticket médio",
                value = report.averageTicket.format(),
                caption = "por venda",
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = "Nº de vendas",
                value = report.purchaseCount.toString(),
                caption = plural(report.customerCount, "cliente", "clientes"),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PaymentSplit(report: MonthReport) {
    val colors = AppTheme.colors
    Panel {
        SectionHeader("À vista x parcelado", subtitle = "Valor vendido no mês")
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = listOf(
                    Slice("À vista", report.cashTotal.cents, colors.ink),
                    Slice("Parcelado", report.installmentsTotal.cents, colors.faint),
                ),
                centerValue = report.purchaseCount.toString(),
                centerLabel = "vendas",
                size = 130.dp,
            )
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(colors.ink, "À vista", report.cashTotal.format())
                Legend(colors.faint, "Parcelado", report.installmentsTotal.format())
            }
        }
    }
}

@Composable
private fun TopProducts(report: MonthReport) {
    Panel {
        SectionHeader("Produtos mais vendidos", subtitle = "Por quantidade no mês")
        if (report.topProducts.isEmpty()) EmptyBox("Nenhuma venda neste mês.")
        val maxQuantity = report.topProducts.maxOfOrNull { it.quantity }?.coerceAtLeast(1) ?: 1
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            report.topProducts.forEachIndexed { index, product ->
                RankedBar(
                    rank = index + 1,
                    title = product.name,
                    detail = "${product.quantity} unidades",
                    value = product.total.format(),
                    fraction = product.quantity.toFloat() / maxQuantity,
                )
            }
        }
    }
}

@Composable
private fun TopCustomers(report: MonthReport, onOpenCustomer: (String) -> Unit) {
    Panel(contentPadding = PaddingValues(vertical = 16.dp)) {
        SectionHeader("Clientes que mais compraram", subtitle = "Valor no mês", modifier = Modifier.padding(horizontal = 20.dp))
        if (report.topCustomers.isEmpty()) EmptyBox("Nenhuma venda neste mês.")
        report.topCustomers.forEachIndexed { index, customer ->
            if (index > 0) RowDivider(Modifier.padding(horizontal = 16.dp))
            ListRow(
                title = customer.name,
                subtitle = if (customer.purchaseCount == 1) "1 compra" else "${customer.purchaseCount} compras",
                leading = { Avatar(customer.name, size = 36.dp) },
                trailing = { Text(customer.total.format(), style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.ink) },
                showArrow = true,
                onClick = { onOpenCustomer(customer.customerId) },
            )
        }
    }
}
