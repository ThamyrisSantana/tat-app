package br.vendas.app.data

import br.vendas.app.domain.Customer
import br.vendas.app.domain.CustomerBalance
import br.vendas.app.domain.OpenInstallment
import br.vendas.app.domain.Product
import br.vendas.app.domain.Purchase
import br.vendas.app.domain.sum
import java.time.LocalDate

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
    val purchases: List<Purchase>,
) {
    fun customerBalances(today: LocalDate): List<CustomerBalance> =
        customers.filter { it.active }
            .map { balanceOf(it.customer, today) }
            .sortedBy { it.name.lowercase() }

    fun customer(id: String): Customer =
        customers.firstOrNull { it.customer.id == id }?.customer ?: throw NotFoundException()

    fun customerBalance(id: String, today: LocalDate): CustomerBalance = balanceOf(customer(id), today)

    fun products(includeInactive: Boolean): List<Product> =
        products.filter { includeInactive || it.active }.sortedBy { it.name.lowercase() }

    fun purchasesOf(customerId: String): List<Purchase> =
        purchases.filter { it.customerId == customerId }
            .sortedWith(compareByDescending<Purchase> { it.purchaseDate }.thenByDescending { it.createdOrder })

    fun purchase(id: String): Purchase = purchases.firstOrNull { it.id == id } ?: throw NotFoundException()

    fun openInstallmentsDueUntil(until: LocalDate): List<OpenInstallment> {
        val names = customers.associate { it.customer.id to it.customer.name }
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
        return CustomerBalance(
            customerId = customer.id,
            name = customer.name,
            phone = customer.phone,
            openAmount = open.map { it.amount }.sum(),
            overdueAmount = open.filter { it.isOverdue(today) }.map { it.amount }.sum(),
            nextDueDate = open.minOfOrNull { it.dueDate },
        )
    }

    // Purchases on the same day keep the order they were added in.
    private val Purchase.createdOrder: Int get() = purchases.indexOf(this)

    companion object {
        val EMPTY = SalesSnapshot(emptyList(), emptyList(), emptyList())
    }
}

class NotFoundException : Exception("Record not found")
