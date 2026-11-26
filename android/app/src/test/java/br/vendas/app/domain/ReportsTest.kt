package br.vendas.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth

class ReportsTest {

    private val today = LocalDate.of(2026, 11, 25)

    private fun installment(id: String, amount: Long, due: LocalDate, paid: Long = 0) =
        Installment(id, "p", 1, Money(amount), due, paidAt = null, paidAmount = Money(paid))

    private fun purchase(
        id: String,
        customer: String,
        date: LocalDate,
        items: List<PurchaseItem>,
        installments: List<Installment> = emptyList(),
        canceled: Boolean = false,
    ) = Purchase(
        id = id,
        customerId = customer,
        purchaseDate = date,
        paymentType = if (installments.isEmpty()) PaymentType.CASH else PaymentType.INSTALLMENTS,
        installmentCount = maxOf(1, installments.size),
        total = items.map { it.subtotal }.sum(),
        source = PurchaseSource.APP,
        canceledAt = if (canceled) OffsetDateTime.now() else null,
        notes = null,
        items = items,
        installments = installments,
    )

    private fun item(product: String, quantity: Int, unitCents: Long) =
        PurchaseItem("$product-$quantity", product, product.replaceFirstChar { it.uppercase() }, quantity, Money(unitCents), Money(unitCents * quantity))

    private val purchases = listOf(
        purchase("a", "maria", LocalDate.of(2026, 11, 2), listOf(item("camiseta", 2, 6000))),
        purchase("b", "joao", LocalDate.of(2026, 11, 10), listOf(item("bone", 1, 3500), item("camiseta", 1, 6000))),
        purchase("c", "maria", LocalDate.of(2026, 10, 5), listOf(item("caneca", 3, 2500))),
        purchase("d", "ana", LocalDate.of(2026, 11, 12), listOf(item("caneca", 10, 2500)), canceled = true),
    )

    @Test
    fun `sales by month ignore canceled purchases`() {
        val months = Reports.salesByMonth(purchases, Reports.monthsUpTo(YearMonth.of(2026, 11), 2))
        assertEquals(
            listOf(
                MonthlySales(YearMonth.of(2026, 10), Money(7500), 1),
                MonthlySales(YearMonth.of(2026, 11), Money(21500), 2),
            ),
            months,
        )
    }

    @Test
    fun `month report ranks products by quantity and customers by value`() {
        val payments = listOf(
            Payment("x", "maria", Money(3000), LocalDate.of(2026, 11, 20), null, emptyList()),
            Payment("y", "maria", Money(1000), LocalDate.of(2026, 10, 20), null, emptyList()),
        )
        val report = Reports.monthReport(
            purchases,
            payments,
            mapOf("maria" to "Maria", "joao" to "João"),
            YearMonth.of(2026, 11),
        )

        assertEquals(Money(21500), report.total)
        assertEquals(2, report.purchaseCount)
        assertEquals(2, report.customerCount)
        assertEquals(Money(10750), report.averageTicket)
        assertEquals(Money(3000), report.received)
        assertEquals(listOf("camiseta", "bone"), report.topProducts.map { it.productId })
        assertEquals(3, report.topProducts.first().quantity)
        assertEquals(listOf("Maria", "João"), report.topCustomers.map { it.name })
    }

    @Test
    fun `receivables split overdue from the coming months and count only what is left`() {
        val withInstallments = purchase(
            "e",
            "maria",
            LocalDate.of(2026, 9, 1),
            listOf(item("camiseta", 1, 6000)),
            listOf(
                installment("1", 2000, LocalDate.of(2026, 10, 1), paid = 2000),
                installment("2", 2000, LocalDate.of(2026, 11, 1), paid = 500),
                installment("3", 2000, LocalDate.of(2026, 12, 1)),
            ),
        )
        val other = purchase(
            "f",
            "joao",
            LocalDate.of(2026, 11, 1),
            listOf(item("bone", 1, 3000)),
            listOf(installment("4", 1500, LocalDate.of(2026, 11, 30)), installment("5", 1500, LocalDate.of(2026, 12, 30))),
        )

        val receivables = Reports.receivables(listOf(withInstallments, other), today)

        assertEquals(Money(1500), receivables.overdue)
        assertEquals(1, receivables.overdueCount)
        assertEquals(Money(1500 + 2000 + 1500 + 1500), receivables.totalOpen)
        assertEquals(
            listOf(
                ReceivableMonth(YearMonth.of(2026, 11), Money(1500), 1),
                ReceivableMonth(YearMonth.of(2026, 12), Money(3500), 2),
            ),
            receivables.months,
        )
    }
}
