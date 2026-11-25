package br.vendas.app.ui.customers

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.vendas.app.data.SalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import br.vendas.app.domain.CustomerBalance
import br.vendas.app.domain.Money
import br.vendas.app.domain.PhoneNumbers
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.EmptyBox
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.formatShort
import java.text.Normalizer

@HiltViewModel
class CustomerListViewModel @Inject constructor(private val repository: SalesRepository) : ViewModel() {
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

private enum class CustomerSort { NAME, AMOUNT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    onOpenCustomer: (String) -> Unit,
    onNewCustomer: () -> Unit,
    viewModel: CustomerListViewModel = hiltViewModel(),
) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(CustomerSort.NAME) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Clientes") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewCustomer,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Cliente") },
            )
        },
    ) { padding ->
        LoadStateContent(state, onRetry = viewModel.loader::retry, modifier = Modifier.padding(padding)) { all ->
            val visible = all
                .filter { matches(it, query) }
                .let { list ->
                    when (sort) {
                        CustomerSort.NAME -> list
                        CustomerSort.AMOUNT -> list.sortedByDescending { it.openAmount }
                    }
                }
            PullToRefreshBox(
                isRefreshing = viewModel.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar por nome ou número") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        FilterChip(
                            selected = sort == CustomerSort.NAME,
                            onClick = { sort = CustomerSort.NAME },
                            label = { Text("Nome") },
                        )
                        FilterChip(
                            selected = sort == CustomerSort.AMOUNT,
                            onClick = { sort = CustomerSort.AMOUNT },
                            label = { Text("Maior valor devido") },
                        )
                    }
                }
                if (visible.isEmpty()) {
                    item {
                        EmptyBox(if (all.isEmpty()) "Nenhum cliente cadastrado ainda." else "Nenhum cliente encontrado.")
                    }
                }
                items(visible, key = { it.customerId }) { customer ->
                    CustomerRow(customer, onClick = { onOpenCustomer(customer.customerId) })
                }
            }
            }
        }
    }
}

@Composable
private fun CustomerRow(customer: CustomerBalance, onClick: () -> Unit) {
    val hasOverdue = customer.overdueAmount > Money.ZERO
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(customer.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    PhoneNumbers.format(customer.phone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (customer.openAmount > Money.ZERO) {
                    Text(
                        customer.openAmount.format(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    val detail = if (hasOverdue) {
                        "Atrasado: ${customer.overdueAmount.format()}"
                    } else {
                        customer.nextDueDate?.let { "Próx.: ${it.formatShort()}" }
                    }
                    detail?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "Nada em aberto",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun matches(customer: CustomerBalance, query: String): Boolean {
    if (query.isBlank()) return true
    val digits = query.filter { it.isDigit() }
    if (digits.length >= 3 && customer.phone.contains(digits)) return true
    return customer.name.withoutAccents().contains(query.trim().withoutAccents(), ignoreCase = true)
}

private fun String.withoutAccents(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
