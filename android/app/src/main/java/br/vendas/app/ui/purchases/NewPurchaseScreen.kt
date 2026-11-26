package br.vendas.app.ui.purchases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.vendas.app.data.SalesRepository
import br.vendas.app.domain.Customer
import br.vendas.app.domain.InstallmentPlan
import br.vendas.app.domain.Money
import br.vendas.app.domain.NewPurchase
import br.vendas.app.domain.NewPurchaseItem
import br.vendas.app.domain.PlannedInstallment
import br.vendas.app.domain.Product
import br.vendas.app.domain.sum
import br.vendas.app.domain.today
import br.vendas.app.ui.common.DateField
import br.vendas.app.ui.common.ErrorBox
import br.vendas.app.ui.common.LoadingBox
import br.vendas.app.ui.common.formatFull
import br.vendas.app.ui.common.friendlyError
import br.vendas.app.ui.design.Avatar
import br.vendas.app.ui.design.ListRow
import br.vendas.app.ui.design.Panel
import br.vendas.app.ui.design.PillTabs
import br.vendas.app.ui.design.PrimaryButton
import br.vendas.app.ui.design.RowDivider
import br.vendas.app.ui.design.ScreenHeader
import br.vendas.app.ui.design.SecondaryButton
import br.vendas.app.ui.design.SectionHeader
import br.vendas.app.ui.navigation.NewPurchaseRoute
import br.vendas.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CartItem(val product: Product, val quantity: Int) {
    val subtotal: Money get() = product.price * quantity
}

@HiltViewModel
class NewPurchaseViewModel @Inject constructor(
    private val repository: SalesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val customerId = savedStateHandle.toRoute<NewPurchaseRoute>().customerId

    var customer by mutableStateOf<Customer?>(null)
    var products by mutableStateOf<List<Product>>(emptyList())
    var loadError by mutableStateOf<String?>(null)

    val cart = mutableStateListOf<CartItem>()
    var purchaseDate by mutableStateOf(today())
    var onInstallments by mutableStateOf(false)
    var installmentCount by mutableStateOf(InstallmentPlan.MIN_INSTALLMENTS)

    /** Null follows the purchase date (one month later). */
    var customFirstDueDate by mutableStateOf<LocalDate?>(null)
    var notes by mutableStateOf("")
    var saving by mutableStateOf(false)
    var saveError by mutableStateOf<String?>(null)
    var saved by mutableStateOf(false)

    val total: Money get() = cart.map { it.subtotal }.sum()

    val firstDueDate: LocalDate
        get() = customFirstDueDate ?: InstallmentPlan.defaultFirstDueDate(purchaseDate)

    val plan: List<PlannedInstallment>
        get() = if (onInstallments && total.cents >= installmentCount) {
            InstallmentPlan.calculate(total, installmentCount, firstDueDate)
        } else {
            emptyList()
        }

    init {
        load()
    }

    fun load() {
        loadError = null
        viewModelScope.launch {
            try {
                customer = repository.customer(customerId)
                products = repository.products()
            } catch (e: Exception) {
                loadError = friendlyError(e)
            }
        }
    }

    fun add(product: Product) {
        val index = cart.indexOfFirst { it.product.id == product.id }
        if (index >= 0) cart[index] = cart[index].copy(quantity = cart[index].quantity + 1) else cart += CartItem(product, 1)
    }

    fun changeQuantity(item: CartItem, delta: Int) {
        val index = cart.indexOf(item)
        val quantity = item.quantity + delta
        if (quantity <= 0) cart.removeAt(index) else cart[index] = item.copy(quantity = quantity)
    }

    fun save() {
        saveError = when {
            cart.isEmpty() -> "Adicione pelo menos um produto."
            onInstallments && total.cents < installmentCount -> "Valor baixo demais para $installmentCount parcelas."
            onInstallments && firstDueDate.isBefore(purchaseDate) -> "O 1º vencimento não pode ser antes da compra."
            else -> null
        }
        if (saveError != null) return
        saving = true
        viewModelScope.launch {
            try {
                repository.createPurchase(
                    NewPurchase(
                        customerId = customerId,
                        items = cart.map { NewPurchaseItem(it.product.id, it.quantity) },
                        installmentCount = if (onInstallments) installmentCount else 1,
                        purchaseDate = purchaseDate,
                        firstDueDate = if (onInstallments) firstDueDate else null,
                        notes = notes.trim().ifEmpty { null },
                    ),
                )
                saved = true
            } catch (e: Exception) {
                saveError = friendlyError(e)
            } finally {
                saving = false
            }
        }
    }
}

