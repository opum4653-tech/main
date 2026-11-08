package com.andorid.ninmods.ui.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andorid.ninmods.data.SessionManager
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max

@Composable
fun LicenseClockCard(
    sessionManager: SessionManager,
    modifier: Modifier = Modifier
) {
    fun calculateRemaining(): Long {
        return if (sessionManager.serverRemainingSeconds > 0L) {
            sessionManager.liveRemainingSeconds
        } else if (sessionManager.licenseExpiryMillis > 0L) {
            val diff = max(0L, sessionManager.licenseExpiryMillis - System.currentTimeMillis())
            diff / 1000L
        } else {
            0L
        }
    }

    // Monotonic live counter state
    var remainingSeconds by remember(sessionManager.serverRemainingSeconds, sessionManager.activeLicenseKey) {
        mutableLongStateOf(calculateRemaining())
    }

    // Live clock ticking every 1 second
    LaunchedEffect(sessionManager.serverRemainingSeconds, sessionManager.activeLicenseKey) {
        remainingSeconds = calculateRemaining()
        while (remainingSeconds > 0L) {
            delay(1000L)
            val updated = calculateRemaining()
            remainingSeconds = updated
            if (updated <= 0L) break
        }
    }

    // Strict policy: Hide completely if no active license or time expired
    if (remainingSeconds <= 0L || sessionManager.activeLicenseKey.isNullOrBlank()) {
        return
    }

    val hours = remainingSeconds / 3600L
    val minutes = (remainingSeconds % 3600L) / 60L
    val seconds = remainingSeconds % 60L

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = "License Timer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "LICENSE REMAINING",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF81C784)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Digital Clock Countdown Display: Hours - Minutes - Seconds (Updates live every second)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "%02d : %02d : %02d", hours, minutes, seconds),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Hours  :  Minutes  :  Seconds",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
