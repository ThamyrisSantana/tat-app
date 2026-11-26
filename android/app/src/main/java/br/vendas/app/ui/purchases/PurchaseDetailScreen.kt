package br.vendas.app.ui.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.vendas.app.data.SalesRepository
import br.vendas.app.domain.Customer
import br.vendas.app.domain.Installment
import br.vendas.app.domain.Money
import br.vendas.app.domain.NewPayment
import br.vendas.app.domain.Payment
import br.vendas.app.domain.PaymentType
import br.vendas.app.domain.Purchase
import br.vendas.app.domain.PurchaseSource
import br.vendas.app.domain.today
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.EmptyBox
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.formatFull
import br.vendas.app.ui.common.friendlyError
import br.vendas.app.ui.customers.PurchaseStatusPill
import br.vendas.app.ui.design.AppIcons
import br.vendas.app.ui.design.ListRow
import br.vendas.app.ui.design.Panel
import br.vendas.app.ui.design.PrimaryButton
import br.vendas.app.ui.design.ProgressLine
import br.vendas.app.ui.design.RowDivider
import br.vendas.app.ui.design.ScreenHeader
import br.vendas.app.ui.design.SecondaryButton
import br.vendas.app.ui.design.SectionHeader
import br.vendas.app.ui.design.StatusPill
import br.vendas.app.ui.design.Tone
import br.vendas.app.ui.navigation.PurchaseRoute
import br.vendas.app.ui.payments.PaymentContext
import br.vendas.app.ui.payments.PaymentSheet
import br.vendas.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PurchaseDetail(
    val purchase: Purchase,
    val customer: Customer,
    val payments: List<Payment>,
    val snapshot: br.vendas.app.data.SalesSnapshot,
)

