package com.mangotv.app.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestGateTest {

    private fun session(refreshExpiresInMs: Long): Session {
        val now = System.currentTimeMillis()
        return Session(
            accessToken = "access",
            accessTokenExpiresAtMillis = now + 60_000,
            refreshToken = "refresh",
            refreshTokenExpiresAtMillis = now + refreshExpiresInMs,
            user = AuthenticatedUser(id = "u1", email = "a@example.com")
        )
    }

    @Test
    fun `nobody is a guest until the stored session has been read`() {
        assertFalse(isGuestSession(loaded = false, session = null))
        assertFalse(isGuestSession(loaded = false, session = session(1_000_000)))
    }

    @Test
    fun `no session once it has been read means a guest`() {
        assertTrue(isGuestSession(loaded = true, session = null))
    }

    @Test
    fun `a session that can still be renewed is not a guest`() {
        assertFalse(isGuestSession(loaded = true, session = session(refreshExpiresInMs = 1_000_000)))
    }

    @Test
    fun `a session that can no longer be renewed is a guest`() {
        assertTrue(isGuestSession(loaded = true, session = session(refreshExpiresInMs = -1_000)))
    }
}
