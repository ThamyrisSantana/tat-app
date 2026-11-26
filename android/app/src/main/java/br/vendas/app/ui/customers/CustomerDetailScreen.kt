package br.vendas.app.ui.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.vendas.app.data.SalesRepository
import br.vendas.app.domain.Customer
import br.vendas.app.domain.CustomerBalance
import br.vendas.app.domain.Money
import br.vendas.app.domain.NewPayment
import br.vendas.app.domain.Payment
import br.vendas.app.domain.PaymentType
import br.vendas.app.domain.PhoneNumbers
import br.vendas.app.domain.Purchase
import br.vendas.app.domain.PurchaseSource
import br.vendas.app.domain.sum
import br.vendas.app.domain.today
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.EmptyBox
import br.vendas.app.ui.common.LoadState
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.compactName
import br.vendas.app.ui.common.formatFull
import br.vendas.app.ui.common.formatShort
import br.vendas.app.ui.common.friendlyError
import br.vendas.app.ui.common.openWhatsApp
import br.vendas.app.ui.common.plural
import br.vendas.app.ui.design.AppIcons
import br.vendas.app.ui.design.Avatar
import br.vendas.app.ui.design.CardShape
import br.vendas.app.ui.design.CircleIconButton
import br.vendas.app.ui.design.KpiRow
import br.vendas.app.ui.design.KpiTile
import br.vendas.app.ui.design.ListRow
import br.vendas.app.ui.design.Panel
import br.vendas.app.ui.design.PrimaryButton
import br.vendas.app.ui.design.ProgressLine
import br.vendas.app.ui.design.RowDivider
import br.vendas.app.ui.design.ScreenHeader
import br.vendas.app.ui.design.SecondaryButton
import br.vendas.app.ui.design.SectionHeader
import br.vendas.app.ui.design.StatusPill
import br.vendas.app.ui.design.TileStyle
import br.vendas.app.ui.design.Tone
import br.vendas.app.ui.navigation.CustomerRoute
import br.vendas.app.ui.payments.PaymentContext
import br.vendas.app.ui.payments.PaymentSheet
import br.vendas.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CustomerDetail(
    val customer: Customer,
    val balance: CustomerBalance,
    val purchases: List<Purchase>,
    val payments: List<Payment>,
    val paymentContext: PaymentContext,
) {
    val totalBought: Money get() = purchases.filterNot { it.isCanceled }.map { it.total }.sum()
}

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val repository: SalesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val customerId = savedStateHandle.toRoute<CustomerRoute>().customerId

    val loader = AutoLoader(viewModelScope, repository.changes) {
        val snapshot = repository.snapshot()
        val today = today()
        CustomerDetail(
            customer = snapshot.customer(customerId),
            balance = snapshot.customerBalance(customerId, today),
            purchases = snapshot.purchasesOf(customerId),
            payments = snapshot.paymentsOf(customerId),
            paymentContext = PaymentContext.from(snapshot, customerId, today = today),
        )
    }

    var sheetOpen by mutableStateOf(false)
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun openSheet() {
        error = null
        sheetOpen = true
    }

    fun registerPayment(amount: Money, paidOn: LocalDate, notes: String?) = act(closeSheet = true) {
        repository.registerPayment(NewPayment(customerId, amount, paidOn, notes = notes))
    }

    fun deletePayment(payment: Payment) = act(closeSheet = false) { repository.deletePayment(payment.id) }

    private fun act(closeSheet: Boolean, block: suspend () -> Unit) {
        busy = true
        error = null
        viewModelScope.launch {
            try {
                block()
                if (closeSheet) sheetOpen = false
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                busy = false
            }
        }
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
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack = onBack) {
            CircleIconButton(Icons.Filled.Edit, "Editar cliente", onEdit)
        }
        LoadStateContent(state, onRetry = viewModel.loader::retry) { detail ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ProfileCard(detail) }
                if (detail.balance.isOverdue) item { OverdueBanner(detail.balance) }
                item { BalanceTiles(detail) }
                item {
                    // Same height for both buttons, even when a label wraps with a large font.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        PrimaryButton(
                            text = "Registrar pagamento",
                            onClick = viewModel::openSheet,
                            icon = AppIcons.Cash,
                            enabled = detail.balance.openAmount > Money.ZERO,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        SecondaryButton("Compra", onClick = onNewPurchase, icon = Icons.Filled.Add, modifier = Modifier.fillMaxHeight())
                    }
                }
                viewModel.error?.takeIf { !viewModel.sheetOpen }?.let { message ->
                    item { Text(message, color = AppTheme.colors.dangerText) }
                }
                item { PurchasesPanel(detail.purchases, onOpenPurchase) }
                item { PaymentsPanel(detail.payments, onDelete = { paymentToDelete = it }) }
            }

            if (viewModel.sheetOpen) {
                PaymentSheet(
                    context = detail.paymentContext,
                    busy = viewModel.busy,
                    error = viewModel.error,
                    onDismiss = { viewModel.sheetOpen = false },
                    onConfirm = viewModel::registerPayment,
                )
            }
        }
    }

    paymentToDelete?.let { payment ->
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("Remover pagamento?") },
            text = { Text("O pagamento de ${payment.amount.format()} em ${payment.paidOn.formatFull()} será removido e as parcelas voltam a ficar em aberto.") },
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
private fun ProfileCard(detail: CustomerDetail) {
    val context = LocalContext.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(detail.customer.name, size = 52.dp, tone = if (detail.balance.isOverdue) Tone.DANGER else null)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(detail.customer.name, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.ink)
                Text("WhatsApp ${PhoneNumbers.format(detail.customer.phone)}", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
            }
        }
        detail.customer.notes?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.muted, modifier = Modifier.padding(top = 12.dp))
        }
        if (detail.balance.openAmount > Money.ZERO) {
            SecondaryButton(
                text = "Cobrar no WhatsApp",
                onClick = { openWhatsApp(context, detail.customer.phone, chargeMessage(detail)) },
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
            )
        }
    }
}

