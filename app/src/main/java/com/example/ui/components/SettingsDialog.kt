package com.example.ui.components

import android.content.res.Resources
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AudioQuality
import com.example.data.preferences.BufferDuration
import com.example.data.preferences.DefaultScreen
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserPreferences
import com.example.playback.EqualizerBandInfo
import com.example.playback.EqualizerPreset
import com.example.playback.EqualizerState
import com.example.playback.NormalizationProfile
import com.example.util.TvUtils
import java.util.Locale

@Composable
fun SettingsDialog(
    userPreferences: UserPreferences,
    equalizerState: EqualizerState,
    onDismiss: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onAudioQualityChange: (AudioQuality) -> Unit,
    onBufferDurationChange: (BufferDuration) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onDefaultScreenChange: (DefaultScreen) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onEqualizerEnabledChange: (Boolean) -> Unit,
    onEqualizerPresetChange: (EqualizerPreset) -> Unit,
    onEqualizerBandLevelChange: (Short, Short) -> Unit,
    onAudioNormalizationEnabledChange: (Boolean) -> Unit = {},
    onNormalizationProfileChange: (NormalizationProfile) -> Unit = {},
    onTvKeepScreenOnChanged: (Boolean) -> Unit = {},
    onOpenZonqAuth: () -> Unit = {},
    onSyncZonqFavorites: () -> Unit = {},
    onLogoutZonq: () -> Unit = {},
    onDeleteAccountZonq: (password: String, callback: (Boolean, String?) -> Unit) -> Unit = { _, _ -> },
    isSyncingZonq: Boolean = false,
    isCheckingForUpdate: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
    availableUpdateInfo: com.example.data.remote.AppUpdateInfo? = null,
    onOpenAddCustomStation: () -> Unit,
    onOpenManageFavorites: () -> Unit
) {
    var currentLanguage by remember(userPreferences.appLanguage) {
        mutableStateOf(userPreferences.appLanguage)
    }

    val targetLocale = when (currentLanguage) {
        AppLanguage.HUNGARIAN -> Locale("hu")
        AppLanguage.SLOVAK -> Locale("sk")
        AppLanguage.ENGLISH -> Locale("en")
        AppLanguage.SYSTEM -> null
    }

    val currentContext = LocalContext.current
    val currentConfig = LocalConfiguration.current
    val isTv = remember(currentContext) { TvUtils.isRunningOnTv(currentContext) }

    val dialogLocalizedConfig = remember(targetLocale, currentConfig) {
        val effective = com.example.util.LocaleUtils.getEffectiveLocale(targetLocale)
        android.content.res.Configuration(currentConfig).apply {
            setLocale(effective)
            setLayoutDirection(effective)
        }
    }

    // Modal state for zonq logout and delete account
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteAccountError by remember { mutableStateOf<String?>(null) }
    var showDeletePasswordVisible by remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalConfiguration provides dialogLocalizedConfig,
        LocalContext provides currentContext
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isTv) 32.dp else 16.dp, vertical = 20.dp)
                .widthIn(max = if (isTv) 640.dp else 560.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                        )
                        if (isTv) {
                            Text(
                                text = "zonq TV • D-pad vezérlés",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ==========================================
                    // SECTION 1: FIÓK ÉS SZINKRONIZÁCIÓ (ACCOUNT & SYNC)
                    // ==========================================
                    SettingsSectionHeader(
                        icon = Icons.Filled.AccountCircle,
                        title = stringResource(R.string.settings_section_account)
                    )

                    TvFocusableCard(
                        onClick = null,
                        isHighlighted = userPreferences.isLoggedIn,
                        modifier = Modifier.testTag("card_zonq_sync")
                    ) { isCardFocused ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_zonq_symbol),
                                    contentDescription = "zonq",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.zonq_sync_title),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val statusText = if (userPreferences.isLoggedIn) {
                                        when (userPreferences.zonqSyncStatus) {
                                            "syncing" -> stringResource(R.string.zonq_status_syncing)
                                            "offline" -> stringResource(R.string.zonq_status_offline)
                                            "synced" -> {
                                                val time = userPreferences.zonqSyncTime
                                                if (!time.isNullOrBlank()) {
                                                    stringResource(R.string.zonq_status_synced, time)
                                                } else {
                                                    stringResource(R.string.zonq_logged_in_as, userPreferences.zonqEmail ?: "")
                                                }
                                            }
                                            else -> stringResource(R.string.zonq_logged_in_as, userPreferences.zonqEmail ?: "")
                                        }
                                    } else {
                                        stringResource(R.string.zonq_not_logged_in_favs_hint)
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = if (userPreferences.isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!userPreferences.isLoggedIn) {
                                TvFocusableButton(
                                    onClick = onOpenZonqAuth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_zonq_login_open")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.zonq_login_button),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TvFocusableButton(
                                        onClick = onSyncZonqFavorites,
                                        enabled = !isSyncingZonq,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_zonq_sync_now")
                                    ) {
                                        if (isSyncingZonq) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = stringResource(R.string.zonq_syncing), fontSize = 11.sp)
                                        } else {
                                            Icon(imageVector = Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = stringResource(R.string.zonq_sync_now), fontSize = 11.sp)
                                        }
                                    }

                                    TvFocusableOutlinedButton(
                                        onClick = { showLogoutConfirm = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_zonq_logout")
                                    ) {
                                        Icon(imageVector = Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = stringResource(R.string.zonq_logout), fontSize = 11.sp)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            deletePassword = ""
                                            deleteAccountError = null
                                            showDeleteAccountConfirm = true
                                        },
                                        modifier = Modifier.testTag("btn_zonq_delete_account_open")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.zonq_delete_account),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Logout confirmation dialog
                    if (showLogoutConfirm) {
                        AlertDialog(
                            onDismissRequest = { showLogoutConfirm = false },
                            title = { Text(text = stringResource(R.string.zonq_logout)) },
                            text = { Text(text = stringResource(R.string.zonq_logout_confirm)) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showLogoutConfirm = false
                                        onLogoutZonq()
                                    },
                                    modifier = Modifier.testTag("btn_confirm_logout")
                                ) {
                                    Text(text = stringResource(R.string.zonq_logout))
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showLogoutConfirm = false }
                                ) {
                                    Text(text = stringResource(R.string.close))
                                }
                            }
                        )
                    }

                    // Delete Account confirmation dialog
                    if (showDeleteAccountConfirm) {
                        AlertDialog(
                            onDismissRequest = {
                                if (!isDeletingAccount) showDeleteAccountConfirm = false
                            },
                            title = {
                                Text(
                                    text = stringResource(R.string.zonq_delete_account),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(text = stringResource(R.string.zonq_delete_account_confirm))
                                    if (deleteAccountError != null) {
                                        val errText = when (deleteAccountError) {
                                            "invalid_credentials" -> stringResource(R.string.zonq_err_invalid_creds)
                                            "admin_account" -> stringResource(R.string.zonq_err_admin_account)
                                            else -> stringResource(R.string.zonq_err_network)
                                        }
                                        Text(
                                            text = errText,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    OutlinedTextField(
                                        value = deletePassword,
                                        onValueChange = { deletePassword = it },
                                        placeholder = { Text(stringResource(R.string.zonq_delete_account_pass_hint)) },
                                        visualTransformation = if (showDeletePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { showDeletePasswordVisible = !showDeletePasswordVisible }) {
                                                Icon(
                                                    imageVector = if (showDeletePasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_delete_account_pass")
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val trimmedPass = deletePassword.trim()
                                        if (trimmedPass.isNotEmpty()) {
                                            isDeletingAccount = true
                                            deleteAccountError = null
                                            onDeleteAccountZonq(trimmedPass) { success, err ->
                                                isDeletingAccount = false
                                                if (success) {
                                                    showDeleteAccountConfirm = false
                                                } else {
                                                    deleteAccountError = err ?: "network"
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isDeletingAccount && deletePassword.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier.testTag("btn_confirm_delete_account")
                                ) {
                                    if (isDeletingAccount) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onError
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(text = stringResource(R.string.zonq_delete_account))
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showDeleteAccountConfirm = false },
                                    enabled = !isDeletingAccount
                                ) {
                                    Text(text = stringResource(R.string.close))
                                }
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    // ==========================================
                    // SECTION 2: HANGZÁS ÉS LEJÁTSZÁS (AUDIO & PLAYBACK)
                    // ==========================================
                    SettingsSectionHeader(
                        icon = Icons.Filled.GraphicEq,
                        title = stringResource(R.string.settings_section_audio)
                    )

                    // Audio Quality
                    val selectedQualityTitle = when (userPreferences.audioQuality) {
                        AudioQuality.AUTO -> stringResource(R.string.pref_audio_quality_auto)
                        AudioQuality.HIGH -> stringResource(R.string.pref_audio_quality_high)
                        AudioQuality.STANDARD -> stringResource(R.string.pref_audio_quality_standard)
                        AudioQuality.SAVER -> stringResource(R.string.pref_audio_quality_saver)
                    }

                    SettingsVerticalDropdownCard(
                        icon = Icons.Filled.HighQuality,
                        label = stringResource(R.string.pref_audio_quality),
                        selectedText = selectedQualityTitle,
                        testTag = "dropdown_audio_quality"
                    ) { closeMenu ->
                        AudioQuality.entries.forEach { quality ->
                            val title = when (quality) {
                                AudioQuality.AUTO -> stringResource(R.string.pref_audio_quality_auto)
                                AudioQuality.HIGH -> stringResource(R.string.pref_audio_quality_high)
                                AudioQuality.STANDARD -> stringResource(R.string.pref_audio_quality_standard)
                                AudioQuality.SAVER -> stringResource(R.string.pref_audio_quality_saver)
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                                    )
                                },
                                onClick = {
                                    onAudioQualityChange(quality)
                                    closeMenu()
                                }
                            )
                        }
                    }

                    // Buffer Duration
                    val selectedBufferTitle = when (userPreferences.bufferDuration) {
                        BufferDuration.SHORT -> stringResource(R.string.pref_buffer_short)
                        BufferDuration.NORMAL -> stringResource(R.string.pref_buffer_normal)
                        BufferDuration.LARGE -> stringResource(R.string.pref_buffer_large)
                    }

                    SettingsVerticalDropdownCard(
                        icon = Icons.Filled.Speed,
                        label = stringResource(R.string.pref_buffer_time),
                        selectedText = selectedBufferTitle,
                        testTag = "dropdown_buffer_duration"
                    ) { closeMenu ->
                        BufferDuration.entries.forEach { duration ->
                            val title = when (duration) {
                                BufferDuration.SHORT -> stringResource(R.string.pref_buffer_short)
                                BufferDuration.NORMAL -> stringResource(R.string.pref_buffer_normal)
                                BufferDuration.LARGE -> stringResource(R.string.pref_buffer_large)
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                                    )
                                },
                                onClick = {
                                    onBufferDurationChange(duration)
                                    closeMenu()
                                }
                            )
                        }
                    }

                    // Audio Normalization Card with Profile selection
                    TvFocusableCard(
                        onClick = null,
                        modifier = Modifier.testTag("card_normalization_settings")
                    ) { isCardFocused ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onAudioNormalizationEnabledChange(!userPreferences.audioNormalizationEnabled) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (userPreferences.audioNormalizationEnabled) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.VolumeUp,
                                                contentDescription = null,
                                                tint = if (userPreferences.audioNormalizationEnabled) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = stringResource(R.string.pref_audio_normalization),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.pref_audio_normalization_desc),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = userPreferences.audioNormalizationEnabled,
                                    onCheckedChange = onAudioNormalizationEnabledChange,
                                    modifier = Modifier.testTag("switch_audio_normalization")
                                )
                            }

                            if (userPreferences.audioNormalizationEnabled) {
                                val currentProfile = userPreferences.normalizationProfile

                                SettingsVerticalDropdownCard(
                                    icon = Icons.Filled.Tune,
                                    label = stringResource(R.string.pref_normalization_profile),
                                    selectedText = stringResource(currentProfile.displayNameRes),
                                    testTag = "dropdown_normalization_profile"
                                ) { closeMenu ->
                                    NormalizationProfile.entries.forEach { profile ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(profile.displayNameRes),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                                                )
                                            },
                                            onClick = {
                                                onNormalizationProfileChange(profile)
                                                closeMenu()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Equalizer Card (Presets & Bands)
                    TvFocusableCard(
                        onClick = null,
                        modifier = Modifier.testTag("card_equalizer_settings")
                    ) { isCardFocused ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onEqualizerEnabledChange(!equalizerState.isEnabled) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (equalizerState.isEnabled) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.GraphicEq,
                                                contentDescription = null,
                                                tint = if (equalizerState.isEnabled) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = stringResource(R.string.pref_equalizer),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.pref_equalizer_desc),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = equalizerState.isEnabled,
                                    onCheckedChange = onEqualizerEnabledChange,
                                    modifier = Modifier.testTag("switch_equalizer_enabled")
                                )
                            }

                            if (equalizerState.isEnabled) {
                                val currentPreset = EqualizerPreset.entries.find { it.id == equalizerState.currentPresetId }
                                    ?: EqualizerPreset.FLAT

                                SettingsVerticalDropdownCard(
                                    icon = Icons.Filled.Equalizer,
                                    label = stringResource(R.string.pref_equalizer_preset),
                                    selectedText = stringResource(currentPreset.displayNameRes),
                                    testTag = "dropdown_eq_presets"
                                ) { closeMenu ->
                                    EqualizerPreset.entries.forEach { preset ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(preset.displayNameRes),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                                                )
                                            },
                                            onClick = {
                                                onEqualizerPresetChange(preset)
                                                closeMenu()
                                            }
                                        )
                                    }
                                }

                                if (equalizerState.bands.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.pref_equalizer_custom_bands),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    equalizerState.bands.forEach { band ->
                                        EqualizerBandSlider(
                                            band = band,
                                            onLevelChange = { newMb ->
                                                onEqualizerBandLevelChange(band.bandIndex, newMb)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Auto Play Switch Card
                    SettingsSwitchCard(
                        icon = Icons.Filled.Speed,
                        title = stringResource(R.string.pref_auto_play),
                        description = stringResource(R.string.pref_auto_play_desc),
                        checked = userPreferences.autoPlayOnStart,
                        onCheckedChange = onAutoPlayChange,
                        testTag = "card_auto_play"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    // ==========================================
                    // SECTION 3: MEGJELENÉS ÉS KEZELŐFELÜLET (DISPLAY & INTERFACE)
                    // ==========================================
                    SettingsSectionHeader(
                        icon = Icons.Filled.SettingsBrightness,
                        title = stringResource(R.string.settings_section_display)
                    )

                    // Language Selector
                    SettingsVerticalDropdownCard(
                        icon = Icons.Filled.Language,
                        label = stringResource(R.string.pref_language),
                        selectedText = stringResource(currentLanguage.displayNameRes),
                        testTag = "dropdown_language"
                    ) { closeMenu ->
                        AppLanguage.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(lang.displayNameRes),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                                    )
                                },
                                onClick = {
                                    currentLanguage = lang
                                    onLanguageChange(lang)
                                    closeMenu()
                                }
                            )
                        }
                    }

                    // Theme Mode Selector
                    val selectedThemeTitle = when (userPreferences.themeMode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.pref_theme_system)
                        ThemeMode.DARK -> stringResource(R.string.pref_theme_dark)
                        ThemeMode.LIGHT -> stringResource(R.string.pref_theme_light)
                    }

                    SettingsVerticalDropdownCard(
                        icon = when (userPreferences.themeMode) {
                            ThemeMode.DARK -> Icons.Filled.DarkMode
                            ThemeMode.LIGHT -> Icons.Filled.LightMode
                            ThemeMode.SYSTEM -> Icons.Filled.SettingsBrightness
                        },
                        label = stringResource(R.string.pref_theme),
                        selectedText = selectedThemeTitle,
                        testTag = "dropdown_theme_mode"
                    ) { closeMenu ->
                        ThemeMode.entries.forEach { mode ->
                            val title = when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.pref_theme_system)
                                ThemeMode.DARK -> stringResource(R.string.pref_theme_dark)
                                ThemeMode.LIGHT -> stringResource(R.string.pref_theme_light)
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                                    )
                                },
                                onClick = {
                                    onThemeModeChange(mode)
                                    closeMenu()
                                }
                            )
                        }
                    }

                    // Default Startup Screen
                    val selectedScreenTitle = when (userPreferences.defaultScreen) {
                        DefaultScreen.ALL -> stringResource(R.string.pref_default_screen_all)
                        DefaultScreen.FAVORITES -> stringResource(R.string.pref_default_screen_favs)
                    }

                    SettingsVerticalDropdownCard(
                        icon = Icons.Filled.Home,
                        label = stringResource(R.string.pref_default_screen),
                        selectedText = selectedScreenTitle,
                        testTag = "dropdown_default_screen"
                    ) { closeMenu ->
                        DefaultScreen.entries.forEach { screen ->
                            val title = when (screen) {
                                DefaultScreen.ALL -> stringResource(R.string.pref_default_screen_all)
                                DefaultScreen.FAVORITES -> stringResource(R.string.pref_default_screen_favs)
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                                    )
                                },
                                onClick = {
                                    onDefaultScreenChange(screen)
                                    closeMenu()
                                }
                            )
                        }
                    }

                    // TV Keep Screen On Switch
                    SettingsSwitchCard(
                        icon = Icons.Filled.Tv,
                        title = stringResource(R.string.pref_tv_keep_screen_on),
                        description = stringResource(R.string.pref_tv_keep_screen_on_desc),
                        checked = userPreferences.tvKeepScreenOn,
                        onCheckedChange = onTvKeepScreenOnChanged,
                        testTag = "card_tv_keep_screen_on"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    // ==========================================
                    // SECTION 4: ÁLLOMÁSOK ÉS KARBANTARTÁS (STATIONS & MAINTENANCE)
                    // ==========================================
                    SettingsSectionHeader(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.settings_section_tools)
                    )

                    // Manage Favorites Action Card
                    SettingsActionCard(
                        icon = Icons.Filled.Favorite,
                        title = stringResource(R.string.manage_favorites),
                        subtitle = stringResource(R.string.manage_favorites_subtitle),
                        onClick = {
                            onDismiss()
                            onOpenManageFavorites()
                        },
                        testTag = "btn_open_manage_favs"
                    )

                    // Add Custom Station Action Card
                    SettingsActionCard(
                        icon = Icons.Filled.AddLink,
                        title = stringResource(R.string.add_custom_station),
                        subtitle = stringResource(R.string.dialog_add_station_title),
                        onClick = {
                            onDismiss()
                            onOpenAddCustomStation()
                        },
                        testTag = "btn_open_add_custom"
                    )

                    // App Update Card
                    TvFocusableCard(
                        onClick = null,
                        isHighlighted = availableUpdateInfo?.isUpdateAvailable == true,
                        modifier = Modifier.testTag("card_app_update")
                    ) { isCardFocused ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (availableUpdateInfo?.isUpdateAvailable == true) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.SystemUpdate,
                                            contentDescription = null,
                                            tint = if (availableUpdateInfo?.isUpdateAvailable == true) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.update_section_title),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (availableUpdateInfo?.isUpdateAvailable == true) {
                                            stringResource(R.string.update_available_title) + " (${availableUpdateInfo.remoteLastModifiedFormatted})"
                                        } else {
                                            stringResource(R.string.update_section_desc)
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = if (availableUpdateInfo?.isUpdateAvailable == true) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            TvFocusableButton(
                                onClick = onCheckForUpdate,
                                enabled = !isCheckingForUpdate,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_check_for_updates")
                            ) {
                                if (isCheckingForUpdate) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.update_checking),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.update_check_button),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TvFocusableButton(
                    onClick = {
                        onLanguageChange(currentLanguage)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("btn_close_settings")
                ) {
                    Text(
                        text = stringResource(R.string.done),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Clean Section Header with icon pill and bold category title.
 */
@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.4.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Universal TV & Touch-friendly Card wrapper.
 * Provides high-visibility border and highlight colors when navigated with D-pad or mouse hover.
 */
@Composable
private fun TvFocusableCard(
    onClick: (() -> Unit)? = null,
    isHighlighted: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    modifier: Modifier = Modifier,
    content: @Composable (isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.5.dp else 1.dp,
        label = "borderWidth"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primary
            isHighlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
        label = "borderColor"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            isHighlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        label = "containerColor"
    )

    val clickableMod = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(borderWidth, borderColor, shape)
            .then(clickableMod),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        content(isFocused)
    }
}

/**
 * High-visibility Button with prominent D-pad focus ring.
 */
@Composable
private fun TvFocusableButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderWidth = if (isFocused) 2.5.dp else 0.dp
    val borderColor = if (isFocused) MaterialTheme.colorScheme.tertiary else Color.Transparent

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
    ) {
        content()
    }
}

