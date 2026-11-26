package br.vendas.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.vendas.app.ui.theme.AppTheme

/** One bar: its label under the axis, its value and the short text shown above it. */
data class Bar(val label: String, val value: Long, val valueLabel: String, val highlighted: Boolean = false)

/**
 * Vertical bars with rounded tops, highlighted bar in ink and the others in a
 * light tone, like the reference "Trend" chart. Tapping a bar calls [onBarClick].
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 160.dp,
    onBarClick: ((Int) -> Unit)? = null,
) {
    val max = (bars.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)
    val colors = AppTheme.colors
    Row(
        modifier.fillMaxWidth().semantics {
            contentDescription = bars.joinToString { "${it.label}: ${it.valueLabel}" }
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { index, bar ->
            Column(
                Modifier
                    .weight(1f)
                    .then(if (onBarClick != null) Modifier.clickable { onBarClick(index) } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    bar.valueLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bar.highlighted) colors.ink else colors.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                )
                Box(Modifier.height(chartHeight).fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.BottomCenter) {
                    val fraction = bar.value.toFloat() / max
                    Box(
                        Modifier
                            .fillMaxWidth(0.72f)
                            .fillMaxHeight(fraction.coerceAtLeast(if (bar.value > 0) 0.03f else 0.015f))
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(if (bar.highlighted) colors.ink else colors.track),
                    )
                }
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bar.highlighted) colors.ink else colors.muted,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

data class Slice(val label: String, val value: Long, val color: Color)

/** Ring chart with the total in the middle, like the reference "Sales" donut. */
@Composable
fun DonutChart(
    slices: List<Slice>,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    val track = AppTheme.colors.track
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            slices.filter { it.value > 0 }.forEach { slice ->
                val sweep = 360f * slice.value / total
                drawArc(slice.color, start, sweep - 1.5f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerValue, style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.ink)
            Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.muted)
        }
    }
}

@Composable
fun Legend(color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
            Text(value, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.ink)
        }
    }
}

/** A ranked line with a horizontal bar, like the reference "Outcome statistics". */
@Composable
fun RankedBar(
    rank: Int,
    title: String,
    detail: String,
    value: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.faint, modifier = Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.ink)
        }
        ProgressLine(fraction, Modifier.padding(start = 22.dp, top = 8.dp), height = 6.dp)
    }
}
