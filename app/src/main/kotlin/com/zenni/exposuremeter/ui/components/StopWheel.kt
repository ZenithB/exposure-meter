package com.zenni.exposuremeter.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zenni.exposuremeter.R
import kotlin.math.abs
import kotlin.math.roundToInt

private val ItemHeight = 44.dp
private const val VisibleItems = 5 // must be odd; center slot is the selection

/**
 * A camera-dial parameter wheel (brief §7): a vertical snap-scrolling picker with
 * the current value large and centred and neighbours receding. Emits [onSelected]
 * when a new value settles under the centre line, with a haptic detent tick.
 *
 * A locked wheel is frozen: dimmed and non-scrollable (brief §4.2).
 *
 * @param values nominal display strings, ascending.
 * @param selectedIndex position of the current value within [values].
 * @param haptics whether to tick on each detent (brief §7 setting).
 */
@Composable
fun StopWheel(
    title: String,
    values: List<String>,
    selectedIndex: Int,
    locked: Boolean,
    onSelected: (Int) -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
    haptics: Boolean = true,
) {
    val density = LocalDensity.current
    val itemPx = with(density) { ItemHeight.toPx() }
    val half = VisibleItems / 2

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val hapticFeedback = LocalHapticFeedback.current

    // Fractional centred position, driving neighbour dimming/scaling.
    val centeredFloat by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex + listState.firstVisibleItemScrollOffset / itemPx
        }
    }
    // The settled, on-grid selection under the centre line.
    val centeredIndex by remember {
        derivedStateOf { centeredFloat.roundToInt().coerceIn(0, values.lastIndex) }
    }

    // Follow external changes (e.g. the solver moved this wheel) when the user
    // is not actively scrolling, without fighting their gesture.
    LaunchedEffect(selectedIndex) {
        if (!listState.isScrollInProgress && centeredIndex != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    // Report a new selection once scrolling settles; tick on each detent change.
    LaunchedEffect(listState, locked) {
        if (locked) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress to centeredIndex }
            .collect { (scrolling, index) ->
                if (!scrolling && index != selectedIndex) {
                    if (haptics) hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelected(index)
                }
            }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Box(
            modifier = Modifier
                .height(ItemHeight * VisibleItems)
                .alpha(if (locked) 0.45f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            // Centre-slot highlight.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ItemHeight)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        RoundedCornerShape(10.dp),
                    ),
            )

            LazyColumn(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                userScrollEnabled = !locked,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                contentPadding = PaddingValues(vertical = ItemHeight * half),
            ) {
                itemsIndexed(values) { position, value ->
                    val distance = abs(position - centeredFloat)
                    val itemAlpha = (1f - 0.30f * distance).coerceIn(0.25f, 1f)
                    val scale = (1f - 0.12f * distance).coerceIn(0.7f, 1f)
                    Box(
                        modifier = Modifier
                            .height(ItemHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = value,
                            textAlign = TextAlign.Center,
                            fontWeight = if (distance < 0.5f) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.graphicsLayer {
                                alpha = itemAlpha
                                scaleX = scale
                                scaleY = scale
                            },
                        )
                    }
                }
            }
        }

        // Lock toggle beneath the wheel.
        Image(
            painter = painterResource(
                if (locked) R.drawable.ic_lock_closed else R.drawable.ic_lock_open,
            ),
            contentDescription = if (locked) "$title locked" else "$title unlocked",
            colorFilter = ColorFilter.tint(
                if (locked) MaterialTheme.colorScheme.primary
                else LocalContentColor.current.copy(alpha = 0.6f),
            ),
            modifier = Modifier
                .padding(top = 8.dp)
                .size(28.dp)
                .clickable(onClick = onToggleLock),
        )
    }
}
