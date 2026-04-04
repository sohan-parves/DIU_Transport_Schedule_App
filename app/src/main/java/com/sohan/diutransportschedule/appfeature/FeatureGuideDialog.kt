package com.sohan.diutransportschedule.appfeature

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
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

    val dragThresholdPx = 28f

    val shouldAutoExpand by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
        }
    }

    val collapsedHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp * 0.48f).dp
    }
    val expandedHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp * 0.72f).dp
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

    val sheetColor = MaterialTheme.colorScheme.surface
    val headerChipColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val closeButtonColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)

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
                    .requiredWidth(configuration.screenWidthDp.dp)
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
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 8.dp)
                            .pointerInput(isExpanded) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount ->
                                        if (dragAmount < -10f) {
                                            isExpanded = true
                                        } else if (dragAmount > 10f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
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
                                        color = MaterialTheme.colorScheme.onSurface,
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
                            .heightIn(min = 120.dp, max = expandedHeight)
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    if (!isExpanded && delta < -dragThresholdPx) {
                                        isExpanded = true
                                    }
                                },
                                onDragStopped = {
                                    if (!isExpanded && shouldAutoExpand) {
                                        isExpanded = true
                                    }
                                }
                            )
                            .padding(horizontal = 6.dp),
                        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.10f),
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
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
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
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
                            color = MaterialTheme.colorScheme.onSurface
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
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