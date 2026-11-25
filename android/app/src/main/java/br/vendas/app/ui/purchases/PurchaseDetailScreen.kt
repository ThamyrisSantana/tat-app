package br.vendas.app.ui.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.vendas.app.ui.navigation.PurchaseRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import br.vendas.app.data.SalesRepository
import br.vendas.app.domain.Customer
import br.vendas.app.domain.Installment
import br.vendas.app.domain.PaymentType
import br.vendas.app.domain.Purchase
import br.vendas.app.domain.PurchaseSource
import br.vendas.app.domain.today
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.BackTopBar
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.SectionTitle
import br.vendas.app.ui.common.formatFull
import br.vendas.app.ui.common.friendlyError
import kotlinx.coroutines.launch

data class PurchaseDetail(val purchase: Purchase, val customer: Customer)

@HiltViewModel
class PurchaseDetailViewModel @Inject constructor(
    private val repository: SalesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val purchaseId = savedStateHandle.toRoute<PurchaseRoute>().purchaseId

    val loader = AutoLoader(viewModelScope, repository.changes) {
        val purchase = repository.purchase(purchaseId)
        PurchaseDetail(purchase, repository.customer(purchase.customerId))
    }

    var actionError by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)

    fun setPaid(installment: Installment, paid: Boolean) = act { repository.setInstallmentPaid(installment.id, paid) }

    fun cancel(purchase: Purchase) = act { repository.cancelPurchase(purchase.id) }

    private fun act(block: suspend () -> Unit) {
        busy = true
        actionError = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                actionError = friendlyError(e)
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun PurchaseDetailScreen(onBack: () -> Unit, viewModel: PurchaseDetailViewModel = hiltViewModel()) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()
    var undoTarget by remember { mutableStateOf<Installment?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar("Compra", onBack) }) { padding ->
        LoadStateContent(state, onRetry = viewModel.loader::retry, modifier = Modifier.padding(padding)) { detail ->
            val purchase = detail.purchase
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Summary(detail) }
                viewModel.actionError?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                if (purchase.installments.isNotEmpty()) {
                    item { SectionTitle("Parcelas") }
                    items(purchase.installments, key = { it.id }) { installment ->
                        InstallmentRow(
                            installment = installment,
                            count = purchase.installmentCount,
                            enabled = !viewModel.busy && !purchase.isCanceled,
                            onPay = { viewModel.setPaid(installment, true) },
                            onUndo = { undoTarget = installment },
                        )
                    }
                }
                if (!purchase.isCanceled) {
                    item {
                        OutlinedButton(
                            onClick = { confirmCancel = true },
                            enabled = !viewModel.busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) { Text("Cancelar compra") }
                    }
                }
            }

            if (confirmCancel) {
                AlertDialog(
                    onDismissRequest = { confirmCancel = false },
                    title = { Text("Cancelar esta compra?") },
                    text = { Text("As parcelas dela deixam de contar no total devido. A compra continua no histórico.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmCancel = false
                            viewModel.cancel(purchase)
                        }) { Text("Cancelar compra") }
                    },
                    dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Voltar") } },
                )
            }
        }
    }

    undoTarget?.let { installment ->
        AlertDialog(
            onDismissRequest = { undoTarget = null },
            title = { Text("Desfazer pagamento?") },
            text = { Text("A parcela ${installment.number} volta a ficar em aberto.") },
            confirmButton = {
                TextButton(onClick = {
                    undoTarget = null
                    viewModel.setPaid(installment, false)
                }) { Text("Desfazer") }
            },
            dismissButton = { TextButton(onClick = { undoTarget = null }) { Text("Voltar") } },
        )
    }
}

@Composable
private fun Summary(detail: PurchaseDetail) {
    val purchase = detail.purchase
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(detail.customer.name, style = MaterialTheme.typography.titleLarge)
            Text("Compra em ${purchase.purchaseDate.formatFull()}", style = MaterialTheme.typography.bodyMedium)
            val payment = if (purchase.paymentType == PaymentType.CASH) "À vista" else "A prazo em ${purchase.installmentCount}x"
            val source = if (purchase.source == PurchaseSource.WHATSAPP) " · registrada pelo WhatsApp" else ""
            Text("$payment$source", style = MaterialTheme.typography.bodyMedium)
            purchase.canceledAt?.let {
                Text(
                    "Cancelada em ${it.formatFull()}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Column(Modifier.padding(top = 12.dp)) {
                purchase.items.forEach { item ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${item.quantity}x ${item.productName}", modifier = Modifier.weight(1f))
                        Text(item.subtotal.format())
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Total", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(purchase.total.format(), fontWeight = FontWeight.SemiBold)
                }
            }
            purchase.notes?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun InstallmentRow(
    installment: Installment,
    count: Int,
    enabled: Boolean,
    onPay: () -> Unit,
    onUndo: () -> Unit,
) {
    val overdue = installment.isOverdue(today())
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${installment.number}/$count · ${installment.amount.format()}",
                    style = MaterialTheme.typography.titleMedium,
                )
                val (status, color) = when {
                    installment.paidAt != null ->
                        "Paga em ${installment.paidAt.formatFull()}" to MaterialTheme.colorScheme.primary
                    overdue ->
                        "Atrasada · venceu ${installment.dueDate.formatFull()}" to MaterialTheme.colorScheme.error
                    else ->
                        "Vence ${installment.dueDate.formatFull()}" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = color)
            }
            if (installment.isPaid) {
                TextButton(onClick = onUndo, enabled = enabled) { Text("Desfazer") }
            } else {
                FilledTonalButton(onClick = onPay, enabled = enabled) { Text("Marcar paga") }
            }
        }
    }
}
