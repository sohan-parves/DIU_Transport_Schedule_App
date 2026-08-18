package com.sohan.diutransportschedule.appfeature

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.PaddingValues


data class AppFeatureGuideItem(
    val title: String,
    val description: String
)

data class AppFeatureGuideModel(
    val title: String,
    val subtitle: String,
    val items: List<AppFeatureGuideItem>,
    val buttonText: String = "Got it"
)

@Composable
fun AppFeatureGuideDialog(
    guide: AppFeatureGuideModel,
    onClose: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val listState = rememberLazyListState()

    val expandDragThresholdPx = 14f

    val shouldAutoExpand by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
        }
    }

    val collapsedHeight = remember(configuration.screenHeightDp) {
        ((configuration.screenHeightDp * 0.52f).dp).coerceIn(360.dp, 560.dp)
    }
    val expandedHeight = remember(configuration.screenHeightDp) {
        ((configuration.screenHeightDp * 0.84f).dp).coerceIn(520.dp, 820.dp)
    }

    var isExpanded by remember { mutableStateOf(false) }

    val targetSheetHeight = if (isExpanded) expandedHeight else collapsedHeight

    val sheetHeight by animateDpAsState(
        targetValue = targetSheetHeight,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 380f
        ),
        label = "featureGuideSheetHeight"
    )

    var targetOffsetFraction by remember { mutableFloatStateOf(1f) }
    val offsetFraction by animateFloatAsState(
        targetValue = targetOffsetFraction,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 420f
        ),
        label = "featureGuideSheetOffset"
    )

    LaunchedEffect(Unit) {
        targetOffsetFraction = 0f
    }

    LaunchedEffect(shouldAutoExpand) {
        if (shouldAutoExpand && !isExpanded) {
            isExpanded = true
        }
    }

    LaunchedEffect(listState.isScrollInProgress, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (
            !isExpanded &&
            listState.isScrollInProgress &&
            (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 2)
        ) {
            isExpanded = true
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val surfaceColor = colorScheme.surface
    val surfaceBrightness = (surfaceColor.red * 0.299f) + (surfaceColor.green * 0.587f) + (surfaceColor.blue * 0.114f)
    val isDarkTheme = surfaceBrightness < 0.5f

    val sheetColor = if (isDarkTheme) {
        colorScheme.surfaceContainerLow
    } else {
        colorScheme.surface
    }
    val headerChipColor = if (isDarkTheme) {
        colorScheme.secondary.copy(alpha = 0.18f)
    } else {
        colorScheme.primary.copy(alpha = 0.10f)
    }
    val closeButtonColor = if (isDarkTheme) {
        colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
    } else {
        colorScheme.surfaceContainerHigh
    }
    val scrimColor = colorScheme.scrim.copy(alpha = if (isDarkTheme) 0.62f else 0.32f)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(align = Alignment.CenterHorizontally)
                    .widthIn(max = 640.dp)
                    .heightIn(min = collapsedHeight, max = expandedHeight)
                    .height(sheetHeight)
                    .graphicsLayer {
                        translationY = offsetFraction * sheetHeight.toPx()
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = sheetColor,
                tonalElevation = 10.dp,
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(sheetColor)
                        .systemBarsPadding()
                        .imePadding()
                        .padding(horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 8.dp)
                            .pointerInput(isExpanded, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount ->
                                        if (dragAmount < -6f) {
                                            isExpanded = true
                                        } else if (
                                            dragAmount > 10f &&
                                            listState.firstVisibleItemIndex == 0 &&
                                            listState.firstVisibleItemScrollOffset == 0
                                        ) {
                                            isExpanded = false
                                        }
                                    }
                                )
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                isExpanded = !isExpanded
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(if (isExpanded) 72.dp else 64.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f))
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = headerChipColor,
                                tonalElevation = 1.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .size(18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✦",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isDarkTheme) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = guide.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 30.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = guide.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                        }

                        Surface(
                            onClick = onClose,
                            shape = RoundedCornerShape(18.dp),
                            color = closeButtonColor,
                            tonalElevation = 1.dp,
                            shadowElevation = 1.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 1.dp
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .heightIn(min = 120.dp, max = expandedHeight)
                            .pointerInput(isExpanded, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount ->
                                        if (!isExpanded && dragAmount < -expandDragThresholdPx) {
                                            isExpanded = true
                                        } else if (
                                            isExpanded &&
                                            dragAmount > 14f &&
                                            listState.firstVisibleItemIndex == 0 &&
                                            listState.firstVisibleItemScrollOffset == 0
                                        ) {
                                            isExpanded = false
                                        }
                                    }
                                )
                            }
                            .padding(horizontal = 6.dp),
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            top = 0.dp,
                            end = 0.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(2.dp)) }

                        itemsIndexed(guide.items) { index, item ->
                            FeatureGuideItemCard(
                                index = index + 1,
                                item = item
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = onClose,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .height(52.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = guide.buttonText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureGuideItemCard(
    index: Int,
    item: AppFeatureGuideItem
) {
    val colorScheme = MaterialTheme.colorScheme
    val surfaceColor = colorScheme.surface
    val surfaceBrightness = (surfaceColor.red * 0.299f) + (surfaceColor.green * 0.587f) + (surfaceColor.blue * 0.114f)
    val isDarkTheme = surfaceBrightness < 0.5f
    val cardContainerColor = if (isDarkTheme) {
        colorScheme.surfaceContainerHigh
    } else {
        colorScheme.surface
    }
    val cardBorderColor = if (isDarkTheme) {
        colorScheme.outlineVariant.copy(alpha = 0.18f)
    } else {
        colorScheme.outlineVariant.copy(alpha = 0.16f)
    }
    val indexChipColor = if (isDarkTheme) {
        colorScheme.secondary.copy(alpha = 0.16f)
    } else {
        colorScheme.primary.copy(alpha = 0.10f)
    }
    val indexTextColor = if (isDarkTheme) {
        colorScheme.secondary
    } else {
        colorScheme.primary
    }
    val accentLineColor = if (isDarkTheme) {
        colorScheme.secondary.copy(alpha = 0.24f)
    } else {
        colorScheme.primary.copy(alpha = 0.22f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .border(
                width = 1.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        color = cardContainerColor,
        tonalElevation = if (isDarkTheme) 1.dp else 2.dp,
        shadowElevation = if (isDarkTheme) 0.dp else 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = indexChipColor,
                    tonalElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 34.dp)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = indexTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .fillMaxWidth(0.16f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(accentLineColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 21.sp
            )
        }
    }
}