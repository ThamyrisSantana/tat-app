package br.vendas.app.ui.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import br.vendas.app.data.SalesRepository
import br.vendas.app.domain.Money
import br.vendas.app.domain.OpenInstallment
import br.vendas.app.domain.sum
import br.vendas.app.domain.today
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.EmptyBox
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.SectionTitle
import br.vendas.app.ui.common.formatFull

private const val UPCOMING_DAYS = 7L

data class SummaryData(
    val totalOpen: Money,
    val totalOverdue: Money,
    val overdue: List<OpenInstallment>,
    val upcoming: List<OpenInstallment>,
)

@HiltViewModel
class SummaryViewModel @Inject constructor(private val repository: SalesRepository) : ViewModel() {

    var refreshing by mutableStateOf(false)
        private set

    fun refresh() {
        refreshing = true
        viewModelScope.launch {
            try {
                repository.refresh()
            } catch (e: Exception) {
                // The offline banner already tells the user; the screen keeps the saved data.
            } finally {
                refreshing = false
            }
        }
    }

    val loader = AutoLoader(viewModelScope, repository.changes) {
        val today = today()
        val balances = repository.customerBalances()
        val open = repository.openInstallmentsDueUntil(today.plusDays(UPCOMING_DAYS))
        SummaryData(
            totalOpen = balances.map { it.openAmount }.sum(),
            totalOverdue = balances.map { it.overdueAmount }.sum(),
            overdue = open.filter { it.installment.dueDate.isBefore(today) },
            upcoming = open.filterNot { it.installment.dueDate.isBefore(today) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    onOpenCustomer: (String) -> Unit,
    onSignOut: (() -> Unit)?,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resumo") },
                actions = {
                    if (onSignOut != null) {
                        IconButton(onClick = onSignOut) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sair")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LoadStateContent(state, onRetry = viewModel.loader::retry, modifier = Modifier.padding(padding)) { data ->
            PullToRefreshBox(
                isRefreshing = viewModel.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TotalCard("A receber", data.totalOpen, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f))
                        TotalCard("Atrasado", data.totalOverdue, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    }
                }
                item { SectionTitle("Parcelas atrasadas") }
                if (data.overdue.isEmpty()) item { EmptyBox("Nenhuma parcela atrasada.") }
                items(data.overdue, key = { it.installment.id }) {
                    InstallmentLine(it, overdue = true) { onOpenCustomer(it.customerId) }
                }
                item { SectionTitle("Vencem nos próximos $UPCOMING_DAYS dias") }
                if (data.upcoming.isEmpty()) item { EmptyBox("Nada vence nos próximos dias.") }
                items(data.upcoming, key = { it.installment.id }) {
                    InstallmentLine(it, overdue = false) { onOpenCustomer(it.customerId) }
                }
            }
            }
        }
    }
}

@Composable
private fun TotalCard(label: String, amount: Money, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(amount.format(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun InstallmentLine(item: OpenInstallment, overdue: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(item.customerName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Parcela ${item.installment.number}/${item.installmentCount} · vence ${item.installment.dueDate.formatFull()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(item.installment.amount.format(), style = MaterialTheme.typography.titleMedium)
        }
    }
}
