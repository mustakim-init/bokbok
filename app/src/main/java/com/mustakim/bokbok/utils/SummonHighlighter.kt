package com.mustakim.bokbok.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Utility to highlight summon mentions in message text.
 * Highlights `\summon @username` and `\summon @everyone` with accent color.
 */
object SummonHighlighter {
    
    private val SUMMON_PATTERN = Regex("""\\summon\s+@(\S+)""", RegexOption.IGNORE_CASE)
    
    /**
     * Check if text contains any summon commands.
     */
    fun containsSummon(text: String): Boolean {
        return SUMMON_PATTERN.containsMatchIn(text)
    }
    
    /**
     * Build an AnnotatedString with highlighted summon mentions.
     * 
     * @param text The message text
     * @param highlightColor Color for the summon mentions
     * @return AnnotatedString with spans applied
     */
    fun highlightSummons(text: String, highlightColor: Color): AnnotatedString {
        return buildAnnotatedString {
            var lastEnd = 0
            
            SUMMON_PATTERN.findAll(text).forEach { match ->
                // Append text before the match
                append(text.substring(lastEnd, match.range.first))
                
                // Append the match with highlight
                withStyle(
                    SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(match.value)
                }
                
                lastEnd = match.range.last + 1
            }
            
            // Append remaining text
            if (lastEnd < text.length) {
                append(text.substring(lastEnd))
            }
        }
    }
    
    /**
     * Get highlighted version of just the @mention parts (not the full \summon command).
     * Useful for cleaner display.
     * 
     * @param text The message text
     * @param highlightColor Color for the mentions
     * @param showCommand If true, shows full "\summon @name", otherwise just "@name"
     */
    fun formatForDisplay(
        text: String, 
        highlightColor: Color,
        showCommand: Boolean = false
    ): AnnotatedString {
        if (!showCommand) {
            // Replace \summon @name with just @name (highlighted)
            val cleanedText = text.replace(Regex("""\\summon\s+@""", RegexOption.IGNORE_CASE), "@")
            return buildAnnotatedString {
                var lastEnd = 0
                
                Regex("""@(\S+)""").findAll(cleanedText).forEach { match ->
                    append(cleanedText.substring(lastEnd, match.range.first))
                    
                    withStyle(
                        SpanStyle(
                            color = highlightColor,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(match.value)
                    }
                    
                    lastEnd = match.range.last + 1
                }
                
                if (lastEnd < cleanedText.length) {
                    append(cleanedText.substring(lastEnd))
                }
            }
        }
        
        return highlightSummons(text, highlightColor)
    }
}
