package com.rayenghanmi.rytunex.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class ListItem(val bullet: String, val text: String, val level: Int = 0) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String = "") : MarkdownBlock()
    data class Alert(val type: AlertType, val lines: List<String>) : MarkdownBlock()
    data class Blockquote(val lines: List<String>) : MarkdownBlock()
    object Divider : MarkdownBlock()
}

enum class AlertType(val title: String) {
    NOTE("Note"),
    TIP("Tip"),
    IMPORTANT("Important"),
    WARNING("Warning"),
    CAUTION("Caution")
}

// Tokenizes markdown into structured blocks (headings, lists, code, alerts, dividers)
fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 1. Code blocks: ```
        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith("```")) {
                i++ // consume closing ```
            }
            blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language))
            continue
        }

        // 2. Horizontal divider: --- or *** or ___
        if (trimmed.matches(Regex("""^([-*_])\1{2,}$"""))) {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // 3. Blockquotes & GitHub-style Alerts: lines starting with >
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            var isAlert = false
            var alertType = AlertType.NOTE

            while (i < lines.size && lines[i].trim().startsWith(">")) {
                val qLine = lines[i].trim().removePrefix(">").trim()
                if (quoteLines.isEmpty()) {
                    val alertMatch = Regex("""^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]""", RegexOption.IGNORE_CASE).find(qLine)
                    if (alertMatch != null) {
                        isAlert = true
                        val typeStr = alertMatch.groupValues[1].uppercase()
                        alertType = when (typeStr) {
                            "TIP" -> AlertType.TIP
                            "IMPORTANT" -> AlertType.IMPORTANT
                            "WARNING" -> AlertType.WARNING
                            "CAUTION" -> AlertType.CAUTION
                            else -> AlertType.NOTE
                        }
                        val remainder = qLine.substring(alertMatch.range.last + 1).trim()
                        if (remainder.isNotEmpty()) {
                            quoteLines.add(remainder)
                        }
                    } else {
                        quoteLines.add(qLine)
                    }
                } else {
                    quoteLines.add(qLine)
                }
                i++
            }

            if (isAlert) {
                blocks.add(MarkdownBlock.Alert(alertType, quoteLines))
            } else {
                blocks.add(MarkdownBlock.Blockquote(quoteLines))
            }
            continue
        }

        // 4. Headings: #, ##, ###, ####, etc.
        val headingMatch = Regex("""^(#{1,6})\s+(.*)$""").find(trimmed)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val headingText = headingMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.Heading(level, headingText))
            i++
            continue
        }

        // 5. List items: - item, * item, + item, 1. item
        val listMatch = Regex("""^(\s*)([-*+]|\d+\.)\s+(.*)$""").find(line)
        if (listMatch != null) {
            val indent = listMatch.groupValues[1].length
            val bullet = listMatch.groupValues[2]
            val itemText = listMatch.groupValues[3].trim()
            blocks.add(MarkdownBlock.ListItem(bullet, itemText, indent / 2))
            i++
            continue
        }

        // 6. Regular paragraphs: join adjacent non-block lines
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size) {
            val pLine = lines[i].trim()
            if (pLine.isEmpty() ||
                pLine.startsWith("```") ||
                pLine.startsWith(">") ||
                pLine.matches(Regex("""^([-*_])\1{2,}$""")) ||
                pLine.matches(Regex("""^#{1,6}\s+.*""")) ||
                pLine.matches(Regex("""^(\s*)([-*+]|\d+\.)\s+.*"""))
            ) {
                break
            }
            paragraphLines.add(pLine)
            i++
        }
        if (paragraphLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString(" ")))
        }
    }

    return blocks
}

// Parses inline formatting (bold, italic, code, links, strikethrough) into AnnotatedString
@Composable
fun parseMarkdownInline(
    text: String,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    codeBgColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    codeTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
): AnnotatedString {
    return remember(text, primaryColor, codeBgColor, codeTextColor) {
        buildAnnotatedString {
            // Token regex groups: 1 link (text,url), 4 bold, 6 strikethrough, 8 code, 10 italic
            val tokenRegex = Regex("""(\[([^\]]+)\]\(([^)]+)\))|(\*\*([^*]+)\*\*)|(~~([^~]+)~~)|(`([^`]+)`)|(\*([^*]+)\*)""")
            var lastIndex = 0

            for (match in tokenRegex.findAll(text)) {
                if (match.range.first > lastIndex) {
                    append(text.substring(lastIndex, match.range.first))
                }
                when {
                    match.groups[2] != null -> { // Link: [text](url)
                        val linkText = match.groups[2]!!.value
                        val linkUrl = match.groups[3]!!.value
                        val start = length
                        append(linkText)
                        addStyle(
                            SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold
                            ),
                            start,
                            length
                        )
                        addStringAnnotation("URL", linkUrl, start, length)
                    }
                    match.groups[5] != null -> { // Bold: **text**
                        val boldText = match.groups[5]!!.value
                        val start = length
                        append(boldText)
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                    }
                    match.groups[7] != null -> { // Strikethrough: ~~text~~
                        val strikeText = match.groups[7]!!.value
                        val start = length
                        append(strikeText)
                        addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, length)
                    }
                    match.groups[9] != null -> { // Inline code: `text`
                        val codeText = match.groups[9]!!.value
                        val start = length
                        append(" $codeText ")
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBgColor,
                                color = codeTextColor,
                                fontSize = 0.9.em
                            ),
                            start,
                            length
                        )
                    }
                    match.groups[11] != null -> { // Italic: *text*
                        val italicText = match.groups[11]!!.value
                        val start = length
                        append(italicText)
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                    }
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }
}

