package com.mangotv.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotesTest {

    private fun release(tag: String, body: String?, draft: Boolean = false, prerelease: Boolean = false) =
        GitHubReleaseDto(tagName = tag, body = body, draft = draft, prerelease = prerelease)

    @Test
    fun `Markdown becomes plain text with dots for bullets`() {
        val md = "## What's new\n\n- **Faster** start\n- `Blocked Genres` in [Settings](https://example.com)\n\n\n\nThanks!"
        assertEquals("What's new\n\n• Faster start\n• Blocked Genres in Settings\n\nThanks!", plainNotes(md))
    }

    @Test
    fun `lines that send you to GitHub are left out`() {
        val md = "- Real change\n**Full Changelog**: https://github.com/a/b/compare/v1...v2\nSee commit history for changes in this release."
        assertEquals("• Real change", plainNotes(md))
    }

    @Test
    fun `a single missing release shows its notes without a version heading`() {
        val releases = listOf(release("v0.2.0", "- New thing"), release("v0.1.0", "- Old thing"))
        assertEquals("• New thing", combineReleaseNotes(releases, installedVersion = "0.1.0", latestTag = "v0.2.0"))
    }

    @Test
    fun `skipped releases all show, newest first, each under its version`() {
        val releases = listOf(
            release("v0.3.0", "- Three"),
            release("v0.2.0", "- Two"),
            release("v0.1.1", "- One point one"),
            release("v0.1.0", "- Installed")
        )
        val notes = combineReleaseNotes(releases, installedVersion = "0.1.0", latestTag = "v0.3.0")
        assertEquals("Version 0.3.0\n• Three\n\nVersion 0.2.0\n• Two\n\nVersion 0.1.1\n• One point one", notes)
    }

    @Test
    fun `drafts, pre-releases and anything newer than the update on offer are ignored`() {
        val releases = listOf(
            release("v0.4.0", "- Future"),
            release("v0.3.0", "- Three"),
            release("v0.2.5-beta", "- Beta", prerelease = true),
            release("v0.2.0", "- Draft", draft = true)
        )
        assertEquals("• Three", combineReleaseNotes(releases, installedVersion = "0.1.0", latestTag = "v0.3.0"))
    }

    @Test
    fun `a release with no notes is skipped, and no notes at all gives the fallback`() {
        val releases = listOf(release("v0.2.0", null), release("v0.1.5", "  "))
        assertEquals(NO_NOTES_FALLBACK, combineReleaseNotes(releases, installedVersion = "0.1.0", latestTag = "v0.2.0"))
        assertFalse(NO_NOTES_FALLBACK.contains("GitHub", ignoreCase = true))
        assertTrue(NO_NOTES_FALLBACK.isNotBlank())
    }
}
