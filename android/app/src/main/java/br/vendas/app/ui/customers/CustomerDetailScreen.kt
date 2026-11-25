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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.vendas.app.ui.navigation.CustomerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import br.vendas.app.data.SalesRepository
import br.vendas.app.domain.Customer
import br.vendas.app.domain.CustomerBalance
import br.vendas.app.domain.Money
import br.vendas.app.domain.PaymentType
import br.vendas.app.domain.PhoneNumbers
import br.vendas.app.domain.Purchase
import br.vendas.app.domain.PurchaseSource
import br.vendas.app.domain.today
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.BackTopBar
import br.vendas.app.ui.common.EmptyBox
import br.vendas.app.ui.common.LoadState
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.SectionTitle
import br.vendas.app.ui.common.formatFull
import br.vendas.app.ui.common.formatShort
import br.vendas.app.ui.common.openWhatsApp

data class CustomerDetail(
    val customer: Customer,
    val balance: CustomerBalance,
    val purchases: List<Purchase>,
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    repository: SalesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val customerId = savedStateHandle.toRoute<CustomerRoute>().customerId

    val loader = AutoLoader(viewModelScope, repository.changes) {
        CustomerDetail(
            customer = repository.customer(customerId),
            balance = repository.customerBalance(customerId),
            purchases = repository.purchasesOfCustomer(customerId),
        )
    }
}

@Composable
fun CustomerDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNewPurchase: () -> Unit,
    onOpenPurchase: (String) -> Unit,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()
    val title = (state as? LoadState.Ready)?.data?.customer?.name ?: "Cliente"

    Scaffold(
        topBar = {
            BackTopBar(title, onBack) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Editar cliente") }
            }
        },
    ) { padding ->
        LoadStateContent(state, onRetry = viewModel.loader::retry, modifier = Modifier.padding(padding)) { detail ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { BalanceCard(detail) }
                item {
                    Button(onClick = onNewPurchase, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Nova compra", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                item { SectionTitle("Compras") }
                if (detail.purchases.isEmpty()) {
                    item { EmptyBox("Nenhuma compra registrada.") }
                }
                items(detail.purchases, key = { it.id }) { purchase ->
                    PurchaseRow(purchase, onClick = { onOpenPurchase(purchase.id) })
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(detail: CustomerDetail) {
    val context = LocalContext.current
    val balance = detail.balance
    val hasOverdue = balance.overdueAmount > Money.ZERO
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "WhatsApp: ${PhoneNumbers.format(detail.customer.phone)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            detail.customer.notes?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            Text("Total devido", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            Text(
                balance.openAmount.format(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (hasOverdue) {
                Text(
                    "Atrasado: ${balance.overdueAmount.format()}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            balance.nextDueDate?.let {
                Text("Próximo vencimento: ${it.formatFull()}", style = MaterialTheme.typography.bodyMedium)
            }
            if (balance.openAmount > Money.ZERO) {
                OutlinedButton(
                    onClick = { openWhatsApp(context, detail.customer.phone, chargeMessage(detail)) },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("Cobrar no WhatsApp") }
            }
        }
    }
}

@Composable
private fun PurchaseRow(purchase: Purchase, onClick: () -> Unit) {
    val today = today()
    val overdue = !purchase.isCanceled && purchase.installments.any { it.isOverdue(today) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    purchase.purchaseDate.formatFull(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(purchase.total.format(), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                purchase.items.joinToString { "${it.quantity}x ${it.productName}" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            val (status, color) = when {
                purchase.isCanceled -> "Cancelada" to MaterialTheme.colorScheme.onSurfaceVariant
                purchase.paymentType == PaymentType.CASH -> "À vista" to MaterialTheme.colorScheme.onSurfaceVariant
                purchase.openAmount == Money.ZERO -> "${purchase.installmentCount}x · Quitada" to MaterialTheme.colorScheme.primary
                overdue -> "${purchase.installmentCount}x · Atrasada · falta ${purchase.openAmount.format()}" to MaterialTheme.colorScheme.error
                else -> "${purchase.installmentCount}x · falta ${purchase.openAmount.format()}" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(Modifier.padding(top = 4.dp)) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
                if (purchase.source == PurchaseSource.WHATSAPP) {
                    Text("via WhatsApp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** A friendly reminder listing the open installments. The user reviews it in WhatsApp before sending. */
private fun chargeMessage(detail: CustomerDetail): String {
    val firstName = detail.customer.name.substringBefore(' ')
    val lines = detail.purchases
        .filterNot { it.isCanceled }
        .flatMap { purchase -> purchase.installments.filterNot { it.isPaid }.map { purchase to it } }
        .sortedBy { (_, installment) -> installment.dueDate }
        .joinToString("\n") { (purchase, installment) ->
            "• Parcela ${installment.number}/${purchase.installmentCount}: ${installment.amount.format()}, " +
                "vence ${installment.dueDate.formatShort()}"
        }
    return "Olá, $firstName! Passando para lembrar das parcelas em aberto:\n$lines\n" +
        "Total: ${detail.balance.openAmount.format()}"
}
