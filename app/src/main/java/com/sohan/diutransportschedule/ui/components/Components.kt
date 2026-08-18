package com.sohan.diutransportschedule.ui.components

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.sohan.diutransportschedule.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
// import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs


@Composable
fun DotsLoadingIndicator(
    modifier: Modifier = Modifier,
    dotSize: Int = 10,
    dotGap: Int = 8
) {
    val transition = rememberInfiniteTransition(label = "dots")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = { it }),
            repeatMode = RepeatMode.Restart
        ),
        label = "t"
    )

    // Three dots pulse with phase offsets
    fun phase(offset: Float): Float {
        val x = (t + offset) % 1f
        // triangle wave 0..1..0
        return 1f - abs(2f * x - 1f)
    }

    val a1 = 0.35f + 0.65f * phase(0.0f)
    val a2 = 0.35f + 0.65f * phase(0.22f)
    val a3 = 0.35f + 0.65f * phase(0.44f)

    val color = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotGap.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(dotSize.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = a1))
        )
        Box(
            Modifier
                .size(dotSize.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = a2))
        )
        Box(
            Modifier
                .size(dotSize.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = a3))
        )
    }
}


@Composable
fun GlobalLoadingOverlay(
    visible: Boolean,
    message: String = "Loading…",
    logoRes: Int = R.mipmap.ic_launcher_foreground,
    title: String = "DIU Transport Schedule"
) {
    if (!visible) return

    // Strong scrim so the overlay is readable on any background
    val scrimAlpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.62f else 0.45f
    val scrim = Color.Black.copy(alpha = scrimAlpha)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim),
        contentAlignment = Alignment.Center
    ) {
        // Center content: logo (bigger, no circle) + title + progress
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo (bigger, no circle)
            Image(
                painter = painterResource(id = logoRes),
                contentDescription = "DIU Logo",
                modifier = Modifier
                    .size(140.dp)
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.90f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

            // Clear loading feedback
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.20f)
            )

            Spacer(Modifier.height(12.dp))

            // Animated dots for extra motion
            DotsLoadingIndicator(dotSize = 10, dotGap = 8)
        }
    }
}