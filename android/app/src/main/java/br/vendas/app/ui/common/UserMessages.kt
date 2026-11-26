package br.vendas.app.ui.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A short message shown above the navigation bar. With [undo] it offers
 * "Desfazer"; [undoneText] confirms that the change was taken back.
 */
class UserMessage(
    val text: String,
    val undoneText: String? = null,
    val undo: (suspend () -> Unit)? = null,
)

/**
 * Carries messages from any screen to the snackbar drawn by the app, so a
 * message stays visible even after the screen that sent it is closed.
 */
@Singleton
class UserMessages @Inject constructor() {
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UserMessage> = _messages

    fun show(message: UserMessage) {
        _messages.tryEmit(message)
    }
}
