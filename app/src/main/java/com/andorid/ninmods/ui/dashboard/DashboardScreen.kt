package com.andorid.ninmods.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.network.ActiveNotificationItem
import com.andorid.ninmods.ui.clock.LicenseClockCard
import com.andorid.ninmods.ui.component.BroadcastNotificationCard
import com.andorid.ninmods.ui.dashboard.external_mods.ExternalModsCard
import com.andorid.ninmods.ui.dashboard.kernel_mods.KernelModsCard

@Composable
fun DashboardScreen(
    sessionManager: SessionManager,
    activeBroadcast: ActiveNotificationItem? = null,
    onDismissBroadcast: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Active Broadcast / Announcement Banner (Push Popups & Alerts from Admin Panel)
        if (activeBroadcast != null) {
            BroadcastNotificationCard(
                notification = activeBroadcast,
                onDismiss = onDismissBroadcast
            )
        }

        // 1. Digital License Expiry Countdown Clock (Real-Time, hidden if no active plan)
        LicenseClockCard(sessionManager = sessionManager)

        // 2. KERNEL MODS Card (Ring 0 Kernel Driver Floating Engine)
        KernelModsCard(sessionManager = sessionManager)

        // 3. EXTERNAL MODS Card (Single official external mod from refer/nin)
        ExternalModsCard(sessionManager = sessionManager)

        Spacer(modifier = Modifier.height(20.dp))
    }
}
