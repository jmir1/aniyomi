package eu.kanade.tachiyomi.ui.main

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.app.SearchManager
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.components.DownloadedOnlyBannerBackgroundColor
import eu.kanade.presentation.components.IncognitoModeBannerBackgroundColor
import eu.kanade.presentation.components.IndexingBannerBackgroundColor
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.Migrations
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.core.Constants
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.EpisodeCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.extension.anime.api.AnimeExtensionGithubApi
import eu.kanade.tachiyomi.extension.manga.api.MangaExtensionGithubApi
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.deeplink.DeepLinkScreenType
import eu.kanade.tachiyomi.ui.deeplink.anime.DeepLinkAnimeScreen
import eu.kanade.tachiyomi.ui.deeplink.manga.DeepLinkMangaScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.isNavigationBarNeedsScrim
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import androidx.compose.ui.graphics.Color.Companion as ComposeColor

class MainActivity : BaseActivity() {

    private val sourcePreferences: SourcePreferences by injectLazy()
    private val libraryPreferences: LibraryPreferences by injectLazy()
    private val uiPreferences: UiPreferences by injectLazy()
    private val preferences: BasePreferences by injectLazy()

    private val animeDownloadCache: AnimeDownloadCache by injectLazy()
    private val downloadCache: MangaDownloadCache by injectLazy()
    private val chapterCache: ChapterCache by injectLazy()
    private val episodeCache: EpisodeCache by injectLazy()

    // To be checked by splash screen. If true then splash screen will be removed.
    var ready = false

    private var navigator: Navigator? = null

    init {
        registerSecureActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val isLaunch = savedInstanceState == null

        // Prevent splash screen showing up on configuration changes
        val splashScreen = if (isLaunch) installSplashScreen() else null

        super.onCreate(savedInstanceState)

        val didMigration = if (isLaunch) {
            Migrations.upgrade(
                context = applicationContext,
                basePreferences = preferences,
                uiPreferences = uiPreferences,
                preferenceStore = Injekt.get(),
                networkPreferences = Injekt.get(),
                sourcePreferences = sourcePreferences,
                securityPreferences = Injekt.get(),
                libraryPreferences = libraryPreferences,
                readerPreferences = Injekt.get(),
                playerPreferences = Injekt.get(),
                backupPreferences = Injekt.get(),
                trackerManager = Injekt.get(),
            )
        } else {
            false
        }

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot) {
            finish()
            return
        }

        // Draw edge-to-edge
        // TODO: replace with ComponentActivity#enableEdgeToEdge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setComposeContent {
            val incognito by preferences.incognitoMode().collectAsState()
            val downloadOnly by preferences.downloadedOnly().collectAsState()
            val indexing by downloadCache.isInitializing.collectAsState()
            val indexingAnime by animeDownloadCache.isInitializing.collectAsState()

            // Set status bar color considering the top app state banner
            val systemUiController = rememberSystemUiController()
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val statusBarBackgroundColor = when {
                indexing || indexingAnime -> IndexingBannerBackgroundColor
                downloadOnly -> DownloadedOnlyBannerBackgroundColor
                incognito -> IncognitoModeBannerBackgroundColor
                else -> MaterialTheme.colorScheme.surface
            }
            LaunchedEffect(systemUiController, statusBarBackgroundColor) {
                systemUiController.setStatusBarColor(
                    color = ComposeColor.Transparent,
                    darkIcons = statusBarBackgroundColor.luminance() > 0.5,
                    transformColorForLightContent = { ComposeColor.Black },
                )
            }

            // Set navigation bar color
            val context = LocalContext.current
            val navbarScrimColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            LaunchedEffect(systemUiController, isSystemInDarkTheme, navbarScrimColor) {
                systemUiController.setNavigationBarColor(
                    color = if (context.isNavigationBarNeedsScrim()) {
                        navbarScrimColor.copy(alpha = 0.7f)
                    } else {
                        ComposeColor.Transparent
                    },
                    darkIcons = !isSystemInDarkTheme,
                    navigationBarContrastEnforced = false,
                    transformColorForLightContent = { ComposeColor.Black },
                )
            }

            Navigator(
                screen = HomeScreen,
                disposeBehavior = NavigatorDisposeBehavior(
                    disposeNestedNavigators = false,
                    disposeSteps = true,
                ),
            ) { navigator ->

                LaunchedEffect(navigator) {
                    this@MainActivity.navigator = navigator

                    if (isLaunch) {
                        // Set start screen
                        handleIntentAction(intent, navigator)

                        // Reset Incognito Mode on relaunch
                        preferences.incognitoMode().set(false)
                    }
                }

                val scaffoldInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                Scaffold(
                    topBar = {
                        AppStateBanners(
                            downloadedOnlyMode = downloadOnly,
                            incognitoMode = incognito,
                            indexing = indexing || indexingAnime,
                            modifier = Modifier.windowInsetsPadding(scaffoldInsets),
                        )
                    },
                    contentWindowInsets = scaffoldInsets,
                ) { contentPadding ->
                    // Consume insets already used by app state banners
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .consumeWindowInsets(contentPadding),
                    ) {
                        // Shows current screen
                        DefaultNavigatorScreenTransition(navigator = navigator)
                    }
                }

