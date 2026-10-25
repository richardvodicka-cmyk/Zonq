package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.RadioViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.OnlineRadioTheme

import android.content.Context
import android.content.res.Resources
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import com.example.util.TvUtils
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {

    private fun updateAppLocale(context: Context, locale: Locale?) {
        val effective = com.example.util.LocaleUtils.getEffectiveLocale(locale)
        Locale.setDefault(effective)

        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(effective)
        config.setLayoutDirection(effective)

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        @Suppress("DEPRECATION")
        context.applicationContext.resources.updateConfiguration(config, context.applicationContext.resources.displayMetrics)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
                val targetList = if (locale != null) {
                    android.os.LocaleList(locale)
                } else {
                    android.os.LocaleList.getEmptyLocaleList()
                }
                if (localeManager != null && localeManager.applicationLocales != targetList) {
                    localeManager.applicationLocales = targetList
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Prevent screensaver / daydream on Android TV & Google TV
        if (TvUtils.isRunningOnTv(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val app = application as RadioApplication

        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RadioViewModel(
                    repository = app.repository,
                    preferences = app.preferences,
                    playbackManager = app.playbackManager,
                    appUpdateManager = app.appUpdateManager
                ) as T
            }
        }

        setContent {
            val radioViewModel: RadioViewModel = viewModel(factory = viewModelFactory)
            val userPrefs by radioViewModel.userPreferences.collectAsState()

            // Keep screen on on Android/Google TV so screensaver (ambient mode) does not start
            val isTv = remember { TvUtils.isRunningOnTv(this@MainActivity) }
            val currentView = androidx.compose.ui.platform.LocalView.current
            LaunchedEffect(isTv, userPrefs.tvKeepScreenOn) {
                if (isTv) {
                    if (userPrefs.tvKeepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        currentView.keepScreenOn = true
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        currentView.keepScreenOn = false
                    }
                }
            }

            // Request Notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* granted / denied */ }

                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // Apply selected language configuration dynamically
            val targetLocale = when (userPrefs.appLanguage) {
                com.example.data.preferences.AppLanguage.HUNGARIAN -> Locale("hu")
                com.example.data.preferences.AppLanguage.SLOVAK -> Locale("sk")
                com.example.data.preferences.AppLanguage.ENGLISH -> Locale("en")
                com.example.data.preferences.AppLanguage.SYSTEM -> null
            }

            // Synchronize Activity and Application resources immediately upon language change
            LaunchedEffect(targetLocale) {
                updateAppLocale(this@MainActivity, targetLocale)
            }

            val currentConfig = LocalConfiguration.current
            val currentContext = LocalContext.current

            val localizedContext = remember(targetLocale, currentContext) {
                if (targetLocale != null) {
                    val config = android.content.res.Configuration(currentContext.resources.configuration)
                    config.setLocale(targetLocale)
                    config.setLayoutDirection(targetLocale)
                    currentContext.createConfigurationContext(config)
                } else {
                    currentContext
                }
            }

            val localizedConfiguration = remember(targetLocale, currentConfig) {
                if (targetLocale != null) {
                    android.content.res.Configuration(currentConfig).apply {
                        setLocale(targetLocale)
                        setLayoutDirection(targetLocale)
                    }
                } else {
                    currentConfig
                }
            }

            var isAppReady by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                delay(800)
                isAppReady = true
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration,
                LocalContext provides currentContext
            ) {
                OnlineRadioTheme(themeMode = userPrefs.themeMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        if (!isAppReady) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.White
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_zonq_q_logo),
                                        contentDescription = "q",
                                        modifier = Modifier.fillMaxSize(0.92f),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        } else {
                            HomeScreen(viewModel = radioViewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (TvUtils.isRunningOnTv(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val app = application as? RadioApplication
        val playbackManager = app?.playbackManager
        if (playbackManager != null) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                android.view.KeyEvent.KEYCODE_HEADSETHOOK -> {
                    playbackManager.togglePlayPause()
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    playbackManager.resume()
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                    playbackManager.pause()
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                    playbackManager.playNext()
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    playbackManager.playPrevious()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

