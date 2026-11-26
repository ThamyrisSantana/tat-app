package br.vendas.app.ui.receipt

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.vendas.app.data.SalesRepository
import br.vendas.app.data.SettingsRepository
import br.vendas.app.data.ShopSettings
import br.vendas.app.domain.Customer
import br.vendas.app.domain.PaymentType
import br.vendas.app.domain.PhoneNumbers
import br.vendas.app.domain.Purchase
import br.vendas.app.domain.today
import br.vendas.app.ui.common.AutoLoader
import br.vendas.app.ui.common.LoadStateContent
import br.vendas.app.ui.common.formatFull
import br.vendas.app.ui.design.AppIcons
import br.vendas.app.ui.design.PrimaryButton
import br.vendas.app.ui.design.ScreenHeader
import br.vendas.app.ui.design.SecondaryButton
import br.vendas.app.ui.navigation.ReceiptRoute
import br.vendas.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ReceiptData(val purchase: Purchase, val customer: Customer)

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    repository: SalesRepository,
    settings: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val purchaseId = savedStateHandle.toRoute<ReceiptRoute>().purchaseId
    val settings = settings.settings

    val loader = AutoLoader(viewModelScope, repository.changes) {
        val snapshot = repository.snapshot()
        val purchase = snapshot.purchase(purchaseId)
        ReceiptData(purchase, snapshot.customer(purchase.customerId))
    }
}

/** Shows the purchase as a receipt and lets the user save it as a PNG or share it. */
@Composable
fun ReceiptScreen(onBack: () -> Unit, viewModel: ReceiptViewModel = hiltViewModel()) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()
    val shop by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = pendingBitmap
        pendingBitmap = null
        if (uri != null && bitmap != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { writePng(context, bitmap, uri) }
                Toast.makeText(context, if (ok) "Comprovante salvo." else "Não foi possível salvar.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun capture(): Bitmap = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Comprovante", subtitle = "Baixe ou compartilhe a imagem", onBack = onBack)
        LoadStateContent(state, onRetry = viewModel.loader::retry) { data ->
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Box(
                        Modifier.drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        },
                    ) {
                        Receipt(data.purchase, data.customer, shop)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SecondaryButton(
                        text = "Baixar",
                        icon = AppIcons.Download,
                        onClick = {
                            scope.launch {
                                pendingBitmap = capture()
                                saveLauncher.launch(fileName(data))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Compartilhar",
                        icon = Icons.Filled.Share,
                        onClick = {
                            scope.launch {
                                val bitmap = capture()
                                val uri = withContext(Dispatchers.IO) { cacheForSharing(context, bitmap, fileName(data)) }
                                share(context, uri)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val Paper = Color(0xFFFFFFFF)
private val PaperInk = Color(0xFF151515)
private val PaperMuted = Color(0xFF6B6B6B)
private val PaperLine = Color(0xFFCFCFCF)
private val Mono = FontFamily.Monospace

/**
 * The receipt itself, drawn with fixed paper colors so the image looks the same
 * in light and dark mode.
 */
@Composable
fun Receipt(purchase: Purchase, customer: Customer, shop: ShopSettings, modifier: Modifier = Modifier) {
    val number = purchase.id.replace("-", "").take(8).uppercase()
    Column(modifier.fillMaxWidth().background(Color(0xFFF2F1EE))) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Paper)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                shop.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = PaperInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (shop.phone.isNotBlank()) {
                PaperText("WhatsApp ${PhoneNumbers.format(shop.phone)}", center = true, muted = true)
            }
            Text(
                "COMPROVANTE DE COMPRA",
                style = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.5.sp),
                color = PaperInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            DashedLine()
            Pair("Nº", number).let { (label, value) -> PaperRow(label, value) }
            PaperRow("Data da compra", purchase.purchaseDate.formatFull())
            PaperRow("Emitido em", today().formatFull())
            PaperRow("Cliente", customer.name)
            PaperRow("WhatsApp", PhoneNumbers.format(customer.phone))
            DashedLine()

            Row(Modifier.fillMaxWidth()) {
                PaperHeader("QTD", Modifier.width(36.dp))
                PaperHeader("ITEM", Modifier.weight(1f))
                PaperHeader("UNIT.", Modifier.width(78.dp), end = true)
                PaperHeader("TOTAL", Modifier.width(84.dp), end = true)
            }
            purchase.items.forEach { item ->
                Row(Modifier.fillMaxWidth()) {
                    PaperCell("${item.quantity}", Modifier.width(36.dp))
                    PaperCell(item.productName, Modifier.weight(1f))
                    PaperCell(item.unitPrice.format(), Modifier.width(78.dp), end = true)
                    PaperCell(item.subtotal.format(), Modifier.width(84.dp), end = true)
                }
            }
            DashedLine()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("TOTAL", style = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp), color = PaperInk, modifier = Modifier.weight(1f))
                Text(purchase.total.format(), style = MaterialTheme.typography.headlineSmall, color = PaperInk)
            }

            DashedLine()
            if (purchase.paymentType == PaymentType.CASH) {
                PaperRow("Pagamento", "À vista")
            } else {
                PaperRow("Pagamento", "Parcelado em ${purchase.installmentCount}x")
                PaperRow("Valor da parcela", purchase.installmentAmount?.format().orEmpty())
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    purchase.installments.forEach { installment ->
                        Row(Modifier.fillMaxWidth()) {
                            PaperCell("${installment.number}/${purchase.installmentCount}", Modifier.width(44.dp))
                            PaperCell("vence ${installment.dueDate.formatFull()}", Modifier.weight(1f))
                            PaperCell(installment.amount.format(), Modifier.width(84.dp), end = true)
                        }
                    }
                }
                if (!purchase.isCanceled) {
                    PaperRow("Pago até hoje", purchase.paidAmount.format())
                    PaperRow("Em aberto", purchase.openAmount.format())
                }
            }
            if (purchase.isCanceled) PaperText("COMPRA CANCELADA", center = true)

            DashedLine()
            PaperText("Obrigado pela preferência!", center = true)
            PaperText("Este comprovante não é documento fiscal.", center = true, muted = true)
        }
        ZigZagEdge()
    }
}

@Composable
private fun PaperRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = TextStyle(fontFamily = Mono, fontSize = 12.sp), color = PaperMuted, modifier = Modifier.weight(1f))
        Text(value, style = TextStyle(fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = PaperInk, textAlign = TextAlign.End)
    }
}

