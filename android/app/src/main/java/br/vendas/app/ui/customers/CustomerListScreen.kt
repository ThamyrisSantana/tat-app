package br.vendas.app.ui.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.vendas.app.data.SalesRepository
import br.vendas.app.domain.CustomerBalance
import br.vendas.app.domain.Money
import br.vendas.app.domain.PhoneNumbers
import br.vendas.app.domain.sum
import br.vendas.app.domain.today
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.EmptyBox
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.formatShort
import br.vendas.app.ui.design.AccentStrip
import br.vendas.app.ui.design.Avatar
import br.vendas.app.ui.design.CardShape
import br.vendas.app.ui.design.fieldColors
import br.vendas.app.ui.design.ListRow
import br.vendas.app.ui.design.OverdueAlert
import br.vendas.app.ui.design.Panel
import br.vendas.app.ui.design.PillTabs
import br.vendas.app.ui.design.PrimaryButton
import br.vendas.app.ui.design.RowDivider
import br.vendas.app.ui.design.ScreenHeader
import br.vendas.app.ui.design.StatusPill
import br.vendas.app.ui.design.Tone
import br.vendas.app.ui.navigation.CustomersRoute
import br.vendas.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.Normalizer
import javax.inject.Inject

enum class CustomerFilter(val label: String) {
    ALL("Todos"),
    OVERDUE("Atrasados"),
    ON_TIME("Em dia"),
    SETTLED("Quitados"),
}

@HiltViewModel
class CustomerListViewModel @Inject constructor(
    private val repository: SalesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val initialFilter = if (savedStateHandle.toRoute<CustomersRoute>().overdueOnly) CustomerFilter.OVERDUE else CustomerFilter.ALL

    val loader = AutoLoader(viewModelScope, repository.changes) { repository.customerBalances() }

    var refreshing by mutableStateOf(false)
        private set

    fun refresh() {
        refreshing = true
        viewModelScope.launch {
            try {
                repository.refresh()
            } catch (e: Exception) {
                // The offline banner already tells the user; the list keeps the saved data.
            } finally {
                refreshing = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    onOpenCustomer: (String) -> Unit,
    onNewCustomer: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: CustomerListViewModel = hiltViewModel(),
) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(viewModel.initialFilter) }
    val today = today()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val count = (state as? br.vendas.app.ui.common.LoadState.Ready)?.data?.size
            ScreenHeader("Clientes", subtitle = count?.let { "$it cadastrados" }, onBack = onBack)
            LoadStateContent(state, onRetry = viewModel.loader::retry) { all ->
                val overdue = all.filter { it.isOverdue }.sortedByDescending { it.overdueAmount }
                val visible = all
                    .filter { matches(it, query) }
                    .filter {
                        when (filter) {
                            CustomerFilter.ALL -> true
                            CustomerFilter.OVERDUE -> it.isOverdue
                            CustomerFilter.ON_TIME -> !it.isOverdue && it.openAmount > Money.ZERO
                            CustomerFilter.SETTLED -> it.openAmount == Money.ZERO
                        }
                    }
                    // Late customers always come first: they need attention.
                    .sortedWith(compareByDescending<CustomerBalance> { it.isOverdue }.thenBy { it.name.lowercase() })

                PullToRefreshBox(isRefreshing = viewModel.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (overdue.isNotEmpty() && filter != CustomerFilter.OVERDUE) {
                            item {
                                OverdueAlert(
                                    customerCount = overdue.size,
                                    total = overdue.map { it.overdueAmount }.sum(),
                                    names = overdue.map { it.name },
                                    onClick = { filter = CustomerFilter.OVERDUE },
                                )
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Buscar por nome ou número") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                shape = CardShape,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        item {
                            PillTabs(
                                options = CustomerFilter.entries.map {
                                    it to if (it == CustomerFilter.OVERDUE) "${it.label} (${overdue.size})" else it.label
                                },
                                selected = filter,
                                onSelect = { filter = it },
                            )
                        }
                        item {
                            Panel(contentPadding = PaddingValues(vertical = 6.dp)) {
                                if (visible.isEmpty()) {
                                    EmptyBox(if (all.isEmpty()) "Nenhum cliente cadastrado ainda." else "Nenhum cliente encontrado.")
                                }
                                visible.forEachIndexed { index, customer ->
                                    if (index > 0) RowDivider(Modifier.padding(horizontal = 16.dp))
                                    CustomerRow(customer, today, onClick = { onOpenCustomer(customer.customerId) })
                                }
                            }
                        }
                    }
                }
            }
        }
        PrimaryButton(
            text = "Novo cliente",
            onClick = onNewCustomer,
            icon = Icons.Filled.Add,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 108.dp),
        )
    }
}

@Composable
private fun CustomerRow(customer: CustomerBalance, today: java.time.LocalDate, onClick: () -> Unit) {
    val colors = AppTheme.colors
    ListRow(
        title = customer.name,
        subtitle = PhoneNumbers.format(customer.phone),
        leading = {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                AccentStrip(if (customer.isOverdue) colors.dangerSolid else Color.Transparent)
                Spacer(Modifier.width(8.dp))
                Avatar(customer.name, tone = if (customer.isOverdue) Tone.DANGER else null)
            }
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (customer.openAmount > Money.ZERO) customer.openAmount.format() else "—",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (customer.isOverdue) colors.dangerText else colors.ink,
                )
                when {
                    customer.isOverdue -> StatusPill("Atrasado há ${customer.daysOverdue(today)} dias", Tone.DANGER)
                    customer.openAmount > Money.ZERO -> StatusPill(
                        customer.nextDueDate?.let { "Vence ${it.formatShort()}" } ?: "Em dia",
                        Tone.INFO,
                    )
                    else -> StatusPill("Quitado", Tone.SUCCESS)
                }
            }
        },
        onClick = onClick,
    )
}

private fun matches(customer: CustomerBalance, query: String): Boolean {
    if (query.isBlank()) return true
    val digits = query.filter { it.isDigit() }
    if (digits.length >= 3 && customer.phone.contains(digits)) return true
    return customer.name.withoutAccents().contains(query.trim().withoutAccents(), ignoreCase = true)
}

private fun String.withoutAccents(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
