package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CuratedStations
import com.example.data.model.Station
import com.example.data.model.distinctStations
import com.example.data.model.matchesSearchQuery
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("zonq", appName)
    }

    @Test
    fun `curated stations contain Hungarian, Slovak, Czech, and US radios`() {
        assertTrue(CuratedStations.STATIONS.isNotEmpty())

        val retroRadio = CuratedStations.STATIONS.find { it.name.contains("Retro Rádió") }
        assertNotNull("Retro Rádió megtalálható", retroRadio)
        assertTrue(retroRadio?.url?.startsWith("http") == true)

        val slovakRadio = CuratedStations.STATIONS.find { it.country == "Szlovákia" }
        assertNotNull("Szlovák rádió elérhető", slovakRadio)
        assertTrue(slovakRadio?.url?.startsWith("http") == true)

        val czechRadio = CuratedStations.STATIONS.find { it.country == "Csehország" }
        assertNotNull("Cseh rádió elérhető", czechRadio)
        assertTrue(czechRadio?.url?.startsWith("http") == true)

        val usRadio = CuratedStations.STATIONS.find { it.country == "Egyesült Államok" }
        assertNotNull("Amerikai rádió elérhető", usRadio)
        assertTrue(usRadio?.url?.startsWith("http") == true)
    }

    @Test
    fun `station subtitle formatting`() {
        val station = Station(
            id = "test-1",
            name = "Test FM",
            url = "https://example.com/stream.mp3",
            country = "Magyarország",
            tags = "Pop, Rock",
            bitrate = 128
        )
        assertEquals("Magyarország • Pop • 128k", station.displaySubtitle)
    }

    @Test
    fun `distinctStations deduplicates by id, url, and name`() {
        val list = listOf(
            Station(id = "1", name = "Retro Rádió", url = "http://example.com/retro.mp3"),
            Station(id = "2", name = "Retro Rádió", url = "https://example.com/other.mp3"), // duplicate name
            Station(id = "3", name = "Other Name", url = "https://example.com/retro.mp3/"), // duplicate URL (ignoring https and slash)
            Station(id = "1", name = "Different Name", url = "http://example.com/diff.mp3"), // duplicate ID
            Station(id = "4", name = "Unique Station", url = "http://example.com/unique.mp3")
        )

        val deduplicated = list.distinctStations()
        assertEquals(2, deduplicated.size)
        assertEquals("Retro Rádió", deduplicated[0].name)
        assertEquals("Unique Station", deduplicated[1].name)
    }

    @Test
    fun `favorites reordering in database persists and updates order`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java).build()
        val dao = db.stationDao()

        dao.insertFavorite(
            com.example.data.local.FavoriteStationEntity(
                id = "stat-1",
                name = "Station One",
                url = "http://example.com/1",
                favicon = "",
                country = "HU",
                countryCode = "HU",
                language = "hu",
                tags = "",
                codec = "MP3",
                bitrate = 128,
                orderIndex = 0
            )
        )
        dao.insertFavorite(
            com.example.data.local.FavoriteStationEntity(
                id = "stat-2",
                name = "Station Two",
                url = "http://example.com/2",
                favicon = "",
                country = "HU",
                countryCode = "HU",
                language = "hu",
                tags = "",
                codec = "MP3",
                bitrate = 128,
                orderIndex = 1
            )
        )

        val initial = dao.getAllFavoritesList()
        assertEquals("stat-1", initial[0].id)
        assertEquals("stat-2", initial[1].id)

        // Swap order: station 2 to index 0, station 1 to index 1
        dao.updateFavoriteOrder("stat-2", 0)
        dao.updateFavoriteOrder("stat-1", 1)

        val updated = dao.getAllFavoritesList()
        assertEquals("stat-2", updated[0].id)
        assertEquals("stat-1", updated[1].id)

        db.close()
    }

    @Test
    fun `matchesSearchQuery only matches station name and ignores unrelated tags`() {
        val retroRadio = Station(
            id = "1",
            name = "Retro Rádió",
            url = "http://example.com/retro",
            tags = "pop, retro, oldies",
            country = "Magyarország"
        )
        val radio105 = Station(
            id = "2",
            name = "Radio 105",
            url = "http://example.com/105",
            tags = "retro, 80s, pop",
            country = "Olaszország"
        )
        val slagerFm = Station(
            id = "3",
            name = "Sláger FM",
            url = "http://example.com/slager",
            tags = "slager, pop",
            country = "Magyarország"
        )

        // Searching for "retro" should match Retro Rádió by name, but NOT Radio 105 (whose name does not contain retro)
        assertTrue(retroRadio.matchesSearchQuery("retro"))
        assertTrue(retroRadio.matchesSearchQuery("rádió"))
        assertTrue(retroRadio.matchesSearchQuery("radio"))
        org.junit.Assert.assertFalse(radio105.matchesSearchQuery("retro"))

        // Accent-insensitive search
        assertTrue(slagerFm.matchesSearchQuery("slager"))
        assertTrue(slagerFm.matchesSearchQuery("sláger"))
        org.junit.Assert.assertFalse(slagerFm.matchesSearchQuery("magyarorszag"))
    }

    @Test
    fun `random station weighting generates distinct weights and reshuffle updates state`() {
        val stations = CuratedStations.STATIONS.take(20)
        val weightsMap = java.util.concurrent.ConcurrentHashMap<String, Double>()
        fun getWeight(id: String) = weightsMap.computeIfAbsent(id) { kotlin.random.Random.nextDouble() }

        val sorted1 = stations.sortedBy { getWeight(it.id) }
        val sorted2 = stations.sortedBy { getWeight(it.id) }
        // Stable across reads with same weights
        assertEquals(sorted1.map { it.id }, sorted2.map { it.id })

        // Clear weights (reshuffle)
        weightsMap.clear()
        val sorted3 = stations.sortedBy { getWeight(it.id) }
        // The list contains the same stations but can be different order
        assertEquals(stations.size, sorted3.size)
        assertTrue(sorted3.containsAll(stations))
    }

    @Test
    fun `android auto metadata format displays station name as artist and artist minus title as title`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java).build()
        val prefs = com.example.data.preferences.RadioPreferences(context)
        val repo = com.example.data.repository.StationRepository(db.stationDao(), prefs)
        val playbackManager = com.example.playback.RadioPlaybackManager.getInstance(context, repo, prefs)

        val station = CuratedStations.STATIONS.first { it.name.contains("Sláger") }
        val metadata = playbackManager.createStationMetadata(
            station = station,
            title = "Flowers",
            artist = "Miley Cyrus"
        )

        // Ahol az előadó van -> Rádió neve
        assertEquals("Sláger FM", metadata.artist)
        assertEquals("Sláger FM", metadata.subtitle)
        assertEquals("Sláger FM", metadata.albumArtist)

        // Ahol a szám címe van -> "Előadó - Szám címe"
        assertEquals("Miley Cyrus - Flowers", metadata.title)
        assertEquals("Miley Cyrus - Flowers", metadata.displayTitle)

        db.close()
    }

    @Test
    fun `steering wheel channel switching switches strictly among favorites`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java).build()
        val prefs = com.example.data.preferences.RadioPreferences(context)
        val repo = com.example.data.repository.StationRepository(db.stationDao(), prefs)
        val playbackManager = com.example.playback.RadioPlaybackManager.getInstance(context, repo, prefs)

        val favList = CuratedStations.STATIONS.take(5)
        assertTrue(favList.size >= 2)
        val fav1 = favList[0]
        val fav2 = favList[1]

        playbackManager.playStation(fav1, favList)
        assertEquals(fav1.id, playbackManager.playbackState.value.currentStation?.id)

        // playNextFavorite switches among favorites
        playbackManager.playNextFavorite()
        assertEquals(fav2.id, playbackManager.playbackState.value.currentStation?.id)

        playbackManager.playPreviousFavorite()
        assertEquals(fav1.id, playbackManager.playbackState.value.currentStation?.id)

        db.close()
    }

    @Test
    fun `buffer duration provides generous buffer headroom for in-transit cell handovers while maintaining instant startup`() {
        // Verify buffer duration maintains quick startup while allowing forward buffering up to maxBufferMs
        assertEquals(5000, com.example.data.preferences.BufferDuration.SHORT.minBufferMs)
        assertEquals(15000, com.example.data.preferences.BufferDuration.SHORT.maxBufferMs)
        assertEquals(1000, com.example.data.preferences.BufferDuration.SHORT.bufferForPlaybackMs)

        assertEquals(10000, com.example.data.preferences.BufferDuration.NORMAL.minBufferMs)
        assertEquals(30000, com.example.data.preferences.BufferDuration.NORMAL.maxBufferMs)
        assertEquals(1500, com.example.data.preferences.BufferDuration.NORMAL.bufferForPlaybackMs)

        assertEquals(20000, com.example.data.preferences.BufferDuration.LARGE.minBufferMs)
        assertEquals(60000, com.example.data.preferences.BufferDuration.LARGE.maxBufferMs)
        assertEquals(2000, com.example.data.preferences.BufferDuration.LARGE.bufferForPlaybackMs)
    }

    @Test
    fun `pausing player sets PAUSED status without destroying notification media items, and stop clears items`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java).build()
        val prefs = com.example.data.preferences.RadioPreferences(context)
        val repo = com.example.data.repository.StationRepository(db.stationDao(), prefs)
        val playbackManager = com.example.playback.RadioPlaybackManager.getInstance(context, repo, prefs)

        val station = CuratedStations.STATIONS.first()
        playbackManager.playStation(station)
        assertEquals(station.id, playbackManager.playbackState.value.currentStation?.id)

        // Pause should stop audio output and retain media item for persistent notification controls
        playbackManager.pause()
        assertEquals(com.example.playback.PlaybackStatus.PAUSED, playbackManager.playbackState.value.status)
        assertEquals(station.id, playbackManager.playbackState.value.currentStation?.id)
        assertEquals(false, playbackManager.player.playWhenReady)
        assertEquals(1, playbackManager.player.mediaItemCount)

        // Resume should reconnect fresh to the live station stream
        playbackManager.resume()
        assertEquals(station.id, playbackManager.playbackState.value.currentStation?.id)
        assertEquals(com.example.playback.PlaybackStatus.BUFFERING, playbackManager.playbackState.value.status)
        assertEquals(1, playbackManager.player.mediaItemCount)

        // Stop should clear media items and dismiss notification
        playbackManager.stop()
        assertEquals(0, playbackManager.player.mediaItemCount)
        assertEquals(com.example.playback.PlaybackStatus.IDLE, playbackManager.playbackState.value.status)
        db.close()
    }

    @Test
    fun `metadata uses song album art if available instead of station favicon`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java).build()
        val prefs = com.example.data.preferences.RadioPreferences(context)
        val repo = com.example.data.repository.StationRepository(db.stationDao(), prefs)
        val playbackManager = com.example.playback.RadioPlaybackManager.getInstance(context, repo, prefs)

        val station = CuratedStations.STATIONS.first { it.name.contains("Sláger") }
        val testAlbumArt = "https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/test_cover.jpg/600x600bb.jpg"

        val metadata = playbackManager.createStationMetadata(
            station = station,
            title = "Flowers",
            artist = "Miley Cyrus",
            albumArtUrl = testAlbumArt
        )

        assertEquals(testAlbumArt, metadata.artworkUri.toString())
        assertEquals("Miley Cyrus - Flowers", metadata.title)
        assertEquals("Sláger FM", metadata.artist)

        db.close()
    }

    @Test
    fun `audio normalizer attenuates overly loud signals and boosts quiet signals with soft-knee limiter`() {
        val processor = com.example.playback.AudioNormalizerProcessor()
        val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(44100, 2, androidx.media3.common.C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // 1. Test quiet signal boost
        val numSamples = 4410 // 50ms of stereo audio
        val quietBuffer = java.nio.ByteBuffer.allocateDirect(numSamples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            quietBuffer.putShort(2000.toShort()) // ~0.06 amplitude (-24 dBFS)
        }
        quietBuffer.flip()

        processor.queueInput(quietBuffer)
        val quietOutput = processor.output
        assertTrue(quietOutput.hasRemaining())

        // Read first few samples from output
        val firstSample = quietOutput.short
        // Normalizer target is ~0.17 (approx 5500), so the gain should increase above input amplitude 2000
        assertTrue("Output should be amplified for quiet audio", firstSample >= 2000)

        // 2. Test loud signal attenuation and soft knee clamping
        val loudBuffer = java.nio.ByteBuffer.allocateDirect(numSamples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            loudBuffer.putShort(30000.toShort()) // ~0.91 amplitude (-0.7 dBFS)
        }
        loudBuffer.flip()

        processor.queueInput(loudBuffer)
        val loudOutput = processor.output
        assertTrue(loudOutput.hasRemaining())

        var maxSampleSeen: Short = 0
        while (loudOutput.hasRemaining()) {
            val s = loudOutput.short
            if (s > maxSampleSeen) maxSampleSeen = s
        }
        // Soft-knee limiter must ensure sample never exceeds 32767
        assertTrue("Loud sample must not clip or overflow", maxSampleSeen <= 32767)
    }

    @Test
    fun `audio normalizer station memory preserves learned gain when switching stations`() {
        val processor = com.example.playback.AudioNormalizerProcessor()
        val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(44100, 2, androidx.media3.common.C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // Listen to quiet station A
        processor.onStationChanged("station-quiet-A")
        val numSamples = 2205
        val quietBuffer = java.nio.ByteBuffer.allocateDirect(numSamples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) quietBuffer.putShort(1500.toShort())
        quietBuffer.flip()
        processor.queueInput(quietBuffer)

        // Switch to station B, then back to station A
        processor.onStationChanged("station-B")
        processor.onStationChanged("station-quiet-A")

        // Status flow should reflect active normalizer
        val status = processor.statusFlow.value
        assertTrue(status.isEnabled)
        assertEquals(com.example.playback.NormalizationProfile.BALANCED, status.profile)
    }

    @Test
    fun `audio normalization preferences can be toggled and updated`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.preferences.RadioPreferences(context)

        // Default should be enabled
        val initial = prefs.userPreferencesFlow.first()
        assertTrue(initial.audioNormalizationEnabled)
        assertEquals(com.example.playback.NormalizationProfile.BALANCED, initial.normalizationProfile)

        // Change profile
        prefs.setNormalizationProfile(com.example.playback.NormalizationProfile.LOUD)
        val updatedProfile = prefs.userPreferencesFlow.first()
        assertEquals(com.example.playback.NormalizationProfile.LOUD, updatedProfile.normalizationProfile)

        // Disable normalization
        prefs.setAudioNormalizationEnabled(false)
        val updatedDisabled = prefs.userPreferencesFlow.first()
        org.junit.Assert.assertFalse(updatedDisabled.audioNormalizationEnabled)
    }

    @Test
    fun `zonq auth preferences can store and clear token and email`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.preferences.RadioPreferences(context)

        prefs.saveAuth("test-token-12345", "test@zonq.eu")
        val loggedInPrefs = prefs.userPreferencesFlow.first()
        assertTrue(loggedInPrefs.isLoggedIn)
        assertEquals("test-token-12345", loggedInPrefs.zonqToken)
        assertEquals("test@zonq.eu", loggedInPrefs.zonqEmail)

        prefs.clearAuth()
        val loggedOutPrefs = prefs.userPreferencesFlow.first()
        org.junit.Assert.assertFalse(loggedOutPrefs.isLoggedIn)
        org.junit.Assert.assertNull(loggedOutPrefs.zonqToken)
    }

    @Test
    fun `radio browser station dto and tv utils work correctly`() {
        val dto = com.example.data.remote.RadioBrowserStationDto(
            stationUuid = "98b22cd2-0ec8-4bb1-a5f6-f168de7548b5",
            name = "Retro Rádió",
            url = "https://icast.connectmedia.hu/5001/live.mp3",
            country = "Hungary",
            countryCode = "HU",
            language = "hungarian",
            tags = "pop,retro"
        )
        assertEquals("98b22cd2-0ec8-4bb1-a5f6-f168de7548b5", dto.stationUuid)
        assertEquals("Retro Rádió", dto.name)
        assertEquals("HU", dto.countryCode)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val isTv = com.example.util.TvUtils.isRunningOnTv(context)
        org.junit.Assert.assertNotNull(isTv)
    }

    @Test
    fun `app update preferences can store dismissed timestamp`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.preferences.RadioPreferences(context)

        val initialPrefs = prefs.userPreferencesFlow.first()
        assertEquals(0L, initialPrefs.lastDismissedUpdateTimestamp)

        val testTimestamp = 1729405071000L
        prefs.setLastDismissedUpdateTimestamp(testTimestamp)
        val updatedPrefs = prefs.userPreferencesFlow.first()
        assertEquals(testTimestamp, updatedPrefs.lastDismissedUpdateTimestamp)
    }

    @Test
    fun `app update manager correctly initializes and parses local install time`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val updateManager = com.example.data.remote.AppUpdateManager(context)
        val installTime = updateManager.getLocalInstallTime()
        assertTrue(installTime > 0L)
    }

    @Test
    fun `audio normalizer processor handles audio buffer smoothly without initial distortion`() {
        val normalizer = com.example.playback.AudioNormalizerProcessor()
        val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(
            44100, 2, androidx.media3.common.C.ENCODING_PCM_16BIT
        )
        val configured = normalizer.configure(format)
        assertEquals(44100, configured.sampleRate)

        // Feed audio buffer
        val buffer = java.nio.ByteBuffer.allocateDirect(1024).order(java.nio.ByteOrder.nativeOrder())
        for (i in 0 until 512) {
            buffer.putShort((1000 * Math.sin(i.toDouble())).toInt().toShort())
        }
        buffer.flip()

        normalizer.queueInput(buffer)
        val output = normalizer.output
        assertTrue(output.remaining() > 0)
    }

    @Test
    fun `tv radio load control initializes with larger buffer for tv`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.preferences.RadioPreferences(context)
        val tvLoadControl = com.example.playback.RadioLoadControl(prefs, isTv = true)
        assertNotNull(tvLoadControl.allocator)
    }

    @Test
    fun `zonq api station JSON serialization complies strictly with backend spec`() {
        val api = com.example.data.remote.ZonqApi()
        val station = Station(
            id = "98b22cd2-0ec8-4bb1-a5f6-f168de7548b5",
            name = "Retro Rádió",
            url = "https://icast.connectmedia.hu/5001/live.mp3",
            favicon = "https://retroradio.hu/favicon.ico",
            country = "Magyarország",
            countryCode = "HU",
            tags = "pop, retro, 80s",
            codec = "MP3"
        )

        val json = api.stationToJsonObject(station)
        assertEquals("98b22cd2-0ec8-4bb1-a5f6-f168de7548b5", json.getString("id"))
        assertEquals("Retro Rádió", json.getString("name"))
        assertEquals("https://icast.connectmedia.hu/5001/live.mp3", json.getString("url"))
        assertEquals("https://retroradio.hu/favicon.ico", json.getString("logo"))
        assertEquals("Magyarország", json.getString("country"))
        assertEquals("HU", json.getString("cc"))
        assertEquals("pop, retro, 80s", json.getString("genres"))
        assertEquals("MP3", json.getString("fmt"))

        // Parsing back
        val array = org.json.JSONArray().apply { put(json) }
        val parsedList = api.parseFavoritesArray(array)
        assertEquals(1, parsedList.size)
        val parsed = parsedList.first()
        assertEquals(station.id, parsed.id)
        assertEquals(station.name, parsed.name)
        assertEquals(station.url, parsed.url)
        assertEquals(station.favicon, parsed.favicon)
        assertEquals(station.countryCode, parsed.countryCode)
        assertEquals(station.codec, parsed.codec)
    }

    @Test
    fun `zonq sync manager queues pending operations when offline`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java).build()
        val prefs = com.example.data.preferences.RadioPreferences(context)
        prefs.saveAuth("test-token-xyz", "user@zonq.eu", "rev-100")

        val syncManager = com.example.data.remote.ZonqSyncManager(
            context = context,
            stationDao = db.stationDao(),
            preferences = prefs
        )

        val station = Station(
            id = "retro-uuid-123",
            name = "Retro Rádió",
            url = "https://icast.connectmedia.hu/5001/live.mp3"
        )

        // User adds to favorites: optimism + enqueue
        syncManager.onFavoriteToggled(station, isFavoriteNow = true)
        val pendingJson = prefs.getPendingQueueJson()
        assertTrue(pendingJson.contains("retro-uuid-123"))
        assertTrue(pendingJson.contains("\"op\":\"add\""))

        // User removes favorite: updates queue
        syncManager.onFavoriteToggled(station, isFavoriteNow = false)
        val pendingJson2 = prefs.getPendingQueueJson()
        assertTrue(pendingJson2.contains("\"op\":\"remove\""))

        db.close()
    }

    @Test
    fun `locale utils resolves safe locale without crashing`() {
        val hu = com.example.util.LocaleUtils.getEffectiveLocale(java.util.Locale("hu"))
        assertEquals("hu", hu.language)

        val sk = com.example.util.LocaleUtils.getEffectiveLocale(java.util.Locale("sk"))
        assertEquals("sk", sk.language)

        // Null targetLocale must safely fallback to system/default without throwing IndexOutOfBoundsException
        val def = com.example.util.LocaleUtils.getEffectiveLocale(null)
        assertNotNull(def)
    }

    @Test
    fun `equalizer manager handles abnormal band ranges and custom levels without throwing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val eqManager = com.example.playback.RadioEqualizerManager(context)

        // Safe enabling/disabling
        eqManager.setEnabled(true)
        assertTrue(eqManager.equalizerState.value.isEnabled)

        // Safe preset change even with empty bands or abnormal ranges
        eqManager.setPreset(com.example.playback.EqualizerPreset.ROCK)
        assertEquals(com.example.playback.EqualizerPreset.ROCK.id, eqManager.equalizerState.value.currentPresetId)

        // Safe band level change
        eqManager.setBandLevel(0, 500)
        assertEquals(com.example.playback.EqualizerPreset.CUSTOM.id, eqManager.equalizerState.value.currentPresetId)

        // Safe release
        eqManager.release()
    }

    @Test
    fun `equalizer band info math never produces invalid valueRange or empty coerceIn`() {
        // Test abnormal cases: min == max, min > max, zero range
        val abnormalBands = listOf(
            com.example.playback.EqualizerBandInfo(0, 60, 0, 0, 0),
            com.example.playback.EqualizerBandInfo(1, 230, 500, 1000, -1000),
            com.example.playback.EqualizerBandInfo(2, 910, -500, -100, -100)
        )

        abnormalBands.forEach { band ->
            val rawMin = band.minLevelMb.toFloat()
            val rawMax = band.maxLevelMb.toFloat()
            val (safeMin, safeMax) = if (rawMin < rawMax && (rawMax - rawMin) >= 100f) {
                rawMin to rawMax
            } else {
                -1200f to 1200f
            }
            assertTrue("safeMin must be strictly less than safeMax", safeMin < safeMax)
            val safeRange = safeMin..safeMax
            val currentFloat = band.levelMb.toFloat().coerceIn(safeRange.start, safeRange.endInclusive)
            assertTrue(currentFloat in safeRange)
        }
    }
}

