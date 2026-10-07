package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Talks to /user/plus: whether this account has ArcTV Plus, and starting a checkout for a plan. Every call is authenticated. */
class PlusApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getStatus(accessToken: String): PlusStatusDto = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/user/plus").header("Authorization", "Bearer $accessToken").build()
        json.decodeFromString(PlusStatusDto.serializer(), execute(request))
    }

    /** Starts a Stripe Checkout for [plan] ("monthly", "yearly" or "lifetime") and returns the hosted payment page's URL and price. */
    suspend fun startCheckout(accessToken: String, plan: String): PlusCheckoutLink = withContext(Dispatchers.IO) {
        val body = json.encodeToString(PlusCheckoutRequest.serializer(), PlusCheckoutRequest(plan))
        val request = Request.Builder()
            .url("$baseUrl/user/plus/checkout")
            .header("Authorization", "Bearer $accessToken")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = json.decodeFromString(PlusCheckoutResponse.serializer(), execute(request))
        PlusCheckoutLink(response.url, response.amountTotal, response.currency, response.trialDays)
    }

    /** Cancels a monthly or yearly subscription at the end of the period already paid for (nothing is refunded); returns the new status. */
    suspend fun cancelSubscription(accessToken: String): PlusStatusDto = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/user/plus/cancel")
            .header("Authorization", "Bearer $accessToken")
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        json.decodeFromString(PlusStatusDto.serializer(), execute(request))
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
