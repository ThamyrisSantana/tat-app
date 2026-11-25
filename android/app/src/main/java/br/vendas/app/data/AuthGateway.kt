package br.vendas.app.data

import kotlinx.coroutines.flow.StateFlow

enum class AuthState { LOADING, SIGNED_IN, SIGNED_OUT }

interface AuthGateway {
    val state: StateFlow<AuthState>

    /** @throws InvalidCredentialsException when the e-mail or password is wrong. */
    suspend fun signIn(email: String, password: String)

    suspend fun signOut()
}

class InvalidCredentialsException : Exception("Invalid e-mail or password")
