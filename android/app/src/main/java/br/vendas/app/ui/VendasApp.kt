package br.vendas.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
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
import br.vendas.app.ui.common.UserMessage
import br.vendas.app.ui.common.UserMessages
import br.vendas.app.ui.common.friendlyError
import br.vendas.app.ui.customers.CustomerDetailScreen
import br.vendas.app.ui.customers.CustomerFormScreen
import br.vendas.app.ui.customers.CustomerListScreen
import br.vendas.app.ui.dashboard.DashboardScreen
import br.vendas.app.ui.design.AppIcons
import br.vendas.app.ui.design.CardShape
import br.vendas.app.ui.design.PillShape
import br.vendas.app.ui.login.LoginScreen
import br.vendas.app.ui.navigation.CustomerFormRoute
import br.vendas.app.ui.navigation.CustomerRoute
import br.vendas.app.ui.navigation.CustomersRoute
import br.vendas.app.ui.navigation.DashboardRoute
import br.vendas.app.ui.navigation.NewPurchaseRoute
import br.vendas.app.ui.navigation.ProductsRoute
import br.vendas.app.ui.navigation.PurchaseRoute
import br.vendas.app.ui.navigation.ReceiptRoute
import br.vendas.app.ui.navigation.ReceivablesRoute
import br.vendas.app.ui.navigation.ReportsRoute
import br.vendas.app.ui.navigation.SettingsRoute
import br.vendas.app.ui.products.ProductListScreen
import br.vendas.app.ui.purchases.NewPurchaseScreen
import br.vendas.app.ui.purchases.PurchaseDetailScreen
import br.vendas.app.ui.receipt.ReceiptScreen
import br.vendas.app.ui.receivables.ReceivablesScreen
import br.vendas.app.ui.reports.ReportsScreen
import br.vendas.app.ui.settings.SettingsScreen
import br.vendas.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.reflect.KClass

@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: AuthGateway,
    private val repository: SalesRepository,
    private val userMessages: UserMessages,
    config: AppConfig,
) : ViewModel() {
    val isDemo = config.isDemo
    val authState = auth.state
    val syncStatus = repository.syncStatus
    val messages = userMessages.messages

    /** Runs the undo here, so it finishes even if the snackbar or the screen that sent it goes away. */
    fun undo(message: UserMessage) {
        val undo = message.undo ?: return
        viewModelScope.launch {
            val result = try {
                undo()
                message.undoneText
            } catch (e: Exception) {
                "Não foi possível desfazer: ${friendlyError(e)}"
            }
            result?.let { userMessages.show(UserMessage(it)) }
        }
    }

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
    TopLevel(DashboardRoute, DashboardRoute::class, "Início", Icons.Filled.Home),
    TopLevel(CustomersRoute(), CustomersRoute::class, "Clientes", Icons.Filled.Person),
    TopLevel(ReceivablesRoute, ReceivablesRoute::class, "A receber", AppIcons.Wallet),
    TopLevel(ReportsRoute, ReportsRoute::class, "Relatórios", AppIcons.BarChart),
    TopLevel(ProductsRoute, ProductsRoute::class, "Produtos", Icons.Filled.ShoppingCart),
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
    val signOut: (() -> Unit)? = if (root.isDemo) null else root::signOut
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        // A new message replaces the one on screen.
        root.messages.collectLatest { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = if (message.undo != null) "Desfazer" else null,
                duration = if (message.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) root.undo(message)
        }
    }

    Scaffold(
        containerColor = AppTheme.colors.canvas,
        topBar = { StatusBanners(isDemo = root.isDemo, syncStatus = syncStatus) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            NavHost(
                navController = navController,
                startDestination = DashboardRoute,
                modifier = Modifier.fillMaxSize(),
                // No animation between screens: the new one simply replaces the old one.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                screen<DashboardRoute> {
                    DashboardScreen(
                        onOpenCustomer = { navController.navigate(CustomerRoute(it)) },
                        onOpenOverdueCustomers = { navController.navigate(CustomersRoute(overdueOnly = true)) },
                        onOpenReceivables = { navController.navigateTopLevel(ReceivablesRoute) },
                        onOpenReports = { navController.navigateTopLevel(ReportsRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                    )
                }
                screen<CustomersRoute> { entry ->
                    val filtered = entry.toRoute<CustomersRoute>().overdueOnly
                    CustomerListScreen(
                        onOpenCustomer = { navController.navigate(CustomerRoute(it)) },
                        onNewCustomer = { navController.navigate(CustomerFormRoute()) },
                        onBack = if (filtered) ({ navController.popBackStack() }) else null,
                    )
                }
                screen<ReceivablesRoute> {
                    ReceivablesScreen(onOpenCustomer = { navController.navigate(CustomerRoute(it)) })
                }
                screen<ReportsRoute> {
                    ReportsScreen(onOpenCustomer = { navController.navigate(CustomerRoute(it)) })
                }
                screen<ProductsRoute> { ProductListScreen() }
                screen<SettingsRoute> {
                    SettingsScreen(onBack = { navController.popBackStack() }, onSignOut = signOut)
                }
                screen<CustomerRoute> { entry ->
                    val customerId = entry.toRoute<CustomerRoute>().customerId
                    CustomerDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(CustomerFormRoute(customerId)) },
                        onNewPurchase = { navController.navigate(NewPurchaseRoute(customerId)) },
                        onOpenPurchase = { navController.navigate(PurchaseRoute(it)) },
                    )
                }
                screen<CustomerFormRoute> { entry ->
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
                        onRemoved = { navController.popBackStack<CustomerRoute>(inclusive = true) },
                    )
                }
                screen<NewPurchaseRoute> {
                    NewPurchaseScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
                screen<PurchaseRoute> {
                    PurchaseDetailScreen(
                        onBack = { navController.popBackStack() },
                        onOpenReceipt = { navController.navigate(ReceiptRoute(it)) },
                    )
                }
                screen<ReceiptRoute> { ReceiptScreen(onBack = { navController.popBackStack() }) }
            }

            val showNavBar = TOP_LEVEL.any { destination.isOn(it.routeClass) } &&
                !(destination.isOn(CustomersRoute::class) && backStack?.toRoute<CustomersRoute>()?.overdueOnly == true)
            // Bottom of the screen, from top to bottom: "Nova venda" (home only), messages, navigation bar.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (destination.isOn(DashboardRoute::class)) {
                    NewSaleButton(
                        onClick = { navController.navigate(NewPurchaseRoute()) },
                        modifier = Modifier.align(Alignment.End).padding(end = 16.dp),
                    )
                }
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = CardShape,
                        containerColor = AppTheme.colors.inverseCard,
                        contentColor = AppTheme.colors.onInverseCard,
                        actionColor = AppTheme.colors.onInverseCard,
                    )
                }
                if (showNavBar) {
                    FloatingNavBar(
                        destination = destination,
                        onSelect = { navController.navigateTopLevel(it.route) },
                    )
                } else {
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

/** A destination drawn on an opaque background, so screens never show through each other. */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        Box(Modifier.fillMaxSize().background(AppTheme.colors.canvas)) { content(entry) }
    }
}

