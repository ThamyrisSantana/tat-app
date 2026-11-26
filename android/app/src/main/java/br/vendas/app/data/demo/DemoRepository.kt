package br.vendas.app.data.demo

import br.vendas.app.data.AuthGateway
import br.vendas.app.data.AuthState
import br.vendas.app.data.CustomerRecord
import br.vendas.app.data.DuplicatePhoneException
import br.vendas.app.data.DuplicateProductException
import br.vendas.app.data.PurchaseHasPaymentsException
import br.vendas.app.data.SalesRepository
import br.vendas.app.data.SalesSnapshot
import br.vendas.app.data.SyncStatus
import br.vendas.app.domain.Customer
import br.vendas.app.domain.CustomerBalance
import br.vendas.app.domain.CustomerInput
import br.vendas.app.domain.Installment
import br.vendas.app.domain.InstallmentPlan
import br.vendas.app.domain.Money
import br.vendas.app.domain.NewPayment
import br.vendas.app.domain.NewPurchase
import br.vendas.app.domain.NewPurchaseItem
import br.vendas.app.domain.OpenInstallment
import br.vendas.app.domain.Payment
import br.vendas.app.domain.PaymentAllocator
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
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

/**
 * In-memory data used when Supabase is not configured. Starts with six months
 * of sample sales and payments; everything is lost when the app closes.
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

    override suspend fun snapshot(): SalesSnapshot = read { it }

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

    override suspend fun cancelPurchase(purchaseId: String) = write {
        val purchase = data.purchase(purchaseId)
        if (purchase.hasPayments) throw PurchaseHasPaymentsException()
        val canceled = purchase.copy(canceledAt = OffsetDateTime.now(SHOP_ZONE))
        data = data.copy(purchases = data.purchases.map { if (it.id == purchaseId) canceled else it })
    }

    override suspend fun registerPayment(input: NewPayment): String = write { addPayment(input).id }

    override suspend fun deletePayment(paymentId: String) = write {
        val payment = data.payments.first { it.id == paymentId }
        val returned = payment.allocations.associate { it.installmentId to it.amount }
        data = data.copy(
            purchases = data.purchases.map { purchase ->
                purchase.copy(
                    installments = purchase.installments.map { installment ->
                        val amount = returned[installment.id] ?: return@map installment
                        installment.copy(paidAmount = installment.paidAmount - amount, paidAt = null)
                    },
                )
            },
            payments = data.payments.filterNot { it.id == paymentId },
        )
    }

    override suspend fun paymentsOfCustomer(customerId: String): List<Payment> = read { it.paymentsOf(customerId) }

    override suspend fun paymentsOfPurchase(purchaseId: String): List<Payment> = read { it.paymentsOfPurchase(purchaseId) }

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

    private fun addPayment(input: NewPayment): Payment {
        val allocations = PaymentAllocator.allocate(
            amount = input.amount,
            candidates = data.allocationCandidates(input.customerId, input.purchaseId),
            preferredInstallmentId = input.installmentId,
        )
        val received = allocations.associate { it.installmentId to it.amount }
        val paidAt = OffsetDateTime.of(input.paidOn, LocalTime.NOON, SHOP_ZONE.rules.getOffset(input.paidOn.atTime(LocalTime.NOON)))
        val payment = Payment(newId(), input.customerId, input.amount, input.paidOn, input.notes, allocations)
        data = data.copy(
            purchases = data.purchases.map { purchase ->
                purchase.copy(
                    installments = purchase.installments.map { installment ->
                        val amount = received[installment.id] ?: return@map installment
                        val paid = installment.paidAmount + amount
                        installment.copy(paidAmount = paid, paidAt = if (paid >= installment.amount) paidAt else null)
                    },
                )
            },
            payments = data.payments + payment,
        )
        return payment
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun seed() {
        val camiseta = Product(newId(), "Camiseta", Money(6000), active = true)
        val bone = Product(newId(), "Boné", Money(3500), active = true)
        val caneca = Product(newId(), "Caneca", Money(2500), active = true)
        val moletom = Product(newId(), "Moletom", Money(12000), active = true)
        val ecobag = Product(newId(), "Ecobag", Money(3000), active = true)
        val chaveiro = Product(newId(), "Chaveiro", Money(1200), active = true)

        val maria = Customer(newId(), "Maria Silva", "+5511999998888", null)
        val joao = Customer(newId(), "João Souza", "+5511988887777", "Prefere pagar no dia 10")
        val ana = Customer(newId(), "Ana Lima", "+5521977776666", null)
        val pedro = Customer(newId(), "Pedro Alves", "+5531966665555", null)
        val juliana = Customer(newId(), "Juliana Costa", "+5541955554444", null)
        val lucas = Customer(newId(), "Lucas Martins", "+5511944443333", null)
        val fernanda = Customer(newId(), "Fernanda Rocha", "+5521933332222", "Cliente desde 2025")
        data = SalesSnapshot(
            customers = listOf(maria, joao, ana, pedro, juliana, lucas, fernanda).map { CustomerRecord(it, active = true) },
            products = listOf(camiseta, bone, caneca, moletom, ecobag, chaveiro),
            purchases = emptyList(),
        )

        val today = today()
        fun buy(
            customer: Customer,
            date: LocalDate,
            count: Int,
            vararg items: Pair<Product, Int>,
            source: PurchaseSource = PurchaseSource.APP,
            notes: String? = null,
        ) = addPurchase(
            NewPurchase(customer.id, items.map { NewPurchaseItem(it.first.id, it.second) }, count, date, null, notes),
            source,
        )
        fun pay(customer: Customer, cents: Long, date: LocalDate) {
            addPayment(NewPayment(customer.id, Money(cents), date))
        }

        // Lucas pays every installment on its due date.
        val lucasPurchase = buy(lucas, today.minusMonths(5).minusDays(2), 6, moletom to 2)
        buy(fernanda, today.minusMonths(5).plusDays(6), 1, bone to 2)
        buy(maria, today.minusMonths(4).minusDays(4), 1, ecobag to 1)
        // Pedro paid less than the third installment and nothing since.
        buy(pedro, today.minusMonths(4).minusDays(10), 4, moletom to 1, camiseta to 1)
        buy(joao, today.minusMonths(3).minusDays(1), 1, moletom to 1)
        buy(fernanda, today.minusMonths(2).minusDays(3), 1, chaveiro to 5)
        // Ana has not paid her first installment yet.
        buy(
            ana,
            today.minusMonths(2),
            2,
            caneca to 2,
            bone to 1,
            source = PurchaseSource.WHATSAPP,
            notes = "Presente de aniversário",
        )
        buy(juliana, today.minusMonths(1).minusDays(12), 1, ecobag to 2, chaveiro to 3)
        buy(maria, today.minusMonths(1).minusDays(5), 3, camiseta to 1)
        buy(juliana, today.minusDays(20), 2, caneca to 4)
        buy(fernanda, today.minusDays(15), 3, camiseta to 3, source = PurchaseSource.WHATSAPP)
        buy(joao, today.minusDays(3), 1, bone to 1)

        lucasPurchase.installments.filter { !it.dueDate.isAfter(today) }.forEach {
            pay(lucas, it.amount.cents, it.dueDate)
        }
        pay(pedro, 4500, today.minusMonths(3).minusDays(12))
        pay(pedro, 4500, today.minusMonths(2).minusDays(8))
        pay(pedro, 3000, today.minusMonths(1).minusDays(5))
        pay(maria, 2000, today.minusDays(6))
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
