package br.vendas.app.ui.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Failed(val message: String) : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
}

/**
 * Runs [load] now and again after every repository change, so a screen that
 * stays in the back stack shows fresh data when the user returns to it.
 * Old data stays on screen while reloading.
 */
class AutoLoader<T>(
    scope: CoroutineScope,
    changes: Flow<Int>,
    private val load: suspend () -> T,
) {
    private val retries = MutableStateFlow(0)
    private val _state = MutableStateFlow<LoadState<T>>(LoadState.Loading)
    val state: StateFlow<LoadState<T>> = _state.asStateFlow()

    init {
        scope.launch {
            combine(changes, retries) { change, retry -> change to retry }.collectLatest {
                _state.value = try {
                    LoadState.Ready(load())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LoadState.Failed(friendlyError(e))
                }
            }
        }
    }

    fun retry() {
        _state.value = LoadState.Loading
        retries.value += 1
    }
}
