package br.vendas.app.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe destinations: the compiler checks every argument a screen needs.

@Serializable
data object CustomersRoute

@Serializable
data object ProductsRoute

@Serializable
data object SummaryRoute

@Serializable
data class CustomerRoute(val customerId: String)

/** New customer when [customerId] is null, edit otherwise. */
@Serializable
data class CustomerFormRoute(val customerId: String? = null)

@Serializable
data class NewPurchaseRoute(val customerId: String)

@Serializable
data class PurchaseRoute(val purchaseId: String)
