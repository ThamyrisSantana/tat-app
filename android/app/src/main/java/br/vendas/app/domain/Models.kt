package br.vendas.app.domain

import java.time.LocalDate
import java.time.OffsetDateTime
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

/** What a customer owes, as listed on the Customers screen. */
data class CustomerBalance(
    val customerId: String,
    val name: String,
    val phone: String,
    val openAmount: Money,
    val overdueAmount: Money,
    val nextDueDate: LocalDate?,
)

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
        get() = if (isCanceled) Money.ZERO else installments.filterNot { it.isPaid }.map { it.amount }.sum()

    val hasPaidInstallments: Boolean get() = installments.any { it.isPaid }
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
    val paidAt: OffsetDateTime?,
) {
    val isPaid: Boolean get() = paidAt != null

    fun isOverdue(today: LocalDate): Boolean = !isPaid && dueDate.isBefore(today)
}

/** An open installment with its customer, for the Summary screen. */
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
