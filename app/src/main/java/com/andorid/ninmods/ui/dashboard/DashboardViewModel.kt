package com.andorid.ninmods.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andorid.ninmods.network.ActiveNotificationItem
import com.andorid.ninmods.network.AuthApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Dashboard owner. Holds the broadcast feed; the mod engines live in
 * ModRuntime so every screen that touches them sees the same instance.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _notifications = MutableStateFlow<List<ActiveNotificationItem>>(emptyList())
    val notifications: StateFlow<List<ActiveNotificationItem>> = _notifications.asStateFlow()

    init {
        loadBroadcasts()
    }

    fun loadBroadcasts() {
        viewModelScope.launch {
            AuthApiClient.getActiveNotifications().onSuccess { list ->
                _notifications.value = list
            }
        }
    }
}
