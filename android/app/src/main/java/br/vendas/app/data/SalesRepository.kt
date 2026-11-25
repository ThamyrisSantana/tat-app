package br.vendas.app.data

import br.vendas.app.domain.Customer
import br.vendas.app.domain.CustomerBalance
import br.vendas.app.domain.CustomerInput
import br.vendas.app.domain.Money
import br.vendas.app.domain.NewPurchase
import br.vendas.app.domain.OpenInstallment
import br.vendas.app.domain.Product
import br.vendas.app.domain.Purchase
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate

/** Whether the data on screen came straight from the server or from the copy saved on the phone. */
data class SyncStatus(
    /** True when the last attempt to reach the server failed for lack of connection. */
    val offline: Boolean = false,
    /** When the saved copy was last updated from the server; null if never. */
    val lastSyncedAt: Instant? = null,
)

/** Everything the screens read and write. Implemented by the offline-first Supabase repository and by the demo mode. */
interface SalesRepository {

    /** Increases after every write or server update, so open screens know to reload. */
    val changes: StateFlow<Int>

    val syncStatus: StateFlow<SyncStatus>

    /** Fetches fresh data from the server now (pull to refresh). */
    suspend fun refresh()

    /** Deletes the copy saved on the phone (on sign-out). */
    suspend fun clearLocalData()

    suspend fun customerBalances(): List<CustomerBalance>

    suspend fun customer(id: String): Customer

    suspend fun customerBalance(id: String): CustomerBalance

    /** @throws DuplicatePhoneException when another active customer has the number. */
    suspend fun createCustomer(input: CustomerInput): Customer

    /** @throws DuplicatePhoneException when another active customer has the number. */
    suspend fun updateCustomer(id: String, input: CustomerInput)

    /** Hides the customer; their purchase history is kept. */
    suspend fun deactivateCustomer(id: String)

    suspend fun products(includeInactive: Boolean = false): List<Product>

    /** @throws DuplicateProductException when an active product has the same name. */
    suspend fun createProduct(name: String, price: Money): Product

    /** @throws DuplicateProductException when another active product has the same name. */
    suspend fun updateProduct(id: String, name: String, price: Money, active: Boolean)

    /** Purchases of a customer, newest first, with items and installments. */
    suspend fun purchasesOfCustomer(customerId: String): List<Purchase>

    suspend fun purchase(id: String): Purchase

    /** Saves the purchase, its items and installments together. Returns the purchase id. */
    suspend fun createPurchase(input: NewPurchase): String

    suspend fun setInstallmentPaid(installmentId: String, paid: Boolean)

    /** @throws PurchaseHasPaidInstallmentsException when a payment must be undone first. */
    suspend fun cancelPurchase(purchaseId: String)

    /** Open installments of purchases that are not canceled, due up to [until] (overdue included). */
    suspend fun openInstallmentsDueUntil(until: LocalDate): List<OpenInstallment>
}

class DuplicatePhoneException(val existingCustomerName: String?) : Exception("Phone already in use")

class DuplicateProductException : Exception("Product name already in use")

class PurchaseHasPaidInstallmentsException : Exception("Undo the paid installments first")
