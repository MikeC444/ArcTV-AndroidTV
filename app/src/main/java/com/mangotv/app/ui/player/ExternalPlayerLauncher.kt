package com.mangotv.app.ui.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager

private fun viewIntent(url: String, title: String): Intent =
    Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(url), "video/*")
        .putExtra("title", title) // read by VLC / MX Player / Just Player
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** True when some installed app can open a video link (needs the `<queries>` entry in the manifest to see them). */
fun hasExternalPlayer(context: Context, url: String): Boolean =
    context.packageManager.queryIntentActivities(viewIntent(url, ""), PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()

/** Hands the stream to another player app (the system picks, or asks if there are several). False when none took it. */
fun openInExternalPlayer(context: Context, url: String, title: String): Boolean =
    try {
        context.startActivity(viewIntent(url, title))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
