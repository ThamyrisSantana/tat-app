package br.vendas.app.domain

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId

/** The shop's time zone: decides what "today" and "overdue" mean. */
val SHOP_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

fun today(): LocalDate = LocalDate.now(SHOP_ZONE)

data class Customer(
    val id: String,
    val name: String,
    /** WhatsApp number in E.164 format. */
    val phone: String,
    val notes: String?,
)

/** What is due in one month. */
data class MonthDue(val month: YearMonth, val amount: Money)

/** What a customer owes, as listed on the Customers screen. */
data class CustomerBalance(
    val customerId: String,
    val name: String,
    val phone: String,
    val openAmount: Money,
    val overdueAmount: Money,
    val nextDueDate: LocalDate?,
    /** Due date of the oldest overdue installment, to say how late the customer is. */
    val oldestOverdueDate: LocalDate? = null,
    /** What is due in the first month (this month or later) that has open installments. */
    val monthlyDue: MonthDue? = null,
) {
    val isOverdue: Boolean get() = overdueAmount > Money.ZERO

    fun daysOverdue(today: LocalDate): Long =
        oldestOverdueDate?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) } ?: 0
}

data class CustomerInput(
    val name: String,
    /** Already normalized to E.164. */
    val phone: String,
    val notes: String?,
)

data class Product(
    val id: String,
    val name: String,
    val price: Money,
    val active: Boolean,
)

enum class PaymentType { CASH, INSTALLMENTS }

enum class PurchaseSource { APP, WHATSAPP }

data class Purchase(
    val id: String,
    val customerId: String,
    val purchaseDate: LocalDate,
    val paymentType: PaymentType,
    val installmentCount: Int,
    val total: Money,
    val source: PurchaseSource,
    val canceledAt: OffsetDateTime?,
    val notes: String?,
    val items: List<PurchaseItem>,
    val installments: List<Installment>,
) {
    val isCanceled: Boolean get() = canceledAt != null

    val openAmount: Money
        get() = if (isCanceled) Money.ZERO else installments.map { it.remaining }.sum()

    val paidAmount: Money get() = installments.map { it.paidAmount }.sum()

    /** True when any amount was paid: payments must be removed before canceling. */
    val hasPayments: Boolean get() = installments.any { it.paidAmount > Money.ZERO }

    /** The regular installment value (the last one may differ by a few cents). */
    val installmentAmount: Money? get() = installments.firstOrNull()?.amount
}

data class PurchaseItem(
    val id: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Money,
    val subtotal: Money,
)

data class Installment(
    val id: String,
    val purchaseId: String,
    val number: Int,
    val amount: Money,
    val dueDate: LocalDate,
    /** Set when the installment is fully paid. */
    val paidAt: OffsetDateTime?,
    /** How much of [amount] was paid so far (payments may cover it partly). */
    val paidAmount: Money = if (paidAt != null) amount else Money.ZERO,
) {
    val remaining: Money get() = amount - paidAmount

    val isPaid: Boolean get() = remaining <= Money.ZERO

    val isPartiallyPaid: Boolean get() = !isPaid && paidAmount > Money.ZERO

    fun isOverdue(today: LocalDate): Boolean = !isPaid && dueDate.isBefore(today)
}

/** Part of a payment applied to one installment. */
data class PaymentAllocation(val installmentId: String, val amount: Money)

/** Money received from a customer, spread over their installments. */
data class Payment(
    val id: String,
    val customerId: String,
    val amount: Money,
    val paidOn: LocalDate,
    val notes: String?,
    val allocations: List<PaymentAllocation>,
)

data class NewPayment(
    val customerId: String,
    val amount: Money,
    val paidOn: LocalDate,
    /** Only this purchase's installments; null spreads over all the customer's purchases. */
    val purchaseId: String? = null,
    /** Installment to pay first. */
    val installmentId: String? = null,
    val notes: String? = null,
)

/** An open installment with its customer, for the dashboard and receivables. */
data class OpenInstallment(
    val installment: Installment,
    val customerId: String,
    val customerName: String,
    val installmentCount: Int,
)

data class NewPurchaseItem(val productId: String, val quantity: Int)

data class NewPurchase(
    val customerId: String,
    val items: List<NewPurchaseItem>,
    /** 1 means cash (no installments). */
    val installmentCount: Int,
    val purchaseDate: LocalDate,
    /** Only for installments; null uses the default (one month later). */
    val firstDueDate: LocalDate?,
    val notes: String?,
)

/** [openAmount] is what could still be paid, when known. */
class PaymentExceedsOpenAmountException(val openAmount: Money?) : Exception("Payment is larger than the open amount")
