package br.vendas.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Shop details printed on receipts. */
data class ShopSettings(
    val name: String = "",
    /** WhatsApp number in E.164 format, or empty. */
    val phone: String = "",
) {
    val displayName: String get() = name.ifBlank { "Minha loja" }
}

/** Kept on the phone only (SharedPreferences): small and specific to this device. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {

    private val preferences = context.getSharedPreferences("shop_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        ShopSettings(
            name = preferences.getString(KEY_NAME, "").orEmpty(),
            phone = preferences.getString(KEY_PHONE, "").orEmpty(),
        ),
    )
    val settings: StateFlow<ShopSettings> = _settings.asStateFlow()

    fun save(settings: ShopSettings) {
        preferences.edit()
            .putString(KEY_NAME, settings.name.trim())
            .putString(KEY_PHONE, settings.phone)
            .apply()
        _settings.value = settings.copy(name = settings.name.trim())
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_PHONE = "phone"
    }
}
