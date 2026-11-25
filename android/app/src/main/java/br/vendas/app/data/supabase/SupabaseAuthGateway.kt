package br.vendas.app.data.supabase

import br.vendas.app.data.AuthGateway
import br.vendas.app.data.AuthState
import br.vendas.app.data.InvalidCredentialsException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SupabaseAuthGateway(
    private val client: SupabaseClient,
    scope: CoroutineScope,
) : AuthGateway {

    override val state: StateFlow<AuthState> = client.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> AuthState.SIGNED_IN
                is SessionStatus.NotAuthenticated -> AuthState.SIGNED_OUT
                // The saved session exists but could not be refreshed (usually offline).
                is SessionStatus.RefreshFailure -> AuthState.SIGNED_IN
                else -> AuthState.LOADING
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AuthState.LOADING)

    override suspend fun signIn(email: String, password: String) {
        try {
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        } catch (e: AuthRestException) {
            if (e.error == "invalid_credentials" || e.error == "invalid_grant") throw InvalidCredentialsException()
            throw e
        }
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }
}
