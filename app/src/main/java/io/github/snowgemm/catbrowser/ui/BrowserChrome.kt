package io.github.snowgemm.catbrowser.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin


private val CatDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA9C9F7), onPrimary = Color(0xFF10243C),
    secondary = Color(0xFFE6C47D), onSecondary = Color(0xFF2A210F),
    background = Color(0xFF10161E), onBackground = Color(0xFFF1F4F8),
    surface = Color(0xFF1B2633), onSurface = Color(0xFFF1F4F8),
    surfaceVariant = Color(0xFF293747), onSurfaceVariant = Color(0xFFBAC7D5),
    outline = Color(0xFF405164), secondaryContainer = Color(0xFF34465C),
    onSecondaryContainer = Color(0xFFEAF1FB)
)

private val CatLightColorScheme = lightColorScheme(
    primary = Color(0xFF416B9B), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9F8), onPrimaryContainer = Color(0xFF173451),
    secondary = Color(0xFF8F713B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E7D4), onSecondaryContainer = Color(0xFF392B13),
    background = Color(0xFFF3F6FA), onBackground = Color(0xFF1B2530),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1B2530),
    surfaceVariant = Color(0xFFE8EEF5), onSurfaceVariant = Color(0xFF526171),
    outline = Color(0xFFD2DCE8)
)


internal enum class BrowserGlyphType {
    BACK,
    FORWARD,
    LOCK,
    GLOBE,
    CLEAR,
    CLOSE,
    GO,
    TABS,
    MORE,
    INCOGNITO,
    SEARCH,
    DOWNLOAD,
    PRINT,
    HOME,
    RELOAD,
    BOOKMARK,
    EXTENSIONS,
    BOOKS,
    HISTORY,
    NEW_TAB,
    SETTINGS,
    AI,
}

