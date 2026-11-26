package br.vendas.app.ui.customers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.vendas.app.ui.navigation.CustomerFormRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import br.vendas.app.data.DuplicatePhoneException
import br.vendas.app.data.SalesRepository
import br.vendas.app.domain.CustomerInput
import br.vendas.app.domain.PhoneNumbers
import br.vendas.app.ui.common.LoadingBox
import br.vendas.app.ui.common.friendlyError
import br.vendas.app.ui.design.fieldColors
import br.vendas.app.ui.design.FieldShape
import br.vendas.app.ui.design.Panel
import br.vendas.app.ui.design.PrimaryButton
import br.vendas.app.ui.design.ScreenHeader
import br.vendas.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

@HiltViewModel
class CustomerFormViewModel @Inject constructor(
    private val repository: SalesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /** Null when creating a customer. */
    val customerId: String? = savedStateHandle.toRoute<CustomerFormRoute>().customerId

    var name by mutableStateOf("")
    var phone by mutableStateOf("")
    var notes by mutableStateOf("")
    var nameError by mutableStateOf<String?>(null)
    var phoneError by mutableStateOf<String?>(null)
    var generalError by mutableStateOf<String?>(null)
    var loading by mutableStateOf(customerId != null)
    var saving by mutableStateOf(false)

    /** Set after a successful save or removal: the id of the saved customer, or "" when removed. */
    var finished by mutableStateOf<String?>(null)

    init {
        if (customerId != null) {
            viewModelScope.launch {
                try {
                    val customer = repository.customer(customerId)
                    name = customer.name
                    phone = PhoneNumbers.format(customer.phone)
                    notes = customer.notes.orEmpty()
                } catch (e: Exception) {
                    generalError = friendlyError(e)
                } finally {
                    loading = false
                }
            }
        }
    }

    fun save() {
        nameError = if (name.isBlank()) "Informe o nome." else null
        val normalizedPhone = PhoneNumbers.normalize(phone)
        phoneError = if (normalizedPhone == null) "Número inválido. Use DDD + número, ex.: 11 99999-8888." else null
        if (nameError != null || normalizedPhone == null) return

        val input = CustomerInput(name.trim(), normalizedPhone, notes.trim().ifEmpty { null })
        saving = true
        generalError = null
        viewModelScope.launch {
            try {
                finished = if (customerId == null) {
                    repository.createCustomer(input).id
                } else {
                    repository.updateCustomer(customerId, input)
                    customerId
                }
            } catch (e: DuplicatePhoneException) {
                phoneError = friendlyError(e)
            } catch (e: Exception) {
                generalError = friendlyError(e)
            } finally {
                saving = false
            }
        }
    }

    fun remove() {
        val id = customerId ?: return
        saving = true
        viewModelScope.launch {
            try {
                repository.deactivateCustomer(id)
                finished = ""
            } catch (e: Exception) {
                generalError = friendlyError(e)
            } finally {
                saving = false
            }
        }
    }
}

/**
 * Creates a customer or edits one, depending on the route. [onSaved] gets the
 * customer id; [onRemoved] is called after the customer is removed.
 */
@Composable
fun CustomerFormScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onRemoved: () -> Unit,
    viewModel: CustomerFormViewModel = hiltViewModel(),
) {
    val customerId = viewModel.customerId
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.finished) {
        when (val result = viewModel.finished) {
            null -> Unit
            "" -> onRemoved()
            else -> onSaved(result)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            if (customerId == null) "Novo cliente" else "Editar cliente",
            subtitle = "Nome e WhatsApp são obrigatórios",
            onBack = onBack,
        )
        if (viewModel.loading) {
            LoadingBox()
            return@Column
        }
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Panel {
                OutlinedTextField(
                    shape = FieldShape,
                    colors = fieldColors(),
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it },
                    label = { Text("Nome *") },
                    isError = viewModel.nameError != null,
                    supportingText = viewModel.nameError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    shape = FieldShape,
                    colors = fieldColors(),
                    value = viewModel.phone,
                    onValueChange = { viewModel.phone = it },
                    label = { Text("WhatsApp com DDD *") },
                    placeholder = { Text("11 99999-8888") },
                    isError = viewModel.phoneError != null,
                    supportingText = viewModel.phoneError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    shape = FieldShape,
                    colors = fieldColors(),
                    value = viewModel.notes,
                    onValueChange = { viewModel.notes = it },
                    label = { Text("Observações") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            viewModel.generalError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
            PrimaryButton(
                text = "Salvar",
                onClick = viewModel::save,
                enabled = !viewModel.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            if (customerId != null) {
                TextButton(
                    onClick = { confirmRemove = true },
                    enabled = !viewModel.saving,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Remover cliente", color = AppTheme.colors.dangerText) }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remover cliente?") },
            text = { Text("O cliente some da lista, mas o histórico de compras continua guardado.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    viewModel.remove()
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } },
        )
    }
}
