package com.mangotv.app.ui.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatFailureTest {
    private fun error(code: Int) = PlaybackException("test", null, code)

    @Test
    fun `decoder and container failures are format failures, so VLC's engine takes over`() {
        assertTrue(isFormatFailure(error(PlaybackException.ERROR_CODE_DECODING_FAILED)))
        assertTrue(isFormatFailure(error(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)))
        assertTrue(isFormatFailure(error(PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES)))
        assertTrue(isFormatFailure(error(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)))
        assertTrue(isFormatFailure(error(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)))
    }

    @Test
    fun `network and other failures are not`() {
        assertFalse(isFormatFailure(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)))
        assertFalse(isFormatFailure(error(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)))
        assertFalse(isFormatFailure(error(PlaybackException.ERROR_CODE_UNSPECIFIED)))
    }
}