@Composable
internal fun BrowserGlyph(
    type: BrowserGlyphType,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp
) {
    Canvas(modifier.size(iconSize)) {
        val unit = size.minDimension / 24f
        val strokeWidth = 1.8f * unit

        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(
                color = tint,
                start = point(x1, y1),
                end = point(x2, y2),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        when (type) {
            BrowserGlyphType.BACK -> {
                line(19f, 12f, 5f, 12f)
                line(5f, 12f, 11f, 6f)
                line(5f, 12f, 11f, 18f)
            }
            BrowserGlyphType.FORWARD, BrowserGlyphType.GO -> {
                line(5f, 12f, 19f, 12f)
                line(19f, 12f, 13f, 6f)
                line(19f, 12f, 13f, 18f)
            }
            BrowserGlyphType.LOCK -> {
                drawRoundRect(
                    tint,
                    topLeft = point(5f, 10f),
                    size = Size(14f * unit, 10f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = Stroke(strokeWidth)
                )
                val shackle = Path().apply {
                    moveTo(8f * unit, 10f * unit)
                    lineTo(8f * unit, 7.5f * unit)
                    cubicTo(8f * unit, 2.5f * unit, 16f * unit, 2.5f * unit, 16f * unit, 7.5f * unit)
                    lineTo(16f * unit, 10f * unit)
                }
                drawPath(shackle, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                line(12f, 14f, 12f, 17f)
            }
            BrowserGlyphType.GLOBE -> {
                drawCircle(tint, 9f * unit, point(12f, 12f), style = Stroke(strokeWidth))
                drawOval(
                    tint,
                    topLeft = point(8f, 3f),
                    size = Size(8f * unit, 18f * unit),
                    style = Stroke(strokeWidth)
                )
                line(3.5f, 12f, 20.5f, 12f)
            }
            BrowserGlyphType.CLEAR, BrowserGlyphType.CLOSE -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
            BrowserGlyphType.SEARCH -> {
                drawCircle(tint, 6.5f * unit, point(10f, 10f), style = Stroke(strokeWidth))
                line(14.5f, 14.5f, 20.5f, 20.5f)
            }
            BrowserGlyphType.AI -> {
                drawRoundRect(
                    tint,
                    topLeft = point(3f, 4f),
                    size = Size(18f * unit, 14f * unit),
                    cornerRadius = CornerRadius(4f * unit),
                    style = Stroke(strokeWidth)
                )
                line(8f, 18f, 6f, 21f)
                line(6f, 21f, 6f, 17f)
                line(12f, 7f, 12f, 14f)
                line(8.5f, 10.5f, 15.5f, 10.5f)
                line(17f, 15f, 17f, 19f)
                line(15f, 17f, 19f, 17f)
            }
            BrowserGlyphType.DOWNLOAD -> {
                line(12f, 3f, 12f, 15f)
                line(7f, 10f, 12f, 15f)
                line(17f, 10f, 12f, 15f)
                line(4f, 18f, 4f, 21f)
                line(4f, 21f, 20f, 21f)
                line(20f, 21f, 20f, 18f)
            }
            BrowserGlyphType.PRINT -> {
                drawRoundRect(
                    tint,
                    topLeft = point(4f, 8f),
                    size = Size(16f * unit, 9f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = Stroke(strokeWidth),
                )
                drawRoundRect(
                    tint,
                    topLeft = point(7f, 3f),
                    size = Size(10f * unit, 7f * unit),
                    cornerRadius = CornerRadius(1f * unit),
                    style = Stroke(strokeWidth),
                )
                drawRect(
                    tint,
                    topLeft = point(7f, 14f),
                    size = Size(10f * unit, 7f * unit),
                    style = Stroke(strokeWidth),
                )
                line(9f, 17f, 15f, 17f)
                line(9f, 19f, 14f, 19f)
            }
            BrowserGlyphType.INCOGNITO -> {
                // Hat brim and glasses create a recognizable private-browsing mark.
                line(3f, 9f, 21f, 9f)
                line(7f, 9f, 9f, 5f)
                line(9f, 5f, 15f, 5f)
                line(15f, 5f, 17f, 9f)
                drawRoundRect(
                    tint,
                    topLeft = point(4f, 12f),
                    size = Size(6f * unit, 5f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = Stroke(strokeWidth)
                )
                drawRoundRect(
                    tint,
                    topLeft = point(14f, 12f),
                    size = Size(6f * unit, 5f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = Stroke(strokeWidth)
                )
                line(10f, 14.5f, 14f, 14.5f)
                line(7f, 18f, 12f, 21f)
                line(12f, 21f, 17f, 18f)
            }
            BrowserGlyphType.TABS -> {
                drawRoundRect(
                    tint,
                    topLeft = point(7f, 4f),
                    size = Size(13f * unit, 15f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = Stroke(strokeWidth)
                )
                drawRoundRect(
                    tint,
                    topLeft = point(4f, 7f),
                    size = Size(13f * unit, 14f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = Stroke(strokeWidth)
                )
            }
            BrowserGlyphType.MORE -> {
                for (y in listOf(5f, 12f, 19f)) {
                    drawCircle(tint, 1.6f * unit, point(12f, y))
                }
            }
            BrowserGlyphType.HOME -> {
                val home = Path().apply {
                    moveTo(3f * unit, 11f * unit)
                    lineTo(12f * unit, 4f * unit)
                    lineTo(21f * unit, 11f * unit)
                    moveTo(5.5f * unit, 10f * unit)
                    lineTo(5.5f * unit, 20f * unit)
                    lineTo(18.5f * unit, 20f * unit)
                    lineTo(18.5f * unit, 10f * unit)
                    moveTo(10f * unit, 20f * unit)
                    lineTo(10f * unit, 14f * unit)
                    lineTo(14f * unit, 14f * unit)
                    lineTo(14f * unit, 20f * unit)
                }
                drawPath(home, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
            BrowserGlyphType.RELOAD -> {
                drawArc(
                    tint,
                    startAngle = 42f,
                    sweepAngle = 285f,
                    useCenter = false,
                    topLeft = point(4f, 4f),
                    size = Size(16f * unit, 16f * unit),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                line(17f, 4.5f, 20f, 5f)
                line(20f, 5f, 19.5f, 8f)
            }
            BrowserGlyphType.BOOKMARK -> {
                val bookmark = Path().apply {
                    moveTo(6f * unit, 4f * unit)
                    lineTo(18f * unit, 4f * unit)
                    lineTo(18f * unit, 21f * unit)
                    lineTo(12f * unit, 16.5f * unit)
                    lineTo(6f * unit, 21f * unit)
                    close()
                }
                drawPath(bookmark, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
            BrowserGlyphType.EXTENSIONS -> {
                val puzzle = Path().apply {
                    moveTo(9f * unit, 4f * unit)
                    lineTo(15f * unit, 4f * unit)
                    lineTo(15f * unit, 7f * unit)
                    cubicTo(15f * unit, 9f * unit, 19f * unit, 8.5f * unit, 19f * unit, 11.5f * unit)
                    cubicTo(19f * unit, 14.5f * unit, 15f * unit, 14f * unit, 15f * unit, 17f * unit)
                    lineTo(15f * unit, 20f * unit)
                    lineTo(9f * unit, 20f * unit)
                    lineTo(9f * unit, 17f * unit)
                    cubicTo(9f * unit, 14f * unit, 5f * unit, 14.5f * unit, 5f * unit, 11.5f * unit)
                    cubicTo(5f * unit, 8.5f * unit, 9f * unit, 9f * unit, 9f * unit, 7f * unit)
                    close()
                }
                drawPath(puzzle, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
            BrowserGlyphType.BOOKS -> {
                val books = Path().apply {
                    moveTo(4f * unit, 6f * unit)
                    lineTo(10f * unit, 4f * unit)
                    lineTo(10f * unit, 19f * unit)
                    lineTo(4f * unit, 21f * unit)
                    close()
                    moveTo(10f * unit, 4f * unit)
                    lineTo(16f * unit, 6f * unit)
                    lineTo(16f * unit, 21f * unit)
                    lineTo(10f * unit, 19f * unit)
                    moveTo(18f * unit, 6f * unit)
                    lineTo(21f * unit, 7f * unit)
                    lineTo(21f * unit, 20f * unit)
                    lineTo(18f * unit, 19f * unit)
                    close()
                }
                drawPath(books, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
            BrowserGlyphType.HISTORY -> {
                drawArc(
                    tint,
                    startAngle = 35f,
                    sweepAngle = 325f,
                    useCenter = false,
                    topLeft = point(4f, 4f),
                    size = Size(16f * unit, 16f * unit),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                line(12f, 7f, 12f, 12f)
                line(12f, 12f, 16f, 14f)
                line(3f, 8f, 3f, 4f)
                line(3f, 4f, 7f, 4f)
            }
            BrowserGlyphType.NEW_TAB -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }
            BrowserGlyphType.SETTINGS -> {
                drawCircle(tint, 7.5f * unit, point(12f, 12f), style = Stroke(strokeWidth))
                drawCircle(tint, 2.4f * unit, point(12f, 12f), style = Stroke(strokeWidth))
                for (index in 0 until 8) {
                    val angle = index * Math.PI / 4.0
                    val inner = 8.5f
                    val outer = 10.8f
                    line(
                        12f + cos(angle).toFloat() * inner,
                        12f + sin(angle).toFloat() * inner,
                        12f + cos(angle).toFloat() * outer,
                        12f + sin(angle).toFloat() * outer
                    )
                }
            }
        }
    }
}

@Composable
internal fun DockAction(
    icon: BrowserGlyphType,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        color = Color.Transparent,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.widthIn(min = 40.dp)
            .height(44.dp)
            .semantics { contentDescription = label }
    ) {
        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            BrowserGlyph(icon)
        }
    }
}

@Composable
internal fun TabSwitcherAction(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(min = 42.dp)
            .height(44.dp)
            .semantics { contentDescription = "Các tab, " + count + " tab đang mở" }
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            BrowserGlyph(BrowserGlyphType.TABS, iconSize = 18.dp)
            Text(count.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun MenuDivider() {
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    )
}

@Composable
internal fun BrowserMenuListAction(
    icon: BrowserGlyphType,
    label: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(13.dp),
        color = Color.Transparent,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    BrowserGlyph(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant, iconSize = 19.dp)
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
internal fun BrowserMenuAction(
    icon: BrowserGlyphType,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.padding(4.dp).heightIn(min = 84.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (enabled) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    BrowserGlyph(
                        icon,
                        tint = if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        iconSize = 19.dp
                    )
                }
            }
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
