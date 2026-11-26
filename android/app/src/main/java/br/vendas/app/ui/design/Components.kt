package br.vendas.app.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.vendas.app.domain.Money
import br.vendas.app.ui.theme.AppTheme

val CardShape = RoundedCornerShape(24.dp)
val PillShape = RoundedCornerShape(50)
val FieldShape = RoundedCornerShape(16.dp)

/** Text fields sit on a white card with a clear border, so they stand out on the warm canvas. */
@Composable
fun fieldColors(): TextFieldColors {
    val colors = AppTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.card,
        unfocusedContainerColor = colors.card,
        disabledContainerColor = colors.card,
        errorContainerColor = colors.card,
        focusedBorderColor = colors.ink,
        unfocusedBorderColor = colors.faint,
        focusedLabelColor = colors.ink,
        unfocusedLabelColor = colors.muted,
        cursorColor = colors.ink,
    )
}

/** Page title area: optional back button, big title, subtitle and actions on the right. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (onBack == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                color = AppTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.muted, maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** A round, bordered icon button like the bell and settings buttons of the reference designs. */
@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.card)
            .border(1.dp, AppTheme.colors.cardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = AppTheme.colors.ink, modifier = Modifier.size(20.dp))
    }
}

/** White rounded card with a hairline border. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(AppTheme.colors.card)
            .border(1.dp, AppTheme.colors.cardBorder, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.ink)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted) }
        }
        action?.invoke()
    }
}

enum class TileStyle { LIGHT, DARK, DANGER }

/** Side-by-side KPI tiles of equal height. Give each tile `Modifier.weight(1f)`. */
@Composable
fun KpiRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A big number with its label, as in the reference dashboards' KPI row. */
@Composable
fun KpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    style: TileStyle = TileStyle.LIGHT,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val (background, foreground, secondary) = when (style) {
        TileStyle.LIGHT -> Triple(colors.card, colors.ink, colors.muted)
        TileStyle.DARK -> Triple(colors.inverseCard, colors.onInverseCard, colors.onInverseCard.copy(alpha = 0.7f))
        TileStyle.DANGER -> Triple(colors.dangerSolid, colors.onDangerSolid, colors.onDangerSolid.copy(alpha = 0.85f))
    }
    Column(
        modifier
            // In a KpiRow, tiles stretch to the tallest one so labels that wrap keep the row even.
            .fillMaxHeight()
            .clip(CardShape)
            .background(background)
            .then(if (style == TileStyle.LIGHT) Modifier.border(1.dp, colors.cardBorder, CardShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp)
            .heightIn(min = 92.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = secondary, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            // Wraps instead of cutting words when the phone uses a large font.
            Text(label, style = MaterialTheme.typography.labelLarge, color = secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 1) }
    }
}

enum class Tone { NEUTRAL, INFO, SUCCESS, WARNING, DANGER }

/** Small rounded status label with a tinted background. */
@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val (background, foreground) = when (tone) {
        Tone.NEUTRAL -> colors.neutralBg to colors.neutralText
        Tone.INFO -> colors.infoBg to colors.infoText
        Tone.SUCCESS -> colors.successBg to colors.successText
        Tone.WARNING -> colors.warningBg to colors.warningText
        Tone.DANGER -> colors.dangerBg to colors.dangerText
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = foreground,
        maxLines = 1,
        modifier = modifier.clip(PillShape).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** The black call-to-action button of the reference designs. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) AppTheme.colors.dangerSolid else AppTheme.colors.inverseCard,
            contentColor = if (danger) AppTheme.colors.onDangerSolid else AppTheme.colors.onInverseCard,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AppTheme.colors.card,
            contentColor = AppTheme.colors.ink,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        modifier = modifier,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Selectable pills, like the Daily / Weekly / Monthly switch of the references. */
@Composable
fun <T> PillTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Break into more lines instead of scrolling sideways, so no option is hidden past the edge. */
    wrap: Boolean = false,
) {
    val pills: @Composable () -> Unit = {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) AppTheme.colors.onInverseCard else AppTheme.colors.ink,
                modifier = Modifier
                    .clip(PillShape)
                    .background(if (isSelected) AppTheme.colors.inverseCard else AppTheme.colors.card)
                    .border(1.dp, if (isSelected) Color.Transparent else AppTheme.colors.cardBorder, PillShape)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
    if (wrap) {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { pills() }
    } else {
        Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { pills() }
    }
}

private val AVATAR_TONES = listOf(Tone.INFO, Tone.SUCCESS, Tone.WARNING, Tone.NEUTRAL)

/** Initials in a tinted circle; the tone follows the name so it stays the same everywhere. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp, tone: Tone? = null) {
    val colors = AppTheme.colors
    val chosen = tone ?: AVATAR_TONES[Math.floorMod(name.hashCode(), AVATAR_TONES.size)]
    val (background, foreground) = when (chosen) {
        Tone.INFO -> colors.infoBg to colors.infoText
        Tone.SUCCESS -> colors.successBg to colors.successText
        Tone.WARNING -> colors.warningBg to colors.warningText
        Tone.DANGER -> colors.dangerBg to colors.dangerText
        Tone.NEUTRAL -> colors.neutralBg to colors.neutralText
    }
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .let { parts -> (parts.firstOrNull()?.take(1).orEmpty() + (if (parts.size > 1) parts.last().take(1) else "")).uppercase() }
    Box(modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Text(initials, style = MaterialTheme.typography.labelLarge, color = foreground)
    }
}

/** Thick rounded progress bar on a light track. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = AppTheme.colors.ink, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(PillShape).background(AppTheme.colors.track)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(PillShape)
                .background(color),
        )
    }
}

@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = AppTheme.colors.cardBorder)
}

/** A list row inside a Panel: leading content, title/subtitle, trailing content and an optional arrow. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (showArrow) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppTheme.colors.faint,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * Solid red alert for customers who are late, meant to be impossible to miss.
 * Shows how many customers, how much and who.
 */
@Composable
fun OverdueAlert(
    customerCount: Int,
    total: Money,
    names: List<String>,
    modifier: Modifier = Modifier,
    actionLabel: String? = "Ver clientes atrasados",
    onClick: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.dangerSolid)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(colors.onDangerSolid.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.onDangerSolid, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (customerCount == 1) "1 cliente com pagamento atrasado" else "$customerCount clientes com pagamento atrasado",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onDangerSolid,
                )
                Text(
                    names.take(3).joinToString() + if (names.size > 3) " e mais ${names.size - 3}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onDangerSolid.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            total.format(),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onDangerSolid,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text("em atraso", style = MaterialTheme.typography.bodySmall, color = colors.onDangerSolid.copy(alpha = 0.85f))
        if (actionLabel != null && onClick != null) {
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .clip(PillShape)
                    .background(colors.onDangerSolid)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = colors.dangerSolid)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.dangerSolid,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** A thin colored strip at the start of a row, to flag overdue customers in lists. */
@Composable
fun AccentStrip(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.width(4.dp).height(40.dp).clip(PillShape).background(color))
}

@Composable
fun IconAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = contentDescription, tint = AppTheme.colors.ink) }
}
