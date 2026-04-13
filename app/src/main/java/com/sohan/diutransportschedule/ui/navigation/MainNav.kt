package com.sohan.diutransportschedule.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.zIndex
import com.sohan.diutransportschedule.ui.components.BottomTab
import com.sohan.diutransportschedule.ui.home.HomeScreen
import com.sohan.diutransportschedule.ui.home.HomeViewModel
import com.sohan.diutransportschedule.ui.map.LiveMapScreen
import com.sohan.diutransportschedule.ui.notice.NoticeScreen
import com.sohan.diutransportschedule.ui.components.PremiumBottomBar
import com.sohan.diutransportschedule.ui.settings.ProfileScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNav(
    vm: HomeViewModel,
    openNotice: Boolean = false,
    onNoticeOpened: () -> Unit = {}
) {
    var currentRoute by rememberSaveable { mutableStateOf("home") }
    val saveableStateHolder = rememberSaveableStateHolder()

    fun navigateInstant(route: String) {
        if (currentRoute == route) return
        currentRoute = route
    }

    LaunchedEffect(openNotice) {
        if (openNotice) {
            navigateInstant("notice")
            onNoticeOpened()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            PremiumBottomBar(
                selected = when (currentRoute) {
                    "map" -> BottomTab.MAP
                    "notice" -> BottomTab.NOTICE
                    "profile" -> BottomTab.PROFILE
                    else -> BottomTab.HOME
                },
                onSelect = { tab ->
                    val route = when (tab) {
                        BottomTab.HOME -> "home"
                        BottomTab.MAP -> "map"
                        BottomTab.NOTICE -> "notice"
                        BottomTab.PROFILE -> "profile"
                    }
                    navigateInstant(route)
                }
            )
        }
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (currentRoute == "home") 4f else 0f)
                    .background(if (currentRoute == "home") MaterialTheme.colorScheme.background else Color.Transparent)
            ) {
                saveableStateHolder.SaveableStateProvider("home") {
                    HomeScreen(
                        vm = vm,
                        pad = pad,
                        onOpenNotice = { navigateInstant("notice") }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (currentRoute == "map") 4f else 0f)
                    .background(if (currentRoute == "map") MaterialTheme.colorScheme.background else Color.Transparent)
            ) {
                saveableStateHolder.SaveableStateProvider("map") {
                    LiveMapScreen(isTabActive = currentRoute == "map")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (currentRoute == "notice") 4f else 0f)
                    .background(if (currentRoute == "notice") MaterialTheme.colorScheme.background else Color.Transparent)
            ) {
                saveableStateHolder.SaveableStateProvider("notice") {
                    NoticeScreen(pad = pad)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (currentRoute == "profile") 4f else 0f)
                    .background(if (currentRoute == "profile") MaterialTheme.colorScheme.background else Color.Transparent)
            ) {
                saveableStateHolder.SaveableStateProvider("profile") {
                    ProfileScreen(vm)
                }
            }
        }
    }
}