package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.example.util.CategoryTranslations
import kotlin.math.roundToInt

@Composable
fun NowPlayingTvFullScreen(
    playbackState: PlaybackState,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val station = playbackState.currentStation ?: return
    val context = LocalContext.current
    val playPauseFocusRequester = remember { FocusRequester() }

    // Intercept TV remote Back button to dismiss full-screen player
    BackHandler {
        onDismiss()
    }

    // Auto-focus play/pause button when TV full-screen player opens
    LaunchedEffect(Unit) {
        try {
            playPauseFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val isPlaying = playbackState.status == PlaybackStatus.PLAYING
    val tvRotationAngle by rememberVinylRotation(isPlaying = isPlaying, rotationDurationMs = 6000)

    // Pulsing aura animation for live status and artwork ambient glow
    val infiniteTransition = rememberInfiniteTransition(label = "tv_ambient_effects")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Ensure TV screen stays on and ambient screensaver is suppressed during full-screen playback
    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose {}
    }

    // Full screen background with deep cinematic slate gradients
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090D16),
                        Color(0xFF0F172A),
                        Color(0xFF131B32)
                    )
                )
            )
            .testTag("tv_player_screen")
    ) {
        // Atmospheric radial glow in center-left behind artwork
        Box(
            modifier = Modifier
                .size(540.dp)
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha * 0.4f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            // Top Bar: Back button, TV branding, and Live status pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TV Back Button with clear D-pad focus highlight
                var backFocused by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = if (backFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = if (backFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .onFocusChanged { backFocused = it.isFocused }
                        .focusable()
                        .clickable { onDismiss() }
                        .testTag("tv_player_close")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.tv_back_to_stations),
                            tint = if (backFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.tv_back_to_stations),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (backFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Brand Logo / TV Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_zonq_full_logo),
                        contentDescription = "zonq TV",
                        modifier = Modifier.height(30.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // Status & Codec Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Codec / Bitrate Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "${station.codec.uppercase()} • ${if (station.bitrate > 0) "${station.bitrate} kbps" else "HQ"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Live Status Chip
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
                        shape = RoundedCornerShape(16.dp),
                        color = statusColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main 2-Column Content Row (Optimized for 16:9 Android TV screens)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT COLUMN: Large Vinyl Artwork & Audio Equalizer Animation (~40% width)
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val isPlaying = playbackState.status == PlaybackStatus.PLAYING

                    Box(
                        modifier = Modifier.size(290.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 1. Animated Concentric Soundwave Ripples expanding from behind/under the vinyl disc
                        TvVinylSoundwaveRipples(
                            isPlaying = isPlaying,
                            pulseColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(290.dp)
                        )

                        // Ambient Radial Glow
                        Box(
                            modifier = Modifier
                                .size(250.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha * 0.5f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Vinyl Disc with grooves and rotating specular light reflection
                        Box(
                            modifier = Modifier
                                .size(235.dp)
                                .graphicsLayer { rotationZ = tvRotationAngle }
                                .clip(CircleShape)
                                .background(Color(0xFF111827))
                                .border(BorderStroke(2.dp, Color(0xFF334155).copy(alpha = 0.6f)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            // Subtle vinyl grooves concentric rings
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .border(1.dp, Color(0xFF1E293B), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(170.dp)
                                    .border(1.dp, Color(0xFF334155).copy(alpha = 0.45f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(145.dp)
                                    .border(1.dp, Color(0xFF1E293B), CircleShape)
                            )

                            // Rotating Vinyl Light Sheen (Specular highlight reflecting off rotating microgrooves)
                            Box(
                                modifier = Modifier
                                    .size(235.dp)
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

                            // Center Artwork / Logo
                            val currentArtModel = playbackState.albumArtUrl?.ifBlank { null }
                                ?: station.favicon.ifBlank { null }

                            if (currentArtModel != null) {
                                AsyncImage(
                                    model = currentArtModel,
                                    contentDescription = playbackState.trackTitle ?: station.name,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(2.dp, Color(0xFFCBD5E1), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_zonq_q_logo),
                                        contentDescription = "zonq",
                                        modifier = Modifier.size(76.dp)
                                    )
                                }
                            }

                            // Center spindle hole
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F172A))
                                    .border(1.5.dp, Color(0xFF64748B), CircleShape)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 2. Dynamic Moving Equalizer Wave Visualizer under the vinyl disc
                    TvVinylWaveEqualizer(
                        isPlaying = isPlaying,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(52.dp)
                    )
                }

                // RIGHT COLUMN: Station info, Real-time ICY Song card, Controls, Volume (~58% width)
                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Station Name
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Country & Tags
                    val translatedCountry = CategoryTranslations.getTranslatedCountry(context, station.country)
                    val tagsText = if (station.tags.isNotBlank()) {
                        " • " + station.tags.split(",", ";").take(3).joinToString(" • ") { it.trim().replaceFirstChar { c -> c.uppercase() } }
                    } else ""
                    Text(
                        text = "$translatedCountry$tagsText",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Track & Live Metadata Glassmorphic Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = stringResource(R.string.music),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.now_playing_title),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.2.sp
                                )

                                val songTitle = playbackState.trackTitle ?: station.name
                                val artist = playbackState.trackArtist

                                if (!artist.isNullOrBlank()) {
                                    Text(
                                        text = artist,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.basicMarquee()
                                    )
                                    Text(
                                        text = songTitle,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.basicMarquee()
                                    )
                                } else {
                                    Text(
                                        text = songTitle,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee()
                                    )
                                }
                            }
                        }
                    }

                    if (!playbackState.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = playbackState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentCoral,
                            textAlign = TextAlign.Start
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // TV Remote-Friendly Playback Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.Start),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favorite Button with TV D-pad focus highlight
                        TvPlayerButton(
                            icon = if (playbackState.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.favorite),
                            iconTint = if (playbackState.isFavorite) AccentAmber else Color.White,
                            size = 56.dp,
                            iconSize = 30.dp,
                            testTag = "tv_player_fav",
                            onClick = onFavoriteToggle
                        )

                        // Previous Station Button
                        TvPlayerButton(
                            icon = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.prev_station),
                            iconTint = Color.White,
                            size = 56.dp,
                            iconSize = 34.dp,
                            testTag = "tv_player_prev",
                            onClick = onPrevious
                        )

                        // Large Center Play / Pause Button (Auto-focused on TV launch)
                        var playPauseFocused by remember { mutableStateOf(false) }
                        Surface(
                            shape = CircleShape,
                            color = if (playPauseFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                            border = if (playPauseFocused) BorderStroke(4.dp, Color.White) else null,
                            shadowElevation = if (playPauseFocused) 12.dp else 4.dp,
                            modifier = Modifier
                                .size(76.dp)
                                .scale(if (playPauseFocused) 1.1f else 1.0f)
                                .focusRequester(playPauseFocusRequester)
                                .onFocusChanged { playPauseFocused = it.isFocused }
                                .focusable()
                                .clickable { onPlayPause() }
                                .testTag("tv_player_play_pause")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (playbackState.status == PlaybackStatus.BUFFERING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(38.dp),
                                        strokeWidth = 4.dp,
                                        color = if (playPauseFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (playbackState.status == PlaybackStatus.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (playbackState.status == PlaybackStatus.PLAYING) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                                        tint = if (playPauseFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                            }
                        }

                        // Next Station Button
                        TvPlayerButton(
                            icon = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.next_station),
                            iconTint = Color.White,
                            size = 56.dp,
                            iconSize = 34.dp,
                            testTag = "tv_player_next",
                            onClick = onNext
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // TV Remote-Friendly Volume Control Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Volume Down Button (D-pad focusable)
                        TvPlayerButton(
                            icon = Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = stringResource(R.string.vol_down),
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 46.dp,
                            iconSize = 22.dp,
                            testTag = "tv_player_vol_down",
                            onClick = {
                                val newVol = (playbackState.volume - 0.1f).coerceIn(0f, 1f)
                                onVolumeChange(newVol)
                            }
                        )

                        // Volume Slider
                        var sliderFocused by remember { mutableStateOf(false) }
                        Slider(
                            value = playbackState.volume,
                            onValueChange = onVolumeChange,
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { sliderFocused = it.isFocused }
                                .focusable()
                                .testTag("tv_player_volume_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = if (sliderFocused) Color.White else MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        // Volume Up Button (D-pad focusable)
                        TvPlayerButton(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.vol_up),
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 46.dp,
                            iconSize = 22.dp,
                            testTag = "tv_player_vol_up",
                            onClick = {
                                val newVol = (playbackState.volume + 0.1f).coerceIn(0f, 1f)
                                onVolumeChange(newVol)
                            }
                        )

                        // Volume % Text
                        Text(
                            text = "${(playbackState.volume * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(42.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable D-pad remote accessible button with high-contrast glowing TV focus border.
 */
@Composable
private fun TvPlayerButton(
    icon: ImageVector,
    contentDescription: String,
    iconTint: Color,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = CircleShape,
        color = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        shadowElevation = if (isFocused) 8.dp else 2.dp,
        modifier = modifier
            .size(size)
            .scale(if (isFocused) 1.15f else 1.0f)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isFocused) MaterialTheme.colorScheme.primary else iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
