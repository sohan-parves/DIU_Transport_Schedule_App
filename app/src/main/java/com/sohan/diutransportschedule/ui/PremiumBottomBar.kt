package com.sohan.diutransportschedule.ui

import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance

enum class BottomTab(val label: String) {
    HOME("Home"),
    MAP("Map"),
    NOTICE("Notice"),
    PROFILE("Profile")
}
private data class BottomTabUi(
    val tab: BottomTab,
    val label: String,
    val icon: ImageVector
)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun PremiumBottomBar(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit
) {
    val tabs = listOf(
        BottomTabUi(BottomTab.HOME, "Home", Icons.Filled.Home),
        BottomTabUi(BottomTab.MAP, "Map", Icons.Filled.LocationOn),
        BottomTabUi(BottomTab.NOTICE, "Notice", Icons.Filled.Notifications),
        BottomTabUi(BottomTab.PROFILE, "Settings", Icons.Filled.Settings)
    )

    var localSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        localSelected = selected
    }

    val selectedIndex = tabs.indexOfFirst { it.tab == localSelected }.coerceAtLeast(0)
    val slotCount = tabs.size

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 22.dp,
                    shape = RoundedCornerShape(22.dp),
                    clip = false
                ),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 2.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, item ->
                    val isSelected = selectedIndex == index
                    val hiddenAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 0f else 1f,
                        animationSpec = tween(durationMillis = 180),
                        label = "tabAlpha_$index"
                    )
                    val hiddenScale by animateFloatAsState(
                        targetValue = if (isSelected) 0.96f else 1f,
                        animationSpec = tween(durationMillis = 220),
                        label = "tabScale_$index"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                alpha = hiddenAlpha
                                scaleX = hiddenScale
                                scaleY = hiddenScale
                                clip = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        PremiumTabItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = item.label,
                            selected = false,
                            onClick = {
                                localSelected = item.tab
                                onSelect(item.tab)
                            }
                        )
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 10.dp)
        ) {
            val slotWidth = maxWidth / slotCount
            val animatedIndex by animateFloatAsState(
                targetValue = selectedIndex.toFloat(),
                animationSpec = tween(
                    durationMillis = 220,
                    easing = FastOutSlowInEasing
                ),
                label = "selectedTabIndex"
            )
            val animatedOffsetX by animateDpAsState(
                targetValue = slotWidth * animatedIndex,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = FastOutSlowInEasing
                ),
                label = "selectedTabOffsetX"
            )

            Box(
                modifier = Modifier
                    .offset(x = animatedOffsetX, y = (-12).dp)
                    .width(slotWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                FloatingSelectedTabButton(
                    icon = tabs[selectedIndex].icon,
                    label = tabs[selectedIndex].label,
                    onClick = {
                        localSelected = tabs[selectedIndex].tab
                        onSelect(tabs[selectedIndex].tab)
                    }
                )
            }
        }
    }
}
@Composable
private fun FloatingSelectedTabButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val iconScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 220,
            easing = FastOutSlowInEasing
        ),
        label = "floatingIconScale"
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(58.dp)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(29.dp),
                clip = false
            ),
        shape = RoundedCornerShape(29.dp),
        color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
            Color(0xFF0D47A1)
        } else {
            Color(0xFF00C853)
        },
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size((26 * iconScale).dp)
            )
        }
    }
}
@Composable
private fun PremiumTabItem(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val pad by animateDpAsState(
        targetValue = if (selected) 12.dp else 10.dp,
        animationSpec = tween(durationMillis = 180),
        label = "pad"
    )

    // Follow app theme (MaterialTheme), ignore system dark mode
    val selectedColor = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        Color(0xFF0D47A1)
    } else {
        Color(0xFF00C853)
    }
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else unselectedColor

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (selected) 3.dp else 0.dp,
        shadowElevation = if (selected) 8.dp else 0.dp
    ) {
        Box(
            modifier = if (selected) {
                Modifier
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF00E676),
                                Color(0xFF00C853)
                            ),
                            tileMode = TileMode.Clamp
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
            } else Modifier
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = pad),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides contentColor
                ) {
                    icon()
                }
            }
        }
    }
}