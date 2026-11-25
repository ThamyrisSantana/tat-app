package br.vendas.app.data.demo

import br.vendas.app.data.AuthGateway
import br.vendas.app.data.AuthState
import br.vendas.app.data.CustomerRecord
import br.vendas.app.data.DuplicatePhoneException
import br.vendas.app.data.DuplicateProductException
import br.vendas.app.data.PurchaseHasPaidInstallmentsException
import br.vendas.app.data.SalesRepository
import br.vendas.app.data.SalesSnapshot
import br.vendas.app.data.SyncStatus
import br.vendas.app.domain.Customer
import br.vendas.app.domain.CustomerBalance
import br.vendas.app.domain.CustomerInput
import br.vendas.app.domain.Installment
import br.vendas.app.domain.InstallmentPlan
import br.vendas.app.domain.Money
import br.vendas.app.domain.NewPurchase
import br.vendas.app.domain.NewPurchaseItem
import br.vendas.app.domain.OpenInstallment
import br.vendas.app.domain.PaymentType
import br.vendas.app.domain.Product
import br.vendas.app.domain.Purchase
import br.vendas.app.domain.PurchaseItem
import br.vendas.app.domain.PurchaseSource
import br.vendas.app.domain.SHOP_ZONE
import br.vendas.app.domain.sum
import br.vendas.app.domain.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * In-memory data used when Supabase is not configured. Starts with sample
 * customers and purchases; everything is lost when the app closes.
 */
class DemoRepository : SalesRepository {