@Composable
fun NewPurchaseScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: NewPurchaseViewModel = hiltViewModel(),
) {
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.saved) { if (viewModel.saved) onSaved() }

    Column(Modifier.fillMaxSize()) {
        val customer = viewModel.customer
        ScreenHeader("Nova compra", subtitle = customer?.name, onBack = onBack)
        when {
            viewModel.loadError != null -> ErrorBox(viewModel.loadError!!, viewModel::load)
            customer == null -> LoadingBox()
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(customer.name)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Cliente", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.muted)
                            Text(customer.name, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.ink)
                        }
                    }
                    DateField(
                        label = "Data da compra",
                        date = viewModel.purchaseDate,
                        onDateChange = { viewModel.purchaseDate = it },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                Panel(contentPadding = PaddingValues(vertical = 16.dp)) {
                    SectionHeader("Produtos", modifier = Modifier.padding(horizontal = 20.dp))
                    viewModel.cart.forEachIndexed { index, item ->
                        if (index > 0) RowDivider(Modifier.padding(horizontal = 16.dp))
                        CartRow(item, viewModel::changeQuantity)
                    }
                    SecondaryButton(
                        "Adicionar produto",
                        onClick = { picking = true },
                        icon = Icons.Filled.Add,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                    RowDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(viewModel.total.format(), style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.ink)
                    }
                }

                Panel {
                    SectionHeader("Pagamento")
                    PillTabs(
                        options = listOf(false to "À vista", true to "A prazo"),
                        selected = viewModel.onInstallments,
                        onSelect = { viewModel.onInstallments = it },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (viewModel.onInstallments) InstallmentOptions(viewModel)
                }

                OutlinedTextField(
                    value = viewModel.notes,
                    onValueChange = { viewModel.notes = it },
                    label = { Text("Observações") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                viewModel.saveError?.let { Text(it, color = AppTheme.colors.dangerText) }
                PrimaryButton(
                    text = "Salvar compra",
                    onClick = viewModel::save,
                    enabled = !viewModel.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (picking) {
        ProductPicker(
            products = viewModel.products,
            onPick = {
                viewModel.add(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun CartRow(item: CartItem, onChange: (CartItem, Int) -> Unit) {
    ListRow(
        title = item.product.name,
        subtitle = "${item.product.price.format()} cada · ${item.subtotal.format()}",
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onChange(item, -1) }) { Text("−", style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.ink) }
                Text("${item.quantity}", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.ink)
                TextButton(onClick = { onChange(item, +1) }) { Text("+", style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.ink) }
            }
        },
    )
}

@Composable
private fun InstallmentOptions(viewModel: NewPurchaseViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Text("Parcelas", style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { viewModel.installmentCount-- },
            enabled = viewModel.installmentCount > InstallmentPlan.MIN_INSTALLMENTS,
        ) { Text("−", style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.ink) }
        Text("${viewModel.installmentCount}x", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.ink)
        TextButton(
            onClick = { viewModel.installmentCount++ },
            enabled = viewModel.installmentCount < InstallmentPlan.MAX_INSTALLMENTS,
        ) { Text("+", style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.ink) }
    }
    DateField(
        label = "1º vencimento",
        date = viewModel.firstDueDate,
        onDateChange = { viewModel.customFirstDueDate = it },
        modifier = Modifier.padding(top = 8.dp),
    )
    val plan = viewModel.plan
    if (plan.isNotEmpty()) {
        Column(Modifier.padding(top = 16.dp)) {
            Text("Simulação", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.muted)
            plan.forEach {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("${it.number}/${plan.size}", color = AppTheme.colors.muted, modifier = Modifier.weight(0.6f))
                    Text(it.dueDate.formatFull(), color = AppTheme.colors.ink, modifier = Modifier.weight(1.2f))
                    Text(it.amount.format(), color = AppTheme.colors.ink)
                }
            }
        }
    }
}

@Composable
private fun ProductPicker(products: List<Product>, onPick: (Product) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val visible = products.filter { it.name.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.card,
        title = { Text("Escolha o produto") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Limpar") }
                        }
                    },
                    singleLine = true,
                )
                if (products.isEmpty()) {
                    Text("Nenhum produto cadastrado. Cadastre na aba Produtos.", modifier = Modifier.padding(top = 16.dp))
                }
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(visible, key = { it.id }) { product ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(product) }.padding(vertical = 14.dp)) {
                            Text(product.name, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
                            Text(product.price.format(), color = AppTheme.colors.ink)
                        }
                        RowDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = AppTheme.colors.ink) } },
    )
}
