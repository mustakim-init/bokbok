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

        // ✅ Cache all transformations together
        val transformations = remember(pageOffset) {
            val offsetClamped = pageOffset.coerceIn(0f, 1f)
            Triple(
                lerp(0.85f, 1f, 1f - offsetClamped), // scale
                lerp(0.5f, 1f, 1f - offsetClamped),  // alpha
                pageOffset * 50f                      // translationX
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    // ✅ Apply all transformations at once
                    scaleX = transformations.first
                    scaleY = transformations.first
                    translationX = transformations.third
                    alpha = transformations.second

                }
        ) {
            content(items[page], page)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun calculatePageOffset(pagerState: PagerState, page: Int): Float {
    return ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
}