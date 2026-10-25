package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.remember
import com.example.util.TvUtils
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CuratedStations
import com.example.data.model.Station
import com.example.data.model.distinctStations
import com.example.data.model.matchesSearchQuery
import com.example.data.model.searchRelevance
import com.example.playback.PlaybackStatus
import com.example.ui.CategoryType
import com.example.ui.MainTab
import com.example.ui.RadioViewModel
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Sync
import com.example.ui.components.AddCustomStationDialog
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.ManageFavoritesDialog
import com.example.ui.components.MiniPlayer
import com.example.ui.components.NowPlayingSheet
import com.example.ui.components.NowPlayingTvFullScreen
import com.example.ui.components.SettingsDialog
import com.example.ui.components.StationCard
import com.example.ui.components.ZonqAuthDialog
import com.example.ui.theme.AccentAmber
import com.example.util.CategoryTranslations
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val customStations by viewModel.customStations.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val equalizerState by viewModel.equalizerState.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = remember(context) { TvUtils.isRunningOnTv(context) }
    val isWideLayout = isTv || configuration.screenWidthDp >= 600

    // Request Notification permission on Android 13+ when playback starts if not yet granted
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { /* granted / denied */ }

        LaunchedEffect(playbackState.status) {
            if (playbackState.status == PlaybackStatus.PLAYING || playbackState.status == PlaybackStatus.BUFFERING) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // Ensure screen stays on on TV when tvKeepScreenOn is active so screensaver does not start
    val currentView = LocalView.current
    DisposableEffect(isTv, userPreferences.tvKeepScreenOn) {
        if (isTv && userPreferences.tvKeepScreenOn) {
            currentView.keepScreenOn = true
        } else {
            currentView.keepScreenOn = false
        }
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResume()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                viewModel.onAppPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Show sync status toast when zonq sync message arrives
    LaunchedEffect(uiState.zonqSyncMessage) {
        val msg = uiState.zonqSyncMessage ?: return@LaunchedEffect
        if (msg.startsWith("success:")) {
            val count = msg.substringAfter("success:").toIntOrNull() ?: 0
            val text = context.getString(R.string.zonq_sync_success, count)
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        } else if (msg.startsWith("error:")) {
            val errCode = msg.substringAfter("error:")
            val text = when (errCode) {
                "auth_expired", "auth" -> context.getString(R.string.zonq_err_auth_expired)
                "limit" -> context.getString(R.string.zonq_err_limit)
                "too_many" -> context.getString(R.string.zonq_err_too_many)
                else -> context.getString(R.string.zonq_sync_failed)
            }
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        }
        viewModel.clearZonqSyncMessage()
    }

    // Show update check toast when manual check completes
    LaunchedEffect(uiState.updateCheckStatusMessage) {
        val msg = uiState.updateCheckStatusMessage ?: return@LaunchedEffect
        if (msg.startsWith("up_to_date:")) {
            val time = msg.substringAfter("up_to_date:")
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.update_up_to_date, time),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else if (msg == "error") {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.update_check_failed),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        viewModel.clearUpdateStatusMessage()
    }

    // Determine current station list based on tab & filters
    val displayedStations: List<Station> = when (uiState.selectedTab) {
        MainTab.ALL -> {
            val favIds = favorites.map { it.id }.toSet()
            if (uiState.searchQuery.isNotBlank()) {
                val combined = (customStations + CuratedStations.STATIONS + uiState.globalStations + uiState.hungarianStations + uiState.onlineResults)
                    .distinctStations()
                    .map { it.copy(isFavorite = favIds.contains(it.id)) }
                combined
                    .filter { it.matchesSearchQuery(uiState.searchQuery) }
                    .sortedByDescending { it.searchRelevance(uiState.searchQuery) }
            } else {
                when (uiState.selectedSection) {
                    com.example.ui.StationSection.HUNGARIAN -> {
                        val curatedHu = CuratedStations.STATIONS.filter {
                            it.countryCode.equals("HU", ignoreCase = true) || it.country == "Magyarország"
                        }
                        val combined = (customStations + curatedHu + uiState.hungarianStations)
                            .distinctStations()
                            .map { it.copy(isFavorite = favIds.contains(it.id)) }
                        combined
                    }
                    com.example.ui.StationSection.TOP_VOTED -> {
                        val base = if (uiState.topVotedStations.isNotEmpty()) uiState.topVotedStations else CuratedStations.STATIONS
                        base.distinctStations().map { it.copy(isFavorite = favIds.contains(it.id)) }
                    }
                    com.example.ui.StationSection.TRENDING -> {
                        val base = if (uiState.trendingStations.isNotEmpty()) uiState.trendingStations else CuratedStations.STATIONS
                        base.distinctStations().map { it.copy(isFavorite = favIds.contains(it.id)) }
                    }
                    com.example.ui.StationSection.ALL -> {
                        val combined = (customStations + CuratedStations.STATIONS + uiState.globalStations + uiState.hungarianStations)
                            .distinctStations()
                            .map { it.copy(isFavorite = favIds.contains(it.id)) }
                        val epoch = uiState.shuffleEpoch
                        combined.sortedBy { viewModel.getStationRandomWeight("${it.id.ifBlank { it.url }}_$epoch") }
                    }
                }
            }
        }
        MainTab.FAVORITES -> {
            val favs = favorites.distinctStations()
            if (uiState.searchQuery.isNotBlank()) {
                favs
                    .filter { it.matchesSearchQuery(uiState.searchQuery) }
                    .sortedByDescending { it.searchRelevance(uiState.searchQuery) }
            } else {
                favs
            }
        }
        MainTab.CATEGORIES -> {
            val favIds = favorites.map { it.id }.toSet()
            val stations = when (uiState.selectedCategoryType) {
                CategoryType.COUNTRIES -> {
                    if (uiState.selectedCountry != null) {
                        val curatedForCountry = CuratedStations.STATIONS.filter {
                            it.country == uiState.selectedCountry ||
                            (uiState.selectedCountry == "Magyarország" && it.countryCode.equals("HU", ignoreCase = true)) ||
                            (uiState.selectedCountry == "Szlovákia" && it.countryCode.equals("SK", ignoreCase = true)) ||
                            (uiState.selectedCountry == "Csehország" && it.countryCode.equals("CZ", ignoreCase = true))
                        }.map { it.copy(isFavorite = favIds.contains(it.id)) }
                        (curatedForCountry + uiState.onlineResults).distinctStations()
                    } else {
                        CuratedStations.STATIONS.map { it.copy(isFavorite = favIds.contains(it.id)) }.distinctStations()
                    }
                }
                CategoryType.GENRES -> {
                    if (uiState.selectedGenre != null) {
                        val curatedForGenre = CuratedStations.STATIONS.filter {
                            it.tags.contains(uiState.selectedGenre ?: "", ignoreCase = true)
                        }.map { it.copy(isFavorite = favIds.contains(it.id)) }
                        (curatedForGenre + uiState.onlineResults).distinctStations()
                    } else {
                        CuratedStations.STATIONS.map { it.copy(isFavorite = favIds.contains(it.id)) }.distinctStations()
                    }
                }
                CategoryType.LANGUAGES -> {
                    if (uiState.selectedLanguage != null) {
                        val curatedForLang = CuratedStations.STATIONS.filter {
                            it.language.equals(uiState.selectedLanguage, ignoreCase = true)
                        }.map { it.copy(isFavorite = favIds.contains(it.id)) }
                        (curatedForLang + uiState.onlineResults).distinctStations()
                    } else {
                        CuratedStations.STATIONS.map { it.copy(isFavorite = favIds.contains(it.id)) }.distinctStations()
                    }
                }
            }

            if (uiState.searchQuery.isNotBlank()) {
                stations
                    .filter { it.matchesSearchQuery(uiState.searchQuery) }
                    .sortedByDescending { it.searchRelevance(uiState.searchQuery) }
            } else {
                stations
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!isWideLayout) {
                TopAppBar(
                    title = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_zonq_full_logo),
                            contentDescription = "zonq",
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("app_logo_top_left"),
                            contentScale = ContentScale.Fit
                        )
                    },
                    actions = {
                        // Zonq.eu Account & Sync Button
                        IconButton(
                            onClick = {
                                if (userPreferences.isLoggedIn) {
                                    viewModel.syncFavoritesWithZonq()
                                } else {
                                    viewModel.setShowZonqAuthDialog(true)
                                }
                            },
                            modifier = Modifier.testTag("btn_top_zonq_sync")
                        ) {
                            if (uiState.isSyncingZonq) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = if (userPreferences.isLoggedIn) Icons.Filled.Sync else Icons.Filled.AccountCircle,
                                    contentDescription = stringResource(R.string.zonq_sync_title),
                                    tint = if (userPreferences.isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Settings Gear Icon (Right top corner)
                        IconButton(
                            onClick = { viewModel.setShowSettingsDialog(true) },
                            modifier = Modifier.testTag("btn_top_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (!isWideLayout) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    // Mini Player docked directly above bottom tabs
                    AnimatedVisibility(
                        visible = playbackState.currentStation != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        MiniPlayer(
                            playbackState = playbackState,
                            onPlayPauseClick = { viewModel.togglePlayPause() },
                            onFavoriteToggle = {
                                playbackState.currentStation?.let { viewModel.toggleFavorite(it) }
                            },
                            onExpandClick = { viewModel.setShowNowPlayingSheet(true) }
                        )
                    }

                    // Bottom Navigation Tabs
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    ) {
                        // Tab 1: All Stations
                        NavigationBarItem(
                            selected = uiState.selectedTab == MainTab.ALL,
                            onClick = {
                                if (uiState.selectedTab == MainTab.ALL) {
                                    viewModel.reshuffleAllStations()
                                } else {
                                    viewModel.selectTab(MainTab.ALL)
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.selectedTab == MainTab.ALL) Icons.Filled.Radio else Icons.Outlined.Radio,
                                    contentDescription = stringResource(R.string.tab_stations)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.tab_stations),
                                    fontWeight = if (uiState.selectedTab == MainTab.ALL) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("nav_tab_all")
                        )

                        // Tab 2: Favorites
                        NavigationBarItem(
                            selected = uiState.selectedTab == MainTab.FAVORITES,
                            onClick = { viewModel.selectTab(MainTab.FAVORITES) },
                            icon = {
                                if (favorites.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = AccentAmber) {
                                                Text("${favorites.size}")
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.selectedTab == MainTab.FAVORITES) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = stringResource(R.string.tab_favorites)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (uiState.selectedTab == MainTab.FAVORITES) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = stringResource(R.string.tab_favorites)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.tab_favorites),
                                    fontWeight = if (uiState.selectedTab == MainTab.FAVORITES) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("nav_tab_favorites")
                        )

                        // Tab 3: Categories (Countries, Genres, Languages under one tab)
                        NavigationBarItem(
                            selected = uiState.selectedTab == MainTab.CATEGORIES,
                            onClick = { viewModel.selectTab(MainTab.CATEGORIES) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.selectedTab == MainTab.CATEGORIES) Icons.Filled.Category else Icons.Outlined.Category,
                                    contentDescription = stringResource(R.string.tab_categories)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.tab_categories),
                                    fontWeight = if (uiState.selectedTab == MainTab.CATEGORIES) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("nav_tab_categories")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isWideLayout) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .testTag("side_navigation_rail"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_zonq_symbol),
                                contentDescription = "zonq",
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("tv_app_logo"),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                ) {
                    NavigationRailItem(
                        selected = uiState.selectedTab == MainTab.ALL,
                        onClick = {
                            if (uiState.selectedTab == MainTab.ALL) {
                                viewModel.reshuffleAllStations()
                            } else {
                                viewModel.selectTab(MainTab.ALL)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (uiState.selectedTab == MainTab.ALL) Icons.Filled.Radio else Icons.Outlined.Radio,
                                contentDescription = stringResource(R.string.tab_stations)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.tab_stations),
                                fontWeight = if (uiState.selectedTab == MainTab.ALL) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.testTag("tv_nav_tab_all")
                    )

                    NavigationRailItem(
                        selected = uiState.selectedTab == MainTab.FAVORITES,
                        onClick = { viewModel.selectTab(MainTab.FAVORITES) },
                        icon = {
                            if (favorites.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = AccentAmber) {
                                            Text("${favorites.size}")
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (uiState.selectedTab == MainTab.FAVORITES) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = stringResource(R.string.tab_favorites)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (uiState.selectedTab == MainTab.FAVORITES) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = stringResource(R.string.tab_favorites)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.tab_favorites),
                                fontWeight = if (uiState.selectedTab == MainTab.FAVORITES) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.testTag("tv_nav_tab_favorites")
                    )

                    NavigationRailItem(
                        selected = uiState.selectedTab == MainTab.CATEGORIES,
                        onClick = { viewModel.selectTab(MainTab.CATEGORIES) },
                        icon = {
                            Icon(
                                imageVector = if (uiState.selectedTab == MainTab.CATEGORIES) Icons.Filled.Category else Icons.Outlined.Category,
                                contentDescription = stringResource(R.string.tab_categories)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.tab_categories),
                                fontWeight = if (uiState.selectedTab == MainTab.CATEGORIES) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.testTag("tv_nav_tab_categories")
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = { viewModel.reshuffleAllStations() },
                        modifier = Modifier.testTag("tv_btn_shuffle")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = stringResource(R.string.action_shuffle),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            if (userPreferences.isLoggedIn) {
                                viewModel.syncFavoritesWithZonq()
                            } else {
                                viewModel.setShowZonqAuthDialog(true)
                            }
                        },
                        modifier = Modifier.testTag("tv_btn_zonq_sync")
                    ) {
                        if (uiState.isSyncingZonq) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = if (userPreferences.isLoggedIn) Icons.Filled.Sync else Icons.Filled.AccountCircle,
                                contentDescription = stringResource(R.string.zonq_sync_title),
                                tint = if (userPreferences.isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.setShowSettingsDialog(true) },
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .testTag("tv_btn_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(
                        start = if (isWideLayout) 12.dp else 0.dp,
                        end = if (isTv) 24.dp else 0.dp,
                        top = if (isTv) 16.dp else 0.dp,
                        bottom = if (isTv) 16.dp else 0.dp
                    )
            ) {
            // User-friendly Search Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = Color.Transparent
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = {
                        Text(
                            stringResource(R.string.search_hint),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.action_search),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.action_clear_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("main_search_bar")
                )
            }

            // Radio Browser Section Filter Chips (Only shown in All tab when not searching)
            if (uiState.selectedTab == MainTab.ALL && uiState.searchQuery.isBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedSection == com.example.ui.StationSection.ALL,
                        onClick = { viewModel.selectSection(com.example.ui.StationSection.ALL) },
                        leadingIcon = {
                            Icon(Icons.Filled.Radio, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.filter_all), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("chip_sec_all")
                    )

                    FilterChip(
                        selected = uiState.selectedSection == com.example.ui.StationSection.HUNGARIAN,
                        onClick = { viewModel.selectSection(com.example.ui.StationSection.HUNGARIAN) },
                        label = { Text("🇭🇺 " + stringResource(R.string.filter_hungarian), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("chip_sec_hu")
                    )

                    FilterChip(
                        selected = uiState.selectedSection == com.example.ui.StationSection.TOP_VOTED,
                        onClick = { viewModel.selectSection(com.example.ui.StationSection.TOP_VOTED) },
                        label = { Text("⭐ " + stringResource(R.string.filter_top_voted), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("chip_sec_top")
                    )

                    FilterChip(
                        selected = uiState.selectedSection == com.example.ui.StationSection.TRENDING,
                        onClick = { viewModel.selectSection(com.example.ui.StationSection.TRENDING) },
                        label = { Text("🔥 " + stringResource(R.string.filter_trending), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("chip_sec_trending")
                    )
                }
            }

            // Categories Sub-filter Controls (Only shown in Categories tab)
            if (uiState.selectedTab == MainTab.CATEGORIES) {
                // Category Type Pills Row (Országok, Műfajok, Nyelvek)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedCategoryType == CategoryType.COUNTRIES,
                        onClick = { viewModel.selectCategoryType(CategoryType.COUNTRIES) },
                        leadingIcon = {
                            Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.tab_countries), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("chip_cat_countries")
                    )

                    FilterChip(
                        selected = uiState.selectedCategoryType == CategoryType.GENRES,
                        onClick = { viewModel.selectCategoryType(CategoryType.GENRES) },
                        leadingIcon = {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.tab_genres), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("chip_cat_genres")
                    )

                    FilterChip(
                        selected = uiState.selectedCategoryType == CategoryType.LANGUAGES,
                        onClick = { viewModel.selectCategoryType(CategoryType.LANGUAGES) },
                        leadingIcon = {
                            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.tab_languages), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("chip_cat_languages")
                    )
                }

                // Specific items list for selected category type
                when (uiState.selectedCategoryType) {
                    CategoryType.COUNTRIES -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CuratedStations.COUNTRIES.forEach { country ->
                                val isSelected = uiState.selectedCountry == country
                                val translatedCountry = CategoryTranslations.getTranslatedCountry(context, country)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.selectCountry(if (isSelected) null else country)
                                    },
                                    label = { Text(translatedCountry, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                    CategoryType.GENRES -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CuratedStations.GENRES.forEach { genre ->
                                val isSelected = uiState.selectedGenre == genre
                                val translatedGenre = CategoryTranslations.getTranslatedGenre(context, genre)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.selectGenre(if (isSelected) null else genre)
                                    },
                                    label = { Text(translatedGenre, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                    CategoryType.LANGUAGES -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CuratedStations.LANGUAGES.forEach { lang ->
                                val isSelected = uiState.selectedLanguage == lang
                                val translatedLang = CategoryTranslations.getTranslatedLanguage(context, lang)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.selectLanguage(if (isSelected) null else lang)
                                    },
                                    label = { Text(translatedLang, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Loading indicator for online queries (only when searching or filtering, never on initial app start)
            if (uiState.isOnlineLoading && (uiState.searchQuery.isNotBlank() || uiState.selectedCountry != null || uiState.selectedGenre != null || uiState.selectedLanguage != null)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.searching_stations),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Content List
            if (displayedStations.isEmpty() && !uiState.isOnlineLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.selectedTab == MainTab.FAVORITES) Icons.Filled.Favorite else Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (uiState.selectedTab == MainTab.FAVORITES) {
                                stringResource(R.string.no_favorites)
                            } else {
                                stringResource(R.string.empty_search)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        if (uiState.selectedTab == MainTab.FAVORITES) {
                            if (!userPreferences.isLoggedIn) {
                                Text(
                                    text = stringResource(R.string.zonq_not_logged_in_favs_hint),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Button(
                                    onClick = { viewModel.setShowZonqAuthDialog(true) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("btn_favs_empty_zonq_login")
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
                                Button(
                                    onClick = { viewModel.syncFavoritesWithZonq() },
                                    enabled = !uiState.isSyncingZonq,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("btn_favs_empty_zonq_sync")
                                ) {
                                    if (uiState.isSyncingZonq) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = stringResource(R.string.zonq_syncing))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Sync,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.zonq_sync_now),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = if (isWideLayout) GridCells.Adaptive(minSize = 300.dp) else GridCells.Fixed(1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    // Search results count header
                    if (uiState.searchQuery.isNotBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(R.string.search_results_header, uiState.searchQuery, displayedStations.size),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    items(displayedStations, key = { it.id.ifBlank { it.url } }) { station ->
                        val isCurrent = playbackState.currentStation?.id == station.id
                        val isPlaying = isCurrent && playbackState.status == PlaybackStatus.PLAYING
                        StationCard(
                            station = station,
                            isPlaying = isPlaying,
                            playbackStatus = if (isCurrent) playbackState.status else PlaybackStatus.IDLE,
                            onPlayClick = {
                                if (isPlaying) {
                                    viewModel.togglePlayPause()
                                } else {
                                    viewModel.playStation(station, displayedStations)
                                }
                            },
                            onFavoriteToggle = {
                                viewModel.toggleFavorite(station)
                            }
                        )
                    }

                    // Load more button for All tab
                    if (uiState.selectedTab == MainTab.ALL && uiState.searchQuery.isBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { viewModel.loadMoreGlobalStations() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("btn_load_more_stations")
                                ) {
                                    Text(stringResource(R.string.btn_load_more_stations))
                                }
                            }
                        }
                    }
                }
            }

            // Docked MiniPlayer when in Wide Layout / TV
            if (isWideLayout) {
                AnimatedVisibility(
                    visible = playbackState.currentStation != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    MiniPlayer(
                        playbackState = playbackState,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onFavoriteToggle = {
                            playbackState.currentStation?.let { viewModel.toggleFavorite(it) }
                        },
                        onExpandClick = { viewModel.setShowNowPlayingSheet(true) }
                    )
                }
            }
        }
    }
}

    // Settings Dialog
    if (uiState.showSettingsDialog) {
        SettingsDialog(
            userPreferences = userPreferences,
            equalizerState = equalizerState,
            onDismiss = { viewModel.setShowSettingsDialog(false) },
            onLanguageChange = { viewModel.setAppLanguage(it) },
            onAudioQualityChange = { viewModel.setAudioQuality(it) },
            onBufferDurationChange = { viewModel.setBufferDuration(it) },
            onAutoPlayChange = { viewModel.setAutoPlay(it) },
            onDefaultScreenChange = { viewModel.setDefaultScreen(it) },
            onThemeModeChange = { viewModel.setThemeMode(it) },
            onEqualizerEnabledChange = { viewModel.setEqualizerEnabled(it) },
            onEqualizerPresetChange = { viewModel.setEqualizerPreset(it) },
            onEqualizerBandLevelChange = { bandIndex, levelMb -> viewModel.setEqualizerBandLevel(bandIndex, levelMb) },
            onAudioNormalizationEnabledChange = { viewModel.setAudioNormalizationEnabled(it) },
            onNormalizationProfileChange = { viewModel.setNormalizationProfile(it) },
            onTvKeepScreenOnChanged = { viewModel.setTvKeepScreenOn(it) },
            onOpenZonqAuth = { viewModel.setShowZonqAuthDialog(true) },
            onSyncZonqFavorites = { viewModel.syncFavoritesWithZonq() },
            onLogoutZonq = { viewModel.logoutZonq() },
            onDeleteAccountZonq = { pass, cb -> viewModel.deleteAccountZonq(pass, cb) },
            isSyncingZonq = uiState.isSyncingZonq,
            isCheckingForUpdate = uiState.isCheckingForUpdate,
            onCheckForUpdate = { viewModel.checkForAppUpdate(isManual = true) },
            availableUpdateInfo = uiState.availableUpdateInfo,
            onOpenAddCustomStation = { viewModel.setShowAddStationDialog(true) },
            onOpenManageFavorites = { viewModel.setShowManageFavoritesDialog(true) }
        )
    }

    // App Update Dialog
    if (uiState.showUpdateDialog && uiState.availableUpdateInfo != null) {
        AppUpdateDialog(
            updateInfo = uiState.availableUpdateInfo!!,
            onDownload = {
                viewModel.downloadUpdate()
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.update_download_started),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                viewModel.dismissUpdateDialog(rememberDismiss = true)
            }
        )
    }

    // Zonq Auth Dialog (zonq.eu account login & registration)
    if (uiState.showZonqAuthDialog) {
        ZonqAuthDialog(
            onDismiss = { viewModel.setShowZonqAuthDialog(false) },
            onLogin = { email, pass, cb -> viewModel.loginZonq(email, pass, cb) },
            onRegister = { email, pass, cb -> viewModel.registerZonq(email, pass, cb) },
            onForgotPassword = { email, cb -> viewModel.forgotZonq(email, cb) }
        )
    }

    // Add Custom Station Dialog
    if (uiState.showAddStationDialog) {
        AddCustomStationDialog(
            onDismiss = { viewModel.setShowAddStationDialog(false) },
            onSave = { name, url, country, genre, language, iconUrl ->
                viewModel.addCustomStation(name, url, country, genre, language, iconUrl)
            }
        )
    }

    // Manage Favorites Dialog
    if (uiState.showManageFavoritesDialog) {
        ManageFavoritesDialog(
            favorites = favorites,
            onDismiss = { viewModel.setShowManageFavoritesDialog(false) },
            onPlayStation = { station ->
                viewModel.playStation(station, favorites)
            },
            onRemoveFavorite = { id ->
                viewModel.removeFavorite(id)
            },
            onMoveUp = { id ->
                viewModel.moveFavoriteUp(id)
            },
            onMoveDown = { id ->
                viewModel.moveFavoriteDown(id)
            }
        )
    }

    // Now Playing Player (Full-screen for Android TV & Google TV & Wide Screens, BottomSheet for phones)
    if (uiState.showNowPlayingSheet && playbackState.currentStation != null) {
        if (isTv || isWideLayout) {
            NowPlayingTvFullScreen(
                playbackState = playbackState,
                onDismiss = {
                    viewModel.setShowNowPlayingSheet(false)
                },
                onPlayPause = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.playbackManager.playPrevious() },
                onNext = { viewModel.playbackManager.playNext() },
                onFavoriteToggle = {
                    playbackState.currentStation?.let { viewModel.toggleFavorite(it) }
                },
                onVolumeChange = { viewModel.playbackManager.setVolume(it) }
            )
        } else {
            NowPlayingSheet(
                playbackState = playbackState,
                onDismiss = { viewModel.setShowNowPlayingSheet(false) },
                onPlayPause = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.playbackManager.playPrevious() },
                onNext = { viewModel.playbackManager.playNext() },
                onFavoriteToggle = {
                    playbackState.currentStation?.let { viewModel.toggleFavorite(it) }
                },
                onVolumeChange = { viewModel.playbackManager.setVolume(it) }
            )
        }
    }
}