/**
 * High-visibility OutlinedButton with prominent D-pad focus ring.
 */
@Composable
private fun TvFocusableOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderWidth = if (isFocused) 2.5.dp else 1.dp
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
    ) {
        content()
    }
}

/**
 * Clean, stacked setting row with D-pad focus highlight:
 * Label and icon are at the TOP.
 * The dropdown selector box is positioned BELOW the label across full width.
 * The entire card is clickable and focusable so pressing D-pad OK opens it immediately.
 */
@Composable
private fun SettingsVerticalDropdownCard(
    icon: ImageVector,
    label: String,
    selectedText: String,
    testTag: String,
    menuItems: @Composable (closeMenu: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    TvFocusableCard(
        onClick = { expanded = true },
        modifier = Modifier.testTag(testTag)
    ) { isFocused ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Icon + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Selector Button positioned BELOW the title
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 1.dp,
                            color = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            if (isFocused) MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    menuItems { expanded = false }
                }
            }
        }
    }
}

/**
 * Action card for opening management screens (Manage Favorites, Add Custom Station).
 * Fully focusable and clickable with D-pad.
 */
@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    TvFocusableCard(
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Reusable switch card.
 * The entire card is focusable and clickable, so on TV pressing DPAD_CENTER toggles it.
 */
@Composable
private fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    TvFocusableCard(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.testTag(testTag)
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun EqualizerBandSlider(
    band: EqualizerBandInfo,
    onLevelChange: (Short) -> Unit
) {
    val freqLabel = if (band.centerFreqHz >= 1000) {
        "${band.centerFreqHz / 1000} kHz"
    } else {
        "${band.centerFreqHz} Hz"
    }

    val gainDb = band.levelMb / 100
    val gainLabel = if (gainDb > 0) "+${gainDb} dB" else "${gainDb} dB"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = freqLabel,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = gainLabel,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }

        val rawMin = band.minLevelMb.toFloat()
        val rawMax = band.maxLevelMb.toFloat()
        val (safeMin, safeMax) = if (rawMin < rawMax && (rawMax - rawMin) >= 100f) {
            rawMin to rawMax
        } else {
            -1200f to 1200f
        }
        val safeRange = safeMin..safeMax
        val currentFloat = band.levelMb.toFloat().coerceIn(safeRange.start, safeRange.endInclusive)

        Slider(
            value = currentFloat,
            onValueChange = { newValue ->
                onLevelChange(newValue.toInt().toShort())
            },
            valueRange = safeRange,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}
