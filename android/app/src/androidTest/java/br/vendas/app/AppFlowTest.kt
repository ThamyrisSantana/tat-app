package br.vendas.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end flows on the real screens with the sample data (see TestDataModule):
 * Ana Lima owes R$ 85,00 in 2 installments of R$ 42,50, the first one overdue.
 */
@HiltAndroidTest
class AppFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun customerListShowsWhatEachCustomerOwes() {
        compose.onNodeWithText("Ana Lima").assertIsDisplayed()
        compose.onNodeWithText("R$ 85,00").assertIsDisplayed()
        compose.onNodeWithText("Atrasado: R$ 42,50").assertIsDisplayed()
        compose.onNodeWithText("Nada em aberto").assertIsDisplayed()
    }

    @Test
    fun payingAnInstallmentLowersTheBalance() {
        compose.onNodeWithText("Ana Lima").performClick()
        compose.onNodeWithText("2x Caneca, 1x Boné").performClick()
        compose.onAllNodesWithText("Marcar paga")[0].performClick()
        waitForText("Desfazer")

        compose.onNodeWithContentDescription("Voltar").performClick()
        waitForText("Total devido")
        compose.onNodeWithText("R$ 42,50").assertIsDisplayed()
    }

    @Test
    fun newCustomerWithAnExistingNumberIsRejected() {
        openNewCustomerForm()
        compose.onNodeWithText("Nome *").performTextInput("Carla Dias")
        compose.onNodeWithText("WhatsApp com DDD *").performTextInput("(21) 97777-6666")
        compose.onNodeWithText("Salvar").performClick()

        waitForText("Este número de WhatsApp já é do cliente Ana Lima.")
    }

    @Test
    fun newCustomerOpensTheirPage() {
        openNewCustomerForm()
        compose.onNodeWithText("Nome *").performTextInput("Carla Dias")
        compose.onNodeWithText("WhatsApp com DDD *").performTextInput("31 91234-5678")
        compose.onNodeWithText("Salvar").performClick()

        waitForText("WhatsApp: (31) 91234-5678")
        compose.onNodeWithText("Nenhuma compra registrada.").assertIsDisplayed()
    }

    @Test
    fun purchaseInInstallmentsShowsThePlanAndUpdatesTheBalance() {
        compose.onNodeWithText("Ana Lima").performClick()
        compose.onNodeWithText("Nova compra").performClick()
        compose.onNodeWithText("Adicionar produto").performClick()
        compose.onNodeWithText("Camiseta").performClick()
        compose.onNodeWithText("A prazo").performClick()
        compose.onAllNodesWithText("+")[1].performClick()

        compose.onNodeWithText("3x").assertIsDisplayed()
        assertEquals(3, compose.onAllNodesWithText("R$ 20,00").fetchSemanticsNodes().size)

        compose.onNodeWithText("Salvar compra").performScrollTo().performClick()
        waitForText("Total devido")
        compose.onNodeWithText("R$ 145,00").assertIsDisplayed()
    }

    /** The "+ Cliente" button keeps its label in a child node, so look in the unmerged tree. */
    private fun openNewCustomerForm() {
        compose.onNodeWithText("Cliente", useUnmergedTree = true).performClick()
        waitForText("Novo cliente")
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
