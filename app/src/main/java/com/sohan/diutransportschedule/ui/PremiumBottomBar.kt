package com.sohan.diutransportschedule.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sohan.diutransportschedule.ui.theme.PrimaryBlue
import androidx.compose.ui.draw.shadow


enum class BottomTab(val label: String) {
    HOME("Home"),
    NOTICE("Notice"),
    PROFILE("Profile")
}

@Composable
fun PremiumBottomBar(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit
) {
    // floating bar padding
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier.shadow(
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PremiumTabItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = "Home",
                    selected = selected == BottomTab.HOME,
                    onClick = { onSelect(BottomTab.HOME) }
                )
                PremiumTabItem(
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    label = "Notice",
                    selected = selected == BottomTab.NOTICE,
                    onClick = { onSelect(BottomTab.NOTICE) }
                )
                PremiumTabItem(
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = "Profile",
                    selected = selected == BottomTab.PROFILE,
                    onClick = { onSelect(BottomTab.PROFILE) }
                )
            }
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
    val bgAlpha = if (selected) 0.12f else 0f
    val pad by animateDpAsState(if (selected) 12.dp else 10.dp, label = "pad")

    // Follow app theme (MaterialTheme), ignore system dark mode
    val selectedColor = Color(0xFF00C853)
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
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = pad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides contentColor
                ) {
                    icon()
                }

                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}