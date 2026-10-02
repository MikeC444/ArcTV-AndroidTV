package com.mangotv.app.data.update

/** What the pop-up shows when a release came with no notes at all. */
const val NO_NOTES_FALLBACK = "Bug fixes and improvements."

// Lines that only point somewhere else ("see the full changelog on GitHub"): the pop-up is where the notes are read, so
// these are left out rather than sending anyone off the TV.
private val POINTER_LINE = Regex("(full changelog|see commit history|view (it |this )?on github|github\\.com|see the (release|changelog))", RegexOption.IGNORE_CASE)
private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
private val HEADING = Regex("^\\s{0,3}#{1,6}\\s*")
private val BULLET = Regex("^(\\s*)[-*+]\\s+")

/** GitHub release notes are Markdown; the pop-up shows plain text, so bullets become dots and the Markdown marks go. */
internal fun plainNotes(markdown: String): String =
    markdown.lineSequence()
        .map { it.trimEnd() }
        .filterNot { POINTER_LINE.containsMatchIn(it) }
        .map { line ->
            line.replace(LINK, "$1")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace(HEADING, "")
                .replace(BULLET, "$1• ")
        }
        .joinToString("\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

/**
 * The notes for everything the person is missing: every published release newer than [installedVersion], up to and
 * including [latestTag], newest first, each under its own "Version x" heading when there is more than one. Someone who
 * skipped releases therefore reads all of what changed, not only the last release's notes. Drafts and pre-releases are
 * ignored. Falls back to [NO_NOTES_FALLBACK] when there is nothing to show.
 */
internal fun combineReleaseNotes(releases: List<GitHubReleaseDto>, installedVersion: String, latestTag: String): String {
    val latest = VersionUtils.parse(latestTag)
    val installed = VersionUtils.parse(installedVersion)
    val wanted = releases
        .filter { !it.draft && !it.prerelease }
        .mapNotNull { release ->
            val tag = release.tagName?.takeIf { it.isNotBlank() } ?: release.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val version = VersionUtils.parse(tag) ?: return@mapNotNull null
            if (installed != null && version <= installed) return@mapNotNull null
            if (latest != null && version > latest) return@mapNotNull null
            Triple(version, VersionUtils.normalize(tag), plainNotes(release.body.orEmpty()))
        }
        .filter { it.third.isNotBlank() }
        .sortedByDescending { it.first }
    return when {
        wanted.isEmpty() -> NO_NOTES_FALLBACK
        wanted.size == 1 -> wanted.single().third
        else -> wanted.joinToString("\n\n") { (_, name, notes) -> "Version $name\n$notes" }
    }
}
