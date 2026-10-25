package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.util.CategoryTranslations
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.playback.PlaybackState
import com.example.playback.PlaybackStatus
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    playbackState: PlaybackState,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onFullScreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val station = playbackState.currentStation ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isPlaying = playbackState.status == PlaybackStatus.PLAYING
    val rotationAngle by rememberVinylRotation(isPlaying = isPlaying, rotationDurationMs = 6000)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("now_playing_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Status badge & close/fullscreen buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live status chip
                val statusText = when (playbackState.status) {
                    PlaybackStatus.PLAYING -> stringResource(R.string.status_live_broadcast)
                    PlaybackStatus.BUFFERING -> stringResource(R.string.status_connecting)
                    PlaybackStatus.PAUSED -> stringResource(R.string.status_paused)
                    PlaybackStatus.ERROR -> stringResource(R.string.status_error)
                    PlaybackStatus.IDLE -> stringResource(R.string.status_idle)
                }
                val statusColor = when (playbackState.status) {
                    PlaybackStatus.PLAYING -> AccentGreen
                    PlaybackStatus.BUFFERING -> MaterialTheme.colorScheme.primary
                    PlaybackStatus.PAUSED -> AccentAmber
                    PlaybackStatus.ERROR -> AccentCoral
                    PlaybackStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isTv = com.example.util.TvUtils.isRunningOnTv(LocalContext.current)
                    if (isTv && onFullScreen != null) {
                        IconButton(
                            onClick = onFullScreen,
                            modifier = Modifier.testTag("now_playing_fullscreen")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fullscreen,
                                contentDescription = stringResource(R.string.tv_expand_fullscreen),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("now_playing_close")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large Vinyl / Station Artwork with Ambient Halo
            Box(
                modifier = Modifier
                    .size(230.dp),
                contentAlignment = Alignment.Center
            ) {
                // Ambient Radial Glow
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Vinyl Disc with realistic grooves, rotating specular light reflection and center label
                Box(
                    modifier = Modifier
                        .size(206.dp)
                        .graphicsLayer { rotationZ = rotationAngle }
                        .clip(CircleShape)
                        .background(Color(0xFF111827))
                        .border(BorderStroke(2.dp, Color(0xFF334155).copy(alpha = 0.6f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Concentric vinyl sound grooves
                    Box(
                        modifier = Modifier
                            .size(178.dp)
                            .border(1.dp, Color(0xFF1E293B), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(154.dp)
                            .border(1.dp, Color(0xFF334155).copy(alpha = 0.40f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(134.dp)
                            .border(1.dp, Color(0xFF1E293B), CircleShape)
                    )

                    // Rotating Vinyl Light Sheen (Specular highlight reflecting off rotating microgrooves)
                    Box(
                        modifier = Modifier
                            .size(206.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.05f),
                                        Color.White.copy(alpha = 0.15f),
                                        Color.White.copy(alpha = 0.05f),
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.05f),
                                        Color.White.copy(alpha = 0.15f),
                                        Color.White.copy(alpha = 0.05f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Center Record Label ("lemezcímke") with border
                    val currentArtModel = playbackState.albumArtUrl?.ifBlank { null }
                        ?: station.favicon.ifBlank { null }

                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .border(BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)), CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentArtModel != null) {
                            AsyncImage(
                                model = currentArtModel,
                                contentDescription = playbackState.trackTitle ?: station.name,
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_zonq_q_logo),
                                    contentDescription = "zonq",
                                    modifier = Modifier.size(70.dp)
                                )
                            }
                        }
                    }

                    // Center Spindle Hole with metallic silver rim
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .border(1.5.dp, Color(0xFF94A3B8), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Station Name
            Text(
                text = station.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Station Subtitle & Codec / Bitrate
            val context = LocalContext.current
            val translatedCountry = CategoryTranslations.getTranslatedCountry(context, station.country)
            Text(
                text = "$translatedCountry • ${station.codec} ${if (station.bitrate > 0) "${station.bitrate} kbps" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Track & Song Metadata Box (Real-time ICY metadata!)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = stringResource(R.string.music),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.now_playing_title),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )

                        val songTitle = playbackState.trackTitle ?: station.name
                        val artist = playbackState.trackArtist

                        if (!artist.isNullOrBlank()) {
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                            Text(
                                text = songTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                        } else {
                            Text(
                                text = songTitle,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                }
            }

            if (!playbackState.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = playbackState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCoral,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Controls (Previous, Play/Pause, Next, Favorite)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorite Button
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_fav")
                ) {
                    Icon(
                        imageVector = if (playbackState.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.favorite),
                        tint = if (playbackState.isFavorite) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Previous Station
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("now_playing_prev")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.prev_station),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Play / Pause Large Circular Button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("now_playing_play_pause")
                    ) {
                        if (playbackState.status == PlaybackStatus.BUFFERING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                strokeWidth = 3.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = if (playbackState.status == PlaybackStatus.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playbackState.status == PlaybackStatus.PLAYING) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                // Next Station
                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("now_playing_next")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.next_station),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Soundwave Indicator / Badge
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Equalizer",
                        tint = if (playbackState.status == PlaybackStatus.PLAYING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Volume Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.VolumeDown,
                    contentDescription = stringResource(R.string.vol_down),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )

                Slider(
                    value = playbackState.volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .testTag("volume_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Icon(
                    imageVector = Icons.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.vol_up),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
