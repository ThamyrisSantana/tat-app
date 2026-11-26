package br.vendas.app.domain

import java.time.LocalDate
import java.time.YearMonth

data class MonthlySales(val month: YearMonth, val total: Money, val count: Int)

data class ProductSales(val productId: String, val name: String, val quantity: Int, val total: Money)

data class CustomerSales(val customerId: String, val name: String, val total: Money, val purchaseCount: Int)

/** Sales and money received in one month. */
data class MonthReport(
    val month: YearMonth,
    val total: Money,
    val purchaseCount: Int,
    /** Distinct customers who bought in the month. */
    val customerCount: Int,
    val averageTicket: Money,
    val cashTotal: Money,
    val installmentsTotal: Money,
    /** Payments received in the month (from any purchase). */
    val received: Money,
    val topProducts: List<ProductSales>,
    val topCustomers: List<CustomerSales>,
)

/** What is still to be received in one month. */
data class ReceivableMonth(val month: YearMonth, val amount: Money, val installmentCount: Int)

data class Receivables(
    val totalOpen: Money,
    val overdue: Money,
    val overdueCount: Int,
    /** From the current month on, one entry per month with open installments. */
    val months: List<ReceivableMonth>,
)

/**
 * Figures for the dashboard, receivables and reports screens. Canceled
 * purchases are left out everywhere.
 */
object Reports {

    fun salesByMonth(purchases: List<Purchase>, months: List<YearMonth>): List<MonthlySales> {
        val byMonth = purchases.filterNot { it.isCanceled }.groupBy { YearMonth.from(it.purchaseDate) }
        return months.map { month ->
            val inMonth = byMonth[month].orEmpty()
            MonthlySales(month, inMonth.map { it.total }.sum(), inMonth.size)
        }
    }

    fun monthReport(
        purchases: List<Purchase>,
        payments: List<Payment>,
        customerNames: Map<String, String>,
        month: YearMonth,
        topCount: Int = 5,
    ): MonthReport {
        val inMonth = purchases.filter { !it.isCanceled && YearMonth.from(it.purchaseDate) == month }
        val total = inMonth.map { it.total }.sum()
        val topProducts = inMonth.flatMap { it.items }
            .groupBy { it.productId }
            .map { (productId, items) ->
                ProductSales(productId, items.last().productName, items.sumOf { it.quantity }, items.map { it.subtotal }.sum())
            }
            .sortedWith(compareByDescending<ProductSales> { it.quantity }.thenByDescending { it.total })
            .take(topCount)
        val topCustomers = inMonth.groupBy { it.customerId }
            .map { (customerId, list) ->
                CustomerSales(customerId, customerNames[customerId].orEmpty(), list.map { it.total }.sum(), list.size)
            }
            .sortedByDescending { it.total }
            .take(topCount)
        return MonthReport(
            month = month,
            total = total,
            purchaseCount = inMonth.size,
            customerCount = inMonth.map { it.customerId }.distinct().size,
            averageTicket = if (inMonth.isEmpty()) Money.ZERO else Money(total.cents / inMonth.size),
            cashTotal = inMonth.filter { it.paymentType == PaymentType.CASH }.map { it.total }.sum(),
            installmentsTotal = inMonth.filter { it.paymentType == PaymentType.INSTALLMENTS }.map { it.total }.sum(),
            received = payments.filter { YearMonth.from(it.paidOn) == month }.map { it.amount }.sum(),
            topProducts = topProducts,
            topCustomers = topCustomers,
        )
    }

    fun receivables(purchases: List<Purchase>, today: LocalDate): Receivables {
        val open = purchases.filterNot { it.isCanceled }.flatMap { it.installments }.filterNot { it.isPaid }
        val overdue = open.filter { it.isOverdue(today) }
        // Installments not overdue are due today or later, so they fall in this month or after.
        val months = open.filterNot { it.isOverdue(today) }
            .groupBy { YearMonth.from(it.dueDate) }
            .map { (month, list) -> ReceivableMonth(month, list.map { it.remaining }.sum(), list.size) }
            .sortedBy { it.month }
        return Receivables(
            totalOpen = open.map { it.remaining }.sum(),
            overdue = overdue.map { it.remaining }.sum(),
            overdueCount = overdue.size,
            months = months,
        )
    }

    /** The last [count] months up to and including [last], oldest first. */
    fun monthsUpTo(last: YearMonth, count: Int): List<YearMonth> = (count - 1 downTo 0).map { last.minusMonths(it.toLong()) }
}
