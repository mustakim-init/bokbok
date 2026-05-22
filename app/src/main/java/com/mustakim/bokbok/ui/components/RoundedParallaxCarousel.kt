package com.mustakim.bokbok.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> RoundedParallaxCarousel(
    items: List<T>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 48.dp),
    itemSpacing: Int = 16,
    content: @Composable (item: T, index: Int) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Empty state handled by caller
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { items.size })

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        contentPadding = contentPadding,
        pageSpacing = itemSpacing.dp,
        key = { page -> page },
        beyondViewportPageCount = 1  // ✅ Limit pre-composition
    ) { page ->
        // ✅ Use derivedStateOf to prevent recomposition on every scroll frame
        val pageOffset = remember {
            derivedStateOf {
                calculatePageOffset(pagerState, page)
            }
        }.value

        // ✅ Cache all transformations together for "Expressive" depth
        val transformations = remember(pageOffset) {
            val offsetClamped = pageOffset.coerceIn(0f, 1f)
            val scale = lerp(0.82f, 1f, 1f - offsetClamped)
            val alpha = lerp(0.4f, 1f, 1f - offsetClamped)
            val rotation = lerp(12f, 0f, 1f - pageOffset.coerceIn(-1f, 1f).absoluteValue) * (if (pageOffset > 0) -1 else 1)
            
            Triple(scale, alpha, rotation)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = transformations.first
                    scaleY = transformations.first
                    alpha = transformations.second
                    // rotationY = transformations.third // Rotation might be too much for some devices, keep it subtle
                },
            contentAlignment = Alignment.Center
        ) {
            content(items[page], page)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun calculatePageOffset(pagerState: PagerState, page: Int): Float {
    return ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
}