                // Pop source-related screens when incognito mode is turned off
                LaunchedEffect(Unit) {
                    preferences.incognitoMode().changes()
                        .drop(1)
                        .filter { !it }
                        .onEach {
                            val currentScreen = navigator.lastItem
                            if ((
                                    currentScreen is BrowseMangaSourceScreen ||
                                        (currentScreen is MangaScreen && currentScreen.fromSource)
                                    ) ||
                                (
                                    currentScreen is BrowseAnimeSourceScreen ||
                                        (currentScreen is AnimeScreen && currentScreen.fromSource)
                                    )
                            ) {
                                navigator.popUntilRoot()
                            }
                        }
                        .launchIn(this)
                }

                HandleOnNewIntent(context = context, navigator = navigator)

                CheckForUpdates()
                ShowOnboarding()
            }

            var showChangelog by remember { mutableStateOf(didMigration && !BuildConfig.DEBUG) }
            if (showChangelog) {
                AlertDialog(
                    onDismissRequest = { showChangelog = false },
                    title = {
                        Text(
                            text = stringResource(MR.strings.updated_version, BuildConfig.VERSION_NAME),
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { openInBrowser(RELEASE_URL) }) {
                            Text(text = stringResource(MR.strings.whats_new))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showChangelog = false }) {
                            Text(text = stringResource(MR.strings.action_ok))
                        }
                    },
                )
            }
        }

        val startTime = System.currentTimeMillis()
        splashScreen?.setKeepOnScreenCondition {
            val elapsed = System.currentTimeMillis() - startTime
            elapsed <= SPLASH_MIN_DURATION || !ready && elapsed <= SPLASH_MAX_DURATION
        }
        setSplashScreenExitAnimation(splashScreen)

        if (isLaunch && libraryPreferences.autoClearItemCache().get()) {
            lifecycleScope.launchIO {
                chapterCache.clear()
                episodeCache.clear()
            }
        }

        externalPlayerResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                ExternalIntents.externalIntents.onActivityResult(result.data)
            }
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        when (val screen = navigator?.lastItem) {
            is AssistContentScreen -> {
                screen.onProvideAssistUrl()?.let { outContent.webUri = it.toUri() }
            }
        }
    }

    @Composable
    private fun HandleOnNewIntent(context: Context, navigator: Navigator) {
        LaunchedEffect(Unit) {
            callbackFlow<Intent> {
                val componentActivity = context as ComponentActivity
                val consumer = Consumer<Intent> { trySend(it) }
                componentActivity.addOnNewIntentListener(consumer)
                awaitClose { componentActivity.removeOnNewIntentListener(consumer) }
            }.collectLatest { handleIntentAction(it, navigator) }
        }
    }

    @Composable
    private fun CheckForUpdates() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        // App updates
        LaunchedEffect(Unit) {
            if (BuildConfig.INCLUDE_UPDATER) {
                try {
                    val result = AppUpdateChecker().checkForUpdate(context)
                    if (result is GetApplicationRelease.Result.NewUpdate) {
                        val updateScreen = NewUpdateScreen(
                            versionName = result.release.version,
                            changelogInfo = result.release.info,
                            releaseLink = result.release.releaseLink,
                            downloadLink = result.release.getDownloadLink(),
                        )
                        navigator.push(updateScreen)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                }
            }
        }

        // Extensions updates
        LaunchedEffect(Unit) {
            try {
                MangaExtensionGithubApi().checkForUpdates(context)
                AnimeExtensionGithubApi().checkForUpdates(context)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    @Composable
    private fun ShowOnboarding() {
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            if (!preferences.shownOnboardingFlow().get()) {
                navigator.push(OnboardingScreen())
            }
        }
    }

    /**
     * Sets custom splash screen exit animation on devices prior to Android 12.
     *
     * When custom animation is used, status and navigation bar color will be set to transparent and will be restored
     * after the animation is finished.
     */
    private fun setSplashScreenExitAnimation(splashScreen: SplashScreen?) {
        val root = findViewById<View>(android.R.id.content)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && splashScreen != null) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            splashScreen.setOnExitAnimationListener { splashProvider ->
                // For some reason the SplashScreen applies (incorrect) Y translation to the iconView
                splashProvider.iconView.translationY = 0F

                val activityAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = LinearOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        root.translationY = value * 16.dpToPx
                    }
                }

                val splashAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = FastOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        splashProvider.view.alpha = value
                    }
                    doOnEnd {
                        splashProvider.remove()
                    }
                }

                activityAnim.start()
                splashAnim.start()
            }
        }
    }

    private fun handleIntentAction(intent: Intent, navigator: Navigator): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(
                applicationContext,
                notificationId,
                intent.getIntExtra("groupId", 0),
            )
        }

        val tabToOpen = when (intent.action) {
            Constants.SHORTCUT_ANIMELIB -> HomeScreen.Tab.Animelib()
            Constants.SHORTCUT_LIBRARY -> HomeScreen.Tab.Library()
            Constants.SHORTCUT_MANGA -> {
                val idToOpen = intent.extras?.getLong(Constants.MANGA_EXTRA) ?: return false
                navigator.popUntilRoot()
                HomeScreen.Tab.Library(idToOpen)
            }
            Constants.SHORTCUT_ANIME -> {
                val idToOpen = intent.extras?.getLong(Constants.ANIME_EXTRA) ?: return false
                navigator.popUntilRoot()
                HomeScreen.Tab.Animelib(idToOpen)
            }
            Constants.SHORTCUT_UPDATES -> HomeScreen.Tab.Updates
            Constants.SHORTCUT_HISTORY -> HomeScreen.Tab.History
            Constants.SHORTCUT_SOURCES -> HomeScreen.Tab.Browse(false)
            Constants.SHORTCUT_EXTENSIONS -> HomeScreen.Tab.Browse(true)
            Constants.SHORTCUT_DOWNLOADS -> {
                navigator.popUntilRoot()
                HomeScreen.Tab.More(toDownloads = true)
            }
            Constants.SHORTCUT_ANIME_DOWNLOADS -> {
                navigator.popUntilRoot()
                HomeScreen.Tab.More(toDownloads = true)
            }
            Intent.ACTION_SEARCH, Intent.ACTION_SEND, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                // If the intent match the "standard" Android search intent
                // or the Google-specific search intent (triggered by saying or typing "search *query* on *Tachiyomi*" in Google Search/Google Assistant)

                // Get the search query provided in extras, and if not null, perform a global search with it.
                val query = intent.getStringExtra(SearchManager.QUERY)
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)

                if (!query.isNullOrEmpty()) {
                    navigator.popUntilRoot()

                    val screenType = intent.getStringExtra(INTENT_SEARCH_TYPE).orEmpty()
                        .ifBlank { "ANIME" }
                        .let(DeepLinkScreenType::valueOf)

                    when (screenType) {
                        DeepLinkScreenType.MANGA -> {
                            navigator.push(GlobalMangaSearchScreen(query))
                            navigator.push(DeepLinkMangaScreen(query))
                        }
                        DeepLinkScreenType.ANIME -> {
                            navigator.push(GlobalAnimeSearchScreen(query))
                            navigator.push(DeepLinkAnimeScreen(query))
                        }
                    }
                }
                null
            }
            INTENT_SEARCH -> { // Used by extensions (url intent handlers)
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (!query.isNullOrEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    navigator.popUntilRoot()
                    navigator.push(GlobalMangaSearchScreen(query, filter))
                }
                null
            }
            INTENT_ANIMESEARCH -> { // Same as above
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (!query.isNullOrEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    navigator.popUntilRoot()
                    navigator.push(GlobalAnimeSearchScreen(query, filter))
                }
                null
            }
            else -> return false
        }

        if (tabToOpen != null) {
            lifecycleScope.launch { HomeScreen.openTab(tabToOpen) }
        }

        ready = true
        return true
    }

    companion object {
        const val INTENT_SEARCH = "eu.kanade.tachiyomi.SEARCH"
        const val INTENT_ANIMESEARCH = "eu.kanade.tachiyomi.ANIMESEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"
        const val INTENT_SEARCH_TYPE = "type"

        private var externalPlayerResult: ActivityResultLauncher<Intent>? = null

        suspend fun startPlayerActivity(
            context: Context,
            animeId: Long,
            episodeId: Long,
            extPlayer: Boolean,
            video: Video? = null,
        ) {
            if (extPlayer) {
                val intent = try {
                    ExternalIntents.newIntent(context, animeId, episodeId, video)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                    withUIContext { Injekt.get<Application>().toast(e.message) }
                    return
                }
                externalPlayerResult?.launch(intent) ?: return
            } else {
                context.startActivity(PlayerActivity.newIntent(context, animeId, episodeId))
            }
        }
    }
}

// Splash screen
private const val SPLASH_MIN_DURATION = 500 // ms
private const val SPLASH_MAX_DURATION = 5000 // ms
private const val SPLASH_EXIT_ANIM_DURATION = 400L // ms
