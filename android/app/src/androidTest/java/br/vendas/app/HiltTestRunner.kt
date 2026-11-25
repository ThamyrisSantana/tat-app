package br.vendas.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/** Runs instrumented tests with Hilt's test application, so test modules can replace real ones. */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(classLoader: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
}
