package br.vendas.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import br.vendas.app.BuildConfig
import br.vendas.app.data.SettingsRepository
import br.vendas.app.data.ShopSettings
import br.vendas.app.domain.PhoneNumbers
import br.vendas.app.ui.design.fieldColors
import br.vendas.app.ui.design.FieldShape
import br.vendas.app.ui.design.Panel
import br.vendas.app.ui.design.PrimaryButton
import br.vendas.app.ui.design.ScreenHeader
import br.vendas.app.ui.design.SectionHeader
import br.vendas.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    private val current = repository.settings.value
    var name by mutableStateOf(current.name)
    var phone by mutableStateOf(current.phone.takeIf { it.isNotBlank() }?.let(PhoneNumbers::format).orEmpty())
    var phoneError by mutableStateOf<String?>(null)
        private set
    var saved by mutableStateOf(false)
        private set

    fun save() {
        val normalized = if (phone.isBlank()) "" else PhoneNumbers.normalize(phone)
        if (normalized == null) {
            phoneError = "Número inválido. Use DDD + número, ex.: 11 99999-8888."
            return
        }
        phoneError = null
        repository.save(ShopSettings(name = name, phone = normalized))
        saved = true
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignOut: (() -> Unit)?,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Ajustes", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel {
                SectionHeader("Sua loja", subtitle = "Aparece no topo dos comprovantes")
                OutlinedTextField(
                    shape = FieldShape,
                    colors = fieldColors(),
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it },
                    label = { Text("Nome da loja") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    shape = FieldShape,
                    colors = fieldColors(),
                    value = viewModel.phone,
                    onValueChange = { viewModel.phone = it },
                    label = { Text("WhatsApp da loja (opcional)") },
                    isError = viewModel.phoneError != null,
                    supportingText = viewModel.phoneError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (viewModel.saved) {
                    Text("Salvo.", color = AppTheme.colors.successText, modifier = Modifier.padding(top = 8.dp))
                }
                PrimaryButton("Salvar", onClick = viewModel::save, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            Panel {
                SectionHeader("Sobre", subtitle = "Vendas ${BuildConfig.VERSION_NAME}")
                if (onSignOut != null) {
                    TextButton(onClick = onSignOut, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Sair da conta", color = AppTheme.colors.dangerText, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Text(
                        "Modo demonstração: conecte o Supabase para guardar os dados de verdade.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
