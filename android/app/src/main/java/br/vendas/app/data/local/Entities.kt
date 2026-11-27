package br.vendas.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Local copy of the server tables. Money is stored in cents, dates as ISO-8601 text.
// The copy is replaced as a whole on every sync, so there are no foreign keys.

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val notes: String?,
    val active: Boolean,
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "price_cents") val priceCents: Long,
    val active: Boolean,
)

@Entity(tableName = "purchases", indices = [Index("customer_id")])
data class PurchaseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "customer_id") val customerId: String,
    @ColumnInfo(name = "purchase_date") val purchaseDate: String,
    @ColumnInfo(name = "payment_type") val paymentType: String,
    @ColumnInfo(name = "installment_count") val installmentCount: Int,
    @ColumnInfo(name = "total_cents") val totalCents: Long,
    val source: String,
    @ColumnInfo(name = "canceled_at") val canceledAt: String?,
    val notes: String?,
    /** Position in the server's creation order, to keep same-day purchases in order. */
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(tableName = "purchase_items", indices = [Index("purchase_id")])
data class PurchaseItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "purchase_id") val purchaseId: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "product_name") val productName: String,
    val quantity: Int,
    @ColumnInfo(name = "unit_price_cents") val unitPriceCents: Long,
    @ColumnInfo(name = "subtotal_cents") val subtotalCents: Long,
)

@Entity(tableName = "installments", indices = [Index("purchase_id")])
data class InstallmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "purchase_id") val purchaseId: String,
    val number: Int,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "due_date") val dueDate: String,
    @ColumnInfo(name = "paid_at") val paidAt: String?,
    @ColumnInfo(name = "paid_amount_cents") val paidAmountCents: Long,
)

@Entity(tableName = "payments", indices = [Index("customer_id")])
data class PaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "customer_id") val customerId: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "paid_on") val paidOn: String,
    val notes: String?,
)

@Entity(tableName = "payment_allocations", primaryKeys = ["payment_id", "installment_id"])
data class PaymentAllocationEntity(
    @ColumnInfo(name = "payment_id") val paymentId: String,
    @ColumnInfo(name = "installment_id") val installmentId: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
)

/** One row (id = 0) with the time of the last successful sync. */
@Entity(tableName = "sync_info")
data class SyncInfoEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: String,
    /** Account the copy belongs to: another account signing in must never see it. */
    @ColumnInfo(name = "user_id") val userId: String?,
)
