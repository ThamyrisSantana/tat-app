package br.vendas.app.data

import br.vendas.app.domain.AllocationCandidate
import br.vendas.app.domain.Customer
import br.vendas.app.domain.CustomerBalance
import br.vendas.app.domain.MonthDue
import br.vendas.app.domain.OpenInstallment
import br.vendas.app.domain.Payment
import br.vendas.app.domain.Product
import br.vendas.app.domain.Purchase
import br.vendas.app.domain.sum
import java.time.LocalDate
import java.time.YearMonth

/** A customer plus whether they are still active (removed customers keep their history). */
data class CustomerRecord(val customer: Customer, val active: Boolean)

/**
 * All the shop's data at one moment. The demo mode keeps one in memory and the
 * offline cache rebuilds one from the local database; both answer the screens'
 * questions with the same rules here, which mirror the database view
 * public.customer_balances.
 */
data class SalesSnapshot(
    val customers: List<CustomerRecord>,
    val products: List<Product>,
    /** In creation order. */
    val purchases: List<Purchase>,
    val payments: List<Payment> = emptyList(),
) {
    fun customerBalances(today: LocalDate): List<CustomerBalance> =
        customers.filter { it.active }
            .map { balanceOf(it.customer, today) }
            .sortedBy { it.name.lowercase() }

    fun customer(id: String): Customer =
        customers.firstOrNull { it.customer.id == id }?.customer ?: throw NotFoundException()

    fun customerBalance(id: String, today: LocalDate): CustomerBalance = balanceOf(customer(id), today)

    val customerNames: Map<String, String> get() = customers.associate { it.customer.id to it.customer.name }

    fun products(includeInactive: Boolean): List<Product> =
        products.filter { includeInactive || it.active }.sortedBy { it.name.lowercase() }

    fun purchasesOf(customerId: String): List<Purchase> =
        purchases.withIndex()
            .filter { it.value.customerId == customerId }
            .sortedWith(compareByDescending<IndexedValue<Purchase>> { it.value.purchaseDate }.thenByDescending { it.index })
            .map { it.value }

    fun purchase(id: String): Purchase = purchases.firstOrNull { it.id == id } ?: throw NotFoundException()

    /** Payments of a customer, newest first. */
    fun paymentsOf(customerId: String): List<Payment> =
        payments.filter { it.customerId == customerId }.sortedByDescending { it.paidOn }

    /** Payments that paid at least part of the purchase's installments, newest first. */
    fun paymentsOfPurchase(purchaseId: String): List<Payment> {
        val installmentIds = purchase(purchaseId).installments.map { it.id }.toSet()
        return payments.filter { payment -> payment.allocations.any { it.installmentId in installmentIds } }
            .sortedByDescending { it.paidOn }
    }

    /** Installments that can receive a payment from [customerId] (only [purchaseId]'s when given). */
    fun allocationCandidates(customerId: String, purchaseId: String?): List<AllocationCandidate> =
        purchases.withIndex()
            .filter { (_, p) -> p.customerId == customerId && !p.isCanceled && (purchaseId == null || p.id == purchaseId) }
            .flatMap { (order, p) ->
                p.installments.filterNot { it.isPaid }
                    .map { AllocationCandidate(it.id, it.remaining, it.dueDate, order, it.number) }
            }

    fun openInstallmentsDueUntil(until: LocalDate): List<OpenInstallment> {
        val names = customerNames
        return purchases.filterNot { it.isCanceled }
            .flatMap { purchase ->
                purchase.installments
                    .filter { !it.isPaid && !it.dueDate.isAfter(until) }
                    .map {
                        OpenInstallment(
                            installment = it,
                            customerId = purchase.customerId,
                            customerName = names[purchase.customerId].orEmpty(),
                            installmentCount = purchase.installmentCount,
                        )
                    }
            }
            .sortedBy { it.installment.dueDate }
    }

    private fun balanceOf(customer: Customer, today: LocalDate): CustomerBalance {
        val open = purchases
            .filter { it.customerId == customer.id && !it.isCanceled }
            .flatMap { it.installments }
            .filterNot { it.isPaid }
        val overdue = open.filter { it.isOverdue(today) }
        val coming = open.filterNot { it.isOverdue(today) }
        val firstMonth = coming.minOfOrNull { YearMonth.from(it.dueDate) }
        return CustomerBalance(
            customerId = customer.id,
            name = customer.name,
            phone = customer.phone,
            openAmount = open.map { it.remaining }.sum(),
            overdueAmount = overdue.map { it.remaining }.sum(),
            nextDueDate = open.minOfOrNull { it.dueDate },
            oldestOverdueDate = overdue.minOfOrNull { it.dueDate },
            monthlyDue = firstMonth?.let { month ->
                MonthDue(month, coming.filter { YearMonth.from(it.dueDate) == month }.map { it.remaining }.sum())
            },
        )
    }

    companion object {
        val EMPTY = SalesSnapshot(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

class NotFoundException : Exception("Record not found")
