package com.andorid.ninmods.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andorid.ninmods.util.network.rememberNetworkConnectivityState
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Global Network Status Banner & Scroll Monitor.
 * Adheres strictly to official Material 3 snackbar/pill physics.
 * Automatically appears when offline, and re-appears when scrolling while offline,
 * smoothly dismissing once scrolling is stopped.
 */
@Composable
fun GlobalNetworkBannerContainer(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val isOnline by rememberNetworkConnectivityState()
    var isVisible by remember { mutableStateOf(false) }
    var lastTriggerTime by remember { mutableLongStateOf(0L) }

    // When connection drops, show the banner briefly
    LaunchedEffect(isOnline) {
        if (!isOnline) {
            lastTriggerTime = System.currentTimeMillis()
            isVisible = true
        } else {
            isVisible = false
        }
    }

    // Auto-dismiss banner after 2.5 seconds of inactivity
    LaunchedEffect(lastTriggerTime, isOnline) {
        if (!isOnline && isVisible) {
            delay(2500L)
            isVisible = false
        }
    }

    // NestedScrollConnection to detect scrolling across all child screens
    val nestedScrollConnection = remember(isOnline) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isOnline && abs(available.y) > 3f) {
                    lastTriggerTime = System.currentTimeMillis()
                    isVisible = true
                }
                return Offset.Zero
            }
        }
    }

    Box(modifier = modifier) {
        // Child content wrapped with the scroll detector
        content(Modifier.nestedScroll(nestedScrollConnection))

        // Official Floating Bottom Pill
        AnimatedVisibility(
            visible = isVisible && !isOnline,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WifiOff,
                        contentDescription = "No internet",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "No internet connection",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
