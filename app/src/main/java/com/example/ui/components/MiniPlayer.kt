package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.playback.PlaybackState
import com.example.playback.PlaybackStatus
import com.example.ui.theme.AccentAmber
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    onPlayPauseClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val station = playbackState.currentStation ?: return
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("mini_player")
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onExpandClick() },
        shape = RoundedCornerShape(20.dp),
        border = if (isFocused) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else null,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (isFocused) 10.dp else 6.dp,
        shadowElevation = if (isFocused) 12.dp else 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isPlaying = playbackState.status == PlaybackStatus.PLAYING
            val miniRotationAngle by rememberVinylRotation(isPlaying = isPlaying, rotationDurationMs = 6000)

            // Mini Rotating Vinyl Disc Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF111827))
                    .border(BorderStroke(1.5.dp, Color(0xFF334155).copy(alpha = 0.7f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Rotating Disc Body (Grooves + Light Sheen + Center Artwork + Spindle Hole)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer { rotationZ = miniRotationAngle },
                    contentAlignment = Alignment.Center
                ) {
                    // Mini vinyl groove
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .border(0.75.dp, Color(0xFF1E293B), CircleShape)
                    )

                    // Mini vinyl light sheen
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.18f),
                                        Color.White.copy(alpha = 0.08f),
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.18f),
                                        Color.White.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Center Label / Album art
                    val currentArtModel = playbackState.albumArtUrl?.ifBlank { null }
                        ?: station.favicon.ifBlank { null }

                    if (currentArtModel != null) {
                        AsyncImage(
                            model = currentArtModel,
                            contentDescription = playbackState.trackTitle ?: station.name,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = station.name.take(2).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Mini spindle hole
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .border(0.75.dp, Color(0xFF94A3B8), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text Info & Now Playing Metadata
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (playbackState.status == PlaybackStatus.PLAYING) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = stringResource(R.string.status_live_broadcast),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Metadata or Station Subtitle
                val metadata = playbackState.trackTitle
                val artist = playbackState.trackArtist
                val isError = playbackState.status == PlaybackStatus.ERROR
                val bufferingText = stringResource(R.string.buffering)
                val liveStreamDefault = stringResource(R.string.live_stream_default)
                val defaultErrorText = stringResource(R.string.stream_error_retry)
                val displayText = when {
                    isError -> playbackState.errorMessage ?: defaultErrorText
                    playbackState.status == PlaybackStatus.BUFFERING -> bufferingText
                    !artist.isNullOrBlank() && !metadata.isNullOrBlank() -> "$artist - $metadata"
                    !artist.isNullOrBlank() -> artist
                    !metadata.isNullOrBlank() -> metadata
                    else -> station.displaySubtitle.ifBlank { liveStreamDefault }
                }

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isError -> MaterialTheme.colorScheme.error
                        playbackState.status == PlaybackStatus.BUFFERING -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }

            // Favorite Button
            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("mini_player_fav")
            ) {
                Icon(
                    imageVector = if (playbackState.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.favorite),
                    tint = if (playbackState.isFavorite) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Play / Pause / Buffering Button
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("mini_player_play_pause")
            ) {
                if (playbackState.status == PlaybackStatus.BUFFERING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = if (playbackState.status == PlaybackStatus.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playbackState.status == PlaybackStatus.PLAYING) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
