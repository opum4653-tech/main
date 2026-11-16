package com.andorid.ninmods.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Official Google Material 3 Loading Indicator for Buttons across NinMods.
 * Provides a standardized official CircularProgressIndicator conforming to Google guidelines.
 * Single source of truth for all submit and action buttons in the application.
 */
@Composable
fun AppButtonLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = Color.White,
    strokeWidth: Dp = 2.5.dp
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = strokeWidth
    )
}
