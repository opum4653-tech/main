package com.andorid.ninmods.ui.mod

import android.content.Context
import com.andorid.ninmods.data.InteractiveProcessBridge
import com.andorid.ninmods.data.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide mod runtime. One bridge, one engine per variant, so the
 * dashboard lists, the mod flow dialog and any screen that reads run state all
 * look at the same live objects.
 */
object ModRuntime {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bridge = InteractiveProcessBridge(scope)

    private var appContext: Context? = null
    private var session: SessionManager? = null
    private var kernel: ModEngine? = null
    private var external: ModEngine? = null

    fun install(context: Context, sessionManager: SessionManager) {
        appContext = context.applicationContext
        session = sessionManager
        kernel = null
        external = null
    }

    fun engine(cacheDirName: String): ModEngine {
        val context = requireNotNull(appContext) { "ModRuntime.install() must run before engine()" }
        val sessionManager = session
        return when (cacheDirName) {
            ModEngine.KERNEL.cacheDirName -> kernel ?: ModEngine(
                context, bridge, sessionManager, ModEngine.KERNEL
            ).also { kernel = it }

            else -> external ?: ModEngine(
                context, bridge, sessionManager, ModEngine.EXTERNAL
            ).also { external = it }
        }
    }

    fun kernel(): ModEngine = engine(ModEngine.KERNEL.cacheDirName)
    fun external(): ModEngine = engine(ModEngine.EXTERNAL.cacheDirName)

    fun reset() {
        kernel = null
        external = null
        bridge.stop()
    }

    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}
