package com.sohan.diutransportschedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNav(
    vm: HomeViewModel,
    openNotice: Boolean = false,
    onNoticeOpened: () -> Unit = {}
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"

    LaunchedEffect(openNotice) {
        if (openNotice) {
            nav.navigate("notice") {
                launchSingleTop = true
                restoreState = true
                popUpTo(nav.graph.startDestinationId) { saveState = true }
            }
            onNoticeOpened()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            PremiumBottomBar(
                selected = when (currentRoute) {
                    "notice" -> BottomTab.NOTICE
                    "profile" -> BottomTab.PROFILE
                    else -> BottomTab.HOME
                },
                onSelect = { tab ->
                    val route = when (tab) {
                        BottomTab.HOME -> "home"
                        BottomTab.NOTICE -> "notice"
                        BottomTab.PROFILE -> "profile"
                    }
                    nav.navigate(route) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                    }
                }
            )
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "home"
        ) {
            composable("home") { HomeScreen(vm = vm, pad = pad) }
            composable("notice") { NoticeScreen(pad = pad) }
            composable("profile") { ProfileScreen(vm) }
        }
    }
}