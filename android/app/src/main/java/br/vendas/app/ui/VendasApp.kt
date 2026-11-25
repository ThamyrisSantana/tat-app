package br.vendas.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import br.vendas.app.data.AuthGateway
import br.vendas.app.data.AuthState
import br.vendas.app.data.SalesRepository
import br.vendas.app.data.SyncStatus
import br.vendas.app.di.AppConfig
import br.vendas.app.domain.SHOP_ZONE
import br.vendas.app.ui.common.LoadingBox
import br.vendas.app.ui.customers.CustomerDetailScreen
import br.vendas.app.ui.customers.CustomerFormScreen
import br.vendas.app.ui.customers.CustomerListScreen
import br.vendas.app.ui.login.LoginScreen
import br.vendas.app.ui.navigation.CustomerFormRoute
import br.vendas.app.ui.navigation.CustomerRoute
import br.vendas.app.ui.navigation.CustomersRoute
import br.vendas.app.ui.navigation.NewPurchaseRoute
import br.vendas.app.ui.navigation.ProductsRoute
import br.vendas.app.ui.navigation.PurchaseRoute
import br.vendas.app.ui.navigation.SummaryRoute
import br.vendas.app.ui.products.ProductListScreen
import br.vendas.app.ui.purchases.NewPurchaseScreen
import br.vendas.app.ui.purchases.PurchaseDetailScreen
import br.vendas.app.ui.summary.SummaryScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.reflect.KClass

@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: AuthGateway,
    private val repository: SalesRepository,
    config: AppConfig,
) : ViewModel() {
    val isDemo = config.isDemo
    val authState = auth.state
    val syncStatus = repository.syncStatus

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            // The saved copy belongs to the signed-in shop: do not leave it on the phone.
            repository.clearLocalData()
        }
    }
}

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector)

private val TOP_LEVEL = listOf(
    TopLevel(CustomersRoute, CustomersRoute::class, "Clientes", Icons.Filled.Person),
    TopLevel(ProductsRoute, ProductsRoute::class, "Produtos", Icons.Filled.ShoppingCart),
    TopLevel(SummaryRoute, SummaryRoute::class, "Resumo", Icons.Filled.Home),
)

@Composable
fun VendasApp(viewModel: RootViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    when (authState) {
        AuthState.LOADING -> LoadingBox()
        AuthState.SIGNED_OUT -> LoginScreen()
        AuthState.SIGNED_IN -> MainNavigation(viewModel)
    }
}

@Composable
private fun MainNavigation(root: RootViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val syncStatus by root.syncStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { StatusBanners(isDemo = root.isDemo, syncStatus = syncStatus) },
        bottomBar = {
            if (TOP_LEVEL.any { destination?.hasRoute(it.routeClass) == true }) {
                NavigationBar {
                    TOP_LEVEL.forEach { item ->
                        NavigationBarItem(
                            selected = destination?.hasRoute(item.routeClass) == true,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = CustomersRoute,
            // Each screen has its own Scaffold: consume what this one already padded
            // so the status and navigation bars are not added twice.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<CustomersRoute> {
                CustomerListScreen(
                    onOpenCustomer = { navController.navigate(CustomerRoute(it)) },
                    onNewCustomer = { navController.navigate(CustomerFormRoute()) },
                )
            }
            composable<ProductsRoute> { ProductListScreen() }
            composable<SummaryRoute> {
                SummaryScreen(
                    onOpenCustomer = { navController.navigate(CustomerRoute(it)) },
                    onSignOut = if (root.isDemo) null else root::signOut,
                )
            }
            composable<CustomerRoute> { entry ->
                val customerId = entry.toRoute<CustomerRoute>().customerId
                CustomerDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(CustomerFormRoute(customerId)) },
                    onNewPurchase = { navController.navigate(NewPurchaseRoute(customerId)) },
                    onOpenPurchase = { navController.navigate(PurchaseRoute(it)) },
                )
            }
            composable<CustomerFormRoute> { entry ->
                val editing = entry.toRoute<CustomerFormRoute>().customerId != null
                CustomerFormScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { savedId ->
                        if (editing) {
                            navController.popBackStack()
                        } else {
                            // New customer: replace the form with the customer's page.
                            navController.navigate(CustomerRoute(savedId)) {
                                popUpTo<CustomerFormRoute> { inclusive = true }
                            }
                        }
                    },
                    onRemoved = { navController.popBackStack<CustomersRoute>(inclusive = false) },
                )
            }
            composable<NewPurchaseRoute> {
                NewPurchaseScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable<PurchaseRoute> {
                PurchaseDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val TIME = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm")

@Composable
private fun StatusBanners(isDemo: Boolean, syncStatus: SyncStatus) {
    val message = when {
        isDemo -> "Modo demonstração: dados de exemplo, apagados ao fechar o app."
        syncStatus.offline -> syncStatus.lastSyncedAt
            ?.let { "Sem internet. Mostrando dados salvos em ${it.atZone(SHOP_ZONE).format(TIME)}." }
            ?: "Sem internet."
        else -> null
    } ?: return
    val (background, foreground) = if (isDemo) {
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Banner(message, background, foreground)
}

@Composable
private fun Banner(text: String, background: Color, foreground: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = foreground)
    }
}
