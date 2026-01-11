package com.mustakim.bokbok.ui.screens.ai

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.AIMessage
import com.mustakim.bokbok.data.model.MessageRole

@Composable
fun AIMessageBubble(
    message: AIMessage,
    isGenerating: Boolean = false
) {
    val isUser = message.role == MessageRole.USER
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = if (isUser) primaryColor else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    
    val cornerRadius = 24.dp
    val shape = if (isUser) {
        RoundedCornerShape(cornerRadius, cornerRadius, 4.dp, cornerRadius)
    } else {
        RoundedCornerShape(4.dp, cornerRadius, cornerRadius, cornerRadius)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(primaryColor, primaryColor.copy(alpha = 0.8f))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BokBok AI",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
            }
        }
        
        Box(
            modifier = Modifier
                .then(
                    if (isUser) Modifier.widthIn(max = 300.dp).align(Alignment.End).padding(end = 16.dp)
                    else Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
                .background(containerColor, shape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            if (message.content.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(message.content))
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    )
                }
                .padding(14.dp)
                .animateContentSize()
        ) {
                SelectionContainer {
                    Column {
                        if (message.imageUri != null) {
                            AsyncImage(
                                model = message.imageUri,
                                contentDescription = "Uploaded Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                        
                        if (isGenerating) {
                            ThreeDotsTypingAnimation()
                        } else if (message.content.isNotEmpty()) {
                            val styledText = remember(message.content) {
                                MarkdownParser.parse(message.content, primaryColor = primaryColor)
                            }
                            Text(
                                text = styledText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor,
                                lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                            )
                        }
                    }
                }
        }
    }
}

@Composable
fun ThreeDotsTypingAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val dotCount = 3
    val dots = List(dotCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 600
                    0f at index * 100
                    1f at index * 100 + 150
                    0f at index * 100 + 300
                },
                repeatMode = RepeatMode.Restart
            )
        )
    }

    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        dots.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha.value), CircleShape)
            )
        }
    }
}