/** Impossible-to-miss notice that the customer is late. */
@Composable
private fun OverdueBanner(balance: CustomerBalance) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.dangerSolid)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.onDangerSolid)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Pagamento atrasado há ${balance.daysOverdue(today())} dias",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onDangerSolid,
            )
            Text(
                "${balance.overdueAmount.format()} vencido desde ${balance.oldestOverdueDate?.formatFull()}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onDangerSolid.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun BalanceTiles(detail: CustomerDetail) {
    val balance = detail.balance
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KpiRow {
            KpiTile(
                label = "Total devido",
                value = balance.openAmount.format(),
                caption = balance.nextDueDate?.let { "Próx. venc. ${it.formatShort()}" } ?: "Nada em aberto",
                style = if (balance.isOverdue) TileStyle.DANGER else TileStyle.DARK,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = "Parcela do mês",
                value = balance.monthlyDue?.amount?.format() ?: "—",
                caption = balance.monthlyDue?.month?.compactName() ?: "Sem parcelas",
                icon = AppIcons.Wallet,
                modifier = Modifier.weight(1f),
            )
        }
        KpiRow {
            KpiTile(
                label = "Total comprado",
                value = detail.totalBought.format(),
                caption = plural(detail.purchases.count { !it.isCanceled }, "compra", "compras"),
                icon = AppIcons.BarChart,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = "Total pago",
                value = detail.payments.map { it.amount }.sum().format(),
                caption = plural(detail.payments.size, "pagamento", "pagamentos"),
                icon = AppIcons.Cash,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PurchasesPanel(purchases: List<Purchase>, onOpen: (String) -> Unit) {
    val today = today()
    Panel(contentPadding = PaddingValues(vertical = 16.dp)) {
        SectionHeader("Compras", subtitle = "Toque para ver parcelas e comprovante", modifier = Modifier.padding(horizontal = 20.dp))
        if (purchases.isEmpty()) EmptyBox("Nenhuma compra registrada.")
        purchases.forEachIndexed { index, purchase ->
            if (index > 0) RowDivider(Modifier.padding(horizontal = 16.dp))
            Column(Modifier.fillMaxWidth()) {
                ListRow(
                    title = purchase.items.joinToString { "${it.quantity}x ${it.productName}" },
                    subtitle = buildString {
                        append(purchase.purchaseDate.formatFull())
                        if (purchase.paymentType == PaymentType.INSTALLMENTS) {
                            append(" · ${purchase.installmentCount}x de ${purchase.installmentAmount?.format()}")
                        }
                        if (purchase.source == PurchaseSource.WHATSAPP) append(" · via WhatsApp")
                    },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(purchase.total.format(), style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.ink)
                            PurchaseStatusPill(purchase, today)
                        }
                    },
                    showArrow = true,
                    onClick = { onOpen(purchase.id) },
                )
                if (purchase.paymentType == PaymentType.INSTALLMENTS && !purchase.isCanceled) {
                    val fraction = purchase.paidAmount.cents.toFloat() / purchase.total.cents.coerceAtLeast(1)
                    ProgressLine(
                        fraction,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        color = if (purchase.installments.any { it.isOverdue(today) }) AppTheme.colors.dangerSolid else AppTheme.colors.ink,
                        height = 6.dp,
                    )
                }
            }
        }
    }
}

@Composable
fun PurchaseStatusPill(purchase: Purchase, today: LocalDate) {
    when {
        purchase.isCanceled -> StatusPill("Cancelada", Tone.NEUTRAL)
        purchase.paymentType == PaymentType.CASH -> StatusPill("À vista", Tone.SUCCESS)
        purchase.openAmount == Money.ZERO -> StatusPill("Quitada", Tone.SUCCESS)
        purchase.installments.any { it.isOverdue(today) } -> StatusPill("Atrasada", Tone.DANGER)
        purchase.hasPayments -> StatusPill("Falta ${purchase.openAmount.format()}", Tone.WARNING)
        else -> StatusPill("Em aberto", Tone.INFO)
    }
}

@Composable
private fun PaymentsPanel(payments: List<Payment>, onDelete: (Payment) -> Unit) {
    Panel(contentPadding = PaddingValues(vertical = 16.dp)) {
        SectionHeader("Pagamentos", subtitle = "Valores recebidos deste cliente", modifier = Modifier.padding(horizontal = 20.dp))
        if (payments.isEmpty()) EmptyBox("Nenhum pagamento registrado.")
        payments.forEachIndexed { index, payment ->
            if (index > 0) RowDivider(Modifier.padding(horizontal = 16.dp))
            ListRow(
                title = payment.amount.format(),
                subtitle = buildString {
                    append(payment.paidOn.formatFull())
                    append(" · ${plural(payment.allocations.size, "parcela", "parcelas")}")
                    payment.notes?.let { append(" · $it") }
                },
                leading = {
                    Icon(
                        AppIcons.Cash,
                        contentDescription = null,
                        tint = AppTheme.colors.successText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.successBg)
                            .padding(8.dp),
                    )
                },
                trailing = {
                    IconButton(onClick = { onDelete(payment) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remover pagamento", tint = AppTheme.colors.faint)
                    }
                },
            )
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
            "• Parcela ${installment.number}/${purchase.installmentCount}: ${installment.remaining.format()}, " +
                "vence ${installment.dueDate.formatShort()}"
        }
    return "Olá, $firstName! Passando para lembrar das parcelas em aberto:\n$lines\n" +
        "Total: ${detail.balance.openAmount.format()}"
}
