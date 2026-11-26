package br.vendas.app.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe destinations: the compiler checks every argument a screen needs.

@Serializable
data object DashboardRoute

/** [overdueOnly] opens the list already filtered to late customers. */
@Serializable
data class CustomersRoute(val overdueOnly: Boolean = false)

@Serializable
data object ReceivablesRoute

@Serializable
data object ReportsRoute

@Serializable
data object ProductsRoute

@Serializable
data object SettingsRoute

@Serializable
data class CustomerRoute(val customerId: String)

/** New customer when [customerId] is null, edit otherwise. */
@Serializable
data class CustomerFormRoute(val customerId: String? = null)

@Serializable
/** Without a customer the screen starts by asking who is buying. */
data class NewPurchaseRoute(val customerId: String? = null)

@Serializable
data class PurchaseRoute(val purchaseId: String)

@Serializable
data class ReceiptRoute(val purchaseId: String)
