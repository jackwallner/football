package com.jackwallner.football

import android.content.Context
import com.jackwallner.football.data.ConversionDiagnostics
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.data.FavoritesStore
import com.jackwallner.football.data.OfflineStatcastApi
import com.jackwallner.football.data.PaywallGate
import com.jackwallner.football.data.ReviewPromptTracker
import com.jackwallner.football.data.SharedPreferencesStore
import com.jackwallner.football.data.StatcastApi
import com.jackwallner.football.data.StatcastProviding
import com.jackwallner.football.data.SubscriptionService
import com.jackwallner.football.data.SystemAppClock
import com.jackwallner.football.data.TwoTierPlayerCache
import kotlinx.coroutines.MainScope

/** Every long-lived object the screens share, built once per process. */
class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = MainScope()
    val clock = SystemAppClock()
    val defaults = SharedPreferencesStore(appContext)
    val review = ReviewPromptTracker(defaults, clock)
    val diagnostics = ConversionDiagnostics(defaults, clock)
    val subscriptions = SubscriptionService(appContext, defaults, diagnostics)
    val favorites = FavoritesStore(defaults)
    val paywallGate = PaywallGate()

    /** Null when the build has no feed configuration: the app says so instead of showing an empty league. */
    val api: StatcastProviding? = if (BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
        StatcastApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    } else null

    val dashboard = DashboardViewModel(
        provider = api ?: OfflineStatcastApi,
        cache = TwoTierPlayerCache(appContext.cacheDir) {
            runCatching { appContext.assets.open("players-historical.json.gz") }.getOrNull()
        },
        defaults = defaults,
        scope = scope,
        log = { if (BuildConfig.DEBUG) android.util.Log.d("StatScoutLoad", it) },
    )
}