private fun NavDestination?.isOn(route: KClass<*>): Boolean = this?.hasRoute(route) == true

/** The most frequent task, always at hand on the home screen. */
@Composable
private fun NewSaleButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text("Nova venda", style = MaterialTheme.typography.labelLarge) },
        shape = PillShape,
        containerColor = AppTheme.colors.inverseCard,
        contentColor = AppTheme.colors.onInverseCard,
        modifier = modifier,
    )
}

/** Dark floating bar; the current section is a light pill with its name. */
@Composable
private fun FloatingNavBar(destination: NavDestination?, onSelect: (TopLevel) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(12.dp, PillShape)
            .clip(PillShape)
            .background(colors.inverseCard)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TOP_LEVEL.forEach { item ->
            val selected = destination.isOn(item.routeClass)
            Row(
                Modifier
                    .clip(PillShape)
                    .background(if (selected) colors.onInverseCard else Color.Transparent)
                    .clickable { onSelect(item) }
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    }
                    .padding(horizontal = if (selected) 14.dp else 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (selected) colors.inverseCard else colors.onInverseCard.copy(alpha = 0.65f),
                    modifier = Modifier.size(20.dp),
                )
                if (selected) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.inverseCard,
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1,
                    )
                }
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
    val colors = AppTheme.colors
    val message = when {
        isDemo -> "Modo demonstração: dados de exemplo, apagados ao fechar o app."
        syncStatus.offline -> syncStatus.lastSyncedAt
            ?.let { "Sem internet. Mostrando dados salvos em ${it.atZone(SHOP_ZONE).format(TIME)}." }
            ?: "Sem internet."
        else -> null
    } ?: return
    val (background, foreground) = if (isDemo) colors.warningBg to colors.warningText else colors.dangerSolid to colors.onDangerSolid
    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Text(message, style = MaterialTheme.typography.labelMedium, color = foreground)
    }
}
