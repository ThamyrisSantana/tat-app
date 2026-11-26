package br.vendas.app

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end flows on the real screens with the sample data (see TestDataModule):
 * Ana Lima owes R$ 85,00 in 2 installments of R$ 42,50, the first one overdue;
 * Pedro Alves is late too; Maria Silva is up to date.
 */
@HiltAndroidTest
class AppFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardHighlightsLateCustomers() {
        waitForText("2 clientes com pagamento atrasado")
        // Most overdue first: Pedro owes R$ 60,00 late, Ana R$ 42,50.
        compose.onNodeWithText("Pedro Alves, Ana Lima").assertIsDisplayed()
    }

    @Test
    fun overdueAlertOpensOnlyLateCustomers() {
        waitForText("2 clientes com pagamento atrasado")
        compose.onNodeWithText("Ver clientes atrasados").performClick()

        waitForText("Pedro Alves")
        compose.onNodeWithText("Ana Lima").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Maria Silva").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun partialPaymentCoversOneInstallmentAndPartOfTheNext() {
        openCustomer("Ana Lima")
        compose.onNodeWithText("Registrar pagamento").performClick()
        compose.onNodeWithText("Valor recebido").performTextClearance()
        compose.onNodeWithText("Valor recebido").performTextInput("50")

        waitForText("Quita a parcela")
        compose.onNodeWithText("Faltarão R$ 35,00").assertIsDisplayed()
        compose.onNodeWithText("Registrar R$ 50,00").performScrollTo().performClick()

        // The balance tile shows exactly what is left: 85,00 - 50,00.
        waitForText("R$ 35,00", exact = true)
        assertTrue(compose.onAllNodesWithText("Pagamento atrasado", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun receiptListsItemsAndInstallmentValue() {
        openCustomer("Ana Lima")
        scrollList().performScrollToNode(hasText("2x Caneca, 1x Boné"))
        compose.onNodeWithText("2x Caneca, 1x Boné").performClick()
        compose.onNodeWithText("Comprovante").performClick()

        waitForText("COMPROVANTE DE COMPRA")
        compose.onNodeWithText("Parcelado em 2x").assertIsDisplayed()
        compose.onNodeWithText("Este comprovante não é documento fiscal.").performScrollTo().assertIsDisplayed()
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

        waitForText("WhatsApp (31) 91234-5678")
    }

    @Test
    fun purchaseInInstallmentsShowsThePlanAndUpdatesTheBalance() {
        openCustomer("Ana Lima")
        compose.onNodeWithText("Compra").performClick()
        compose.onNodeWithText("Adicionar produto").performClick()
        compose.onNodeWithText("Camiseta").performClick()
        compose.onNodeWithText("A prazo").performClick()
        compose.onAllNodesWithText("+")[1].performClick()

        compose.onNodeWithText("3x").assertIsDisplayed()
        assertEquals(3, compose.onAllNodesWithText("R$ 20,00").fetchSemanticsNodes().size)

        compose.onNodeWithText("Salvar venda").performScrollTo().performClick()
        waitForText("R$ 145,00", exact = true)
    }

    @Test
    fun newSaleFromHomePicksTheCustomerAndCanBeUndone() {
        waitForText("Nova venda")
        compose.onNodeWithText("Nova venda").performClick()
        waitForText("Quem está comprando?")
        compose.onNodeWithText("Nome ou número").performTextInput("maria")
        compose.onNodeWithText("Maria Silva").performClick()

        compose.onNodeWithText("Adicionar produto").performClick()
        compose.onNodeWithText("Camiseta").performClick()
        compose.onNodeWithText("Salvar venda").performScrollTo().performClick()

        waitForText("Venda de R$ 60,00 registrada.")
        compose.onNodeWithText("Desfazer").performClick()
        waitForText("Venda cancelada.")
    }

    @Test
    fun newSaleCanRegisterTheCustomer() {
        waitForText("Nova venda")
        compose.onNodeWithText("Nova venda").performClick()
        waitForText("Quem está comprando?")
        compose.onNodeWithText("Nome ou número").performTextInput("carla dias")
        compose.onNodeWithText("Cadastrar \"Carla Dias\"").performClick()

        compose.onNodeWithText("WhatsApp com DDD").performTextInput("31 91234-5678")
        compose.onNodeWithText("Salvar e escolher").performClick()

        // The new customer is picked: the header and the customer panel show the name.
        waitForText("Trocar")
        assertEquals(2, compose.onAllNodesWithText("Carla Dias").fetchSemanticsNodes().size)
    }

    @Test
    fun productMissingFromThePickerCanBeCreatedAndAdded() {
        openCustomer("Ana Lima")
        compose.onNodeWithText("Compra").performClick()
        compose.onNodeWithText("Adicionar produto").performClick()
        compose.onNodeWithText("Buscar").performTextInput("blusa")
        compose.onNodeWithText("Nenhum produto encontrado.").assertIsDisplayed()

        compose.onNodeWithText("Cadastrar \"Blusa\"").performClick()
        compose.onNodeWithText("Preço (R$)").performTextInput("49,90")
        compose.onNodeWithText("Salvar e adicionar").performClick()

        waitForText("R$ 49,90 cada · R$ 49,90")
    }

    @Test
    fun reportsShowSalesAndTopProducts() {
        compose.onNodeWithContentDescription("Relatórios").performClick()
        waitForText("Vendas por mês")
        scrollList().performScrollToNode(hasText("Produtos mais vendidos"))
        compose.onNodeWithText("Produtos mais vendidos").assertIsDisplayed()
    }

    @Test
    fun receivablesShowTotalsByMonth() {
        compose.onNodeWithContentDescription("A receber").performClick()
        waitForText("Total a receber")
        compose.onNodeWithText("Previsão de recebimento").assertIsDisplayed()
    }

    private fun openCustomer(name: String) {
        compose.onNodeWithContentDescription("Clientes").performClick()
        waitForText(name)
        compose.onNodeWithText(name).performClick()
        waitForText("Total devido")
    }

    private fun openNewCustomerForm() {
        compose.onNodeWithContentDescription("Clientes").performClick()
        waitForText("Novo cliente")
        compose.onNodeWithText("Novo cliente").performClick()
        waitForText("Nome e WhatsApp são obrigatórios")
    }

    private fun scrollList(): SemanticsNodeInteraction = compose.onAllNodes(hasScrollAction())[0]

    private fun waitForText(text: String, exact: Boolean = false) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text, substring = !exact)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
