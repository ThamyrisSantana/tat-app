package br.vendas.app.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.vendas.app.data.SalesSnapshot
import br.vendas.app.domain.AllocationCandidate
import br.vendas.app.domain.Money
import br.vendas.app.domain.PaymentAllocator
import br.vendas.app.domain.PaymentExceedsOpenAmountException
import br.vendas.app.domain.sum
import br.vendas.app.ui.common.DateField
import br.vendas.app.ui.common.formatShort
import br.vendas.app.ui.design.fieldColors
import br.vendas.app.ui.design.FieldShape
import br.vendas.app.ui.design.Panel
import br.vendas.app.ui.design.PillTabs
import br.vendas.app.ui.design.PrimaryButton
import br.vendas.app.ui.theme.AppTheme
import java.time.LocalDate

/** Everything the payment sheet needs: where the money can go and the amounts to suggest. */
data class PaymentContext(
    val customerId: String,
    val customerName: String,
    /** Only this purchase's installments; null for any of the customer's purchases. */
    val purchaseId: String?,
    /** Installment to pay first, when the user tapped "Pagar" on one. */
    val installmentId: String?,
    val candidates: List<AllocationCandidate>,
    /** Installment id to a readable name ("Parcela 2/3 · vence 25/11"). */
    val labels: Map<String, String>,
    /** Quick amounts, e.g. "Parcela" to R$ 20,00. */
    val suggestions: List<Pair<String, Money>>,
) {
    val openAmount: Money get() = candidates.map { it.remaining }.sum()

    companion object {
        fun from(
            snapshot: SalesSnapshot,
            customerId: String,
            purchaseId: String? = null,
            installmentId: String? = null,
            today: LocalDate,
        ): PaymentContext {
            val candidates = snapshot.allocationCandidates(customerId, purchaseId)
            val purchases = snapshot.purchases.filter { it.customerId == customerId }
            val labels = purchases.flatMap { purchase ->
                purchase.installments.map { installment ->
                    val prefix = if (purchaseId == null) "Compra de ${purchase.purchaseDate.formatShort()} · " else ""
                    installment.id to "${prefix}parcela ${installment.number}/${purchase.installmentCount} · vence ${installment.dueDate.formatShort()}"
                }
            }.toMap()
            val ordered = candidates.sortedWith(compareBy({ it.dueDate }, { it.purchaseOrder }, { it.number }))
            val chosen = candidates.firstOrNull { it.installmentId == installmentId } ?: ordered.firstOrNull()
            val overdue = candidates.filter { it.dueDate.isBefore(today) }.map { it.remaining }.sum()
            val total = candidates.map { it.remaining }.sum()
            val suggestions = buildList {
                chosen?.let { add((if (installmentId != null) "Esta parcela" else "Próxima parcela") to it.remaining) }
                if (overdue > Money.ZERO) add("Atrasado" to overdue)
                if (total > Money.ZERO) add("Tudo em aberto" to total)
            }.distinctBy { it.second }
            return PaymentContext(
                customerId = customerId,
                customerName = snapshot.customer(customerId).name,
                purchaseId = purchaseId,
                installmentId = installmentId,
                candidates = candidates,
                labels = labels,
                suggestions = suggestions,
            )
        }
    }
}

/**
 * Bottom sheet to record money received. Any amount up to what is open is
 * accepted; the preview shows which installments it pays before confirming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentSheet(
    context: PaymentContext,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (amount: Money, paidOn: LocalDate, notes: String?) -> Unit,
) {
    var amountText by remember { mutableStateOf(context.suggestions.firstOrNull()?.second?.toInputText().orEmpty()) }
    var paidOn by remember { mutableStateOf(br.vendas.app.domain.today()) }
    var notes by remember { mutableStateOf("") }
    val amount = Money.parse(amountText)

    val preview = remember(amount, context) {
        if (amount == null || amount <= Money.ZERO) {
            null
        } else {
            try {
                Result.success(PaymentAllocator.allocate(amount, context.candidates, context.installmentId))
            } catch (e: PaymentExceedsOpenAmountException) {
                Result.failure(e)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.canvas,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text("Registrar pagamento", style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.ink)
                Text(
                    "${context.customerName} · em aberto ${context.openAmount.format()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.muted,
                )
            }

            if (context.suggestions.isNotEmpty()) {
                PillTabs(
                    options = context.suggestions.map { (label, value) -> value to "$label ${value.format()}" },
                    selected = amount ?: Money(-1),
                    onSelect = { amountText = it.toInputText() },
                )
            }

            OutlinedTextField(
                shape = FieldShape,
                colors = fieldColors(),
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Valor recebido") },
                prefix = { Text("R$ ", style = MaterialTheme.typography.headlineSmall) },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = AppTheme.colors.ink),
                singleLine = true,
                isError = amountText.isNotBlank() && amount == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            DateField(label = "Data do pagamento", date = paidOn, onDateChange = { paidOn = it })

            Panel(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Text("Como o valor será aplicado", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.muted)
                when {
                    preview == null -> Text(
                        "Digite o valor. Ele quita as parcelas mais antigas primeiro; valor a mais vai para a próxima, valor a menos deixa a parcela parcialmente paga.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    preview.isFailure -> Text(
                        "O valor é maior que o total em aberto (${context.openAmount.format()}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.dangerText,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    else -> preview.getOrThrow().forEach { allocation ->
                        val candidate = context.candidates.first { it.installmentId == allocation.installmentId }
                        val settles = allocation.amount >= candidate.remaining
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    context.labels[allocation.installmentId].orEmpty().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.ink,
                                )
                                Text(
                                    if (settles) "Quita a parcela" else "Faltarão ${(candidate.remaining - allocation.amount).format()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (settles) AppTheme.colors.successText else AppTheme.colors.warningText,
                                )
                            }
                            Text(allocation.amount.format(), style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.ink)
                        }
                    }
                }
            }

            OutlinedTextField(
                shape = FieldShape,
                colors = fieldColors(),
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Observação (opcional)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, color = AppTheme.colors.dangerText, style = MaterialTheme.typography.bodyMedium) }

            PrimaryButton(
                text = if (amount != null && preview?.isSuccess == true) "Registrar ${amount.format()}" else "Registrar pagamento",
                onClick = { if (amount != null) onConfirm(amount, paidOn, notes.trim().ifEmpty { null }) },
                enabled = !busy && preview?.isSuccess == true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
    }
}
