package br.vendas.app

import br.vendas.app.data.AuthGateway
import br.vendas.app.data.SalesRepository
import br.vendas.app.data.demo.DemoAuthGateway
import br.vendas.app.data.demo.DemoRepository
import br.vendas.app.di.AppConfig
import br.vendas.app.di.DataModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * UI tests always use the sample data, even when local.properties points the
 * debug build at a real Supabase project. Each test gets a fresh copy.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object TestDataModule {

    @Provides
    @Singleton
    fun provideAppConfig(): AppConfig = AppConfig(isDemo = true)

    @Provides
    @Singleton
    fun provideSalesRepository(): SalesRepository = DemoRepository()

    @Provides
    @Singleton
    fun provideAuthGateway(): AuthGateway = DemoAuthGateway()
}
