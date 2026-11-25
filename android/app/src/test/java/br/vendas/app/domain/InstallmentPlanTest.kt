package br.vendas.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InstallmentPlanTest {

    @Test
    fun `splits evenly and puts the leftover cents in the last installment`() {
        val plan = InstallmentPlan.calculate(Money(10_000), 3, LocalDate.of(2026, 12, 10))

        assertEquals(listOf(3333L, 3333L, 3334L), plan.map { it.amount.cents })
        assertEquals(Money(10_000), plan.map { it.amount }.sum())
    }

    @Test
    fun `even totals have equal installments`() {
        val plan = InstallmentPlan.calculate(Money(6_000), 3, LocalDate.of(2026, 12, 10))

        assertEquals(listOf(2000L, 2000L, 2000L), plan.map { it.amount.cents })
    }

    @Test
    fun `due dates fall on the same day of the following months`() {
        val plan = InstallmentPlan.calculate(Money(6_000), 3, LocalDate.of(2026, 12, 10))

        assertEquals(
            listOf(LocalDate.of(2026, 12, 10), LocalDate.of(2027, 1, 10), LocalDate.of(2027, 2, 10)),
            plan.map { it.dueDate },
        )
        assertEquals(listOf(1, 2, 3), plan.map { it.number })
    }

    @Test
    fun `day 31 is clamped to the last day of shorter months`() {
        val plan = InstallmentPlan.calculate(Money(4_000), 4, LocalDate.of(2027, 1, 31))

        assertEquals(
            listOf(
                LocalDate.of(2027, 1, 31),
                LocalDate.of(2027, 2, 28),
                LocalDate.of(2027, 3, 31),
                LocalDate.of(2027, 4, 30),
            ),
            plan.map { it.dueDate },
        )
    }

    @Test
    fun `default first due date is one month after the purchase`() {
        assertEquals(LocalDate.of(2026, 12, 25), InstallmentPlan.defaultFirstDueDate(LocalDate.of(2026, 11, 25)))
        assertEquals(LocalDate.of(2027, 2, 28), InstallmentPlan.defaultFirstDueDate(LocalDate.of(2027, 1, 31)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a single installment`() {
        InstallmentPlan.calculate(Money(1_000), 1, LocalDate.of(2026, 12, 10))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects more than 24 installments`() {
        InstallmentPlan.calculate(Money(100_000), 25, LocalDate.of(2026, 12, 10))
    }
}
