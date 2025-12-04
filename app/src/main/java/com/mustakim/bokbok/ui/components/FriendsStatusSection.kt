package com.mustakim.bokbok.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.FriendStatus
import com.mustakim.bokbok.data.model.UserStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.combinedClickable


// Global scallop shape cached once
private val CachedScallopShape = ScallopShape()

@Composable
fun FriendsStatusSection(
    friends: List<FriendStatus>,
    modifier: Modifier = Modifier,
    onFriendClick: (FriendStatus) -> Unit,
    onFriendLongClick: ((FriendStatus) -> Unit)? = null
) {
    // Only compute online friends once per friends list change
    val onlineFriends = remember(friends) {
        friends.filter { it.status != UserStatus.OFFLINE }
    }

    if (onlineFriends.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = "Friends",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = onlineFriends,
                key = { friend -> friend.userId },
                contentType = { "friend_card" }
            ) { friend ->
                FriendStatusCard(
                    friend = friend,
                    onClick = { onFriendClick(friend) },
                    onLongClick = { onFriendLongClick?.invoke(friend) }
                )
            }
        }
    }
}

@Composable
fun FriendStatusCard(
    friend: FriendStatus,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val badgeText = remember(friend.status, friend.currentRoomCategory) {
        when {
            friend.status == UserStatus.IN_ROOM && friend.currentRoomCategory != null ->
                "Join\n${friend.currentRoomCategory.displayName}"
            friend.status == UserStatus.IN_ROOM ->
                "In room"
            friend.status == UserStatus.ONLINE ->
                "Online"
            else ->
                "Idle"
        }
    }

    val isInRoom = remember(friend.status) { friend.status == UserStatus.IN_ROOM }
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier = Modifier
                .height(42.dp)
                .combinedClickable(
                    enabled = friend.status == UserStatus.IN_ROOM && friend.currentRoomId != null,
                    onClick = onClick,
                    onLongClick = { onLongClick?.invoke() }
                )
        ) {
            ThoughtBubbleBadge(
                text = badgeText,
                isInRoom = isInRoom
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Card(
            modifier = Modifier
                .size(80.dp),
            shape = CachedScallopShape,
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (friend.profileImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = friend.profileImageUrl,
                        contentDescription = friend.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(primaryContainerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = friend.displayName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = onPrimaryContainerColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = friend.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ThoughtBubbleBadge(
    text: String,
    isInRoom: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isInRoom) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isInRoom) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier.offset(x = (-16).dp),
        contentAlignment = Alignment.TopStart
    ) {
        // Main bubble
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bubbleColor,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                color = textColor
            )
        }

        // Tail
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 8.dp, y = 6.dp)
                .size(14.dp, 10.dp)
        ) {
            val tailPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width * 0.6f, 0f)
                lineTo(size.width, size.height)
                close()
            }

            drawPath(
                path = tailPath,
                color = bubbleColor
            )
        }
    }
}

// Scallop shape with cached path so it's cheap at runtime
class ScallopShape(
    private val lobes: Int = 9,
    private val innerRadiusRatio: Float = 0.87f,
    private val smoothness: Float = 1f,
    private val rotationDegrees: Float = 30f
) : Shape {

    private var cachedPath: Path? = null
    private var cachedSize: Size? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (cachedPath == null || cachedSize != size) {
            cachedSize = size
            cachedPath = createScallopPath(size)
        }
        return Outline.Generic(cachedPath!!)
    }

    private fun createScallopPath(size: Size): Path {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f
        val baseInnerRadius = outerRadius * innerRadiusRatio
        val points = buildAlternatingPoints(
            lobes = lobes,
            center = center,
            outerR = outerRadius,
            baseInnerR = baseInnerRadius,
            rotationRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
        )
        return catmullRomClosedPath(points, smoothness)
    }

    private fun buildAlternatingPoints(
        lobes: Int,
        center: Offset,
        outerR: Float,
        baseInnerR: Float,
        rotationRad: Float
    ): List<Offset> {
        val pts = mutableListOf<Offset>()
        val step = 2.0 * PI / lobes
        for (i in 0 until lobes) {
            val baseAngle = i * step + rotationRad
            // Outer
            val aOuter = baseAngle
            val outerX = center.x + (outerR * cos(aOuter)).toFloat()
            val outerY = center.y + (outerR * sin(aOuter)).toFloat()
            pts.add(Offset(outerX, outerY))
            // Inner
            val aInner = baseAngle + step / 2.0
            val innerX = center.x + (baseInnerR * cos(aInner)).toFloat()
            val innerY = center.y + (baseInnerR * sin(aInner)).toFloat()
            pts.add(Offset(innerX, innerY))
        }
        return pts
    }

    private fun catmullRomClosedPath(points: List<Offset>, smoothness: Float): Path {
        val path = Path()
        if (points.isEmpty()) return path
        val n = points.size
        path.moveTo(points[0].x, points[0].y)
        val tension = 1f - smoothness
        val factor = (1f - tension) / 6f
        for (i in 0 until n) {
            val im1 = (i - 1 + n) % n
            val ip1 = (i + 1) % n
            val ip2 = (i + 2) % n
            val p0 = points[im1]
            val p1 = points[i]
            val p2 = points[ip1]
            val p3 = points[ip2]
            val c1 = Offset(
                x = p1.x + (p2.x - p0.x) * factor,
                y = p1.y + (p2.y - p0.y) * factor
            )
            val c2 = Offset(
                x = p2.x - (p3.x - p1.x) * factor,
                y = p2.y - (p3.y - p1.y) * factor
            )
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }
        path.close()
        return path
    }
}
