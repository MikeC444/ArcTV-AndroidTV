package com.mangotv.app.data.network

import com.mangotv.app.data.profile.PinChange
import com.mangotv.app.data.profile.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Talks to /user/profiles: the account's profiles, and checking a profile's PIN. Account-level, so [AccountApiHttpClient] never
 * adds the profile header to these. Every call is authenticated. A backend that predates profiles answers 404 on the list.
 */
class ProfileApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(accessToken: String): List<Profile> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/user/profiles").header("Authorization", "Bearer $accessToken").build()
        json.decodeFromString(ProfilesResponse.serializer(), execute(request)).profiles
    }

    suspend fun create(accessToken: String, name: String, avatar: String, kind: String, pin: String?): Profile = withContext(Dispatchers.IO) {
        val body = json.encodeToString(ProfileCreateRequest.serializer(), ProfileCreateRequest(name, avatar, kind, pin))
        val request = Request.Builder()
            .url("$baseUrl/user/profiles")
            .header("Authorization", "Bearer $accessToken")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        json.decodeFromString(Profile.serializer(), execute(request))
    }

    /** Changes only what is given; [pin] says whether the PIN stays, goes, or becomes a new one. */
    suspend fun update(
        accessToken: String,
        profileId: String,
        name: String?,
        avatar: String?,
        kind: String?,
        pin: PinChange
    ): Profile = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            if (name != null) put("name", name)
            if (avatar != null) put("avatar", avatar)
            if (kind != null) put("kind", kind)
            when (pin) {
                PinChange.Keep -> Unit
                PinChange.Remove -> put("pin", JsonNull)
                is PinChange.Set -> put("pin", JsonPrimitive(pin.pin))
            }
        }.toString()
        val request = Request.Builder()
            .url("$baseUrl/user/profiles/$profileId")
            .header("Authorization", "Bearer $accessToken")
            .put(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        json.decodeFromString(Profile.serializer(), execute(request))
    }

    suspend fun delete(accessToken: String, profileId: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/user/profiles/$profileId").header("Authorization", "Bearer $accessToken").delete().build()
        execute(request)
        Unit
    }

    /** Succeeds for the right PIN (or a profile without one); throws [ApiException] 403 for a wrong one and 429 once guessing is locked. */
    suspend fun verifyPin(accessToken: String, profileId: String, pin: String) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(VerifyPinRequest.serializer(), VerifyPinRequest(pin))
        val request = Request.Builder()
            .url("$baseUrl/user/profiles/$profileId/verify-pin")
            .header("Authorization", "Bearer $accessToken")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        execute(request)
        Unit
    }

    private fun execute(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { json.decodeFromString(ErrorResponse.serializer(), bodyString).error }.getOrNull()
                throw ApiException(response.code, message ?: "Request failed with HTTP ${response.code}")
            }
            return bodyString
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
