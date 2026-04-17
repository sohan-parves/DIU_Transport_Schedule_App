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
import androidx.compose.ui.graphics.graphicsLayer
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
    var profileRenderKey by rememberSaveable { mutableIntStateOf(0) }
    val saveableStateHolder = rememberSaveableStateHolder()

    fun navigateInstant(route: String) {
        if (currentRoute == route) return

        // 🔥 reset ProfileScreen state when leaving profile tab
        if (currentRoute == "profile" && route != "profile") {
            profileRenderKey++
        }

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

                    if (tab == BottomTab.PROFILE && currentRoute == "profile") {
                        profileRenderKey++
                    } else {
                        navigateInstant(route)
                    }
                }
            )
        }
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize()) {
            val homeActive = currentRoute == "home"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (homeActive) 4f else 0f)
                    .graphicsLayer {
                        alpha = if (homeActive) 1f else 0f
                        translationX = if (homeActive) 0f else 100000f
                    }
                    .background(if (homeActive) MaterialTheme.colorScheme.background else Color.Transparent)
            ) {
                saveableStateHolder.SaveableStateProvider("home") {
                    HomeScreen(
                        vm = vm,
                        pad = pad,
                        onOpenNotice = { navigateInstant("notice") }
                    )
                }
            }

            val mapActive = currentRoute == "map"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (mapActive) 4f else 0f)
                    .graphicsLayer {
                        alpha = if (mapActive) 1f else 0f
                        translationX = if (mapActive) 0f else 100000f
                    }
                    .background(if (mapActive) MaterialTheme.colorScheme.background else Color.Transparent)
            ) {
                saveableStateHolder.SaveableStateProvider("map") {
                    LiveMapScreen(isTabActive = mapActive)
                }
            }

            val noticeActive = currentRoute == "notice"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (noticeActive) 4f else 0f)
                    .graphicsLayer {
                        alpha = if (noticeActive) 1f else 0f
                        translationX = if (noticeActive) 0f else 100000f
                    }
                    .background(if (noticeActive) MaterialTheme.colorScheme.background else Color.Transparent)
            ) {
                saveableStateHolder.SaveableStateProvider("notice") {
                    NoticeScreen(pad = pad)
                }
            }

            val profileActive = currentRoute == "profile"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (profileActive) 4f else 0f)
                    .graphicsLayer {
                        alpha = if (profileActive) 1f else 0f
                        translationX = if (profileActive) 0f else 100000f
                    }
                    .background(if (profileActive) MaterialTheme.colorScheme.background else Color.Transparent)
            ) {
                saveableStateHolder.SaveableStateProvider("profile_$profileRenderKey") {
                    ProfileScreen(vm)
                }
            }
        }
    }
}