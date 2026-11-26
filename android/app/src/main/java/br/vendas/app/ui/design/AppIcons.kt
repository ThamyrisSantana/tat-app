package br.vendas.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons missing from material-icons-core, drawn on a 24x24 grid. Tinted like any Icon. */
object AppIcons {

    val BarChart: ImageVector by lazy {
        icon("BarChart") {
            moveTo(4f, 11f); horizontalLineTo(8f); verticalLineTo(20f); horizontalLineTo(4f); close()
            moveTo(10f, 4f); horizontalLineTo(14f); verticalLineTo(20f); horizontalLineTo(10f); close()
            moveTo(16f, 14f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(16f); close()
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            moveTo(11f, 3f); horizontalLineTo(13f); verticalLineTo(12f); lineTo(16.5f, 8.5f); lineTo(18f, 10f)
            lineTo(12f, 16f); lineTo(6f, 10f); lineTo(7.5f, 8.5f); lineTo(11f, 12f); close()
            moveTo(5f, 18f); horizontalLineTo(19f); verticalLineTo(20f); horizontalLineTo(5f); close()
        }
    }

    /** A paper slip with a torn edge and text lines. */
    val Receipt: ImageVector by lazy {
        icon("Receipt") {
            moveTo(5f, 2f); horizontalLineTo(19f); verticalLineTo(22f); lineTo(16.7f, 20.5f); lineTo(14.3f, 22f)
            lineTo(12f, 20.5f); lineTo(9.7f, 22f); lineTo(7.3f, 20.5f); lineTo(5f, 22f); close()
            moveTo(8f, 6f); horizontalLineTo(16f); verticalLineTo(8f); horizontalLineTo(8f); close()
            moveTo(8f, 10f); horizontalLineTo(16f); verticalLineTo(12f); horizontalLineTo(8f); close()
            moveTo(8f, 14f); horizontalLineTo(13f); verticalLineTo(16f); horizontalLineTo(8f); close()
        }
    }

    /** A banknote: money received. */
    val Cash: ImageVector by lazy {
        icon("Cash") {
            moveTo(2f, 6f); horizontalLineTo(22f); verticalLineTo(18f); horizontalLineTo(2f); close()
            moveTo(15f, 12f); arcTo(3f, 3f, 0f, true, true, 9f, 12f); arcTo(3f, 3f, 0f, true, true, 15f, 12f); close()
        }
    }

    /** Wallet: money to receive. */
    val Wallet: ImageVector by lazy {
        icon("Wallet") {
            moveTo(3f, 6f); horizontalLineTo(19f); verticalLineTo(9f); horizontalLineTo(21f); verticalLineTo(19f)
            horizontalLineTo(3f); close()
            moveTo(14f, 12f); horizontalLineTo(19f); verticalLineTo(16f); horizontalLineTo(14f); close()
        }
    }

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd, pathBuilder = block)
            .build()
}