@HiltViewModel
class PurchaseDetailViewModel @Inject constructor(
    private val repository: SalesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val purchaseId = savedStateHandle.toRoute<PurchaseRoute>().purchaseId

    val loader = AutoLoader(viewModelScope, repository.changes) {
        val snapshot = repository.snapshot()
        val purchase = snapshot.purchase(purchaseId)
        PurchaseDetail(purchase, snapshot.customer(purchase.customerId), snapshot.paymentsOfPurchase(purchaseId), snapshot)
    }

    /** Non-null while the payment sheet is open: the context it was opened with. */
    var paymentContext by mutableStateOf<PaymentContext?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun openPayment(detail: PurchaseDetail, installment: Installment?) {
        error = null
        paymentContext = PaymentContext.from(
            snapshot = detail.snapshot,
            customerId = detail.purchase.customerId,
            purchaseId = detail.purchase.id,
            installmentId = installment?.id,
            today = today(),
        )
    }

    fun closePayment() {
        paymentContext = null
    }

    fun registerPayment(amount: Money, paidOn: LocalDate, notes: String?) {
        val context = paymentContext ?: return
        act {
            repository.registerPayment(
                NewPayment(context.customerId, amount, paidOn, context.purchaseId, context.installmentId, notes),
            )
            paymentContext = null
        }
    }

    fun deletePayment(payment: Payment) = act { repository.deletePayment(payment.id) }

    fun cancel(purchase: Purchase) = act { repository.cancelPurchase(purchase.id) }

    private fun act(block: suspend () -> Unit) {
        busy = true
        error = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun PurchaseDetailScreen(
    onBack: () -> Unit,
    onOpenReceipt: (String) -> Unit,
    viewModel: PurchaseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Compra", onBack = onBack)
        LoadStateContent(state, onRetry = viewModel.loader::retry) { detail ->
            val purchase = detail.purchase
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Summary(detail) }
                item {
                    // Same height for both buttons, even when a label wraps with a large font.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        if (purchase.openAmount > Money.ZERO) {
                            PrimaryButton(
                                "Registrar pagamento",
                                onClick = { viewModel.openPayment(detail, installment = null) },
                                icon = AppIcons.Cash,
                                enabled = !viewModel.busy,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        SecondaryButton(
                            "Comprovante",
                            onClick = { onOpenReceipt(purchase.id) },
                            icon = AppIcons.Receipt,
                            modifier = Modifier.fillMaxHeight().then(
                                if (purchase.openAmount > Money.ZERO) Modifier else Modifier.weight(1f),
                            ),
                        )
                    }
                }
                viewModel.error?.takeIf { viewModel.paymentContext == null }?.let { message ->
                    item { Text(message, color = AppTheme.colors.dangerText) }
                }
                if (purchase.installments.isNotEmpty()) {
                    item {
                        Panel(contentPadding = PaddingValues(vertical = 16.dp)) {
                            SectionHeader(
                                "Parcelas",
                                subtitle = "${purchase.installmentCount}x de ${purchase.installmentAmount?.format()}",
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            purchase.installments.forEachIndexed { index, installment ->
                                if (index > 0) RowDivider(Modifier.padding(horizontal = 16.dp))
                                InstallmentRow(
                                    installment = installment,
                                    count = purchase.installmentCount,
                                    enabled = !viewModel.busy && !purchase.isCanceled,
                                    onPay = { viewModel.openPayment(detail, installment) },
                                )
                            }
                        }
                    }
                    item {
                        Panel(contentPadding = PaddingValues(vertical = 16.dp)) {
                            SectionHeader("Pagamentos desta compra", modifier = Modifier.padding(horizontal = 20.dp))
                            if (detail.payments.isEmpty()) EmptyBox("Nenhum pagamento ainda.")
                            val installmentIds = purchase.installments.map { it.id }.toSet()
                            detail.payments.forEachIndexed { index, payment ->
                                if (index > 0) RowDivider(Modifier.padding(horizontal = 16.dp))
                                val here = payment.allocations.filter { it.installmentId in installmentIds }
                                ListRow(
                                    title = payment.amount.format(),
                                    subtitle = buildString {
                                        append(payment.paidOn.formatFull())
                                        append(" · parcelas ")
                                        append(
                                            here.joinToString { allocation ->
                                                purchase.installments.first { it.id == allocation.installmentId }.number.toString()
                                            },
                                        )
                                        payment.notes?.let { append(" · $it") }
                                    },
                                    trailing = {
                                        IconButton(onClick = { paymentToDelete = payment }, enabled = !viewModel.busy) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Remover pagamento", tint = AppTheme.colors.faint)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                if (!purchase.isCanceled) {
                    item {
                        TextButton(
                            onClick = { confirmCancel = true },
                            enabled = !viewModel.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Cancelar compra", color = AppTheme.colors.dangerText) }
                    }
                }
            }

            viewModel.paymentContext?.let { context ->
                PaymentSheet(
                    context = context,
                    busy = viewModel.busy,
                    error = viewModel.error,
                    onDismiss = viewModel::closePayment,
                    onConfirm = viewModel::registerPayment,
                )
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

    paymentToDelete?.let { payment ->
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("Remover pagamento?") },
            text = { Text("O pagamento de ${payment.amount.format()} será removido e as parcelas que ele pagou voltam a ficar em aberto.") },
            confirmButton = {
                TextButton(onClick = {
                    paymentToDelete = null
                    viewModel.deletePayment(payment)
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { paymentToDelete = null }) { Text("Voltar") } },
        )
    }
}

@Composable
private fun Summary(detail: PurchaseDetail) {
    val purchase = detail.purchase
    val today = today()
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(detail.customer.name, style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.ink)
                Text("Compra em ${purchase.purchaseDate.formatFull()}", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
            }
            PurchaseStatusPill(purchase, today)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
            StatusPill(
                if (purchase.paymentType == PaymentType.CASH) "À vista" else "${purchase.installmentCount}x de ${purchase.installmentAmount?.format()}",
                Tone.NEUTRAL,
            )
            if (purchase.source == PurchaseSource.WHATSAPP) StatusPill("Via WhatsApp", Tone.INFO)
        }
        purchase.canceledAt?.let {
            Text("Cancelada em ${it.formatFull()}", color = AppTheme.colors.dangerText, modifier = Modifier.padding(top = 8.dp))
        }

        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            purchase.items.forEach { item ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${item.quantity}x ${item.productName}", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.ink)
                        Text("${item.unitPrice.format()} cada", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
                    }
                    Text(item.subtotal.format(), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.ink)
                }
            }
            RowDivider()
            Row(Modifier.fillMaxWidth()) {
                Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(purchase.total.format(), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (purchase.paymentType == PaymentType.INSTALLMENTS && !purchase.isCanceled) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Pago ${purchase.paidAmount.format()}", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted, modifier = Modifier.weight(1f))
                Text("Falta ${purchase.openAmount.format()}", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.ink)
            }
            ProgressLine(
                purchase.paidAmount.cents.toFloat() / purchase.total.cents.coerceAtLeast(1),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        purchase.notes?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.muted, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun InstallmentRow(installment: Installment, count: Int, enabled: Boolean, onPay: () -> Unit) {
    val overdue = installment.isOverdue(today())
    ListRow(
        title = "${installment.number}/$count · ${installment.amount.format()}",
        subtitle = when {
            installment.isPaid -> "Paga em ${installment.paidAt?.formatFull() ?: "-"}"
            installment.isPartiallyPaid -> "Pago ${installment.paidAmount.format()} · falta ${installment.remaining.format()} · vence ${installment.dueDate.formatFull()}"
            overdue -> "Venceu em ${installment.dueDate.formatFull()}"
            else -> "Vence em ${installment.dueDate.formatFull()}"
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    installment.isPaid -> StatusPill("Paga", Tone.SUCCESS)
                    else -> {
                        StatusPill(
                            when {
                                overdue -> "Atrasada"
                                installment.isPartiallyPaid -> "Parcial"
                                else -> "A vencer"
                            },
                            when {
                                overdue -> Tone.DANGER
                                installment.isPartiallyPaid -> Tone.WARNING
                                else -> Tone.INFO
                            },
                        )
                        TextButton(onClick = onPay, enabled = enabled) { Text("Pagar", color = AppTheme.colors.ink) }
                    }
                }
            }
        },
    )
}
