package br.vendas.app.data.supabase

import br.vendas.app.data.CustomerRecord
import br.vendas.app.domain.Customer
import br.vendas.app.domain.Installment
import br.vendas.app.domain.Money
import br.vendas.app.domain.PaymentType
import br.vendas.app.domain.Product
import br.vendas.app.domain.Purchase
import br.vendas.app.domain.PurchaseItem
import br.vendas.app.domain.PurchaseSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime

// Row shapes as PostgREST returns them. Column names follow the database (snake_case).

@Serializable
internal data class CustomerDto(
    val id: String,
    val name: String,
    val phone: String,
    val notes: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    fun toDomain() = Customer(id, name, phone, notes)

    fun toRecord() = CustomerRecord(toDomain(), active = deletedAt == null)
}

@Serializable
internal data class CustomerWriteDto(
    val name: String,
    val phone: String,
    val notes: String?,
)

@Serializable
internal data class ProductDto(
    val id: String,
    val name: String,
    @Serializable(with = MoneySerializer::class) val price: Money,
    val active: Boolean,
) {
    fun toDomain() = Product(id, name, price, active)
}

@Serializable
internal data class ProductWriteDto(
    val name: String,
    @Serializable(with = MoneySerializer::class) val price: Money,
    val active: Boolean,
)

@Serializable
internal data class ProductNameDto(val name: String)

@Serializable
internal data class PurchaseItemDto(
    val id: String,
    @SerialName("product_id") val productId: String,
    val quantity: Int,
    @SerialName("unit_price") @Serializable(with = MoneySerializer::class) val unitPrice: Money,
    @Serializable(with = MoneySerializer::class) val subtotal: Money,
    val products: ProductNameDto? = null,
) {
    fun toDomain() = PurchaseItem(id, productId, products?.name.orEmpty(), quantity, unitPrice, subtotal)
}

@Serializable
internal data class InstallmentDto(
    val id: String,
    @SerialName("purchase_id") val purchaseId: String,
    val number: Int,
    @Serializable(with = MoneySerializer::class) val amount: Money,
    @SerialName("due_date") @Serializable(with = LocalDateSerializer::class) val dueDate: LocalDate,
    @SerialName("paid_at") @Serializable(with = OffsetDateTimeSerializer::class) val paidAt: OffsetDateTime? = null,
) {
    fun toDomain() = Installment(id, purchaseId, number, amount, dueDate, paidAt)
}

@Serializable
internal data class PurchaseDto(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("purchase_date") @Serializable(with = LocalDateSerializer::class) val purchaseDate: LocalDate,
    @SerialName("payment_type") val paymentType: String,
    @SerialName("installment_count") val installmentCount: Int,
    @SerialName("total_amount") @Serializable(with = MoneySerializer::class) val totalAmount: Money,
    val source: String,
    @SerialName("canceled_at") @Serializable(with = OffsetDateTimeSerializer::class) val canceledAt: OffsetDateTime? = null,
    val notes: String? = null,
    @SerialName("purchase_items") val items: List<PurchaseItemDto> = emptyList(),
    val installments: List<InstallmentDto> = emptyList(),
) {
    fun toDomain() = Purchase(
        id = id,
        customerId = customerId,
        purchaseDate = purchaseDate,
        paymentType = if (paymentType == "installments") PaymentType.INSTALLMENTS else PaymentType.CASH,
        installmentCount = installmentCount,
        total = totalAmount,
        source = if (source == "whatsapp") PurchaseSource.WHATSAPP else PurchaseSource.APP,
        canceledAt = canceledAt,
        notes = notes,
        items = items.map { it.toDomain() },
        installments = installments.map { it.toDomain() }.sortedBy { it.number },
    )
}