@Composable
private fun PaperHeader(text: String, modifier: Modifier, end: Boolean = false) {
    Text(
        text,
        style = TextStyle(fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        color = PaperMuted,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        modifier = modifier,
    )
}

@Composable
private fun PaperCell(text: String, modifier: Modifier, end: Boolean = false) {
    Text(
        text,
        style = TextStyle(fontFamily = Mono, fontSize = 12.sp),
        color = PaperInk,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        modifier = modifier,
    )
}

@Composable
private fun PaperText(text: String, center: Boolean = false, muted: Boolean = false) {
    Text(
        text,
        style = TextStyle(fontFamily = Mono, fontSize = 12.sp),
        color = if (muted) PaperMuted else PaperInk,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DashedLine() {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = PaperLine,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}

/** Torn-paper bottom edge. */
@Composable
private fun ZigZagEdge() {
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val tooth = 14.dp.toPx()
        val path = Path().apply {
            moveTo(0f, 0f)
            var x = 0f
            while (x < size.width) {
                lineTo(x + tooth / 2, size.height)
                lineTo((x + tooth).coerceAtMost(size.width), 0f)
                x += tooth
            }
            close()
        }
        drawPath(path, Paper)
    }
}

private fun fileName(data: ReceiptData): String {
    val name = data.customer.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    return "comprovante-$name-${data.purchase.purchaseDate}.png"
}

private fun writePng(context: Context, bitmap: Bitmap, uri: Uri): Boolean =
    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false

/** Writes the image to the app's cache and returns a content URI other apps can read. */
private fun cacheForSharing(context: Context, bitmap: Bitmap, name: String): Uri {
    val folder = File(context.cacheDir, "receipts").apply { mkdirs() }
    folder.listFiles()?.forEach { it.delete() }
    val file = File(folder, name)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun share(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("comprovante", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar comprovante"))
}
