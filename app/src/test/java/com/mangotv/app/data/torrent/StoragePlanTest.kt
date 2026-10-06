package com.mangotv.app.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePlanTest {
    private val mb = 1024L * 1024
    private val config = TorrentStreamConfig(readAheadBytes = 64 * mb, startBufferBytes = 8 * mb, maxStorageBytes = 2048 * mb)

    @Test fun capIsTheConfiguredLimitWhenThereIsRoom() {
        assertEquals(StoragePlan.Ok(2048 * mb), planStorage(10_000 * mb, config, 50_000 * mb))
    }

    @Test fun capShrinksToFreeSpaceMinusMargin() {
        val plan = planStorage(10_000 * mb, config, 600 * mb) as StoragePlan.Ok
        assertEquals(600 * mb - SAFETY_MARGIN_BYTES, plan.capBytes)
    }

    @Test fun smallFileNeedsNoMoreThanItself() {
        val plan = planStorage(100 * mb, config, 50_000 * mb) as StoragePlan.Ok
        assertTrue(plan.capBytes <= 102 * mb)
    }

    @Test fun tooLittleSpaceIsRefusedWithWhatIsNeeded() {
        val plan = planStorage(10_000 * mb, config, 100 * mb)
        assertTrue(plan is StoragePlan.NotEnough)
        assertTrue((plan as StoragePlan.NotEnough).neededBytes > 72 * mb)
    }

    @Test fun capNeverDropsBelowTheWindowItself() {
        val tight = config.copy(maxStorageBytes = 10 * mb)
        assertEquals(StoragePlan.Ok(72 * mb), planStorage(10_000 * mb, tight, 50_000 * mb))
    }

    @Test fun startBufferIsNeverBiggerThanReadAhead() {
        assertEquals(16 * mb, TorrentStreamConfig(readAheadBytes = 16 * mb, startBufferBytes = 64 * mb).effectiveStartBytes)
    }
}
