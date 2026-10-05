package dev.lumbina.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** A bar under the clock while some app plays (or has paused) media; nothing otherwise. */
@Composable
fun NowPlayingRoute(modifier: Modifier = Modifier, viewModel: NowPlayingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Color.Black.copy(alpha = BAR_ALPHA), RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(Modifier.widthIn(max = 640.dp)) {
            Text(
                text = current.playing.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(current.playing.artist, current.app).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = viewModel::previous) {
            Icon(
                painterResource(R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.media_previous),
            )
        }
        IconButton(onClick = viewModel::playPause) {
            val playing = current.playing.playing
            Icon(
                painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(if (playing) R.string.media_pause else R.string.media_play),
            )
        }
        IconButton(onClick = viewModel::next) {
            Icon(painterResource(R.drawable.ic_skip_next), contentDescription = stringResource(R.string.media_next))
        }
    }
}

private const val BAR_ALPHA = 0.45f
