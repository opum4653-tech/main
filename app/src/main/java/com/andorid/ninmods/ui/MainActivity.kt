package com.andorid.ninmods.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.andorid.ninmods.R
import com.andorid.ninmods.data.UserRepository
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.databinding.ActivityMainBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.ui.dashboard.DashboardFragment
import com.andorid.ninmods.ui.denylist.DenyListFragment
import com.andorid.ninmods.ui.deposit.AddFundsFragment
import com.andorid.ninmods.ui.identity.IdentityFragment
import com.andorid.ninmods.ui.licenses.LicensesFragment
import com.andorid.ninmods.ui.loader.LoaderFragment
import com.andorid.ninmods.ui.login.LoginActivity
import com.andorid.ninmods.ui.profile.ProfileFragment
import com.andorid.ninmods.ui.settings.SettingsFragment
import com.andorid.ninmods.ui.support.SupportFragment
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

/**
 * Single-activity shell. DrawerLayout holds the NavigationView and one
 * FragmentContainerView. Every destination is a Fragment bound to its own XML.
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    lateinit var sessionManager: SessionManager
        private set
    private val viewModel: MainViewModel by viewModels()

    /** Cached fragment instances so drawer round-trips keep their state. */
    private val screens = mutableMapOf<Destination, Fragment>()
    private var currentDestination: Destination? = null
    private var subScreenDepth = 0
    private var lastBackPress = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { handleNavigationIcon() }
        binding.navigationView.setNavigationItemSelectedListener(this)

        subScreenDepth = supportFragmentManager.backStackEntryCount
        if (!restoreDestination(savedInstanceState)) {
            selectDestination(Destination.DASHBOARD)
        }

        populateHeader()
        observeSessionToken()
        registerBackHandler()
        verifySession()
    }

    // ---------------------------------------------------------------- navigation

    private fun restoreDestination(state: Bundle?): Boolean {
        val name = state?.getString(KEY_DESTINATION) ?: return false
        val dest = runCatching { Destination.valueOf(name) }.getOrNull() ?: return false
        selectDestination(dest, animate = false)
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentDestination?.let { outState.putString(KEY_DESTINATION, it.name) }
    }

    fun selectDestination(dest: Destination, animate: Boolean = true) {
        if (subScreenDepth > 0) {
            supportFragmentManager.popBackStackImmediate(
                null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
            subScreenDepth = 0
        }

        val fragment = screens.getOrPut(dest) { dest.create() }
        val tag = dest.name

        supportFragmentManager.commitNow {
            setCustomAnimations(
                if (animate) android.R.anim.fade_in else 0,
                if (animate) android.R.anim.fade_out else 0
            )
            replace(R.id.main_fragment_container, fragment, tag)
        }

        currentDestination = dest
        binding.toolbar.title = getString(dest.titleRes)
        binding.toolbar.setNavigationIcon(R.drawable.ic_menu)
        binding.navigationView.setCheckedItem(dest.menuId)
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    /** Push a detail screen onto the stack; the toolbar switches to a back arrow. */
    fun openSubScreen(fragment: Fragment, titleRes: Int) {
        supportFragmentManager.commit {
            setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.fade_out,
                R.anim.fade_in,
                R.anim.slide_out_right
            )
            replace(R.id.main_fragment_container, fragment)
            addToBackStack(titleRes.toString())
        }
        subScreenDepth = supportFragmentManager.backStackEntryCount
        binding.toolbar.title = getString(titleRes)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
    }

    fun setToolbarTitle(titleRes: Int) {
        binding.toolbar.title = getString(titleRes)
    }

    fun setToolbarTitle(title: CharSequence) {
        binding.toolbar.title = title
    }

    fun closeDrawer() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun handleNavigationIcon() {
        if (subScreenDepth > 0) {
            supportFragmentManager.popBackStack()
            subScreenDepth = supportFragmentManager.backStackEntryCount
            if (subScreenDepth == 0) {
                currentDestination?.let {
                    binding.toolbar.title = getString(it.titleRes)
                    binding.toolbar.setNavigationIcon(R.drawable.ic_menu)
                }
            } else {
                binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            }
        } else {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val dest = Destination.fromMenuId(item.itemId)
        if (dest == null) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return false
        }
        if (dest == currentDestination && subScreenDepth == 0) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return true
        }
        selectDestination(dest)
        return true
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> closeDrawer()

                    subScreenDepth > 0 -> {
                        supportFragmentManager.popBackStack()
                        subScreenDepth = supportFragmentManager.backStackEntryCount
                        if (subScreenDepth == 0) {
                            currentDestination?.let {
                                binding.toolbar.title = getString(it.titleRes)
                                binding.toolbar.setNavigationIcon(R.drawable.ic_menu)
                            }
                        }
                    }

                    else -> {
                        val now = System.currentTimeMillis()
                        if (now - lastBackPress <= 2000L) {
                            finish()
                        } else {
                            lastBackPress = now
                            Toast.makeText(
                                this@MainActivity,
                                R.string.error_press_back_exit,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        })
    }

    // ---------------------------------------------------------------- session

    private fun populateHeader() {
        val header = binding.navigationView.getHeaderView(0)
        header.findViewById<android.widget.TextView>(R.id.header_name).text =
            sessionManager.userName?.takeIf { it.isNotBlank() } ?: getString(R.string.brand_default_user)
        header.findViewById<android.widget.TextView>(R.id.header_email).text =
            sessionManager.userEmail?.takeIf { it.isNotBlank() } ?: getString(R.string.brand_tagline)
        val roleView = header.findViewById<android.widget.TextView>(R.id.header_role)
        val role = sessionManager.userRole
        if (role.isNotBlank() && !role.equals("user", ignoreCase = true)) {
            roleView.text = role
            roleView.visibility = View.VISIBLE
        } else {
            roleView.visibility = View.GONE
        }
    }

    private fun observeSessionToken() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Re-read header whenever the profile cache is refreshed elsewhere.
                populateHeader()
            }
        }
    }

    /**
     * Validates the stored token once per launch. A revoked or expired token
     * sends the user back to LoginActivity with the cached profile wiped.
     */
    private fun verifySession() {
        val token = sessionManager.authToken
        if (!sessionManager.isLoggedIn || token.isNullOrBlank()) {
            signOut(cacheOnly = true)
            return
        }
        binding.sessionProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = UserRepository.getProfile(token, forceRefresh = true)
            binding.sessionProgress.visibility = View.GONE
            result.fold(
                onSuccess = { profile ->
                    sessionManager.userName = profile.username
                    sessionManager.userEmail = profile.email
                    sessionManager.userRole = profile.role
                    sessionManager.userBalance = profile.balance
                    val license = profile.activeLicense
                    if (license != null && license.licenseKey.isNotBlank()) {
                        sessionManager.activeLicenseKey = license.licenseKey
                        sessionManager.serverRemainingSeconds = license.remainingSeconds
                    }
                    populateHeader()
                },
                onFailure = { error ->
                    val revoked = error.message?.let(::looksLikeAuthFailure) ?: false
                    if (revoked) {
                        Toast.makeText(this@MainActivity, R.string.error_session_revoked, Toast.LENGTH_LONG).show()
                        signOut(cacheOnly = false)
                    }
                }
            )
        }
    }

    private fun looksLikeAuthFailure(message: String): Boolean =
        message.contains("401") ||
            message.contains("Session expired", ignoreCase = true) ||
            message.contains("SESSION_REVOKED", ignoreCase = true) ||
            message.contains("Unauthorized", ignoreCase = true) ||
            message.contains("token expired", ignoreCase = true) ||
            message.contains("Invalid API key", ignoreCase = true)

    fun signOut(cacheOnly: Boolean = false) {
        sessionManager.logout()
        UserRepository.clear()
        if (cacheOnly) {
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
        finish()
    }

    fun showProgress(visible: Boolean) {
        binding.sessionProgress.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun loaderViewModel(): MainViewModel = viewModel

    companion object {
        private const val KEY_DESTINATION = "state_destination"

        fun refreshProfileAsync(activity: AppCompatActivity, token: String) {
            (activity as? MainActivity)?.let { host ->
                host.lifecycleScope.launch {
                    AuthApiClient.getProfileWithEtag(token).onSuccess { (profile, _, _) ->
                        profile?.let {
                            host.sessionManager.userBalance = it.balance
                            host.populateHeader()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Drawer destinations. Detail screens (deposit form, ticket chat, ...) are not
 * listed here; those are pushed with [MainActivity.openSubScreen].
 */
enum class Destination(val titleRes: Int, val menuId: Int) {
    DASHBOARD(R.string.nav_dashboard, R.id.nav_dashboard),
    LOADER(R.string.nav_loader, R.id.nav_loader),
    DENYLIST(R.string.nav_denylist, R.id.nav_denylist),
    IDENTITY(R.string.nav_identity, R.id.nav_identity),
    LICENSES(R.string.nav_my_licenses, R.id.nav_my_licenses),
    ADD_FUNDS(R.string.nav_add_funds, R.id.nav_add_funds),
    PROFILE(R.string.nav_profile, R.id.nav_profile),
    SETTINGS(R.string.nav_settings, R.id.nav_settings),
    SUPPORT(R.string.nav_support, R.id.nav_support);

    fun create(): Fragment = when (this) {
        DASHBOARD -> DashboardFragment()
        LOADER -> LoaderFragment()
        DENYLIST -> DenyListFragment()
        IDENTITY -> IdentityFragment()
        LICENSES -> LicensesFragment()
        ADD_FUNDS -> AddFundsFragment()
        PROFILE -> ProfileFragment()
        SETTINGS -> SettingsFragment()
        SUPPORT -> SupportFragment()
    }

    companion object {
        fun fromMenuId(id: Int): Destination? = entries.firstOrNull { it.menuId == id }
    }
}
