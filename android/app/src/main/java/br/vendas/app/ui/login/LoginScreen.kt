package br.vendas.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.vendas.app.data.AuthGateway
import br.vendas.app.ui.common.friendlyError
import br.vendas.app.ui.design.PrimaryButton
import br.vendas.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: AuthGateway) : ViewModel() {
    val email = TextFieldState()
    val password = TextFieldState()
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    fun submit() {
        val emailText = email.text.toString()
        val passwordText = password.text.toString()
        if (emailText.isBlank() || passwordText.isBlank()) {
            error = "Preencha o e-mail e a senha."
            return
        }
        busy = true
        error = null
        viewModelScope.launch {
            try {
                auth.signIn(emailText, passwordText)
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    Scaffold(containerColor = AppTheme.colors.canvas) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Vendas", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Entre com o e-mail e a senha da loja",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            OutlinedTextField(
                state = viewModel.email,
                label = { Text("E-mail") },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedSecureTextField(
                state = viewModel.password,
                label = { Text("Senha") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            viewModel.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
            PrimaryButton(
                text = if (viewModel.busy) "Entrando..." else "Entrar",
                onClick = viewModel::submit,
                enabled = !viewModel.busy,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
        }
    }
}
