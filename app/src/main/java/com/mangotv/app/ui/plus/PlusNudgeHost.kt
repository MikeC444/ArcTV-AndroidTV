package com.mangotv.app.ui.plus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.plus.NudgeTrigger
import com.mangotv.app.data.plus.nudgeDue
import kotlinx.coroutines.delay

/** How long after the person is back on a screen the after-watch ask waits, so it does not land on top of the screen changing. */
private const val AFTER_WATCH_DELAY_MS = 1_200L

/**
 * The unprompted Plus asks, at the root of the app. After a title has been watched through the player raises "after watch"; once the person is back
 * on a screen where an ask may open ([blocked] is false), this checks they are eligible (signed in, Plus a paid tier, no Plus, not a kids profile)
 * and that the gap since the last ask and this kind's cap allow it ([nudgeDue]); if so it opens [AFTER_WATCH_ASK], counted. Either way the request is
 * then used up, so it never queues up. "Take me there" ([onGo]) goes to Settings -> Arc TV Plus.
 */
@Composable
fun PlusNudgeHost(blocked: Boolean, onGo: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MangoTvApplication).container }
    val repository = container.plusNudgeRepository
    val pending by repository.afterWatchPending.collectAsStateWithLifecycle()
    val record by repository.record.collectAsStateWithLifecycle()
    val eligible = rememberPlusAskEligible()
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(pending, blocked, record, eligible) {
        if (!pending || blocked || record == null) return@LaunchedEffect
        delay(AFTER_WATCH_DELAY_MS)
        if (eligible && nudgeDue(record, NudgeTrigger.AFTER_WATCH, System.currentTimeMillis())) {
            repository.markShown(NudgeTrigger.AFTER_WATCH)
            open = true
        }
        repository.clearAfterWatch()
    }

    if (open) {
        PlusAskDialog(
            ask = AFTER_WATCH_ASK,
            onClose = { open = false },
            onGo = {
                open = false
                onGo()
            }
        )
    }
}
