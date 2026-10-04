package com.mangotv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.Episode
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * Shown while a title is opening (the web app's loading screen): its backdrop behind its logo (or name), "S1 E2 • title" for an episode,
 * and a loading symbol underneath, until the first picture plays. [busy] false (the resume question is up) leaves the symbol out.
 */
@Composable
fun PlayerLoadingScreen(content: Content, episode: Episode?, busy: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(MangoBackground)) {
        AsyncImage(
            model = content.backdropUrl ?: content.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (content.logoUrl != null) {
                AsyncImage(
                    model = content.logoUrl,
                    contentDescription = content.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 360.dp, height = 110.dp)
                )
            } else {
                Text(
                    text = content.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
            if (episode != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "S${episode.seasonNumber} E${episode.episodeNumber} • ${episode.title}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
            if (busy) CircularProgressIndicator(modifier = Modifier.size(44.dp), color = Color.White)
        }
    }
}
