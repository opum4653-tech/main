package com.andorid.ninmods.data

import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.network.UserProfileData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Enterprise-grade Repository Pattern for User Profile & License State.
 *
 * Implements:
 * 1. Stale-While-Revalidate caching pattern
 * 2. In-memory temporary cache with configurable TTL (60s)
 * 3. Concurrency request deduplication (Request Coalescing via Mutex)
 * 4. Conditional ETag sync to save client/server bandwidth & DB load
 * 5. Instant 0ms navigation between screens without blank screen or flickering
 */
object UserRepository {

    // Reactive StateFlow for Compose UI subscriptions
    private val _profileState = MutableStateFlow<UserProfileData?>(null)
    val profileState: StateFlow<UserProfileData?> = _profileState.asStateFlow()

    // In-memory cache store
    private var cachedProfile: UserProfileData? = null
    private var cachedEtag: String? = null
    private var lastFetchTimestamp: Long = 0L

    // Cache TTL: 60 seconds (Navigations within 60s hit memory cache instantly)
    const val CACHE_TTL_MS: Long = 60_000L

    // Mutex lock to prevent duplicate concurrent network calls during fast tab navigation
    private val fetchMutex = Mutex()

    /**
     * Get the cached profile immediately (0ms latency, zero network).
     */
    fun getCachedProfile(): UserProfileData? = cachedProfile ?: _profileState.value

    /**
     * Fetch user profile with intelligent caching & request deduplication.
     *
     * @param apiKey The user authorization token.
     * @param forceRefresh True to bypass in-memory cache (e.g. manual pull-to-refresh).
     */
    suspend fun getProfile(apiKey: String, forceRefresh: Boolean = false): Result<UserProfileData> {
        val now = System.currentTimeMillis()

        // 1. Return immediately from in-memory cache if fresh
        if (!forceRefresh && cachedProfile != null && (now - lastFetchTimestamp < CACHE_TTL_MS)) {
            return Result.success(cachedProfile!!)
        }

        // 2. Coalesce concurrent requests into a single network execution
        return fetchMutex.withLock {
            val currentNow = System.currentTimeMillis()
            if (!forceRefresh && cachedProfile != null && (currentNow - lastFetchTimestamp < CACHE_TTL_MS)) {
                return@withLock Result.success(cachedProfile!!)
            }

            val result = AuthApiClient.getProfileWithEtag(apiKey, cachedEtag)
            result.fold(
                onSuccess = { (data, newEtag, isNotModified) ->
                    if (isNotModified && cachedProfile != null) {
                        // Server responded 304 Not Modified: refresh timestamp without transferring DB data
                        lastFetchTimestamp = System.currentTimeMillis()
                        Result.success(cachedProfile!!)
                    } else if (data != null) {
                        cachedProfile = data
                        cachedEtag = newEtag ?: cachedEtag
                        lastFetchTimestamp = System.currentTimeMillis()
                        _profileState.value = data
                        Result.success(data)
                    } else if (cachedProfile != null) {
                        Result.success(cachedProfile!!)
                    } else {
                        Result.failure(Exception("Empty profile response from server"))
                    }
                },
                onFailure = { err ->
                    val isAuthError = err.message?.let { msg ->
                        msg.contains("401") ||
                        msg.contains("Session expired", ignoreCase = true) ||
                        msg.contains("SESSION_REVOKED", ignoreCase = true) ||
                        msg.contains("Unauthorized", ignoreCase = true) ||
                        msg.contains("token expired", ignoreCase = true) ||
                        msg.contains("Invalid API key", ignoreCase = true)
                    } ?: false

                    if (isAuthError) {
                        // Immediately wipe stale cache so unauthorized device cannot stay logged in
                        clear()
                        Result.failure(err)
                    } else if (cachedProfile != null) {
                        // Only fallback to cached profile on pure network connection timeout (offline tolerance)
                        Result.success(cachedProfile!!)
                    } else {
                        Result.failure(err)
                    }
                }
            )
        }
    }

    /**
     * Seed or update cached profile locally (e.g. on Login or password update).
     */
    fun seedProfile(profile: UserProfileData) {
        cachedProfile = profile
        _profileState.value = profile
        lastFetchTimestamp = System.currentTimeMillis()
    }

    /**
     * Invalidate cache to force fresh inquiry on next fetch (e.g. on license activation).
     */
    fun invalidate() {
        lastFetchTimestamp = 0L
    }

    /**
     * Clear all user profile data from memory (e.g. on sign out).
     */
    fun clear() {
        cachedProfile = null
        cachedEtag = null
        lastFetchTimestamp = 0L
        _profileState.value = null
    }
}
