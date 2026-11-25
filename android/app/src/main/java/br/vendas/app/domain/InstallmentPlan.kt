package br.vendas.app.domain

import java.time.LocalDate

data class PlannedInstallment(
    val number: Int,
    val amount: Money,
    val dueDate: LocalDate,
)

/**
 * Splits a purchase into installments. Mirrors public.create_purchase in the
 * database, which is the source of truth; this copy is used for the preview
 * on the New purchase screen and by the demo repository.
 */
object InstallmentPlan {

    const val MIN_INSTALLMENTS = 2
    const val MAX_INSTALLMENTS = 24

    /** The first installment is due on the same day of the next month. */
    fun defaultFirstDueDate(purchaseDate: LocalDate): LocalDate = purchaseDate.plusMonths(1)

    /**
     * Each installment is total / count rounded down to the cent; the last one
     * takes the difference. Due dates fall on the same day of the following
     * months, clamped to the last day of shorter months (Jan 31 -> Feb 28).
     */
    fun calculate(total: Money, count: Int, firstDueDate: LocalDate): List<PlannedInstallment> {
        require(count in MIN_INSTALLMENTS..MAX_INSTALLMENTS) { "count must be 2..24, was $count" }
        require(total.cents >= count) { "total is too small for $count installments" }

        val base = total.cents / count
        return (1..count).map { number ->
            val cents = if (number == count) total.cents - base * (count - 1) else base
            PlannedInstallment(
                number = number,
                amount = Money(cents),
                dueDate = firstDueDate.plusMonths((number - 1).toLong()),
            )
        }
    }
}
