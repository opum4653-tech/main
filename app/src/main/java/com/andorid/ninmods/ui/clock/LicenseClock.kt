package com.andorid.ninmods.ui.clock

import com.andorid.ninmods.data.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Live license countdown. Emits once per second from the monotonic server
 * duration stored in SessionManager, so device clock changes cannot move it.
 */
class LicenseClock(private val intervalMs: Long = 1_000L) {

    fun remaining(session: SessionManager): Flow<Long> = flow {
        while (true) {
            emit(session.liveRemainingSeconds)
            delay(intervalMs)
        }
    }
}
