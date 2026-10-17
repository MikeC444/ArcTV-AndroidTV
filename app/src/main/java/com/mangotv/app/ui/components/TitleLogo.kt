package com.mangotv.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.provider.ProviderRegistry
import com.mangotv.app.data.provider.loadDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

// One look-up per title for the life of the app: the answer (a title with no logo too) is kept, parallel asks share one request and a failed ask is not kept.
private val answers = ConcurrentHashMap<String, String>()
private val knownNull = ConcurrentHashMap.newKeySet<String>()
private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()

// The look-ups run here, not in the card that asked: a card scrolling out of sight must not cancel the answer other cards are waiting for.
private val lookupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun keyOf(type: ContentType, id: String) = "${type.name}|$id"

/**
 * A title's logo picture (its name set in its own lettering), looked up through the same addons as its details page. A Continue Watching entry is saved without
 * one, so the card asks once per title. Null while the addons are not loaded yet (nothing is remembered then), or when the title has none. Same job as the web
 * app's `state/titleLogo.ts`.
 */
suspend fun fetchTitleLogo(providerId: String, type: ContentType, id: String): String? {
    val key = keyOf(type, id)
    answers[key]?.let { return it }
    if (key in knownNull) return null
    val providers = ProviderRegistry.activeProviders()
    if (providers.isEmpty()) return null
    val request = inFlight.getOrPut(key) {
        lookupScope.async {
            try {
                // remembered only when the details were found (a title that has no logo is then known to have none); a failed look-up is asked again
                val detail = loadDetails(providers, providerId, type, id)
                val logo = detail?.logoUrl
                if (logo != null) answers[key] = logo else if (detail != null) knownNull += key
                logo
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                inFlight.remove(key)
            }
        }
    }
    return request.await()
}

/** The logo to show for [content]: the one it already has, else the looked-up one once [enabled]. Null while unknown or when it has none. */
@Composable
fun rememberTitleLogo(content: Content, enabled: Boolean): State<String?> {
    // asked again when the installed addons change, so a card drawn before the addons loaded gets its logo once they have
    val installed by ProviderRegistry.providers.collectAsState()
    return produceState(initialValue = content.logoUrl ?: answers[keyOf(content.type, content.id)], content.id, content.type, content.providerId, enabled, installed.size) {
        if (content.logoUrl != null) {
            value = content.logoUrl
        } else if (enabled && content.providerId != null) {
            value = fetchTitleLogo(content.providerId, content.type, content.id) ?: value
        }
    }
}
