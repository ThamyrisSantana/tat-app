package br.vendas.app.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import br.vendas.app.data.SalesRepository
import br.vendas.app.domain.Money
import br.vendas.app.domain.Product
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.EmptyBox
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.friendlyError
import br.vendas.app.ui.design.ListRow
import br.vendas.app.ui.design.Panel
import br.vendas.app.ui.design.PillTabs
import br.vendas.app.ui.design.PrimaryButton
import br.vendas.app.ui.design.RowDivider
import br.vendas.app.ui.design.ScreenHeader
import br.vendas.app.ui.design.StatusPill
import br.vendas.app.ui.design.Tone
import br.vendas.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductListViewModel @Inject constructor(private val repository: SalesRepository) : ViewModel() {
    val loader = AutoLoader(viewModelScope, repository.changes) { repository.products(includeInactive = true) }

    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Saves the dialog's product; calls [onDone] when it worked. */
    fun save(id: String?, name: String, price: Money, active: Boolean, onDone: () -> Unit) {
        saving = true
        error = null
        viewModelScope.launch {
            try {
                if (id == null) repository.createProduct(name, price) else repository.updateProduct(id, name, price, active)
                onDone()
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                saving = false
            }
        }
    }

    fun clearError() {
        error = null
    }
}

/** The product being created (id null) or edited in the dialog. */
private data class ProductDraft(val id: String?, val name: String, val price: String, val active: Boolean)

@Composable
fun ProductListScreen(viewModel: ProductListViewModel = hiltViewModel()) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()
    var showInactive by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf<ProductDraft?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val count = (state as? br.vendas.app.ui.common.LoadState.Ready)?.data?.count { it.active }
            ScreenHeader("Produtos", subtitle = count?.let { "$it ativos" })
            LoadStateContent(state, onRetry = viewModel.loader::retry) { products ->
                val visible = products.filter { showInactive || it.active }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (products.any { !it.active }) {
                        item {
                            PillTabs(
                                options = listOf(false to "Ativos", true to "Todos"),
                                selected = showInactive,
                                onSelect = { showInactive = it },
                            )
                        }
                    }
                    item {
                        Panel(contentPadding = PaddingValues(vertical = 6.dp)) {
                            if (visible.isEmpty()) EmptyBox("Nenhum produto cadastrado ainda.")
                            visible.forEachIndexed { index, product ->
                                if (index > 0) RowDivider(Modifier.padding(horizontal = 16.dp))
                                ListRow(
                                    title = product.name,
                                    subtitle = if (product.active) null else "Desativado",
                                    trailing = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (!product.active) StatusPill("Inativo", Tone.NEUTRAL)
                                            Text(
                                                product.price.format(),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = AppTheme.colors.ink,
                                                modifier = Modifier.padding(start = 8.dp),
                                            )
                                        }
                                    },
                                    showArrow = true,
                                    onClick = {
                                        viewModel.clearError()
                                        draft = ProductDraft(product.id, product.name, product.price.toInputText(), product.active)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        PrimaryButton(
            text = "Novo produto",
            onClick = {
                viewModel.clearError()
                draft = ProductDraft(null, "", "", active = true)
            },
            icon = Icons.Filled.Add,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 108.dp),
        )
    }

    draft?.let { current ->
        ProductDialog(
            initial = current,
            saving = viewModel.saving,
            serverError = viewModel.error,
            onSave = { name, price, active -> viewModel.save(current.id, name, price, active) { draft = null } },
            onDismiss = { draft = null },
        )
    }
}

@Composable
private fun ProductDialog(
    initial: ProductDraft,
    saving: Boolean,
    serverError: String?,
    onSave: (String, Money, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var price by remember { mutableStateOf(initial.price) }
    var active by remember { mutableStateOf(initial.active) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val parsedPrice = Money.parse(price)
        error = when {
            name.isBlank() -> "Informe o nome."
            parsedPrice == null || parsedPrice <= Money.ZERO -> "Preço inválido. Ex.: 59,90"
            else -> null
        }
        if (error == null && parsedPrice != null) onSave(name, parsedPrice, active)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.card,
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
                        Switch(
                            checked = active,
                            onCheckedChange = { active = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = AppTheme.colors.inverseCard),
                        )
                    }
                    Text(
                        "Mudar o preço não altera compras já registradas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.muted,
                    )
                }
                (error ?: serverError)?.let { Text(it, color = AppTheme.colors.dangerText, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = ::save, enabled = !saving) { Text("Salvar", color = AppTheme.colors.ink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = AppTheme.colors.muted) } },
    )
}
