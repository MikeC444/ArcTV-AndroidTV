package com.mangotv.app.data.update

private const val GITHUB_OWNER = "MikeC444"
private const val GITHUB_REPO = "ArcTV-AndroidTV"

internal class NoEligibleUpdateException : IllegalStateException("Latest release has no APK asset")

/**
 * Looks up the latest GitHub release and turns it into an [AppUpdate] if
 * it has an APK attached. GitHub's own "latest release" endpoint already
 * excludes drafts and prereleases, so there's no channel/prerelease
 * filtering to do here beyond that.
 */
class UpdateRepository(
    private val apiClient: GitHubUpdateApiClient = GitHubUpdateApiClient(GITHUB_OWNER, GITHUB_REPO)
) {
    suspend fun getLatestUpdate(installedVersion: String): Result<AppUpdate> {
        return runCatching {
            val dto = apiClient.getLatestRelease()
            val asset = AbiSelector.chooseBestApkAsset(dto.assets) ?: throw NoEligibleUpdateException()
            val tag = dto.tagName?.takeIf { it.isNotBlank() }
                ?: dto.name?.takeIf { it.isNotBlank() }
                ?: error("Release has no tag/name")

            // Notes for every release the person is missing, not only the newest; if the list can't be read, the newest
            // release's own notes still show.
            val notes = runCatching { combineReleaseNotes(apiClient.listReleases(), installedVersion, tag) }
                .getOrElse { plainNotes(dto.body.orEmpty()).ifBlank { NO_NOTES_FALLBACK } }

            AppUpdate(
                tag = tag,
                notes = notes,
                assetUrl = asset.browserDownloadUrl,
                assetSizeBytes = asset.size
            )
        }
    }
}
