package br.vendas.app.data.supabase

import br.vendas.app.data.DuplicatePhoneException
import br.vendas.app.data.DuplicateProductException
import br.vendas.app.data.PurchaseHasPaymentsException
import br.vendas.app.data.SalesSnapshot
import br.vendas.app.domain.Customer
import br.vendas.app.domain.CustomerInput
import br.vendas.app.domain.Money
import br.vendas.app.domain.NewPayment
import br.vendas.app.domain.NewPurchase
import br.vendas.app.domain.PaymentExceedsOpenAmountException
import br.vendas.app.domain.Product
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.OffsetDateTime
import javax.inject.Inject

private const val UNIQUE_VIOLATION = "23505"
private const val PAGE_SIZE = 1000L
private const val PURCHASE_COLUMNS = "*, purchase_items(*, products(name)), installments(*)"

/**
 * Talks to Supabase. Reads the signed-in seller's whole data set for the local copy (the
 * database's access rules leave out other sellers' rows); writes one change at a time.
 */
class SupabaseRemoteDataSource @Inject constructor(private val client: SupabaseClient) {

    /** Everything the app shows. Paged, since PostgREST returns at most 1000 rows per request. */
    suspend fun fetchSnapshot(): SalesSnapshot = SalesSnapshot(
        customers = fetchAll<CustomerDto>("customers", Columns.ALL).map { it.toRecord() },
        products = fetchAll<ProductDto>("products", Columns.ALL).map { it.toDomain() },
        purchases = fetchAll<PurchaseDto>("purchases", Columns.raw(PURCHASE_COLUMNS)).map { it.toDomain() },
        payments = fetchAll<PaymentDto>("payments", Columns.raw("*, payment_allocations(installment_id, amount)"))
            .map { it.toDomain() },
    )

    /** The signed-in account, or null when there is no saved session. */
    fun currentUserId(): String? = client.auth.currentSessionOrNull()?.user?.id

    suspend fun createCustomer(input: CustomerInput): Customer {
        ensurePhoneFree(input.phone, exceptId = null)
        return translateUniqueViolation({ DuplicatePhoneException(null) }) {
            client.from("customers")
                .insert(CustomerWriteDto(input.name.trim(), input.phone, input.notes)) { select() }
                .decodeSingle<CustomerDto>()
                .toDomain()
        }
    }

    suspend fun updateCustomer(id: String, input: CustomerInput) {
        ensurePhoneFree(input.phone, exceptId = id)
        translateUniqueViolation({ DuplicatePhoneException(null) }) {
            client.from("customers")
                .update(CustomerWriteDto(input.name.trim(), input.phone, input.notes)) { filter { eq("id", id) } }
        }
    }

    suspend fun deactivateCustomer(id: String) {
        client.from("customers")
            .update(mapOf("deleted_at" to OffsetDateTime.now().toString())) { filter { eq("id", id) } }
    }

    suspend fun createProduct(name: String, price: Money): Product =
        translateUniqueViolation({ DuplicateProductException() }) {
            client.from("products")
                .insert(ProductWriteDto(name.trim(), price, active = true)) { select() }
                .decodeSingle<ProductDto>()
                .toDomain()
        }

    suspend fun updateProduct(id: String, name: String, price: Money, active: Boolean) {
        translateUniqueViolation({ DuplicateProductException() }) {
            client.from("products")
                .update(ProductWriteDto(name.trim(), price, active)) { filter { eq("id", id) } }
        }
    }

    suspend fun createPurchase(input: NewPurchase): String {
        val parameters = buildJsonObject {
            put("p_customer_id", input.customerId)
            putJsonArray("p_items") {
                input.items.forEach { item ->
                    addJsonObject {
                        put("product_id", item.productId)
                        put("quantity", item.quantity)
                    }
                }
            }
            put("p_installment_count", input.installmentCount)
            put("p_purchase_date", input.purchaseDate.toString())
            input.firstDueDate?.let { put("p_first_due_date", it.toString()) }
            put("p_source", "app")
            input.notes?.let { put("p_notes", it) }
        }
        return client.postgrest.rpc("create_purchase", parameters).decodeAs<String>()
    }

    suspend fun registerPayment(input: NewPayment): String {
        val parameters = buildJsonObject {
            put("p_customer_id", input.customerId)
            put("p_amount", input.amount.toBigDecimal())
            put("p_paid_on", input.paidOn.toString())
            input.purchaseId?.let { put("p_purchase_id", it) }
            input.installmentId?.let { put("p_installment_id", it) }
            input.notes?.let { put("p_notes", it) }
        }
        try {
            return client.postgrest.rpc("register_payment", parameters).decodeAs<String>()
        } catch (e: PostgrestRestException) {
            if (e.hint == "payment_exceeds_open_amount") throw PaymentExceedsOpenAmountException(null)
            throw e
        }
    }

    suspend fun deletePayment(paymentId: String) {
        client.postgrest.rpc("delete_payment", buildJsonObject { put("p_payment_id", paymentId) })
    }

    suspend fun cancelPurchase(purchaseId: String) {
        try {
            client.postgrest.rpc("cancel_purchase", buildJsonObject { put("p_purchase_id", purchaseId) })
        } catch (e: PostgrestRestException) {
            if (e.hint == "purchase_has_paid_installments") throw PurchaseHasPaymentsException()
            throw e
        }
    }

    private suspend inline fun <reified T : Any> fetchAll(table: String, columns: Columns): List<T> {
        val rows = mutableListOf<T>()
        var from = 0L
        while (true) {
            val page = client.from(table)
                .select(columns) {
                    order("created_at", Order.ASCENDING)
                    order("id", Order.ASCENDING)
                    range(from, from + PAGE_SIZE - 1)
                }
                .decodeList<T>()
            rows += page
            if (page.size < PAGE_SIZE) return rows
            from += PAGE_SIZE
        }
    }

    /** Checks the server (not the local copy) so the message can name who has the number. */
    private suspend fun ensurePhoneFree(phone: String, exceptId: String?) {
        val owners = client.from("customers")
            .select {
                filter {
                    eq("phone", phone)
                    exact("deleted_at", null)
                    if (exceptId != null) neq("id", exceptId)
                }
            }
            .decodeList<CustomerDto>()
        if (owners.isNotEmpty()) throw DuplicatePhoneException(owners.first().name)
    }

    private inline fun <T> translateUniqueViolation(error: () -> Exception, block: () -> T): T =
        try {
            block()
        } catch (e: PostgrestRestException) {
            if (e.code == UNIQUE_VIOLATION) throw error()
            throw e
        }
}
