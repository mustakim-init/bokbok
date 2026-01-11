package com.mustakim.bokbok.ui.screens.ai

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object MarkdownParser {
    /**
     * A simple parser to convert basic Markdown (bold, italic, inline code, headers) 
     * into Compose AnnotatedString with hierarchical sizing.
     */
    fun parse(text: String, primaryColor: Color): AnnotatedString {
        return buildAnnotatedString {
            val lines = text.split("\n")
            lines.forEachIndexed { index, line ->
                val trimmedLine = line.trim()
                when {
                    // Headers: # Header (H1), ## Header (H2), ### Header (H3)
                    trimmedLine.startsWith("#") -> {
                        val level = trimmedLine.takeWhile { it == '#' }.length
                        val headerText = trimmedLine.drop(level).trim()
                        if (level in 1..6 && headerText.isNotEmpty()) {
                            val fontSize = when (level) {
                                1 -> 24.sp
                                2 -> 20.sp
                                3 -> 18.sp
                                else -> 16.sp
                            }
                            withStyle(SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSize
                            )) {
                                parseInline(this, headerText, primaryColor)
                            }
                        } else {
                            parseInline(this, line, primaryColor)
                        }
                    }
                    // Bullet points: - item or * item
                    (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ")) && !trimmedLine.startsWith("**") -> {
                        append("  • ")
                        parseInline(this, trimmedLine.drop(2), primaryColor)
                    }
                    // Numbered lists: 1. item
                    trimmedLine.firstOrNull()?.isDigit() == true && trimmedLine.contains(". ") -> {
                        val dotIndex = trimmedLine.indexOf(". ")
                        val number = trimmedLine.substring(0, dotIndex + 2)
                        val content = trimmedLine.substring(dotIndex + 2)
                        append("  $number")
                        parseInline(this, content, primaryColor)
                    }
                    else -> {
                        parseInline(this, line, primaryColor)
                    }
                }
                if (index < lines.size - 1) append("\n")
            }
        }
    }

    private fun parseInline(builder: AnnotatedString.Builder, text: String, primaryColor: Color) {
        var i = 0
        while (i < text.length) {
            when {
                // Bold: **text** or __text__
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val symbol = text.substring(i, i + 2)
                    val end = text.indexOf(symbol, i + 2)
                    if (end != -1) {
                        builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        builder.append(symbol)
                        i += 2
                    }
                }
                // Italic: *text* or _text_
                (text.startsWith("*", i) && !text.startsWith("**", i)) || 
                (text.startsWith("_", i) && !text.startsWith("__", i)) -> {
                    val symbol = text.substring(i, i + 1)
                    val end = text.indexOf(symbol, i + 1)
                    if (end != -1) {
                        builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        builder.append(symbol)
                        i += 1
                    }
                }
                // Inline Code: `code`
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        builder.withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = primaryColor.copy(alpha = 0.1f),
                            color = primaryColor
                        )) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        builder.append("`")
                        i += 1
                    }
                }
                else -> {
                    builder.append(text[i])
                    i++
                }
            }
        }
    }
}
