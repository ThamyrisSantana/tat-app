package br.vendas.app.data.local

import br.vendas.app.data.CustomerRecord
import br.vendas.app.data.SalesSnapshot
import br.vendas.app.domain.Customer
import br.vendas.app.domain.Installment
import br.vendas.app.domain.Money
import br.vendas.app.domain.Payment
import br.vendas.app.domain.PaymentAllocation
import br.vendas.app.domain.PaymentType
import br.vendas.app.domain.Product
import br.vendas.app.domain.Purchase
import br.vendas.app.domain.PurchaseItem
import br.vendas.app.domain.PurchaseSource
import java.time.LocalDate
import java.time.OffsetDateTime

fun LocalData.toSnapshot(): SalesSnapshot {
    val itemsByPurchase = items.groupBy { it.purchaseId }
    val installmentsByPurchase = installments.groupBy { it.purchaseId }
    val allocationsByPayment = allocations.groupBy { it.paymentId }
    return SalesSnapshot(
        customers = customers.map { CustomerRecord(Customer(it.id, it.name, it.phone, it.notes), it.active) },
        products = products.map { Product(it.id, it.name, Money(it.priceCents), it.active) },
        purchases = purchases.map { purchase ->
            Purchase(
                id = purchase.id,
                customerId = purchase.customerId,
                purchaseDate = LocalDate.parse(purchase.purchaseDate),
                paymentType = PaymentType.valueOf(purchase.paymentType),
                installmentCount = purchase.installmentCount,
                total = Money(purchase.totalCents),
                source = PurchaseSource.valueOf(purchase.source),
                canceledAt = purchase.canceledAt?.let(OffsetDateTime::parse),
                notes = purchase.notes,
                items = itemsByPurchase[purchase.id].orEmpty().map {
                    PurchaseItem(it.id, it.productId, it.productName, it.quantity, Money(it.unitPriceCents), Money(it.subtotalCents))
                },
                installments = installmentsByPurchase[purchase.id].orEmpty().map {
                    Installment(
                        id = it.id,
                        purchaseId = it.purchaseId,
                        number = it.number,
                        amount = Money(it.amountCents),
                        dueDate = LocalDate.parse(it.dueDate),
                        paidAt = it.paidAt?.let(OffsetDateTime::parse),
                        paidAmount = Money(it.paidAmountCents),
                    )
                },
            )
        },
        payments = payments.map { payment ->
            Payment(
                id = payment.id,
                customerId = payment.customerId,
                amount = Money(payment.amountCents),
                paidOn = LocalDate.parse(payment.paidOn),
                notes = payment.notes,
                allocations = allocationsByPayment[payment.id].orEmpty().map {
                    PaymentAllocation(it.installmentId, Money(it.amountCents))
                },
            )
        },
    )
}

fun SalesSnapshot.toLocalData(): LocalData = LocalData(
    customers = customers.map {
        CustomerEntity(it.customer.id, it.customer.name, it.customer.phone, it.customer.notes, it.active)
    },
    products = products.map { ProductEntity(it.id, it.name, it.price.cents, it.active) },
    purchases = purchases.mapIndexed { index, it ->
        PurchaseEntity(
            id = it.id,
            customerId = it.customerId,
            purchaseDate = it.purchaseDate.toString(),
            paymentType = it.paymentType.name,
            installmentCount = it.installmentCount,
            totalCents = it.total.cents,
            source = it.source.name,
            canceledAt = it.canceledAt?.toString(),
            notes = it.notes,
            sortOrder = index,
        )
    },
    items = purchases.flatMap { purchase ->
        purchase.items.map {
            PurchaseItemEntity(it.id, purchase.id, it.productId, it.productName, it.quantity, it.unitPrice.cents, it.subtotal.cents)
        }
    },
    installments = purchases.flatMap { purchase ->
        purchase.installments.map {
            InstallmentEntity(
                id = it.id,
                purchaseId = purchase.id,
                number = it.number,
                amountCents = it.amount.cents,
                dueDate = it.dueDate.toString(),
                paidAt = it.paidAt?.toString(),
                paidAmountCents = it.paidAmount.cents,
            )
        }
    },
    payments = payments.map { PaymentEntity(it.id, it.customerId, it.amount.cents, it.paidOn.toString(), it.notes) },
    allocations = payments.flatMap { payment ->
        payment.allocations.map { PaymentAllocationEntity(payment.id, it.installmentId, it.amount.cents) }
    },
)
