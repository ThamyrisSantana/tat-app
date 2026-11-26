package br.vendas.app.ui.common

import android.util.Log
import br.vendas.app.data.DuplicatePhoneException
import br.vendas.app.data.DuplicateProductException
import br.vendas.app.data.InvalidCredentialsException
import br.vendas.app.data.NotFoundException
import br.vendas.app.data.PurchaseHasPaymentsException
import br.vendas.app.data.isNetworkError
import br.vendas.app.domain.PaymentExceedsOpenAmountException

private const val TAG = "Vendas"

/** Turns an exception into a message for the person using the app (pt-BR). */
fun friendlyError(error: Throwable): String = when (error) {
    is DuplicatePhoneException -> error.existingCustomerName
        ?.let { "Este número de WhatsApp já é do cliente $it." }
        ?: "Este número de WhatsApp já é de outro cliente."
    is DuplicateProductException -> "Já existe um produto ativo com esse nome."
    is InvalidCredentialsException -> "E-mail ou senha incorretos."
    is PurchaseHasPaymentsException -> "Remova os pagamentos desta compra antes de cancelá-la."
    is PaymentExceedsOpenAmountException -> error.openAmount
        ?.let { "O valor é maior que o total em aberto (${it.format()})." }
        ?: "O valor é maior que o total em aberto."
    is NotFoundException -> "Registro não encontrado. Ele pode ter sido removido."
    else -> if (error.isNetworkError()) {
        "Sem conexão com a internet. Verifique e tente de novo."
    } else {
        Log.e(TAG, "Unexpected error", error)
        "Algo deu errado. Tente de novo."
    }
}
