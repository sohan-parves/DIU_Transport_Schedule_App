
package com.sohan.diutransportschedule.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
// import androidx.compose.foundation.layout.calculateBottomPadding -- removed, not available
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: HomeViewModel) {
    val dark by vm.darkMode.collectAsState()
    val selectedRoute by vm.selectedRoute.collectAsState()

    // ✅ First install default: route = ALL -> notifications must be OFF.
    // ✅ Any specific route selected -> notifications auto ON.
    LaunchedEffect(selectedRoute) {
        val isAll = selectedRoute.trim().equals("ALL", ignoreCase = true)
        if (isAll) {
            if (vm.notificationsEnabled.value) {
                vm.setNotificationsEnabled(false)
            } else {
                // still ensure persisted OFF on first install
                vm.setNotificationsEnabled(false)
            }
        } else {
            // auto-enable when user selects a real route
            if (!vm.notificationsEnabled.value) {
                vm.setNotificationsEnabled(true)
            }
        }
    }

// ✅ Always use FULL route list from VM (not filtered by Home)
    val routeOptions by vm.routeOptions.collectAsState()
    val selectedRouteLabel by vm.selectedRouteLabel.collectAsState()
    val isSyncing by vm.isSyncing.collectAsState()
    val primaryText = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (dark) Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onSurfaceVariant
    val view = LocalView.current
    val notificationsEnabled by vm.notificationsEnabled.collectAsState()
    val notifyLeadMinutes by vm.notifyLeadMinutes.collectAsState()
    val navBarBottomPad = with(LocalDensity.current) {
        val bottomPx = runCatching {
            ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())
                ?.bottom
        }.getOrNull() ?: 0
        bottomPx.toDp()
    }
    val ctx = LocalContext.current

    val alertPrefs = remember(ctx) { ctx.getSharedPreferences("notice_alert_prefs", Context.MODE_PRIVATE) }

    var alarmSound5mEnabled by rememberSaveable {
        mutableStateOf(alertPrefs.getBoolean("alarm_sound_5m", true))
    }

    var alarmVibrate5mEnabled by rememberSaveable {
        mutableStateOf(alertPrefs.getBoolean("alarm_vibrate_5m", true))
    }

    // Premium card styling (light mode)
    val premiumLightCard = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val premiumLightBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
    val premiumLightDivider = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    val greenSwitchColors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.secondary,
        checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
        checkedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f),
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
    )

    var routeMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val showUpdateBanner by vm.showUpdateBanner.collectAsState()
    val compactMode by vm.compactMode.collectAsState()


    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .padding(bottom = 24.dp + navBarBottomPad + 72.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(28.dp))

            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineSmall,
                color = primaryText
            )

            // ---------------- Route Select ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                colors = CardDefaults.cardColors(
                    containerColor = if (dark) MaterialTheme.colorScheme.surface else premiumLightCard
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Route Filter",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Select which route to show on Home",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val refreshColor = if (dark)
                            MaterialTheme.colorScheme.secondary   // green in dark mode
                        else
                            MaterialTheme.colorScheme.primary

                        // 🔒 UI-level throttle for refresh button (5 minutes)
                        var lastRefreshTapMs by rememberSaveable { mutableStateOf(0L) }
                        val canTapRefresh = !isSyncing &&
                            (System.currentTimeMillis() - lastRefreshTapMs >= 5 * 60 * 1000L)

                        IconButton(
                            onClick = {
                                lastRefreshTapMs = System.currentTimeMillis()
                                vm.refresh(showBannerIfUpdated = true)
                            },
                            enabled = canTapRefresh,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = refreshColor)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = refreshColor
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = refreshColor)
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = routeMenuExpanded,
                        onExpandedChange = { routeMenuExpanded = !routeMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedRouteLabel,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            label = { Text("Selected route", color = secondaryText) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = routeMenuExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )

                        // ✅ Anchored menu (starts from the Selected route field)
                        ExposedDropdownMenu(
                            expanded = routeMenuExpanded,
                            onDismissRequest = { routeMenuExpanded = false },
                            modifier = Modifier
                                .exposedDropdownSize(true)
                                .heightIn(max = 420.dp)
                                .padding(bottom = navBarBottomPad)
                                .background(
                                    color = if (dark) MaterialTheme.colorScheme.surface else premiumLightCard,
                                    shape = RoundedCornerShape(18.dp)
                                )
                        ) {
                            val dailyRouteOptions = routeOptions.filter { opt ->
                                val rn = opt.routeNo.trim()
                                rn.isNotBlank() &&
                                        !rn.equals("ALL", ignoreCase = true) &&   // ✅ remove ALL
                                        !rn.startsWith("F", ignoreCase = true)
                            }

                            dailyRouteOptions.forEachIndexed { index, opt ->

                                val isSelected = opt.routeNo == selectedRoute

                                DropdownMenuItem(
                                    contentPadding = PaddingValues(0.dp),
                                    text = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                                    else Color.Transparent,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .padding(vertical = 10.dp, horizontal = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier.width(26.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Check,
                                                            contentDescription = "Selected",
                                                            tint = if (dark) MaterialTheme.colorScheme.secondary
                                                            else MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }

                                                Spacer(Modifier.size(2.dp))

                                                Text(
                                                    text = opt.routeNo,
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                    color = if (isSelected)
                                                        if (dark) MaterialTheme.colorScheme.secondary
                                                        else MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(Modifier.size(6.dp))

                                                Box(
                                                    modifier = Modifier
                                                        .height(18.dp)
                                                        .width(1.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                                                            shape = RoundedCornerShape(1.dp)
                                                        )
                                                )

                                                Spacer(Modifier.size(6.dp))

                                                Text(
                                                    text = opt.label,
                                                    style = if (isSelected)
                                                        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                                    else
                                                        MaterialTheme.typography.bodyLarge,
                                                    color = if (isSelected)
                                                        if (dark) MaterialTheme.colorScheme.secondary
                                                        else MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        routeMenuExpanded = false
                                        vm.setSelectedRoute(opt.routeNo)

                                        // If user picks ALL (only possible if ALL exists), force notifications OFF.
                                        // If user picks a real route, auto-enable notifications.
                                        val picked = opt.routeNo.trim()
                                        if (picked.equals("ALL", ignoreCase = true)) {
                                            vm.setNotificationsEnabled(false)
                                        } else {
                                            vm.setNotificationsEnabled(true)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // ✅ divider between items
                                if (index != dailyRouteOptions.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Tip: Pull to refresh on Home too.",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText
                    )
                }
            }

            // ---------------- Dark Mode ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                colors = CardDefaults.cardColors(
                    containerColor = if (dark) MaterialTheme.colorScheme.surface else premiumLightCard
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dark Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = primaryText
                        )
                        Text(
                            text = "Turn on dark theme",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryText
                        )
                    }

                    Switch(
                        checked = dark,
                        onCheckedChange = { vm.setDarkMode(it) },
                        colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                    )
                }
            }

            // ---------------- Features ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                colors = CardDefaults.cardColors(
                    containerColor = if (dark) MaterialTheme.colorScheme.surface else premiumLightCard
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Features",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show update banner",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Show update notice after sync",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryText
                            )
                        }
                        Switch(
                            checked = showUpdateBanner,
                            onCheckedChange = { vm.setShowUpdateBanner(it) },
                            colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                        )
                    }


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Compact cards",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Smaller padding for faster scrolling (UI only)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = compactMode,
                            onCheckedChange = { vm.setCompactMode(it) },
                            colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                        )
                    }

                    HorizontalDivider(color = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else premiumLightDivider)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notifications",
                                style = MaterialTheme.typography.bodyLarge,
                                color = primaryText
                            )
                            Text(
                                text = "Get alerts before start/departure time",
                                style = MaterialTheme.typography.bodyMedium,
                                color = primaryText
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                vm.setNotificationsEnabled(enabled)

                                // If notifications are turned OFF, also turn OFF ringtone & vibration and persist,
                                // and update local UI state so switches reflect the saved values.
                                if (!enabled) {
                                    alarmSound5mEnabled = false
                                    alarmVibrate5mEnabled = false
                                    alertPrefs.edit()
                                        .putBoolean("alarm_sound_5m", false)
                                        .putBoolean("alarm_vibrate_5m", false)
                                        .apply()
                                }
                            },
                            enabled = true,
                            colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                        )
                    }

                    AnimatedVisibility(visible = notificationsEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Notify me ${notifyLeadMinutes} minutes before",
                                style = MaterialTheme.typography.bodyMedium,
                                color = primaryText
                            )

                            Slider(
                                value = notifyLeadMinutes.toFloat(),
                                onValueChange = { vm.setNotifyLeadMinutes(it.toInt()) },
                                valueRange = 5f..120f,
                                steps = 22,
                                colors = if (dark) SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.secondary,
                                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f)
                                ) else SliderDefaults.colors()
                            )

                            Text(
                                text = "Default: 30 minutes",
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryText
                            )
                        }
                    }
                    AnimatedVisibility(visible = notificationsEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            HorizontalDivider(color = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else premiumLightDivider)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ringtone (5 min)",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = primaryText
                                    )
                                    Text(
                                        text = "Play alarm ringtone for ~5 minutes when a notice arrives",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryText
                                    )
                                }
                                Switch(
                                    checked = alarmSound5mEnabled,
                                    onCheckedChange = {
                                        alarmSound5mEnabled = it
                                        alertPrefs.edit().putBoolean("alarm_sound_5m", it).apply()
                                    },
                                    enabled = notificationsEnabled,
                                    colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                                )
                            }

                            HorizontalDivider(color = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else premiumLightDivider)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Vibration",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = primaryText
                                    )
                                    Text(
                                        text = "Vibrate strongly when a notice arrives",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryText
                                    )
                                }
                                Switch(
                                    checked = alarmVibrate5mEnabled,
                                    onCheckedChange = {
                                        alarmVibrate5mEnabled = it
                                        alertPrefs.edit().putBoolean("alarm_vibrate_5m", it).apply()
                                    },
                                    enabled = notificationsEnabled,
                                    colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                                )
                            }
                        }
                    }

                    Text(
                        text = "These settings are saved automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Compatibility: older Material3 versions may not include ColorScheme.surfaceColorAtElevation
private fun androidx.compose.material3.ColorScheme.surfaceColorAtElevation(elevation: Dp): Color {
    // Simple, stable approximation: use Surface as base and lightly overlay SurfaceVariant
    // to create a subtle elevated look in light theme.
    val base = this.surface
    if (elevation <= 0.dp) return base

    // Higher elevation => slightly stronger overlay
    val alpha = when {
        elevation < 1.dp -> 0.04f
        elevation < 2.dp -> 0.06f
        elevation < 3.dp -> 0.08f
        elevation < 6.dp -> 0.10f
        else -> 0.12f
    }

    return this.surfaceVariant.copy(alpha = alpha).compositeOver(base)
}