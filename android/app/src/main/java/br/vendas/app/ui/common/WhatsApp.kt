package br.vendas.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import br.vendas.app.domain.PhoneNumbers
import java.net.URLEncoder

/** Opens WhatsApp (or the browser) with [message] ready to send to [phone]. Sends nothing by itself. */
fun openWhatsApp(context: Context, phone: String, message: String) {
    val text = URLEncoder.encode(message, "UTF-8").replace("+", "%20")
    val uri = Uri.parse("https://wa.me/${PhoneNumbers.waMeDigits(phone)}?text=$text")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Não encontrei o WhatsApp neste celular.", Toast.LENGTH_LONG).show()
    }
}