    private val mutex = Mutex()
    private val _changes = MutableStateFlow(0)
    override val changes: StateFlow<Int> = _changes.asStateFlow()
    override val syncStatus: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus()).asStateFlow()

    private var data = SalesSnapshot.EMPTY

    init {
        seed()
    }

    override suspend fun refresh() = Unit

    override suspend fun clearLocalData() = Unit

    override suspend fun customerBalances(): List<CustomerBalance> = read { it.customerBalances(today()) }

    override suspend fun customer(id: String): Customer = read { it.customer(id) }

    override suspend fun customerBalance(id: String): CustomerBalance = read { it.customerBalance(id, today()) }

    override suspend fun createCustomer(input: CustomerInput): Customer = write {
        ensurePhoneFree(input.phone, exceptId = null)
        val customer = Customer(newId(), input.name.trim(), input.phone, input.notes)
        data = data.copy(customers = data.customers + CustomerRecord(customer, active = true))
        customer
    }

    override suspend fun updateCustomer(id: String, input: CustomerInput) = write {
        ensurePhoneFree(input.phone, exceptId = id)
        data = data.copy(
            customers = data.customers.map {
                if (it.customer.id == id) it.copy(customer = Customer(id, input.name.trim(), input.phone, input.notes)) else it
            },
        )
    }

    override suspend fun deactivateCustomer(id: String) = write {
        data = data.copy(customers = data.customers.map { if (it.customer.id == id) it.copy(active = false) else it })
    }

    override suspend fun products(includeInactive: Boolean): List<Product> = read { it.products(includeInactive) }

    override suspend fun createProduct(name: String, price: Money): Product = write {
        ensureProductNameFree(name, exceptId = null)
        val product = Product(newId(), name.trim(), price, active = true)
        data = data.copy(products = data.products + product)
        product
    }

    override suspend fun updateProduct(id: String, name: String, price: Money, active: Boolean) = write {
        if (active) ensureProductNameFree(name, exceptId = id)
        data = data.copy(products = data.products.map { if (it.id == id) Product(id, name.trim(), price, active) else it })
    }

    override suspend fun purchasesOfCustomer(customerId: String): List<Purchase> = read { it.purchasesOf(customerId) }

    override suspend fun purchase(id: String): Purchase = read { it.purchase(id) }

    override suspend fun createPurchase(input: NewPurchase): String = write { addPurchase(input, PurchaseSource.APP).id }

    override suspend fun setInstallmentPaid(installmentId: String, paid: Boolean) = write {
        val paidAt = if (paid) OffsetDateTime.now(SHOP_ZONE) else null
        data = data.copy(
            purchases = data.purchases.map { purchase ->
                purchase.copy(installments = purchase.installments.map { if (it.id == installmentId) it.copy(paidAt = paidAt) else it })
            },
        )
    }

    override suspend fun cancelPurchase(purchaseId: String) = write {
        val purchase = data.purchase(purchaseId)
        if (purchase.hasPaidInstallments) throw PurchaseHasPaidInstallmentsException()
        val canceled = purchase.copy(canceledAt = OffsetDateTime.now(SHOP_ZONE))
        data = data.copy(purchases = data.purchases.map { if (it.id == purchaseId) canceled else it })
    }

    override suspend fun openInstallmentsDueUntil(until: LocalDate): List<OpenInstallment> =
        read { it.openInstallmentsDueUntil(until) }

    // --- helpers ---

    private suspend fun <T> read(block: (SalesSnapshot) -> T): T = mutex.withLock { block(data) }

    private suspend fun <T> write(block: () -> T): T {
        val result = mutex.withLock(action = block)
        _changes.value += 1
        return result
    }

    private fun ensurePhoneFree(phone: String, exceptId: String?) {
        val owner = data.customers.firstOrNull { it.active && it.customer.phone == phone && it.customer.id != exceptId }
        if (owner != null) throw DuplicatePhoneException(owner.customer.name)
    }

    private fun ensureProductNameFree(name: String, exceptId: String?) {
        val taken = data.products.any { it.active && it.id != exceptId && it.name.equals(name.trim(), ignoreCase = true) }
        if (taken) throw DuplicateProductException()
    }

    private fun addPurchase(input: NewPurchase, source: PurchaseSource): Purchase {
        require(input.items.isNotEmpty()) { "A purchase needs at least one item" }
        val purchaseId = newId()
        val items = input.items.map { item ->
            val product = data.products.first { it.id == item.productId && it.active }
            PurchaseItem(newId(), product.id, product.name, item.quantity, product.price, product.price * item.quantity)
        }
        val total = items.map { it.subtotal }.sum()
        val installments = if (input.installmentCount > 1) {
            val firstDue = input.firstDueDate ?: InstallmentPlan.defaultFirstDueDate(input.purchaseDate)
            InstallmentPlan.calculate(total, input.installmentCount, firstDue).map {
                Installment(newId(), purchaseId, it.number, it.amount, it.dueDate, paidAt = null)
            }
        } else {
            emptyList()
        }
        val purchase = Purchase(
            id = purchaseId,
            customerId = input.customerId,
            purchaseDate = input.purchaseDate,
            paymentType = if (input.installmentCount > 1) PaymentType.INSTALLMENTS else PaymentType.CASH,
            installmentCount = input.installmentCount,
            total = total,
            source = source,
            canceledAt = null,
            notes = input.notes,
            items = items,
            installments = installments,
        )
        data = data.copy(purchases = data.purchases + purchase)
        return purchase
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun seed() {
        val camiseta = Product(newId(), "Camiseta", Money(6000), active = true)
        val bone = Product(newId(), "Boné", Money(3500), active = true)
        val caneca = Product(newId(), "Caneca", Money(2500), active = true)
        val maria = Customer(newId(), "Maria Silva", "+5511999998888", null)
        val joao = Customer(newId(), "João Souza", "+5511988887777", "Prefere pagar no dia 10")
        val ana = Customer(newId(), "Ana Lima", "+5521977776666", null)
        data = SalesSnapshot(
            customers = listOf(maria, joao, ana).map { CustomerRecord(it, active = true) },
            products = listOf(camiseta, bone, caneca),
            purchases = emptyList(),
        )

        val today = today()
        val mariaPurchase = addPurchase(
            NewPurchase(maria.id, listOf(NewPurchaseItem(camiseta.id, 1)), 3, today.minusMonths(1).minusDays(5), null, null),
            PurchaseSource.APP,
        )
        // Maria already paid her first installment.
        val firstInstallment = mariaPurchase.installments.first().id
        data = data.copy(
            purchases = data.purchases.map { purchase ->
                purchase.copy(
                    installments = purchase.installments.map {
                        if (it.id == firstInstallment) it.copy(paidAt = OffsetDateTime.now(SHOP_ZONE).minusDays(6)) else it
                    },
                )
            },
        )
        addPurchase(
            NewPurchase(joao.id, listOf(NewPurchaseItem(bone.id, 1)), 1, today.minusDays(3), null, null),
            PurchaseSource.APP,
        )
        addPurchase(
            NewPurchase(
                ana.id,
                listOf(NewPurchaseItem(caneca.id, 2), NewPurchaseItem(bone.id, 1)),
                2,
                today.minusMonths(2),
                null,
                "Presente de aniversário",
            ),
            PurchaseSource.WHATSAPP,
        )
    }
}

/** Demo mode needs no login. */
class DemoAuthGateway : AuthGateway {
    private val _state = MutableStateFlow(AuthState.SIGNED_IN)
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    override suspend fun signIn(email: String, password: String) {
        _state.value = AuthState.SIGNED_IN
    }

    override suspend fun signOut() {
        _state.value = AuthState.SIGNED_OUT
    }
}
