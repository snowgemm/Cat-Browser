@file:Suppress("ktlint:standard:function-naming")

package io.github.snowgemm.catbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.net.URI

internal enum class ReleaseNoteBlockType {
    HEADING,
    BULLET,
    PARAGRAPH,
    SPACER,
    DIVIDER,
}

internal data class ReleaseNoteBlock(
    val type: ReleaseNoteBlockType,
    val text: String = "",
    val level: Int = 0,
    val indent: Int = 0,
    val marker: String = "",
)

private val headingPattern = Regex("^(#{1,4})\\s+(.+?)\\s*#*$")
private val bulletPattern = Regex("^(\\s*)[-*+]\\s+(.+)$")
private val numberedPattern = Regex("^(\\s*)(\\d+[.)])\\s+(.+)$")
private val dividerPattern = Regex("^(---+|\\*\\*\\*+|___+)$")
internal val inlineMarkdownPattern =
    Regex(
        """\\([\\`*_{}\[\]()#+\-.!|<>~])|""" +
            """\*\*(.+?)\*\*|`([^`]+)`|\*([^*]+)\*|""" +
            """\[([^\]]+)]\((https://[^)\s]+)\)|(https://[^\s)]+)""",
    )

internal fun parseReleaseNotes(markdown: String): List<ReleaseNoteBlock> {
    val blocks = mutableListOf<ReleaseNoteBlock>()
    var previousWasSpacer = true

    markdown.lineSequence().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> {
                if (!previousWasSpacer && blocks.isNotEmpty()) {
                    blocks += ReleaseNoteBlock(ReleaseNoteBlockType.SPACER)
                }
                previousWasSpacer = true
            }
            dividerPattern.matches(trimmed) -> {
                blocks += ReleaseNoteBlock(ReleaseNoteBlockType.DIVIDER)
                previousWasSpacer = false
            }
            else -> {
                val heading = headingPattern.matchEntire(trimmed)
                val bullet = bulletPattern.matchEntire(line)
                val numbered = numberedPattern.matchEntire(line)
                when {
                    heading != null ->
                        blocks +=
                            ReleaseNoteBlock(
                                type = ReleaseNoteBlockType.HEADING,
                                text = heading.groupValues[2],
                                level = heading.groupValues[1].length,
                            )
                    bullet != null ->
                        blocks +=
                            ReleaseNoteBlock(
                                type = ReleaseNoteBlockType.BULLET,
                                text = bullet.groupValues[2],
                                indent = bullet.groupValues[1].length / 2,
                                marker = "•",
                            )
                    numbered != null ->
                        blocks +=
                            ReleaseNoteBlock(
                                type = ReleaseNoteBlockType.BULLET,
                                text = numbered.groupValues[3],
                                indent = numbered.groupValues[1].length / 2,
                                marker = numbered.groupValues[2],
                            )
                    else ->
                        blocks +=
                            ReleaseNoteBlock(
                                type = ReleaseNoteBlockType.PARAGRAPH,
                                text = trimmed,
                            )
                }
                previousWasSpacer = false
            }
        }
    }

    while (blocks.lastOrNull()?.type == ReleaseNoteBlockType.SPACER) {
        blocks.removeAt(blocks.lastIndex)
    }
    return blocks
}

@Composable
internal fun ReleaseNotesContent(markdown: String) {
    val blocks = remember(markdown) { parseReleaseNotes(markdown) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        blocks.forEach { block ->
            when (block.type) {
                ReleaseNoteBlockType.HEADING -> {
                    val style =
                        when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                    Text(
                        text = block.text,
                        modifier = Modifier.padding(top = 4.dp),
                        style = style,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                }
                ReleaseNoteBlockType.BULLET ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = (block.indent * 12).dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = block.marker,
                            modifier = Modifier.padding(end = 7.dp),
                            color = colors.secondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = markdownText(block.text, colors.secondary, colors.onSurface),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                ReleaseNoteBlockType.PARAGRAPH ->
                    Text(
                        text = markdownText(block.text, colors.secondary, colors.onSurface),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                ReleaseNoteBlockType.SPACER -> Spacer(Modifier.height(3.dp))
                ReleaseNoteBlockType.DIVIDER -> HorizontalDivider(color = colors.outlineVariant)
            }
        }
    }
}

private fun markdownText(
    value: String,
    linkColor: Color,
    codeColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        inlineMarkdownPattern.findAll(value).forEach { match ->
            append(value.substring(cursor, match.range.first))
            val escaped = match.groups[1]?.value
            val bold = match.groups[2]?.value
            val code = match.groups[3]?.value
            val italic = match.groups[4]?.value
            val linkLabel = match.groups[5]?.value
            val markdownUrl = match.groups[6]?.value
            val plainUrl = match.groups[7]?.value

            when {
                escaped != null -> append(escaped)
                bold != null ->
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(bold)
                    }
                code != null ->
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor,
                            background = codeColor.copy(alpha = 0.10f),
                        ),
                    ) {
                        append(code)
                    }
                italic != null ->
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(italic)
                    }
                linkLabel != null && markdownUrl != null && isSafeReleaseUrl(markdownUrl) -> {
                    val visibleLabel = linkLabel.removeSurrounding("`")
                    val link =
                        LinkAnnotation.Url(
                            markdownUrl,
                            TextLinkStyles(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        )
                    withLink(link) {
                        if (visibleLabel != linkLabel) {
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                                append(visibleLabel)
                            }
                        } else {
                            append(linkLabel)
                        }
                    }
                }
                plainUrl != null && isSafeReleaseUrl(plainUrl) -> {
                    val link =
                        LinkAnnotation.Url(
                            plainUrl,
                            TextLinkStyles(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        )
                    withLink(link) { append(plainUrl) }
                }
                else -> append(match.value)
            }
            cursor = match.range.last + 1
        }
        append(value.substring(cursor))
    }

private fun isSafeReleaseUrl(value: String): Boolean =
    runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
