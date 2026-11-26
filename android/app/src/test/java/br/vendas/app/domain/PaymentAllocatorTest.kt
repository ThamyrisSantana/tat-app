package br.vendas.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PaymentAllocatorTest {

    private val first = AllocationCandidate("i1", Money(2000), LocalDate.of(2026, 12, 10), purchaseOrder = 0, number = 1)
    private val second = AllocationCandidate("i2", Money(2000), LocalDate.of(2027, 1, 10), purchaseOrder = 0, number = 2)
    private val third = AllocationCandidate("i3", Money(2000), LocalDate.of(2027, 2, 10), purchaseOrder = 0, number = 3)
    private val all = listOf(third, first, second)

    @Test
    fun `exact installment amount pays the oldest one`() {
        assertEquals(listOf(PaymentAllocation("i1", Money(2000))), PaymentAllocator.allocate(Money(2000), all))
    }

    @Test
    fun `paying more covers part of the next installment`() {
        assertEquals(
            listOf(PaymentAllocation("i1", Money(2000)), PaymentAllocation("i2", Money(500))),
            PaymentAllocator.allocate(Money(2500), all),
        )
    }

    @Test
    fun `paying less leaves the installment partly open`() {
        assertEquals(listOf(PaymentAllocation("i1", Money(1200))), PaymentAllocator.allocate(Money(1200), all))
    }

    @Test
    fun `partly paid installments receive only what is left`() {
        val partly = first.copy(remaining = Money(800))
        assertEquals(
            listOf(PaymentAllocation("i1", Money(800)), PaymentAllocation("i2", Money(1200))),
            PaymentAllocator.allocate(Money(2000), listOf(partly, second, third)),
        )
    }

    @Test
    fun `the chosen installment is paid first`() {
        assertEquals(
            listOf(PaymentAllocation("i3", Money(2000)), PaymentAllocation("i1", Money(1000))),
            PaymentAllocator.allocate(Money(3000), all, preferredInstallmentId = "i3"),
        )
    }

    @Test
    fun `older purchases come first on the same due date`() {
        val newer = AllocationCandidate("n1", Money(1000), first.dueDate, purchaseOrder = 1, number = 1)
        assertEquals(
            listOf(PaymentAllocation("i1", Money(2000)), PaymentAllocation("n1", Money(500))),
            PaymentAllocator.allocate(Money(2500), listOf(newer, first)),
        )
    }

    @Test
    fun `paying everything settles all installments`() {
        assertEquals(3, PaymentAllocator.allocate(Money(6000), all).size)
    }

    @Test(expected = PaymentExceedsOpenAmountException::class)
    fun `paying more than what is open is refused`() {
        PaymentAllocator.allocate(Money(6001), all)
    }
}
