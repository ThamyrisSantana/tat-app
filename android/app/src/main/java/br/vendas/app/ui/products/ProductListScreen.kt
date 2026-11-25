package br.vendas.app.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import br.vendas.app.data.SalesRepository
import br.vendas.app.domain.Money
import br.vendas.app.domain.Product
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.EmptyBox
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.friendlyError
import kotlinx.coroutines.launch

@HiltViewModel
class ProductListViewModel @Inject constructor(val repository: SalesRepository) : ViewModel() {
    val loader = AutoLoader(viewModelScope, repository.changes) { repository.products(includeInactive = true) }
}

/** The product being created (id null) or edited in the dialog. */
private data class ProductDraft(val id: String?, val name: String, val price: String, val active: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(viewModel: ProductListViewModel = hiltViewModel()) {
    val repository = viewModel.repository
    val state by viewModel.loader.state.collectAsStateWithLifecycle()
    var showInactive by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf<ProductDraft?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Produtos") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { draft = ProductDraft(null, "", "", active = true) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Produto") },
            )
        },
    ) { padding ->
        LoadStateContent(state, onRetry = viewModel.loader::retry, modifier = Modifier.padding(padding)) { products ->
            val visible = products.filter { showInactive || it.active }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (products.any { !it.active }) {
                    item {
                        FilterChip(
                            selected = showInactive,
                            onClick = { showInactive = !showInactive },
                            label = { Text("Mostrar desativados") },
                        )
                    }
                }
                if (visible.isEmpty()) {
                    item { EmptyBox("Nenhum produto cadastrado ainda.") }
                }
                items(visible, key = { it.id }) { product ->
                    ProductRow(product) {
                        draft = ProductDraft(product.id, product.name, product.price.toInputText(), product.active)
                    }
                }
            }
        }
    }

    draft?.let { current ->
        ProductDialog(
            repository = repository,
            initial = current,
            onDismiss = { draft = null },
        )
    }
}

@Composable
private fun ProductRow(product: Product, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                if (!product.active) {
                    Text(
                        "Desativado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(product.price.format(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ProductDialog(repository: SalesRepository, initial: ProductDraft, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var price by remember { mutableStateOf(initial.price) }
    var active by remember { mutableStateOf(initial.active) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save() {
        val parsedPrice = Money.parse(price)
        error = when {
            name.isBlank() -> "Informe o nome."
            parsedPrice == null || parsedPrice <= Money.ZERO -> "Preço inválido. Ex.: 59,90"
            else -> null
        }
        if (error != null || parsedPrice == null) return
        saving = true
        scope.launch {
            try {
                if (initial.id == null) {
                    repository.createProduct(name, parsedPrice)
                } else {
                    repository.updateProduct(initial.id, name, parsedPrice, active)
                }
                onDismiss()
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                saving = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == null) "Novo produto" else "Editar produto") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Preço (R$)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (initial.id != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Ativo (aparece nas vendas)", modifier = Modifier.weight(1f))
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                    Text(
                        "Mudar o preço não altera compras já registradas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = ::save, enabled = !saving) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
