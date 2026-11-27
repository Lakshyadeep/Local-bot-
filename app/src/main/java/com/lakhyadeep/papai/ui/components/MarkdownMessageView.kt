package com.lakhyadeep.papai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lakhyadeep.papai.ui.theme.CodeBlockBgDark
import com.lakhyadeep.papai.ui.theme.MonoFontFamily
import kotlinx.coroutines.delay

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Numbered(val index: String, val text: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class CodeBlock(val language: String, val code: String) : MdBlock()
    data object Divider : MdBlock()
}

@Composable
fun MarkdownMessageView(
    markdown: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = formatInlineMarkdown(block.text, textColor),
                        style = style,
                        color = textColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = formatInlineMarkdown(block.text, textColor),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }

                is MdBlock.Bullet -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = formatInlineMarkdown(block.text, textColor),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.Numbered -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.index}.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = formatInlineMarkdown(block.text, textColor),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .padding(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = formatInlineMarkdown(block.text, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.9f)
                        )
                    }
                }

                is MdBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                is MdBlock.CodeBlock -> {
                    CodeBlockCard(language = block.language, code = block.code)
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800L)
            copied = false
        }
    }

    val highlighted = remember(code, language) { highlightCodeSyntax(code, language) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = CodeBlockBgDark
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF162033))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("copy_code_block_button")
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code block",
                        tint = if (copied) Color(0xFF2DD4BF) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = highlighted,
                    fontFamily = MonoFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.lines()
    val blocks = mutableListOf<MdBlock>()
    var inCode = false
    var codeLang = ""
    val codeBuf = StringBuilder()
    val paragraphBuf = StringBuilder()

    fun flushParagraph() {
        val text = paragraphBuf.toString().trim()
        if (text.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(text))
        }
        paragraphBuf.clear()
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (!inCode) {
                flushParagraph()
                inCode = true
                codeLang = trimmed.removePrefix("```").trim()
                codeBuf.clear()
            } else {
                inCode = false
                blocks.add(MdBlock.CodeBlock(codeLang, codeBuf.toString().trimEnd()))
                codeLang = ""
                codeBuf.clear()
            }
            continue
        }

        if (inCode) {
            codeBuf.append(line).append('\n')
            continue
        }

        when {
            trimmed.isEmpty() -> flushParagraph()
            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                blocks.add(MdBlock.Divider)
            }
            trimmed.startsWith("### ") -> {
                flushParagraph()
                blocks.add(MdBlock.Heading(3, trimmed.removePrefix("### ")))
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                blocks.add(MdBlock.Heading(2, trimmed.removePrefix("## ")))
            }
            trimmed.startsWith("# ") -> {
                flushParagraph()
                blocks.add(MdBlock.Heading(1, trimmed.removePrefix("# ")))
            }
            trimmed.startsWith("> ") -> {
                flushParagraph()
                blocks.add(MdBlock.Quote(trimmed.removePrefix("> ")))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks.add(MdBlock.Bullet(trimmed.drop(2)))
            }
            Regex("^\\d+\\.\\s+.*").matches(trimmed) -> {
                flushParagraph()
                val idx = trimmed.substringBefore('.')
                val body = trimmed.substringAfter('.').trim()
                blocks.add(MdBlock.Numbered(idx, body))
            }
            else -> {
                if (paragraphBuf.isNotEmpty()) paragraphBuf.append('\n')
                paragraphBuf.append(line)
            }
        }
    }

    if (inCode && codeBuf.isNotEmpty()) {
        blocks.add(MdBlock.CodeBlock(codeLang, codeBuf.toString().trimEnd()))
    } else {
        flushParagraph()
    }

    return blocks
}

private fun formatInlineMarkdown(text: String, baseColor: Color): AnnotatedString {
    val tokenRegex = Regex("(`[^`]+`|\\*\\*[^*]+\\*\\*|\\*[^*]+\\*)")
    return buildAnnotatedString {
        var lastIndex = 0
        for (match in tokenRegex.findAll(text)) {
            if (match.range.first > lastIndex) {
                append(text.substring(lastIndex, match.range.first))
            }
            val token = match.value
            when {
                token.startsWith("`") && token.endsWith("`") -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 13.sp,
                            background = baseColor.copy(alpha = 0.10f)
                        )
                    ) {
                        append(token.removeSurrounding("`"))
                    }
                }
                token.startsWith("**") && token.endsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(token.removeSurrounding("**"))
                    }
                }
                token.startsWith("*") && token.endsWith("*") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.removeSurrounding("*"))
                    }
                }
                else -> append(token)
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

private fun highlightCodeSyntax(code: String, @Suppress("UNUSED_PARAMETER") language: String): AnnotatedString {
    val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "return", "if", "else", "when",
        "for", "while", "import", "package", "private", "public", "suspend", "data", "sealed",
        "def", "async", "await", "try", "catch", "finally", "const", "let", "function", "export",
        "true", "false", "null", "None", "int", "float", "string", "bool", "void", "override"
    )
    val tokenRegex = Regex(
        "(//[^\\n]*|#[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z0-9_]*\\b)"
    )
    return buildAnnotatedString {
        var last = 0
        for (m in tokenRegex.findAll(code)) {
            if (m.range.first > last) {
                append(code.substring(last, m.range.first))
            }
            val tok = m.value
            when {
                tok.startsWith("//") || tok.startsWith("#") -> {
                    withStyle(SpanStyle(color = Color(0xFF64748B), fontStyle = FontStyle.Italic)) {
                        append(tok)
                    }
                }
                tok.startsWith("\"") || tok.startsWith("'") -> {
                    withStyle(SpanStyle(color = Color(0xFF34D399))) {
                        append(tok)
                    }
                }
                tok.first().isDigit() -> {
                    withStyle(SpanStyle(color = Color(0xFFFBBF24))) {
                        append(tok)
                    }
                }
                tok in keywords -> {
                    withStyle(SpanStyle(color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)) {
                        append(tok)
                    }
                }
                tok.first().isUpperCase() -> {
                    withStyle(SpanStyle(color = Color(0xFFC084FC))) {
                        append(tok)
                    }
                }
                else -> append(tok)
            }
            last = m.range.last + 1
        }
        if (last < code.length) {
            append(code.substring(last))
        }
    }
}
