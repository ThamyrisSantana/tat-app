package br.vendas.app.domain

import java.time.LocalDate

/** An installment that can still receive money, in the order the shop wants it paid. */
data class AllocationCandidate(
    val installmentId: String,
    val remaining: Money,
    val dueDate: LocalDate,
    /** Position of its purchase in creation order (older purchases first on the same due date). */
    val purchaseOrder: Int,
    val number: Int,
)

/**
 * Spreads a payment over open installments. Mirrors public.register_payment in
 * the database (the source of truth); used by the demo mode and the preview.
 */
object PaymentAllocator {

    /**
     * Pays [preferredInstallmentId] first (when given), then the others by due
     * date. Paying more than one installment covers part of the next; paying
     * less leaves the installment partly open.
     *
     * @throws PaymentExceedsOpenAmountException when [amount] is more than what is open.
     */
    fun allocate(
        amount: Money,
        candidates: List<AllocationCandidate>,
        preferredInstallmentId: String? = null,
    ): List<PaymentAllocation> {
        require(amount > Money.ZERO) { "amount must be positive" }
        val open = candidates.filter { it.remaining > Money.ZERO }
        val totalOpen = open.map { it.remaining }.sum()
        if (amount > totalOpen) throw PaymentExceedsOpenAmountException(totalOpen)

        val ordered = open.sortedWith(
            compareByDescending<AllocationCandidate> { it.installmentId == preferredInstallmentId }
                .thenBy { it.dueDate }
                .thenBy { it.purchaseOrder }
                .thenBy { it.number },
        )
        var left = amount
        val allocations = mutableListOf<PaymentAllocation>()
        for (candidate in ordered) {
            if (left <= Money.ZERO) break
            val take = if (candidate.remaining < left) candidate.remaining else left
            allocations += PaymentAllocation(candidate.installmentId, take)
            left -= take
        }
        return allocations
    }
}
