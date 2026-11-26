package br.vendas.app.data

import br.vendas.app.data.local.SalesDao
import br.vendas.app.data.local.SyncInfoEntity
import br.vendas.app.data.local.toLocalData
import br.vendas.app.data.local.toSnapshot
import br.vendas.app.data.supabase.SupabaseRemoteDataSource
import br.vendas.app.domain.Customer
import br.vendas.app.domain.CustomerBalance
import br.vendas.app.domain.CustomerInput
import br.vendas.app.domain.Money
import br.vendas.app.domain.NewPayment
import br.vendas.app.domain.NewPurchase
import br.vendas.app.domain.OpenInstallment
import br.vendas.app.domain.Payment
import br.vendas.app.domain.PaymentExceedsOpenAmountException
import br.vendas.app.domain.Product
import br.vendas.app.domain.Purchase
import br.vendas.app.domain.sum
import br.vendas.app.domain.today
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Reads come from the copy saved on the phone, refreshed from Supabase when it
 * is older than [STALE_AFTER_MS]. Without internet the saved copy is shown and
 * [syncStatus] says so. Writes always go to Supabase first (they fail offline),
 * then the copy is refreshed.
 */
class OfflineFirstRepository @Inject constructor(
    private val remote: SupabaseRemoteDataSource,
    private val dao: SalesDao,
) : SalesRepository {

    private val _changes = MutableStateFlow(0)
    override val changes: StateFlow<Int> = _changes.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncStatus())
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val syncMutex = Mutex()
    private var lastAttemptMs = 0L

    override suspend fun refresh() {
        sync(force = true)
        _changes.update { it + 1 }
    }

    override suspend fun clearLocalData() {
        syncMutex.withLock {
            dao.clearAll()
            lastAttemptMs = 0L
            _syncStatus.value = SyncStatus()
        }
    }

    override suspend fun snapshot(): SalesSnapshot {
        sync(force = false)
        return dao.load().toSnapshot()
    }

    override suspend fun customerBalances(): List<CustomerBalance> = snapshot().customerBalances(today())

    override suspend fun customer(id: String): Customer = snapshot().customer(id)

    override suspend fun customerBalance(id: String): CustomerBalance = snapshot().customerBalance(id, today())

    override suspend fun products(includeInactive: Boolean): List<Product> = snapshot().products(includeInactive)

    override suspend fun purchasesOfCustomer(customerId: String): List<Purchase> = snapshot().purchasesOf(customerId)

    override suspend fun purchase(id: String): Purchase = snapshot().purchase(id)

    override suspend fun openInstallmentsDueUntil(until: LocalDate): List<OpenInstallment> =
        snapshot().openInstallmentsDueUntil(until)

    override suspend fun createCustomer(input: CustomerInput): Customer = write { remote.createCustomer(input) }

    override suspend fun updateCustomer(id: String, input: CustomerInput) = write { remote.updateCustomer(id, input) }

    override suspend fun deactivateCustomer(id: String) = write { remote.deactivateCustomer(id) }

    override suspend fun createProduct(name: String, price: Money): Product = write { remote.createProduct(name, price) }

    override suspend fun updateProduct(id: String, name: String, price: Money, active: Boolean) =
        write { remote.updateProduct(id, name, price, active) }

    override suspend fun createPurchase(input: NewPurchase): String = write { remote.createPurchase(input) }

    override suspend fun cancelPurchase(purchaseId: String) = write { remote.cancelPurchase(purchaseId) }

    override suspend fun registerPayment(input: NewPayment): String {
        // Checked here too so the message can say how much is open; the server checks again.
        val open = snapshot().allocationCandidates(input.customerId, input.purchaseId).map { it.remaining }.sum()
        if (input.amount > open) throw PaymentExceedsOpenAmountException(open)
        return write { remote.registerPayment(input) }
    }

    override suspend fun deletePayment(paymentId: String) = write { remote.deletePayment(paymentId) }

    override suspend fun paymentsOfCustomer(customerId: String): List<Payment> = snapshot().paymentsOf(customerId)

    override suspend fun paymentsOfPurchase(purchaseId: String): List<Payment> = snapshot().paymentsOfPurchase(purchaseId)

    private suspend fun <T> write(block: suspend () -> T): T {
        val result = block()
        // The change is on the server; a failed refresh only means the copy catches up later.
        try {
            sync(force = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ignored on purpose: the next read retries.
        }
        _changes.update { it + 1 }
        return result
    }

    /**
     * Replaces the local copy with the server's. Network failures are tolerated
     * when a copy exists; other failures (and offline with no copy) are thrown.
     */
    private suspend fun sync(force: Boolean) = syncMutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && now - lastAttemptMs < STALE_AFTER_MS) return@withLock
        lastAttemptMs = now

        val lastSynced = dao.syncInfo()?.lastSyncedAt?.let(Instant::parse)
        try {
            val fresh = remote.fetchSnapshot()
            val syncedAt = Instant.now()
            val before = if (lastSynced != null) dao.load().toSnapshot() else null
            dao.replaceAll(fresh.toLocalData(), SyncInfoEntity(lastSyncedAt = syncedAt.toString()))
            _syncStatus.value = SyncStatus(offline = false, lastSyncedAt = syncedAt)
            // Someone else (another phone, the bot) changed the data: reload open screens.
            if (before != null && before != fresh) _changes.update { it + 1 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            _syncStatus.value = SyncStatus(offline = true, lastSyncedAt = lastSynced)
            if (lastSynced == null) throw e
        }
    }

    private companion object {
        const val STALE_AFTER_MS = 15_000L
    }
}

/** True for failures caused by having no connection (as opposed to server or data errors). */
fun Throwable.isNetworkError(): Boolean =
    generateSequence(this) { it.cause }.any {
        it is IOException || it.javaClass.simpleName == "HttpRequestException"
    }
