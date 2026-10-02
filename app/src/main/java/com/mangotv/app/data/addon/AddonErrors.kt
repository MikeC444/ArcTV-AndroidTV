package com.mangotv.app.data.addon

import java.net.SocketTimeoutException

/**
 * Why an addon didn't answer, as the end of a sentence about it ("<addon> took too long to answer"). Shown on Select a
 * Source so a missing source is never a mystery. Never includes the address, which can carry an account key.
 */
fun describeAddonError(error: Throwable): String {
    val status = (error as? AddonHttpException)?.status
    return when {
        status == 429 -> "is limiting requests right now \u2014 try again in a moment"
        status != null && status >= 500 -> "had a problem answering (HTTP $status)"
        status != null -> "refused the request (HTTP $status)"
        error is SocketTimeoutException -> "took too long to answer"
        else -> "couldn't be reached"
    }
}
