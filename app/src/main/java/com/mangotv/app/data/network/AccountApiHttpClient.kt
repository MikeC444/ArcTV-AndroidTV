package com.mangotv.app.data.network

import com.mangotv.app.data.profile.ActiveProfile
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One shared OkHttpClient for every account-API client (Auth, Settings,
 * Watchlist, PlaybackProgress, AddonSync) — Milestone 15. Each of the five
 * used to build its own `OkHttpClient.Builder()` with the exact same
 * timeouts, which meant five separate connection pools and five separate
 * dispatcher thread pools all talking to the same `API_BASE_URL` host —
 * pure overhead with no upside, and measurable on Fire TV Stick hardware's
 * limited RAM/CPU. Sharing one client here means a keep-alive connection
 * opened for, say, a settings pull can be reused by a watchlist push
 * moments later instead of every domain maintaining its own idle pool to
 * the identical host.
 *
 * Deliberately scoped to just these five: StremioAddonClient (arbitrary,
 * often slower self-hosted addon servers) and PlayerEngine's OkHttpDataSource
 * (streaming media, different timeout needs entirely) stay on their own
 * clients — those are genuinely different traffic with different
 * performance characteristics, not the same kind of duplication this object
 * fixes.
 */
object AccountApiHttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // ArcTV Plus profiles: every library request says which profile it is for. One place, so no API client can forget it.
        .addInterceptor { chain ->
            val request = chain.request()
            val profile = profileHeaderValue(request.url.encodedPath, ActiveProfile.id)
            chain.proceed(if (profile == null) request else request.newBuilder().header(PROFILE_HEADER, profile).build())
        }
        .build()
}

const val PROFILE_HEADER = "X-ArcTV-Profile"

/**
 * The value of X-ArcTV-Profile for a request to [path], or null for none. None for the account's own profile (no header means
 * exactly that, so the app keeps working against a backend that predates profiles) and for the account-level calls (who the
 * account is, whether it has Plus, the profile list itself), which belong to no one profile.
 */
fun profileHeaderValue(path: String, profileId: String): String? {
    if (profileId == ActiveProfile.DEFAULT_ID) return null
    val at = path.indexOf("/user/")
    if (at < 0) return null
    val rest = path.substring(at + "/user/".length)
    val first = rest.substringBefore('/')
    return if (first == "me" || first == "plus" || first == "profiles") null else profileId
}
