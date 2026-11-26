package br.vendas.app.di

import android.content.Context
import androidx.room.Room
import br.vendas.app.BuildConfig
import br.vendas.app.data.AuthGateway
import br.vendas.app.data.OfflineFirstRepository
import br.vendas.app.data.SalesRepository
import br.vendas.app.data.demo.DemoAuthGateway
import br.vendas.app.data.demo.DemoRepository
import br.vendas.app.data.local.AppDatabase
import br.vendas.app.data.local.SalesDao
import br.vendas.app.data.supabase.SupabaseAuthGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import javax.inject.Provider
import javax.inject.Singleton

/** Settings decided at build time. */
data class AppConfig(
    /** True when Supabase is not configured: sample data, no login. */
    val isDemo: Boolean,
)

/**
 * Chooses between the demo data and Supabase. Supabase objects are behind
 * [Provider] so demo mode never creates them. UI tests replace this module.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAppConfig(): AppConfig =
        AppConfig(isDemo = BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank())

    @Provides
    @Singleton
    fun provideSalesRepository(config: AppConfig, offlineFirst: Provider<OfflineFirstRepository>): SalesRepository =
        if (config.isDemo) DemoRepository() else offlineFirst.get()

    @Provides
    @Singleton
    fun provideAuthGateway(config: AppConfig, client: Provider<SupabaseClient>): AuthGateway =
        if (config.isDemo) {
            DemoAuthGateway()
        } else {
            SupabaseAuthGateway(client.get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
        }

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            defaultSerializer = KotlinXSerializer(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                },
            )
            install(Auth)
            install(Postgrest)
        }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // The database is only a copy of the server: rebuild it instead of migrating.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideSalesDao(database: AppDatabase): SalesDao = database.salesDao()
}