// Text that renders styled markdown with clickable URL links
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val uriHandler = LocalUriHandler.current
    val annotated = parseMarkdownInline(text)
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = style,
        color = color,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { offset ->
                layoutResult?.let { layout ->
                    val position = layout.getOffsetForPosition(offset)
                    annotated.getStringAnnotations("URL", position, position)
                        .firstOrNull()?.let { annotation ->
                            try {
                                uriHandler.openUri(annotation.item)
                            } catch (_: Exception) {}
                        }
                }
            }
        }
    )
}

// Markdown renderer tailored for GitHub release notes
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    when (block.level) {
                        1 -> Text(
                            text = block.text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                        2 -> Text(
                            text = block.text,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                        else -> Text(
                            text = block.text,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    MarkdownText(
                        text = block.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.level * 14 + 2).dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (block.bullet.matches(Regex("""\d+\."""))) {
                            Text(
                                text = block.bullet,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(5.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                        MarkdownText(
                            text = block.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    @Suppress("DEPRECATION")
                    val clipboard = LocalClipboardManager.current
                    val context = LocalContext.current

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (block.language.isNotBlank()) block.language.uppercase() else "CODE / CHECKSUM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(block.code))
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                Text(
                                    text = block.code,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.Alert -> {
                    val alertConfig = getAlertConfig(block.type)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = alertConfig.containerColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, alertConfig.accentColor.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = alertConfig.icon,
                                    contentDescription = null,
                                    tint = alertConfig.accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = block.type.title.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = alertConfig.accentColor
                                )
                            }

                            // Render alert lines with full markdown block support
                            val alertSubBlocks = remember(block.lines) { parseMarkdownBlocks(block.lines.joinToString("\n")) }
                            alertSubBlocks.forEach { subBlock ->
                                when (subBlock) {
                                    is MarkdownBlock.Paragraph -> {
                                        MarkdownText(
                                            text = subBlock.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    is MarkdownBlock.Heading -> {
                                        Text(
                                            text = subBlock.text,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    is MarkdownBlock.ListItem -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = (subBlock.level * 12 + 2).dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (subBlock.bullet.matches(Regex("""\d+\."""))) {
                                                Text(
                                                    text = subBlock.bullet,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = alertConfig.accentColor
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(top = 6.dp)
                                                        .size(5.dp)
                                                        .background(alertConfig.accentColor, CircleShape)
                                                )
                                            }
                                            MarkdownText(
                                                text = subBlock.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            block.lines.forEach { qLine ->
                                MarkdownText(
                                    text = qLine,
                                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private data class AlertVisualConfig(
    val containerColor: Color,
    val accentColor: Color,
    val icon: ImageVector
)

@Composable
private fun getAlertConfig(type: AlertType): AlertVisualConfig {
    return when (type) {
        AlertType.NOTE -> AlertVisualConfig(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            accentColor = MaterialTheme.colorScheme.primary,
            icon = Icons.Rounded.Info
        )
        AlertType.TIP -> AlertVisualConfig(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
            accentColor = MaterialTheme.colorScheme.tertiary,
            icon = Icons.Rounded.TipsAndUpdates
        )
        AlertType.IMPORTANT -> AlertVisualConfig(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            accentColor = MaterialTheme.colorScheme.primary,
            icon = Icons.Rounded.PriorityHigh
        )
        AlertType.WARNING -> AlertVisualConfig(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            accentColor = MaterialTheme.colorScheme.error,
            icon = Icons.Rounded.Warning
        )
        AlertType.CAUTION -> AlertVisualConfig(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            accentColor = MaterialTheme.colorScheme.error,
            icon = Icons.Rounded.Report
        )
    }
}
