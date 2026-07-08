package com.zenni.exposuremeter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zenni.exposuremeter.engine.ExposureDelta
import com.zenni.exposuremeter.engine.StopFormat
import kotlin.math.abs

/**
 * The over/under-exposure delta badge (brief §4): first-class, colour-coded
 * (amber within ±1 EV, red beyond), always in thirds notation. When exposure is
 * correct it shows a calm "balanced" state instead of a warning colour.
 */
@Composable
fun DeltaBadge(delta: ExposureDelta, modifier: Modifier = Modifier) {
    val balanced = delta.isBalanced
    val beyondOneStop = abs(delta.thirds) > 3.0 || delta.clamped

    val container = when {
        balanced -> MaterialTheme.colorScheme.secondaryContainer
        beyondOneStop -> Color(0xFFB3261E) // red
        else -> Color(0xFFF2A600)          // amber
    }
    val onContainer = when {
        balanced -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> Color.White
    }

    val label = when {
        balanced -> "Exposure OK"
        else -> {
            val badge = StopFormat.badge(delta)
            if (delta.clamped) "$badge · out of range" else badge
        }
    }

    Text(
        text = label,
        color = onContainer,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
