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

/**
 * APatch Authentic Digital License Countdown Clock Card.
 * Exact 1:1 match to official design (media_1794458367238_0aa42bf1.png):
 * - Stopwatch timer icon + "LICENSE REMAINING" header in cyan/blue
 * - "ACTIVE" status badge in green
 * - Inner dark digital display pill with monospace countdown ("00 : 00 : 00")
 * - Centered "Hours : Minutes : Seconds" label
 */
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

    var remainingSeconds by remember(sessionManager.serverRemainingSeconds, sessionManager.activeLicenseKey) {
        mutableLongStateOf(calculateRemaining())
    }

    LaunchedEffect(sessionManager.serverRemainingSeconds, sessionManager.activeLicenseKey) {
        remainingSeconds = calculateRemaining()
        while (remainingSeconds > 0L) {
            delay(1000L)
            val updated = calculateRemaining()
            remainingSeconds = updated
            if (updated <= 0L) break
        }
    }

    if (remainingSeconds <= 0L && sessionManager.activeLicenseKey.isNullOrBlank()) {
        return
    }

    DigitalClockCardLayout(
        remainingSeconds = remainingSeconds,
        title = "LICENSE REMAINING",
        statusText = if (remainingSeconds > 0L) "ACTIVE" else "EXPIRED",
        isActive = remainingSeconds > 0L,
        modifier = modifier
    )
}

/**
 * Dedicated Clock Timer for Individual License Item.
 * Allows each active license to have its own real-time ticking digital countdown.
 */
@Composable
fun IndividualLicenseClockCard(
    initialRemainingSeconds: Long,
    title: String = "LICENSE REMAINING",
    statusText: String = "ACTIVE",
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    var remainingSeconds by remember(initialRemainingSeconds) {
        mutableLongStateOf(initialRemainingSeconds)
    }

    LaunchedEffect(initialRemainingSeconds) {
        remainingSeconds = initialRemainingSeconds
        while (remainingSeconds > 0L) {
            delay(1000L)
            remainingSeconds = max(0L, remainingSeconds - 1L)
            if (remainingSeconds <= 0L) break
        }
    }

    DigitalClockCardLayout(
        remainingSeconds = remainingSeconds,
        title = title,
        statusText = if (remainingSeconds > 0L) statusText else "EXPIRED",
        isActive = remainingSeconds > 0L && isActive,
        modifier = modifier
    )
}

@Composable
fun DigitalClockCardLayout(
    remainingSeconds: Long,
    title: String = "LICENSE REMAINING",
    statusText: String = "ACTIVE",
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val safeSeconds = max(0L, remainingSeconds)
    val hours = safeSeconds / 3600L
    val minutes = (safeSeconds % 3600L) / 60L
    val seconds = safeSeconds % 60L

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
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color(0xFF81C784) else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Digital Clock Countdown Display: Hours : Minutes : Seconds
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
                    text = String.format(Locale.US, "%02d  :  %02d  :  %02d", hours, minutes, seconds),
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
