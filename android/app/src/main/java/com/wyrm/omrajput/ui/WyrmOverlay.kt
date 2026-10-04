package com.wyrm.omrajput.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets as ComposeInsets
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.remember
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.wyrm.omrajput.BuildConfig
import com.wyrm.omrajput.R
import com.wyrm.omrajput.WyrmActivity
import com.wyrm.omrajput.WyrmMessagingService
import com.wyrm.omrajput.data.ApiPlayer
import com.wyrm.omrajput.data.Arena
import com.wyrm.omrajput.data.ArenaDirectory
import com.wyrm.omrajput.data.BackupState
import com.wyrm.omrajput.data.ChatMessage
import com.wyrm.omrajput.data.Conversation
import com.wyrm.omrajput.data.Hotkey
import com.wyrm.omrajput.data.LocalNotifications
import com.wyrm.omrajput.data.NotificationKind
import com.wyrm.omrajput.data.NotificationPreferences
import com.wyrm.omrajput.data.PendingRunStore
import com.wyrm.omrajput.data.ReleaseNotesRepository
import com.wyrm.omrajput.data.SavedArenas
import com.wyrm.omrajput.data.SocialCache
import com.wyrm.omrajput.data.TRAILS_ENABLED
import com.wyrm.omrajput.data.WyrmNotification
import com.wyrm.omrajput.data.preferenceKey
import com.wyrm.omrajput.data.toWyrmNotification
import com.wyrm.omrajput.data.Setting
import com.wyrm.omrajput.data.SettingType
import com.wyrm.omrajput.data.SettingsCodec
import com.wyrm.omrajput.data.TeamMessage
import com.wyrm.omrajput.data.TeamPresence
import com.wyrm.omrajput.data.TeamService
import com.wyrm.omrajput.data.TeamState
import com.wyrm.omrajput.data.UpdateState
import com.wyrm.omrajput.data.VoiceApiException
import com.wyrm.omrajput.data.VoicePreferences
import com.wyrm.omrajput.data.VoiceRepository
import com.wyrm.omrajput.data.VoiceRoom
import com.wyrm.omrajput.data.WyrmRepository
import com.wyrm.omrajput.data.googleIdToken
import com.wyrm.omrajput.data.hasStarted
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import com.wyrm.omrajput.voice.VoiceCallController
import com.wyrm.omrajput.voice.VoiceCallService
import com.wyrm.omrajput.voice.VoiceConnectionStage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.util.UUID

private data class NotificationOpen(
    val kind: String,
    val id: String,
    val actorId: String,
    /** "play": a Battledome notification's Play button (with [address]). */
    val action: String = "",
    val address: String = "",
)

/** A skin being tried from a trail: whose it is, the skin as a Skin-tab state, and the Wyrm look. */
/** [tagId]: the poster's tag as an index in [SkinCatalog.tags], -1 for none. */
private data class SkinTrial(val author: String, val skin: SkinState, val look: WyrmLookSpec, val tagId: Int = -1)

private const val STATS_RECONCILE_MS = 5L * 60L * 60L * 1000L
private const val STATS_RETRY_MS = 15L * 60L * 1000L

/**
 * Hosts Wyrm's Compose interface above the native surface.
 *
 * Compose owns Home outright — the engine draws nothing behind it — so a tap
 * here can never fall through to an old Vlither screen. The engine keeps
 * rendering and gameplay; this layer is detached from input and layout whenever
 * the engine owns the screen, so it can never stall a frame or steal a finger.
 *
 * SDL's activity is a plain [Activity], so the Compose view tree needs its
 * lifecycle, saved-state and view-model owners supplied by hand.
 */
/** How long an armed Play waits for the engine to report the port busy. */
private const val ARENA_GATE_ACK_MS = 4_000L

class WyrmOverlay(private val activity: Activity) :
    LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private var root: ComposeView? = null
    private var voiceHud: ComposeView? = null

    // Screen state. Native callbacks arrive on the engine thread and are hopped
    // to the main thread before touching any of this.
    private var nickname by mutableStateOf("")
    private var arenaLabel by mutableStateOf("Choose arena")
    private var arenaOnline by mutableStateOf(false)
    private var shown by mutableStateOf(false)
    private var insetTop by mutableStateOf(0.dp)
    private var insetBottom by mutableStateOf(0.dp)
    private val repository = WyrmRepository(activity, BuildConfig.WYRM_API_URL).also {
        TrailsStore.repository = it
        TrailImages.attach(activity)
    }
    /** The trail open on Route.TRAIL. */
    private var trailOpenId by mutableStateOf("")
    /** Where Back from a trail goes: the feed, a profile's grid, or an alert. */
    private var trailReturn by mutableStateOf(Route.TRAILS)
    /** Where the Trails studio closes to: the feed, or your profile's "New trail". */
    private var studioReturn by mutableStateOf(Route.TRAILS)
    /** Where Back from Alerts goes: a page inside the panel, or the panel closes. */
    private var notificationsReturn by mutableStateOf(Route.HOME)

    // "Share this run" and "Try this skin" (OM, 2026-09-30).
    /** The last finished run, for the lobby's Share run. Memory only; cleared when a run starts. */
    private var lastRun by mutableStateOf<LastRun?>(null)
    /** Numbers each finished run, so a late screenshot never lands on a newer one. */
    private var lastRunSeq = 0
    /** The run the Share editor was opened on. */
    private var shareRun by mutableStateOf<LastRun?>(null)
    /** "Share this skin" (Skin tab): the Share editor with the skin only, no run. */
    private var shareSkinOnly by mutableStateOf(false)
    /** A skin tried from a trail: the Skin tab shows it; nothing is worn or saved until Wear. */
    private var skinTrial by mutableStateOf<SkinTrial?>(null)

    // Help & feedback (OM, 2026-09-29).
    private var supportKind by mutableStateOf(com.wyrm.omrajput.data.SupportKind.BUG)
    private var supportReturn by mutableStateOf(Route.HELP)
    /** Your reports: back to Help, or (from an alert) out of the panel. */
    private var supportReportsReturn by mutableStateOf<Route?>(Route.HELP)
    /** Where a picked photo lands after the crop: Edit profile, or the profile itself. */
    private var photoReturn by mutableStateOf(Route.EDIT_PROFILE)
    /** Only what went wrong with the photo, shown under the grown avatar. */
    private var photoError by mutableStateOf("")

    /*
     * SDLActivity is not a ComponentActivity, so Compose's result launchers
     * (permissions, the photo picker, the system camera) had no registry and
     * crashed the Trails studio the moment it opened. This registry launches
     * through the activity; WyrmActivity hands every result back to it.
     */
    private val resultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
            input: I,
            options: androidx.core.app.ActivityOptionsCompat?,
        ) {
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            contract.getSynchronousResult(activity, input)?.let { ready ->
                main.post { dispatchResult(requestCode, ready.value) }
                return
            }
            val intent = contract.createIntent(activity, input)
            val permissions = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
            try {
                if (intent.action == permissions.ACTION_REQUEST_PERMISSIONS) {
                    activity.requestPermissions(intent.getStringArrayExtra(permissions.EXTRA_PERMISSIONS) ?: emptyArray(), requestCode)
                } else {
                    activity.startActivityForResult(intent, requestCode, options?.toBundle())
                }
            } catch (unavailable: Exception) {
                main.post { dispatchResult(requestCode, android.app.Activity.RESULT_CANCELED, null) }
            }
        }
    }
    private val resultOwner = object : androidx.activity.result.ActivityResultRegistryOwner {
        override val activityResultRegistry get() = resultRegistry
    }

    /** From WyrmActivity.onActivityResult: true when a Compose launcher asked for it. */
    fun dispatchActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean =
        resultRegistry.dispatchResult(requestCode, resultCode, data)

    /** From WyrmActivity.onRequestPermissionsResult. */
    fun dispatchPermissionResult(requestCode: Int, permissions: Array<String>, grants: IntArray): Boolean {
        val contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
        val data = android.content.Intent()
            .putExtra(contract.EXTRA_PERMISSIONS, permissions)
            .putExtra(contract.EXTRA_PERMISSION_GRANT_RESULTS, grants)
        return resultRegistry.dispatchResult(requestCode, android.app.Activity.RESULT_OK, data)
    }
    private val voiceRepository = VoiceRepository(activity, BuildConfig.WYRM_API_URL)
    private val socialCache = SocialCache(activity)
    private val voicePreferences = VoicePreferences(activity)
    private val uiPreferences = activity.getSharedPreferences(
        "wyrm_ui_preferences",
        android.content.Context.MODE_PRIVATE,
    )
    private var appTheme by mutableStateOf(
        WyrmThemeId.fromStored(uiPreferences.getString("theme", null)),
    )
    private var themeIntensity by mutableFloatStateOf(
        uiPreferences.getFloat("theme_intensity", 0.5f).coerceIn(0f, 1f),
    )
    private val statsSyncPreferences = activity.getSharedPreferences(
        "wyrm_stats_sync",
        android.content.Context.MODE_PRIVATE,
    )
    private val releaseNotes = ReleaseNotesRepository(activity)
    private val localNotifications = LocalNotifications(activity)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var authState by mutableStateOf(AuthState())
    /** Non-null while the full-screen W transition (sign-out) is showing. */
    private var sessionTransitionTitle by mutableStateOf<String?>(null)
    /** True while a restored session syncs behind the launch W. */
    private var launchSyncing by mutableStateOf(false)
    private var voiceState by mutableStateOf(VoiceScreenState(
        rooms = socialCache.voiceRooms()?.value.orEmpty(),
        updatedAt = socialCache.voiceRooms()?.savedAt ?: 0L,
    ))
    private var pendingVoicePermission: (() -> Unit)? = null
    private var voiceRetryAction: (() -> Unit)? = null
    private var voiceOperationGeneration = 0L
    private var voiceOperationJob: Job? = null

    init {
        // Apply before attach() creates either Compose surface, avoiding a
        // one-frame flash of Paper when a darker saved theme is active.
        Wyrm.applyTheme(appTheme, themeIntensity)
    }

    /** Which Compose screen the engine says we are on. */
    private var route by mutableStateOf(
        if (repository.hasSession) Route.HOME else Route.AUTH
    )
    private var skinTables by mutableStateOf(SkinTables())
    private var skinState by mutableStateOf(SkinState())

    /**
     * Whether the engine is actually drawing the skin preview yet.
     *
     * The editor is mostly transparent: the snake in the middle of it is drawn
     * by the engine, underneath Compose. Showing the controls before the engine
     * has switched screens meant a moment of editor-with-no-snake, which is the
     * flicker. It waits behind a quiet loading screen instead, and appears when
     * there is something to appear over.
     */
    private var skinReady by mutableStateOf(false)
    private var deathStats by mutableStateOf(DeathStats())
    private var autoRespawn by mutableStateOf(false)
    private var profile by mutableStateOf(socialCache.profile()?.value?.toWyrmProfile() ?: WyrmProfile())

    // Onboarding, editing and the board all talk to the same endpoints, so they
    // share one busy flag and one error slot rather than three of each.
    private var formBusy by mutableStateOf(false)
    private var formError by mutableStateOf("")
    private var googleName by mutableStateOf("")
    // The boards are an on-device cache: built by the sync at app start,
    // refreshed in the background once an hour, and only read by the
    // leaderboard screen — opening it or switching Score/Kills never fetches.
    private var scoreBoard by mutableStateOf(socialCache.leaderboard("score")?.value.orEmpty())
    private var killBoard by mutableStateOf(socialCache.leaderboard("kills")?.value.orEmpty())
    private var boardRefreshJob: Job? = null
    private val boardPlayers: List<ApiPlayer> get() = if (boardSort == LeaderboardSort.SCORE) scoreBoard else killBoard
    private var boardSort by mutableStateOf(LeaderboardSort.SCORE)
    private var boardLoading by mutableStateOf(false)
    private var boardError by mutableStateOf("")
    private var boardOffline by mutableStateOf(false)
    private var boardUpdatedAt by mutableStateOf(0L)
    private var profileLoading by mutableStateOf(false)
    private var profileOffline by mutableStateOf(false)
    private var profileUpdatedAt by mutableStateOf(socialCache.profile()?.savedAt ?: 0L)
    private var boardLoadGen = 0

    // The arena directory, and the photograph waiting to be cropped. Both are
    // owned here rather than by their screens so that leaving a screen and
    // coming back does not throw away a finished sweep or a chosen picture.
    private var arenaState by mutableStateOf(ArenaListState())
    private var savedArenas by mutableStateOf(SavedArenas.load(activity))
    private var recentArenas by mutableStateOf(com.wyrm.omrajput.data.RecentArenas.load(activity))
    private var arenaJob: Job? = null

    // The rectangle a panel grows out of, and whether it is open. The control
    // that was pressed reports its own bounds, so a screen always opens from
    // exactly where the finger landed.
    private var panelOrigin by mutableStateOf<Rect?>(null)
    private var panelOpen by mutableStateOf(false)
    private var pendingPhoto by mutableStateOf<Bitmap?>(null)

    // Settings, and the two landscape editors that hang off it.
    private var settings by mutableStateOf<List<Setting>>(emptyList())
    private var settingsVersion by mutableStateOf("")
    private var hotkeys by mutableStateOf<List<Hotkey>>(emptyList())
    private var editorSettingsSnapshot: List<Setting> = emptyList()
    private var editorHotkeysSnapshot: List<Hotkey> = emptyList()
    private var layoutEditorExitPending = false
    private var updateState by mutableStateOf(UpdateState())
    private var whatsNewState by mutableStateOf<WhatsNewState?>(null)
    private var whatsNewBackdropVisible by mutableStateOf(false)
    private var automaticWhatsNewRequested = false
    private var backupState by mutableStateOf(BackupState())
    private var backupAction by mutableStateOf<BackupAction?>(null)
    private var backupActionStartedAt = 0L
    private var safeInsets by mutableStateOf(SafeInsets())

    /*
     * The launch update prompt.
     *
     * `promptDismissed` lasts exactly as long as the app does: Later means not
     * now, not never, and the next launch asks again. Once the download has
     * started the prompt stops being dismissible — there is no sensible way to
     * un-start an install, and hiding it would leave the player wondering what
     * the phone was doing.
     */
    private var updatePromptShown by mutableStateOf(false)
    private var promptDismissed = false
    private var updateStarted by mutableStateOf(false)
    private var backupPromptShown by mutableStateOf(false)
    private var backupPromptDismissed = false
    /** True only from Restore press until its one result card is acknowledged. */
    private var backupRestoreInFlight = false

    /* The private-tag field, and whatever the tag service last said about it. */
    private var tagCode by mutableStateOf("")
    private var tagFetchState by mutableStateOf("")

    /** Where the privacy policy was opened from, so closing it goes back there. */
    private var privacyReturn by mutableStateOf(Route.AUTH)

    /** Backup is reached from Settings or from Profile; Back must honour that. */
    private var backupReturn by mutableStateOf(Route.SETTINGS)

    private var profileScoreRank by mutableStateOf<Int?>(null)
    private var profileKillRank by mutableStateOf<Int?>(null)

    // Chat, and the people in it.
    private var chatTab by mutableStateOf(ChatTab.DIRECT)
    private var globalMessages by mutableStateOf<List<ChatMessage>>(emptyList())
    /** New global messages from others since the room was last read (OM, 2026-10-04). */
    private var globalUnread by mutableStateOf(0)
    private val globalChatPrefs by lazy {
        activity.getSharedPreferences("wyrm_global_chat", android.content.Context.MODE_PRIVATE)
    }
    /** False while the engine holds an arena connection: no extra requests mid-match. */
    private var arenaPortFree = true
    private var conversations by mutableStateOf<List<Conversation>>(emptyList())
    private var following by mutableStateOf<List<ApiPlayer>>(emptyList())
    private var threadPlayer by mutableStateOf<ApiPlayer?>(null)
    private var threadMessages by mutableStateOf<List<ChatMessage>>(emptyList())
    private var draft by mutableStateOf("")
    private var sending by mutableStateOf(false)
    private var chatError by mutableStateOf("")
    private var viewedPlayer by mutableStateOf<ApiPlayer?>(null)

    /** Where Back goes from a player's profile: wherever you opened it from. */
    private var playerReturn by mutableStateOf(Route.LEADERBOARD)
    private var connections by mutableStateOf<List<ApiPlayer>>(emptyList())
    private var followers by mutableStateOf<List<ApiPlayer>>(emptyList())
    private var connectionsFollowers by mutableStateOf<List<ApiPlayer>>(emptyList())
    private var connectionsFollowing by mutableStateOf<List<ApiPlayer>>(emptyList())
    private var connectionsKind by mutableStateOf("followers")
    private var connectionsPlayerId by mutableStateOf("")
    private var connectionsReturn by mutableStateOf(Route.PROFILE)
    private var connectionsLoading by mutableStateOf(false)
    private var renameAllowance by mutableStateOf<com.wyrm.omrajput.data.RenameAllowance?>(null)
    private var chatJob: Job? = null

    /** Refreshes the running interface as soon as its data-only push arrives. */
    private val pushReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WyrmMessagingService.ACTION_PUSH_RECEIVED) return
            val kind = intent.getStringExtra("kind")
            if (kind == "dm") refreshUnreadDmCount()
            else refreshNotifications()
            if (kind == "support") com.wyrm.omrajput.data.SupportStore.refresh()
        }
    }
    private var pushReceiverRegistered = false

    // The invite sheet, drawn over the thread it was opened from. It reuses
    // the same arena directory the Arena picker searches, because "which
    // server" already means the same thing there.
    private var inviteSheetOpen by mutableStateOf(false)
    private var inviteQuery by mutableStateOf("")
    private var inviteNote by mutableStateOf("")
    private var inviteSelectedServer by mutableStateOf("")
    private var inviteSending by mutableStateOf(false)

    // Team Mode. The service is not ours and is asked once every four seconds,
    // whether or not this screen is open — teammates have to appear on the map
    // during a match, which is the only time it matters.
    private val team = TeamService(activity)
    private val pendingRuns = PendingRunStore(activity)
    private var teamState by mutableStateOf(TeamState())
    private var teamMessages by mutableStateOf<List<TeamMessage>>(emptyList())
    private var teamTab by mutableStateOf(TeamTab.TEAM)
    private var teamDraft by mutableStateOf("")
    private var teamSending by mutableStateOf(false)
    private var teamOutbox: String? = null
    private var teamJob: Job? = null
    /** The master switch for every NTL service, kept beside the roster it feeds. */
    private var teamEnabled by mutableStateOf(true)
    private var runUploadJob: Job? = null
    private var statsSyncJob: Job? = null
    private var packedTeamSnapshot = ""
    private var teamProfiles by mutableStateOf<List<com.wyrm.omrajput.data.TeamProfile>>(emptyList())
    private var teamActive by mutableStateOf(0)
    private var teamAdding by mutableStateOf(false)

    /**
     * How long the bot keeps the snake after chat closes.
     *
     * Kept on the phone rather than in the engine's settings file, which has a
     * fixed layout and a migration for every field added to it.
     */
    private var handoverSeconds by mutableStateOf(
        activity.getSharedPreferences("wyrm_team_mode", android.content.Context.MODE_PRIVATE)
            .getInt("handover_seconds", 5)
    )
    private var chatReturn by mutableStateOf(Route.HOME)
    /** A thread opened from a profile's Message goes back to that profile. */
    private var threadReturn by mutableStateOf(Route.CHAT)

    /** The panel behind Home's bell — an operator's broadcasts, newest first. */
    private var notifications by mutableStateOf<List<WyrmNotification>>(emptyList())
    private var notificationsAllowed by mutableStateOf(NotificationPreferences.systemEnabled(activity))
    private var enabledNotificationKinds by mutableStateOf(
        NotificationPreferences.enabledKinds(activity)
    )
    private var highlightedNotification by mutableStateOf<String?>(null)
    private var eventGate by mutableStateOf<WyrmNotification?>(null)
    private var enteringArena by mutableStateOf(false)
    /** The root tab bar folded into its circle, as iOS minimizes it on scroll. */
    private var rootBarCollapsed by mutableStateOf(false)
    private var betaUpdates by mutableStateOf(com.wyrm.omrajput.UpdateChannel.isBetaEnabled(activity))
    private var arenaNativePortBusySeen = false
    /* Compose state, not a plain field: the Play button reads it. As a plain
       field its release changed nothing Compose could see, so after a match the
       lobby kept drawing ENTERING although the gate had already opened. */
    private var arenaProbeGatePending by mutableStateOf(false)
    private var arenaGateWatchdog: Job? = null
    private var lobbyQuickSettings by mutableStateOf(false)
    private var lobbyPing by mutableStateOf(0)
    private val lobbyArenaState = mutableStateOf<Arena?>(null)
    /** The lobby's arena; its number also labels the Near Original minimap. */
    private var lobbyArena: Arena?
        get() = lobbyArenaState.value
        set(value) {
            lobbyArenaState.value = value
            NearOriginalStore.applyServer(value?.id ?: 0)
        }
    private var lobbyJob: Job? = null
    /** Index into the background table; the engine holds the same order. */
    private var arenaBackground by mutableStateOf(0)
    /** Earned during a run, held until Home can give each one its own impact. */
    private var pendingAchievements by mutableStateOf<List<WyrmNotification>>(emptyList())
    private var lastBackupReceiptStatus = 0
    private var sessionReady = repository.hasSession && profile.id.isNotBlank()
    private var activityResumed = false
    private var pendingNotificationOpen: NotificationOpen? = null
    private var gameModeEligible = false

    /** The count behind the dot on Home's Chat row — unread direct messages, summed across every thread. */
    private var unreadDmCount by mutableStateOf(0L)
    /** The active root tab — drill-ins collapse back here. */
    private var tabRoot by mutableStateOf(Route.HOME)
    private var skinEditTab by mutableStateOf(0)
    private var controlsWorkspaceTab by mutableStateOf(ControlsWorkspaceTab.CONTROLS)

    enum class Route {
        AUTH, ONBOARDING, HOME, SOCIAL, SKIN, SKIN_EDIT, SKIN_PATTERN, SKIN_ACCESSORY, SKIN_TAG,
        SKIN_BACKGROUND, DEATH, PROFILE, EDIT_PROFILE, LEADERBOARD, ARENA,
        CROP_PHOTO, SETTINGS, SETTINGS_GENERAL, SETTINGS_ASSIST, SETTINGS_NORMAL,
        SETTINGS_CONTROLS, SETTINGS_BUTTONS, SETTINGS_BOT, SETTINGS_NOTIFICATIONS,
        SETTINGS_ACCESSIBILITY, SETTINGS_FOOD, SETTINGS_PERFORMANCE, SETTINGS_BACKUP,
        SETTINGS_UPDATES, CONTROL_LAYOUT, ON_SCREEN_BUTTON_LAYOUT, ARENA_HUD_LAYOUT, BACKGROUND_SIZE_EDITOR,
        CHAT, THREAD, PLAYER, CONNECTIONS, TEAM, VOICE, ARENA_CHAT, PRIVACY, NOTIFICATIONS,
        GUEST_SIGN_UP, GUEST_LOG_IN, LOBBY, ABOUT, TRAILS, TRAIL, TRAIL_STUDIO,
        HELP, SUPPORT_COMPOSE, SUPPORT_REPORTS, TRAIL_SHARE;

        /**
         * Whether this screen was reached from a row on Home.
         *
         * Everything that was — and everything reachable from it without going
         * back to Home — is drawn inside the panel that grew out of that row.
         * The ones that are not are the ones Home cannot open: the sign-in
         * before there is a Home, the death card, and the chat panel — both of
         * which arrive mid-match, over an arena that is still being played.
         * The privacy policy is on that list too, because it is read from the
         * sign-in screen, before there is a Home to have grown from.
         *
         * The lobby is on it for the same reason as the death card: it is a
         * full landscape surface rather than a panel grown out of a portrait
         * row. Left off, it was wrapped in a panel that was never opened, so it
         * rotated and then drew nothing — and pressing the row again animated
         * that empty panel, which is the popup that appeared instead.
         */
        val growsFromHome: Boolean
            get() = this !in setOf(
                // Trails is a tab and Alerts opens from Home's bell (OM, 2026-10-04).
                AUTH, ONBOARDING, HOME, SOCIAL, SKIN, SETTINGS, TRAILS, DEATH, ARENA_CHAT, PRIVACY, LOBBY,
                CONTROL_LAYOUT, ON_SCREEN_BUTTON_LAYOUT, ARENA_HUD_LAYOUT, BACKGROUND_SIZE_EDITOR,
                GUEST_SIGN_UP, GUEST_LOG_IN,
                // Share run is opened from the lobby, a full page of its own like it.
                TRAIL_SHARE,
            )
    }

    /** What the interface asks the engine to do. Implemented on the Java side. */
    interface Host {
        fun onEnterArena(nickname: String, address: String, attemptId: Long)
        fun onEnterAiMode(nickname: String)
        fun onEnterAiLayoutEditor(nickname: String)
        fun onExitAiLayoutEditor()
        fun onToggleEditorLeaderboard()
        /** The background-size editor: the engine draws only its map and board, assist off. */
        fun onSetEditorBare(bare: Boolean)
        fun onSetNickname(nickname: String)
        fun onOpenLobby()
        fun onLeaveLobby()
        fun onSelectArena(address: String)
        fun onArenaTheme(colours: IntArray, dark: Boolean)
        fun onPickPhoto()
        fun onOpenSkinEditor()

        /** The settings bridge. The engine describes, Compose renders. */
        fun onReadSettings(): String
        fun onReadHotkeys(): String
        fun onReadKeyOptions(): String
        fun onReadSettingsVersion(): String
        fun onSetAutoRespawn(on: Boolean)
        fun onWriteSetting(id: String, values: FloatArray)
        fun onWriteHotkey(action: Int, key: Int, mode: Int, visible: Boolean, x: Float, y: Float)
        fun onSettingsAction(action: Int)
        fun onRequestLandscape(landscape: Boolean)
        fun onGameModeEligible(eligible: Boolean)
        fun onReadPresence(): String
        fun onWriteTeamMembers(packed: String)
        /** The team chat for the arena's chat window: a total, then `author<TAB>body` lines. */
        fun onWriteTeamChat(packed: String)
        fun onCloseTeamChat(seconds: Float)
        fun onReadUpdate(): String
        fun onUpdateAction(action: Int)
        fun onBackupAction(action: Int)

        fun onSkinPreset(index: Int)
        /** [colours] is packed ARGB per code position; 0 keeps the palette. */
        fun onSkinCode(code: String, colours: IntArray)
        fun onSkinAccessory(id: Int)

        /** Index into the background table shared with the engine. */
        fun onSkinBackground(index: Int)
        fun onSkinCommit()
        fun onDeathPlay()
        fun onDeathHome()
        fun onSkinLayout(
            previewCentreY: Float,
            previewScale: Float,
            accessoryX: Float,
            accessoryY: Float,
            accessoryCell: Float,
            accessoryGap: Float,
        )

        /** Paper Skin tab: two-row engine snake in the preview card hole. */
        fun onSkinPostcard(on: Boolean)

        /** Asks the engine to publish the worn skin (and to save it, if [save]). */
        fun onSkinSync(save: Boolean) {}
    }

    var host: Host? = null
        set(value) {
            field = value
            value?.onGameModeEligible(gameModeEligible)
            publishArenaTheme(value)
        }

    fun attach() {
        AvatarImages.init(activity)
        // Stale-while-revalidate stores and Help & feedback (OM, 2026-09-29).
        TrailsStore.cache = socialCache
        com.wyrm.omrajput.data.BadgeStore.repository = repository
        com.wyrm.omrajput.data.BadgeStore.cache = socialCache
        com.wyrm.omrajput.data.SupportStore.attach(activity, repository, socialCache)
        savedStateController.performAttach()
        savedStateController.performRestore(Bundle())
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        // Compose resolves its window recomposer by walking up from the root of
        // the window, not from the ComposeView, so the decor view needs the
        // owners too.
        activity.window.decorView.apply {
            setViewTreeLifecycleOwner(this@WyrmOverlay)
            setViewTreeSavedStateRegistryOwner(this@WyrmOverlay)
            setViewTreeViewModelStoreOwner(this@WyrmOverlay)
        }

        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(this@WyrmOverlay)
            setViewTreeSavedStateRegistryOwner(this@WyrmOverlay)
            setViewTreeViewModelStoreOwner(this@WyrmOverlay)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // Paper launch is up immediately so the engine's ImGui title and
            // green veil never flash. Hidden only while a match owns the
            // surface (setInterfaceVisible(false)).
            visibility = View.VISIBLE
            setContent {
                WyrmTheme {
                    if (!shown) {
                        WyrmDesignLaunch()
                        return@WyrmTheme
                    }
                    if (launchSyncing) {
                        WyrmSessionTransition("Syncing your Wyrm…")
                        return@WyrmTheme
                    }
                    val voiceCall by VoiceCallController.state.collectAsState()
                    val eligible = sessionReady && profile.username.isNotBlank() && route !in setOf(
                        Route.AUTH,
                        Route.ONBOARDING,
                        Route.GUEST_SIGN_UP,
                        Route.GUEST_LOG_IN,
                    )
                    LaunchedEffect(eligible) {
                        gameModeEligible = eligible
                        host?.onGameModeEligible(eligible)
                    }
                    LaunchedEffect(sessionReady, route) {
                        if (sessionReady && route == Route.HOME) maybeShowAutomaticWhatsNew()
                    }
                    val backgroundBlur by animateDpAsState(
                        targetValue = if (whatsNewBackdropVisible) 11.dp else 0.dp,
                        animationSpec = tween(240),
                        label = "whats-new-background-blur",
                    )
                    Box(modifier = Modifier.fillMaxSize().blur(backgroundBlur)) {
                    Grown(route) { shownRoute ->
                    when (shownRoute) {
                        Route.AUTH, Route.GUEST_SIGN_UP, Route.GUEST_LOG_IN -> CinematicAuthScreen(
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            checkUsername = { name ->
                                runCatching { repository.usernameAvailable(name) }.fold(
                                    onSuccess = { if (it) UsernameAvailability.AVAILABLE else UsernameAvailability.TAKEN },
                                    onFailure = { UsernameAvailability.UNAVAILABLE },
                                )
                            },
                            signUp = { username, password ->
                                // The backend wants a display name; the username is an
                                // honest default and stays editable from Profile.
                                runCatching { repository.guestSignUp(username, username, password) }.fold(
                                    onSuccess = { acceptPlayer(it); null },
                                    onFailure = ::friendlyAuthError,
                                )
                            },
                            logIn = { username, password ->
                                runCatching { repository.logIn(username, password) }.fold(
                                    onSuccess = { acceptPlayer(it); null },
                                    onFailure = ::friendlyAuthError,
                                )
                            },
                            bootstrap = { restoreAccountSettings(); bootstrapSession() },
                            onComplete = {
                                tabRoot = Route.HOME
                                route = Route.HOME
                            },
                            onPrivacy = {
                                privacyReturn = Route.AUTH
                                route = Route.PRIVACY
                            },
                        )

                        Route.PRIVACY -> PrivacyScreen(
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            backLabel = if (privacyReturn == Route.AUTH) "Back" else "Settings",
                            onBack = { route = privacyReturn },
                        )

                        Route.HOME -> PlayHome(showRootTabs = false)

                        Route.SOCIAL -> SocialHome(showRootTabs = false)

                        Route.LOBBY -> LobbyScreen(
                            address = arenaLabel,
                            serverId = lobbyArena?.id ?: if (arenaLabel.isBlank()) -1 else -2,
                            cluster = lobbyArena?.cluster ?: -1,
                            nickname = nickname,
                            entering = enteringArena || arenaProbeGatePending,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onNicknameChange = ::chooseNickname,
                            onPlayAi = {
                                if (!arenaProbeGatePending) {
                                    lobbyJob?.cancel()
                                    arenaJob?.cancel()
                                    armArenaGate()
                                    enteringArena = false
                                    host?.onEnterAiMode(nickname)
                                }
                            },
                            onPlay = { requestArenaEntry(arenaLabel) },
                            onHome = ::leaveLobby,
                            canShareRun = TRAILS_ENABLED && lastRun != null && repository.hasSession,
                            onShareRun = ::openShareRun,
                            lastRun = lastRun,
                        )

                        Route.NOTIFICATIONS -> NotificationsScreen(
                            notifications = visibleNotifications(),
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            showRootTabs = false,
                            onBack = {
                                if (notificationsReturn.growsFromHome) route = notificationsReturn else panelOpen = false
                            },
                            onMarkAllRead = ::markAllNotificationsRead,
                            onOpenNotification = ::openNotificationCard,
                            onSetNotificationRead = ::setNotificationRead,
                            onDeleteNotification = ::deleteNotification,
                            onJoinEvent = ::joinBattledomeEvent,
                            onOpenUpdate = {
                                backupReturn = Route.SETTINGS
                                pollTransferState()
                                route = Route.SETTINGS_UPDATES
                            },
                            onOpenLeaderboard = ::openLeaderboard,
                            onOpenBackup = {
                                backupReturn = Route.SETTINGS
                                pollTransferState()
                                route = Route.SETTINGS_BACKUP
                            },
                            onTabSocial = {
                                tabRoot = Route.SOCIAL
                                route = Route.SOCIAL
                            },
                            onTabPlay = {
                                tabRoot = Route.HOME
                                route = Route.HOME
                            },
                            onTabSkin = { openSkinTab() },
                            onTabSettings = { openSettingsTab() },
                            highlightId = highlightedNotification,
                            onHighlightConsumed = { highlightedNotification = null },
                            onRefresh = { done ->
                                scope.launch {
                                    runCatching { repository.notifications() }
                                        .onSuccess { rows -> mergeNotifications(rows.map { it.toWyrmNotification() }) }
                                    done()
                                }
                            },
                        )

                        Route.ARENA -> IosArenaPicker(
                            state = arenaState,
                            saved = savedArenas,
                            recent = recentArenas,
                            selection = featuredArena()?.endpoint ?: arenaLabel,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onClose = { panelOpen = false },
                            onSelect = ::selectArena,
                            onSaveCustom = { address ->
                                saveArena(address)
                                selectArena(address, close = false)
                            },
                        )

                        Route.SKIN -> SkinHome(showRootTabs = false)

                        Route.SKIN_EDIT -> if (!skinReady) {
                            LoadingScreen(
                                title = "SKIN",
                                insetTop = insetTop,
                                insetBottom = insetBottom,
                                onBack = { panelOpen = false },
                            )
                        } else SkinEditorScreen(
                            tables = skinTables,
                            state = skinState,
                            settings = settings,
                            tagCode = tagCode,
                            tagFetchState = tagFetchState,
                            onSettingChange = ::writeSetting,
                            onTagCodeChange = { tagCode = it },
                            onFetchTag = ::fetchTag,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = {
                                host?.onSkinCommit()
                                panelOpen = false
                            },
                            onPickPreset = { host?.onSkinPreset(it) },
                            onCodeChange = { code, colours ->
                                host?.onSkinCode(code, colours)
                            },
                            onPickAccessory = { host?.onSkinAccessory(it) },
                            background = arenaBackground,
                            onPickBackground = {
                                arenaBackground = it
                                host?.onSkinBackground(it)
                            },
                            onPreviewLayout = { centreY, scale ->
                                previewCentreY = centreY
                                previewScale = scale
                                pushSkinLayout()
                            },
                            onAccessoryLayout = { x, y, cell, gap ->
                                accessoryX = x
                                accessoryY = y
                                accessoryCell = cell
                                accessoryGap = gap
                                pushSkinLayout()
                            },
                            // Wearing the skin is also leaving with it. This
                            // used to commit and stay, and since committing
                            // tells the engine to stop drawing the preview, the
                            // editor was left standing there empty.
                            onWear = {
                                host?.onSkinCommit()
                                panelOpen = false
                            },
                            startTab = skinEditTab,
                        )

                        Route.SKIN_PATTERN -> SkinPatternScreen(
                            tables = skinTables,
                            state = skinState,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                            onCodeChange = { code, colours -> host?.onSkinCode(code, colours) },
                            onWear = {
                                host?.onSkinCommit()
                                panelOpen = false
                            },
                        )

                        Route.SKIN_ACCESSORY -> SkinAccessoryScreen(
                            worn = skinState.accessory,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                            onPick = { host?.onSkinAccessory(it) },
                            onWear = {
                                host?.onSkinCommit()
                                panelOpen = false
                            },
                        )

                        Route.SKIN_TAG -> SkinTagScreen(
                            settings = settings,
                            tagCode = tagCode,
                            tagFetchState = tagFetchState,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                            onSettingChange = ::writeSetting,
                            onTagCodeChange = { tagCode = it },
                            onFetchTag = ::fetchTag,
                        )

                        Route.SKIN_BACKGROUND -> SkinBackgroundScreen(
                            selected = arenaBackground,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                            onPick = {
                                arenaBackground = it
                                host?.onSkinBackground(it)
                            },
                            onWear = {
                                host?.onSkinCommit()
                                panelOpen = false
                            },
                        )

                        Route.ONBOARDING -> OnboardingScreen(
                            suggestedName = googleName.ifEmpty { profile.displayName },
                            busy = formBusy,
                            error = formError,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onFinish = ::completeOnboarding,
                        )

                        Route.PROFILE -> IosProfileScreen(
                            own = true,
                            playerId = profile.id.takeIf { it != "local" }.orEmpty(),
                            displayName = profile.displayName.ifBlank { "Wyrm" },
                            handle = if (profile.username.isBlank()) "" else "@${profile.username}",
                            bio = profile.bio,
                            avatarUrl = profile.avatarUrl,
                            avatarKey = profile.avatarKey,
                            score = profile.highestScore,
                            kills = profile.kills,
                            followers = profile.followerCount,
                            following = profile.followingCount,
                            isFollowing = false,
                            followsYou = false,
                            canMessage = false,
                            refreshing = profileLoading,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                            onEdit = ::openEditProfile,
                            onRefresh = { refreshProfile(force = true) },
                            onFollowers = {
                                viewedPlayer = null
                                openConnections(profile.id, "followers")
                            },
                            onFollowing = {
                                viewedPlayer = null
                                openConnections(profile.id, "following")
                            },
                            onToggleFollow = {},
                            onMessage = {},
                            onOpenTrail = ::openTrail,
                            onNewTrail = {
                                if (TRAILS_ENABLED) {
                                    studioReturn = Route.PROFILE
                                    route = Route.TRAIL_STUDIO
                                }
                            },
                            onShare = ::shareProfile,
                            onChangePhoto = {
                                photoError = ""
                                formError = ""
                                photoReturn = Route.PROFILE
                                host?.onPickPhoto()
                            },
                            onRemovePhoto = {
                                photoError = ""
                                removePhoto()
                            },
                            photoBusy = formBusy,
                            photoError = photoError,
                        )

                        Route.EDIT_PROFILE -> IosEditProfileScreen(
                            displayName = profile.displayName,
                            ingameName = profile.ingameName,
                            username = profile.username,
                            bio = profile.bio,
                            avatarUrl = profile.avatarUrl,
                            avatarKey = profile.avatarKey,
                            renames = renameAllowance?.let { "Renames left this month: display name ${it.displayName} · username ${it.username}" }.orEmpty(),
                            busy = formBusy,
                            error = formError,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { route = Route.PROFILE },
                            onSave = { name, arena, user, about ->
                                saveProfile(name, user, about, profile.avatarKey, arena)
                            },
                            onPickPhoto = {
                                formError = ""
                                photoReturn = Route.EDIT_PROFILE
                                host?.onPickPhoto()
                            },
                            onRemovePhoto = ::removePhoto,
                            onDeleteAccount = ::deleteAccount,
                        )

                        Route.CROP_PHOTO -> {
                            val photo = pendingPhoto
                            if (photo == null) {
                                LoadingScreen(
                                    title = "PHOTO",
                                    insetTop = insetTop,
                                    insetBottom = insetBottom,
                                    onBack = { route = photoReturn },
                                )
                            } else {
                                PhotoCropScreen(
                                    photo = photo,
                                    busy = formBusy,
                                    error = formError,
                                    insetTop = insetTop,
                                    insetBottom = insetBottom,
                                    onCancel = {
                                        pendingPhoto = null
                                        route = photoReturn
                                    },
                                    onConfirm = ::uploadPhoto,
                                )
                            }
                        }

                        Route.LEADERBOARD -> IosLeaderboardScreen(
                            score = scoreBoard,
                            kills = killBoard,
                            sort = if (boardSort == LeaderboardSort.KILLS) 1 else 0,
                            refreshing = boardLoading,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onSort = { boardSort = if (it == 1) LeaderboardSort.KILLS else LeaderboardSort.SCORE },
                            onRefresh = { loadLeaderboard(boardSort, force = true) },
                            onBack = { panelOpen = false },
                            onOpenPlayer = ::openPlayer,
                        )

                        Route.CHAT -> if (chatTab == ChatTab.GLOBAL) {
                            IosGlobalChatScreen(
                                messages = globalMessages,
                                meId = profile.id,
                                draft = draft,
                                sending = sending,
                                error = chatError,
                                insetTop = insetTop,
                                insetBottom = insetBottom,
                                onDraftChange = { draft = it },
                                onSend = ::sendGlobal,
                                onBack = ::leaveChat,
                                onOpenAuthor = ::openPlayer,
                                onReport = { message, reason -> reportMessage(message, reason) },
                            )
                        } else {
                            IosMessagesScreen(
                                conversations = conversations,
                                candidates = (followers + following).filter { it.canMessage }.distinctBy { it.id },
                                refreshing = false,
                                insetTop = insetTop,
                                insetBottom = insetBottom,
                                onRefresh = {
                                    scope.launch {
                                        runCatching { repository.conversations() }.onSuccess {
                                            conversations = it
                                            unreadDmCount = it.sumOf { row -> row.unread }
                                        }
                                        refreshFollowing()
                                    }
                                },
                                onBack = ::leaveChat,
                                onOpenThread = ::openThread,
                            )
                        }

                        Route.THREAD -> IosThreadScreen(
                            title = threadPlayer?.displayName ?: "Message",
                            messages = threadMessages,
                            meId = profile.id,
                            draft = draft,
                            sending = sending,
                            error = chatError,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onDraftChange = { draft = it },
                            onSend = ::sendDirect,
                            onBack = {
                                chatJob?.cancel()
                                draft = ""
                                if (threadReturn == Route.PLAYER) {
                                    route = Route.PLAYER
                                } else {
                                    chatTab = ChatTab.DIRECT
                                    route = Route.CHAT
                                    pollChat()
                                }
                            },
                        )

                        Route.PLAYER -> {
                            val other = viewedPlayer
                            IosProfileScreen(
                                own = false,
                                playerId = other?.id.orEmpty(),
                                displayName = other?.displayName ?: "Player",
                                handle = other?.handle.orEmpty(),
                                bio = other?.bio.orEmpty(),
                                avatarUrl = other?.avatarUrl.orEmpty(),
                                avatarKey = other?.avatarKey ?: "mono-ink",
                                score = other?.highestScore ?: 0,
                                kills = other?.kills ?: 0,
                                followers = other?.followerCount ?: 0,
                                following = other?.followingCount ?: 0,
                                isFollowing = other?.isFollowing == true,
                                followsYou = other?.followsYou == true,
                                canMessage = other?.canMessage == true,
                                refreshing = false,
                                insetTop = insetTop,
                                insetBottom = insetBottom,
                                onBack = {
                                    route = playerReturn
                                    if (route == Route.CHAT) pollChat()
                                },
                                onEdit = {},
                                onRefresh = { other?.let { refreshPlayer(it.id) } },
                                onFollowers = { other?.let { openConnections(it.id, "followers") } },
                                onFollowing = { other?.let { openConnections(it.id, "following") } },
                                onToggleFollow = ::toggleFollow,
                                onMessage = {
                                    other?.takeIf { it.canMessage }?.let { person ->
                                        openThread(person)
                                        threadReturn = Route.PLAYER
                                    }
                                },
                                onOpenTrail = ::openTrail,
                                onNewTrail = {},
                                onShare = {},
                                onChangePhoto = {},
                                onRemovePhoto = {},
                                ready = other != null,
                                followBusy = formBusy,
                            )
                        }

                        Route.CONNECTIONS -> IosConnectionsScreen(
                            followers = connectionsFollowers,
                            following = connectionsFollowing,
                            initialPage = if (connectionsKind == "following") 1 else 0,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { route = connectionsReturn },
                            onOpenPlayer = ::openPlayer,
                        )

                        Route.TEAM -> TeamScreen(
                            enabled = teamEnabled,
                            onEnabled = { on ->
                                teamEnabled = on
                                // Switching off has to leave the overlay and
                                // the roster empty, not merely stop refreshing
                                // a roster that stays on screen looking live.
                                if (!on) {
                                    teamState = TeamState()
                                    teamMessages = emptyList()
                                    host?.onWriteTeamMembers("")
                                }
                            },
                            state = teamState,
                            tab = teamTab,
                            messages = teamMessages,
                            myArena = arenaLabel,
                            myName = profile.displayName.ifBlank { nickname },
                            draft = teamDraft,
                            sending = teamSending,
                            backLabel = settingsBackLabel(),
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            teams = teamProfiles,
                            activeTeam = teamActive,
                            adding = teamAdding,
                            onTabChange = { teamTab = it },
                            onBack = { panelOpen = false },
                            onSelectTeam = ::selectTeam,
                            onAddTeam = ::addTeam,
                            onStartAdding = { teamAdding = it },
                            onForget = {
                                team.removeTeam(teamActive)
                                teamMessages = emptyList()
                                teamState = TeamState()
                                clearPackedTeam()
                                refreshTeams()
                            },
                            onJoinArena = { address ->
                                beginArenaEntry(address)
                            },
                            onDraftChange = { teamDraft = it },
                            onSend = ::sendTeamMessage,
                        )

                        Route.VOICE -> VoiceChatScreen(
                            state = voiceState,
                            call = voiceCall,
                            backLabel = settingsBackLabel(),
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = ::voiceBack,
                            onRefresh = ::refreshVoiceRooms,
                            onOpenCreate = ::openVoiceCreate,
                            onOpenRoom = ::openVoiceRoom,
                            onStartVerification = ::startVoiceVerification,
                            onConfirmVerification = ::confirmVoiceVerification,
                            onResendVerification = ::resendVoiceVerification,
                            onCreateRoom = ::createVoiceRoom,
                            onJoinRoom = ::joinVoiceRoom,
                            onToggleMineGate = { toggleVoiceGate(it) },
                            onRevealPassword = ::revealVoicePassword,
                            onRegeneratePassword = ::regenerateVoicePassword,
                            onToggleGate = ::toggleVoiceGate,
                            onDeleteRoom = ::deleteVoiceRoom,
                            onKick = { moderateVoiceParticipant(it, false) },
                            onBan = { moderateVoiceParticipant(it, true) },
                            onUnban = ::unbanVoiceParticipant,
                            onOpenProfile = ::openPlayer,
                            onToggleMute = { VoiceCallService.toggleMute(activity) },
                            onToggleDeafen = { VoiceCallService.toggleDeafen(activity) },
                            onLeaveCall = ::leaveVoiceCall,
                            onVolume = { VoiceCallService.setVolume(activity, it) },
                            onRoute = { VoiceCallService.setRoute(activity, it) },
                            onCancelOperation = ::cancelVoiceOperation,
                            onRetryOperation = ::retryVoiceOperation,
                            onVerify = { voiceState = voiceState.copy(page = VoicePage.VERIFY_EMAIL, error = "") },
                        )

                        Route.SETTINGS -> SettingsHome(showRootTabs = false)

                        Route.SETTINGS_GENERAL -> SettingsDisplayScreen(
                            settings = settings,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                            onChange = ::writeSetting,
                        )

                        Route.SETTINGS_NORMAL, Route.SETTINGS_ASSIST -> SettingsAssistScreen(
                            settings = settings,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            backLabel = settingsBackLabel(),
                            onBack = { panelOpen = false },
                            onChange = ::writeSetting,
                            onAdjustBackground = { openBackgroundSizeEditor(route) },
                        )

                        Route.BACKGROUND_SIZE_EDITOR -> ArenaBackgroundSizeEditor(
                            scale = value("normal.bg_scale", DEFAULT_BG_SCALE),
                            minimap = layoutPosition("hud.minimap_x", "hud.minimap_y"),
                            leaderboard = layoutPosition("hud.leaderboard_x", "hud.leaderboard_y"),
                            minimapSize = value("general.minimap_size", 300f),
                            leaderboardFont = value("general.lb_font", 1f).toInt(),
                            safeInsets = SafeInsets(),
                            onScale = ::setBackgroundScale,
                            onReset = { setBackgroundScale(DEFAULT_BG_SCALE) },
                            onSave = { closeEditor(backgroundEditorReturn, save = true) },
                            onCancel = { closeEditor(backgroundEditorReturn, save = false) },
                        )

                        Route.SETTINGS_BOT -> SettingsBotScreen(
                            settings = settings,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                            onChange = ::writeSetting,
                        )

                        // Both Controls hosts carry the adjust preview card over the page.
                        Route.SETTINGS_CONTROLS -> Box(Modifier.fillMaxSize()) { if (tabRoot != Route.HOME) {
                            ControlsScreen(
                                settings = settings,
                                insetTop = insetTop,
                                insetBottom = insetBottom,
                                backLabel = settingsBackLabel(),
                                onBack = { panelOpen = false },
                                onChange = ::writeSetting,
                                onEditLayout = { openEditor(Route.CONTROL_LAYOUT) },
                                onResetLayout = { resetOrientationLayout(listOf(2)) },
                                portraitPlay = com.wyrm.omrajput.data.PlayOrientation.portrait,
                                onPortraitPlay = ::switchPlayOrientation,
                            )
                        } else SettingsDrillScaffold(
                            title = "Controls",
                            parent = "Play",
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                            sectionTabs = {
                                ControlsWorkspaceTabs(
                                    selected = controlsWorkspaceTab,
                                    onSelect = { controlsWorkspaceTab = it },
                                )
                            },
                        ) {
                          AnimatedContent(
                            targetState = controlsWorkspaceTab,
                            // The local editor hands this screen back while its Vulkan frame is
                            // still alive. Keep a fully opaque surface through both halves of
                            // the tab transition so that frame can never flash through.
                            modifier = Modifier.fillMaxWidth().background(Wyrm.Paper),
                            transitionSpec = {
                                if (targetState.ordinal > initialState.ordinal) {
                                    (fadeIn(tween(180)) + slideInHorizontally(
                                        tween(260, easing = FastOutSlowInEasing),
                                    ) { it / 5 }) togetherWith
                                        (fadeOut(tween(130)) + slideOutHorizontally(tween(220)) { -it / 6 })
                                } else {
                                    (fadeIn(tween(180)) + slideInHorizontally(
                                        tween(260, easing = FastOutSlowInEasing),
                                    ) { -it / 5 }) togetherWith
                                        (fadeOut(tween(130)) + slideOutHorizontally(tween(220)) { it / 6 })
                                }
                            },
                            label = "controls-workspace",
                        ) { section ->
                            when (section) {
                                ControlsWorkspaceTab.CONTROLS -> ControlsScreen(
                                    settings = settings,
                                    insetTop = insetTop,
                                    insetBottom = insetBottom,
                                    backLabel = "Play",
                                    onBack = { panelOpen = false },
                                    onChange = ::writeSetting,
                                    onEditLayout = { openEditor(Route.CONTROL_LAYOUT) },
                                    onResetLayout = { resetOrientationLayout(listOf(2)) },
                                    workspaceTab = section,
                                    onWorkspaceTab = { controlsWorkspaceTab = it },
                                    contentOnly = true,
                                    portraitPlay = com.wyrm.omrajput.data.PlayOrientation.portrait,
                                    onPortraitPlay = ::switchPlayOrientation,
                                )
                                ControlsWorkspaceTab.BUTTONS -> SettingsButtonsScreen(
                                    buttons = hotkeys,
                                    settings = settings,
                                    insetTop = insetTop,
                                    insetBottom = insetBottom,
                                    backLabel = "Play",
                                    onBack = { panelOpen = false },
                                    onChange = ::writeSetting,
                                    onButtonChange = ::writeHotkey,
                                    onEditLayout = { openEditor(Route.CONTROL_LAYOUT) },
                                    onResetLayout = { resetOrientationLayout(listOf(4)) },
                                    workspaceTab = section,
                                    onWorkspaceTab = { controlsWorkspaceTab = it },
                                    contentOnly = true,
                                )
                                ControlsWorkspaceTab.ARENA_UI -> ArenaUiScreen(
                                    settings = settings,
                                    insetTop = insetTop,
                                    insetBottom = insetBottom,
                                    backLabel = "Play",
                                    onBack = { panelOpen = false },
                                    onChange = ::writeSetting,
                                    onEditLayout = { openEditor(Route.CONTROL_LAYOUT) },
                                    onResetLayout = { resetOrientationLayout(listOf(8)) },
                                    workspaceTab = section,
                                    onWorkspaceTab = { controlsWorkspaceTab = it },
                                    contentOnly = true,
                                )
                            }
                          }
                        }
                          AdjustPreviewCard(settings = settings, top = insetTop + 58.dp)
                        }

                        Route.SETTINGS_BUTTONS -> SettingsButtonsScreen(
                            buttons = hotkeys,
                            settings = settings,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                            onChange = ::writeSetting,
                            onButtonChange = ::writeHotkey,
                            onEditLayout = { openEditor(Route.CONTROL_LAYOUT) },
                            onResetLayout = { resetOrientationLayout(listOf(4)) },
                        )

                        // The Trails tab (OM, 2026-10-04). Switched off: the "in development" page.
                        Route.TRAILS -> if (!TRAILS_ENABLED) TrailsComingSoonScreen(
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                        ) else TrailsFeedScreen(
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onNew = {
                                studioReturn = Route.TRAILS
                                tabRoot = Route.TRAILS
                                openPanel(Rect.Zero) { route = Route.TRAIL_STUDIO }
                            },
                            onOpen = ::openTrail,
                            // From the tab page a profile opens in the panel, as from Social.
                            onAuthor = { id ->
                                if (!route.growsFromHome) {
                                    tabRoot = Route.TRAILS
                                    openPanel(Rect.Zero) { openPlayer(id) }
                                } else openPlayer(id)
                            },
                            onTrySkin = ::tryTrailSkin,
                        )

                        Route.TRAIL -> if (!TRAILS_ENABLED) TrailsPausedExit() else TrailDetailScreen(
                            trailId = trailOpenId,
                            meId = profile.id,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = {
                                if (trailReturn.growsFromHome) route = trailReturn else panelOpen = false
                            },
                            onAuthor = { id -> openPlayer(id) },
                            onTrySkin = ::tryTrailSkin,
                        )

                        Route.TRAIL_STUDIO -> if (!TRAILS_ENABLED) TrailsPausedExit() else androidx.compose.runtime.CompositionLocalProvider(
                            androidx.activity.compose.LocalActivityResultRegistryOwner provides resultOwner,
                        ) {
                            TrailStudioScreen(
                                insetTop = insetTop,
                                insetBottom = insetBottom,
                                onClose = {
                                    if (studioReturn.growsFromHome) route = studioReturn else panelOpen = false
                                },
                            )
                        }

                        // Share run: the studio as the Share editor, portrait, over Home.
                        Route.TRAIL_SHARE -> {
                            val run = shareRun
                            if (!TRAILS_ENABLED || (run == null && !shareSkinOnly)) TrailsPausedExit() else androidx.compose.runtime.CompositionLocalProvider(
                                androidx.activity.compose.LocalActivityResultRegistryOwner provides resultOwner,
                            ) {
                                TrailStudioScreen(
                                    insetTop = insetTop,
                                    insetBottom = insetBottom,
                                    onClose = ::closeShareRun,
                                    share = ShareRunInput(run?.takeIf { !shareSkinOnly }, skinState, WyrmLookStore.spec(), shareTagId),
                                    onPosted = ::openTrailsAfterShare,
                                )
                            }
                        }

                        Route.HELP -> HelpCenterScreen(
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            repository = repository,
                            onBack = { panelOpen = false },
                            onCompose = { kind -> openSupportCompose(kind) },
                            onReports = {
                                supportReportsReturn = Route.HELP
                                route = Route.SUPPORT_REPORTS
                            },
                        )

                        Route.SUPPORT_COMPOSE -> SupportComposeScreen(
                            initialKind = supportKind,
                            signedIn = repository.hasSession,
                            handle = if (profile.username.isBlank()) "" else "@${profile.username}",
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { route = supportReturn },
                            onReports = {
                                supportReportsReturn = Route.HELP
                                route = Route.SUPPORT_REPORTS
                            },
                        )

                        Route.SUPPORT_REPORTS -> SupportReportsScreen(
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = {
                                val back = supportReportsReturn
                                if (back != null) route = back else panelOpen = false
                            },
                            onNew = { openSupportCompose(com.wyrm.omrajput.data.SupportKind.BUG) },
                        )

                        Route.ABOUT -> AboutScreen(
                            appVersion = BuildConfig.VERSION_NAME,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                        )

                        Route.SETTINGS_NOTIFICATIONS -> NotificationSettingsScreen(
                            masterEnabled = notificationsAllowed,
                            enabledKinds = enabledNotificationKinds,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                            onOpenSystemSettings = {
                                NotificationPreferences.openSystemSettings(activity)
                            },
                            onKindChanged = ::setNotificationKindEnabled,
                        )

                        Route.SETTINGS_ACCESSIBILITY -> SettingsAccessibilityScreen(
                            selected = appTheme,
                            intensity = themeIntensity,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                            onTheme = ::selectAppTheme,
                            onIntensityChange = ::applyThemeIntensity,
                            onResetIntensity = { applyThemeIntensity(0.5f) },
                        )

                        Route.SETTINGS_FOOD -> SettingsFoodScreen(
                            settings = settings,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                            onChange = ::writeSetting,
                        )

                        Route.SETTINGS_PERFORMANCE -> SettingsPerformanceScreen(
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onBack = { panelOpen = false },
                        )

                        Route.SETTINGS_BACKUP, Route.SETTINGS_UPDATES -> BackupScreen(
                            update = updateState,
                            installedVersion = BuildConfig.VERSION_NAME,
                            settingsVersion = settingsVersion,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            backLabel = when (backupReturn) {
                                Route.PROFILE -> "Profile"
                                else -> "Settings"
                            },
                            onBack = { route = backupReturn; if (backupReturn == Route.SETTINGS) panelOpen = false },
                            onCheckUpdate = { host?.onUpdateAction(0) },
                            betaUpdates = betaUpdates,
                            onBetaUpdates = ::applyBetaUpdates,
                            onInstall = ::startUpdate,
                            onOpenInstalledNotes = {
                                openWhatsNew(
                                    BuildConfig.VERSION_NAME,
                                    automatic = false,
                                    newVersion = false,
                                )
                            },
                            onOpenAvailableNotes = {
                                updateState.version.takeIf { it.isNotBlank() }?.let {
                                    openWhatsNew(it, automatic = false, newVersion = true)
                                }
                            },
                            onResetAll = {
                                host?.onSettingsAction(1)
                                refreshSettingsSoon()
                            },
                        )

                        Route.CONTROL_LAYOUT -> UnifiedArenaLayoutEditor(
                            joystick = layoutPosition("layout.joystick_x", "layout.joystick_y"),
                            boost = layoutPosition("layout.boost_x", "layout.boost_y"),
                            zoom = layoutPosition("layout.zoom_x", "layout.zoom_y"),
                            // Upright steers with the arrow only (OM, 2026-10-02): no joystick to place.
                            // Near Original (OM, 2026-10-02): the original's joystick, boost and
                            // HUD are fixed; only the on-screen buttons (and the zoom bar) move.
                            showJoystick = !NearOriginalStore.on &&
                                !com.wyrm.omrajput.data.PlayOrientation.portrait &&
                                value("controls.joystick_mode").toInt() != 2,
                            showBoost = !NearOriginalStore.on && value("controls.boost_mode").toInt() == 1,
                            hudEditable = !NearOriginalStore.on,
                            showZoom = flag("controls.zoom_enabled"),
                            joystickSize = value("controls.joystick_size", 1f),
                            boostSize = value("controls.boost_size", 1f),
                            zoomLength = value("controls.zoom_length", 1f),
                            zoomVertical = value("controls.zoom_orientation").toInt() == 1,
                            joystickOpacity = value("layout.joystick_opacity", value("controls.opacity", 1f)),
                            boostOpacity = value("layout.boost_opacity", value("controls.opacity", 1f)),
                            zoomOpacity = value("layout.zoom_opacity", value("controls.opacity", 1f)),
                            hotkeys = hotkeys,
                            keyScales = hotkeys.associate { it.action to value("layout.key_${it.action}_scale", value("keys.key_scale", 1f)) },
                            keyOpacities = hotkeys.associate { it.action to value("layout.key_${it.action}_opacity", value("keys.opacity", 1f)) },
                            hudPositions = ArenaHudTarget.entries.associateWith { target ->
                                layoutPosition("${target.prefix}_x", "${target.prefix}_y")
                            },
                            minimapSize = value("general.minimap_size", 300f),
                            leaderboardFont = value("general.lb_font", 1f).toInt(),
                            statsFont = value("general.stats_font", 1f).toInt(),
                            statsScale = value("layout.stats_scale", 1f),
                            statsOpacity = value("layout.stats_opacity", 1f),
                            chatScale = value("layout.chat_scale", 1f),
                            chatOpacity = value("layout.chat_opacity", 1f),
                            onLeaderboardTap = { host?.onToggleEditorLeaderboard() },
                            onMoveControl = ::moveControl,
                            onMoveKey = ::moveKey,
                            onMoveHud = ::moveArenaHud,
                            onSettingChange = { id, next ->
                                if (TeamHudStore.owns(id)) {
                                    TeamHudStore.apply(id, next)
                                } else {
                                    settings.firstOrNull { it.id == id }?.let { setting ->
                                        writeSetting(setting, listOf(next))
                                    }
                                }
                            },
                            onReset = { resetOrientationLayout(listOf(2, 4, 8)) },
                            onSave = { closeEditor(Route.SETTINGS_CONTROLS, save = true) },
                            onCancel = { closeEditor(Route.SETTINGS_CONTROLS, save = false) },
                            portrait = com.wyrm.omrajput.data.PlayOrientation.portrait,
                            onToggleOrientation = { switchPlayOrientation(!com.wyrm.omrajput.data.PlayOrientation.portrait) },
                        )

                        Route.ON_SCREEN_BUTTON_LAYOUT -> OnScreenButtonLayoutEditor(
                            hotkeys = hotkeys,
                            keyScale = value("keys.key_scale", 1f),
                            opacity = value("keys.opacity", 1f),
                            safeInsets = SafeInsets(),
                            onMove = ::moveKey,
                            onReset = {
                                host?.onSettingsAction(4)
                                refreshSettingsSoon()
                            },
                            onSave = { closeEditor(Route.SETTINGS_CONTROLS, save = true) },
                            onCancel = { closeEditor(Route.SETTINGS_CONTROLS, save = false) },
                        )

                        Route.ARENA_HUD_LAYOUT -> ArenaHudLayoutEditor(
                            positions = ArenaHudTarget.entries.associateWith { target ->
                                layoutPosition("${target.prefix}_x", "${target.prefix}_y")
                            },
                            minimapSize = value("general.minimap_size", 300f),
                            leaderboardFont = value("general.lb_font", 1f).toInt(),
                            statsFont = value("general.stats_font", 1f).toInt(),
                            safeInsets = SafeInsets(),
                            onMove = ::moveArenaHud,
                            onReset = {
                                host?.onSettingsAction(8)
                                refreshSettingsSoon()
                            },
                            onSave = { closeEditor(Route.SETTINGS_CONTROLS, save = true) },
                            onCancel = { closeEditor(Route.SETTINGS_CONTROLS, save = false) },
                        )

                        Route.ARENA_CHAT -> ArenaChatScreen(
                            messages = teamMessages,
                            draft = teamDraft,
                            sending = teamSending,
                            handoverSeconds = handoverSeconds,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onDraftChange = { teamDraft = it },
                            onSend = ::sendTeamMessage,
                            onHandoverChange = ::chooseHandover,
                            onClose = ::closeArenaChat,
                        )

                        Route.DEATH -> DeathScreen(
                            stats = deathStats,
                            autoRespawn = autoRespawn,
                            onAutoRespawn = {
                                autoRespawn = it
                                host?.onSetAutoRespawn(it)
                                // Turning it on means this card is the last one
                                // that will be shown, so it gets out of the way
                                // and the snake goes straight back in.
                                if (it) {
                                    host?.onDeathPlay()
                                }
                            },
                            onPlay = {
                                host?.onDeathPlay()
                            },
                            onHome = {
                                host?.onDeathHome()
                            },
                            onLobby = {
                                // The engine still has to be told the match is
                                // over; the lobby is only where Compose lands
                                // afterwards instead of Home.
                                host?.onDeathHome()
                                openLobby()
                            },
                        )
                    }
                    }
                    }

                    /*
                     * The update prompt, over everything.
                     *
                     * Outside the route table on purpose: an update is not a
                     * place in the app, it is something that has happened to
                     * it, and it should be able to interrupt Home without
                     * Home having to know. It never appears over a live match
                     * — the check runs at launch, when Home is what is there.
                     */
                    if (updatePromptVisible) {
                        UpdatePrompt(
                            state = updateState,
                            backup = backupState,
                            updateStarted = updateStarted,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onLater = ::dismissUpdatePrompt,
                            onUpdate = ::startUpdate,
                            onChooseFolder = {},
                            beta = com.wyrm.omrajput.UpdateChannel.isOfferedBeta(activity),
                            onBetaSettings = ::openBetaUpdateSetting,
                        )
                    } else if (whatsNewState != null) {
                        whatsNewState?.let { notes ->
                            WhatsNewSheet(
                                state = notes,
                                insetTop = insetTop,
                                insetBottom = insetBottom,
                                onRetry = { loadWhatsNew(notes) },
                                onClosing = { whatsNewBackdropVisible = false },
                                onAcknowledged = {
                                    // Seen once is enough, even if a retry failed.
                                    if (notes.automatic) releaseNotes.acknowledge(notes.versionCode)
                                    whatsNewBackdropVisible = false
                                    whatsNewState = null
                                },
                            )
                        }
                    }

                    // Also outside the route table: an invite is opened over
                    // whichever thread it was asked for, not a place of its own.
                    if (inviteSheetOpen) {
                        threadPlayer?.let { other ->
                            InviteSheet(
                                recipientName = other.displayName,
                                state = arenaState,
                                query = inviteQuery,
                                note = inviteNote,
                                selected = inviteSelectedServer,
                                sending = inviteSending,
                                insetTop = insetTop,
                                insetBottom = insetBottom,
                                onQueryChange = { inviteQuery = it },
                                onNoteChange = { inviteNote = it },
                                onSelect = { inviteSelectedServer = it },
                                onDismiss = ::closeInvite,
                                onSend = ::sendInvite,
                            )
                        }
                    }

                    eventGate?.let { event ->
                        EventGateSheet(
                            event = event,
                            insetTop = insetTop,
                            insetBottom = insetBottom,
                            onOkay = { eventGate = null },
                            onJoinAnyway = {
                                val address = event.serverAddress.orEmpty()
                                eventGate = null
                                if (address.isNotBlank()) beginArenaEntry(address)
                            },
                            onDismiss = { eventGate = null },
                        )
                    }

                    if (
                        route == Route.HOME &&
                        pendingAchievements.isNotEmpty() &&
                        !updatePromptVisible &&
                        whatsNewState == null &&
                        !backupPromptVisible &&
                        eventGate == null
                    ) {
                        AchievementImpact(
                            achievement = pendingAchievements.first(),
                            onFinished = {
                                pendingAchievements = pendingAchievements.drop(1)
                            },
                        )
                    }

                    /* Deliberately gone. The engine draws the only connecting
                     * screen there is; this was a second one that appeared
                     * before it and handed over with a visible cut. */

                    // Where the player is, for a crash report.
                    LaunchedEffect(route) {
                        // A tried skin lives only on the Skin tab: leaving it is "Back to mine".
                        if (route != Route.SKIN) skinTrial = null
                        com.wyrm.omrajput.data.CrashWatch.screen = route.name
                        // A manual report names where the player came from, not Help.
                        if (route !in setOf(Route.HELP, Route.SUPPORT_COMPOSE, Route.SUPPORT_REPORTS)) {
                            com.wyrm.omrajput.data.SupportStore.screenBefore = route.name
                        }
                    }

                    // The launch after a crash asks whether to send the report,
                    // over everything but the arena's own landscape surfaces and
                    // after any update or backup card has been answered.
                    if (
                        !updatePromptVisible &&
                        whatsNewState == null &&
                        !backupPromptVisible &&
                        !backupResultPromptVisible &&
                        route !in setOf(
                            Route.LOBBY, Route.DEATH, Route.ARENA_CHAT,
                            Route.CONTROL_LAYOUT, Route.ON_SCREEN_BUTTON_LAYOUT, Route.ARENA_HUD_LAYOUT, Route.BACKGROUND_SIZE_EDITOR,
                        )
                    ) {
                        CrashPromptHost(repository = repository, insetBottom = insetBottom, insetTop = insetTop)
                    }

                    // An arena drop asks when the player is back from the match:
                    // the lobby included (landscape), after any crash card.
                    if (
                        !updatePromptVisible &&
                        whatsNewState == null &&
                        !backupPromptVisible &&
                        !backupResultPromptVisible &&
                        com.wyrm.omrajput.data.CrashWatch.prompt == null &&
                        !enteringArena &&
                        route !in setOf(
                            Route.DEATH, Route.ARENA_CHAT,
                            Route.CONTROL_LAYOUT, Route.ON_SCREEN_BUTTON_LAYOUT, Route.ARENA_HUD_LAYOUT, Route.BACKGROUND_SIZE_EDITOR,
                        )
                    ) {
                        DropPromptHost(repository = repository, insetBottom = insetBottom, insetTop = insetTop)
                    }

                    // Wyrm iOS's session transition: the W and "Signing you out…".
                    sessionTransitionTitle?.let { WyrmSessionTransition(it) }
                    logOutFailure?.let { reason ->
                        LogOutSaveFailed(
                            message = reason,
                            onRetry = { logOut() },
                            onLogOutAnyway = { logOut(skipSave = true) },
                            onStay = { logOutFailure = null },
                        )
                    }
                    LogOutSheet(
                        visible = logOutAsking,
                        name = profile.displayName.ifBlank { profile.username.ifBlank { "Wyrm player" } },
                        handle = if (profile.username.isBlank()) "" else "@${profile.username}",
                        avatarUrl = profile.avatarUrl,
                        avatarKey = profile.avatarKey,
                        insetBottom = insetBottom,
                        onLogOut = { logOut() },
                        onCancel = { logOutAsking = false },
                    )

                    if (voiceCall.active && !enteringArena) {
                        FloatingVoiceCall(
                            state = voiceCall,
                            visible = shown && route != Route.VOICE,
                            onOpen = ::openVoiceChat,
                            onMute = { VoiceCallService.toggleMute(activity) },
                            onSound = { VoiceCallService.toggleDeafen(activity) },
                            onLeave = ::leaveVoiceCall,
                        )
                    }
                }
            }
        }
        // Added to the activity's content frame rather than into SDL's own
        // layout: a child of SDL's layout sits behind the game surface for the
        // purposes of touch dispatch, so Home would draw correctly and then
        // quietly hand every tap to the engine.
        activity.addContentView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root = view
        val hud = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(this@WyrmOverlay)
            setViewTreeSavedStateRegistryOwner(this@WyrmOverlay)
            setViewTreeViewModelStoreOwner(this@WyrmOverlay)
            visibility = View.GONE
        }
        hud.setContent {
            WyrmTheme {
                val call by VoiceCallController.state.collectAsState()
                LaunchedEffect(call.active, shown) {
                    hud.visibility = if (call.active && !shown) View.VISIBLE else View.GONE
                }
                ArenaVoiceHud(
                    state = call,
                    onToggleMute = { VoiceCallService.toggleMute(activity) },
                    onLeave = { VoiceCallService.leave(activity) },
                )
            }
        }
        val density = activity.resources.displayMetrics.density
        activity.addContentView(
            hud,
            FrameLayout.LayoutParams((106 * density).toInt(), (50 * density).toInt()).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = (328 * density).toInt()
                topMargin = (16 * density).toInt()
            },
        )
        voiceHud = hud
        val pushFilter = IntentFilter(WyrmMessagingService.ACTION_PUSH_RECEIVED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(pushReceiver, pushFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(pushReceiver, pushFilter)
        }
        pushReceiverRegistered = true
        observeSystemBars()
        refreshTeams()
        startTeamPolling()
        watchForUpdates()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        validateStoredSession()
    }

    /**
     * Home, held still behind whatever grew out of it.
     *
     * Inert on purpose — nothing here is meant to be pressed while a panel is
     * over it, and the panel covers it entirely within a third of a second.
     */
    @Composable
    private fun HomeUnderneath() {
        TabUnderneath()
    }

    @Composable
    private fun TabUnderneath() {
        when (tabRoot) {
            Route.TRAILS -> if (!TRAILS_ENABLED) TrailsComingSoonScreen(
                insetTop = insetTop,
                insetBottom = insetBottom,
            ) else TrailsFeedScreen(
                insetTop = insetTop,
                insetBottom = insetBottom,
                onNew = {},
                onOpen = {},
                onAuthor = {},
            )
            Route.SOCIAL -> SocialHome(interactive = false)
            Route.SKIN -> SkinHome(interactive = false)
            Route.SETTINGS -> SettingsHome(interactive = false)
            else -> PlayHome(interactive = false)
        }
    }

    @Composable
    private fun PlayHome(interactive: Boolean = true, showRootTabs: Boolean = true) {
        val arena = featuredArena()
        val ping = arena?.let { arenaState.pings[it.endpoint] } ?: lobbyPing
        val teamName = teamProfiles.getOrNull(teamActive)?.name.orEmpty()
        val room = voiceState.rooms.firstOrNull { it.mine }
        val normalFood = settings.named("normal.food_type")
        HomeScreen(
            nickname = nickname,
            nicknameEditable = interactive,
            displayName = profile.displayName.ifBlank { nickname },
            username = profile.username,
            avatarUrl = profile.avatarUrl,
            avatarKey = profile.avatarKey,
            arenaTitle = iosArenaTitle(arena),
            arenaPing = ping,
            arenaPlayers = arena?.players ?: 0,
            arenaOnline = ping > 0 || arenaOnline,
            arenaReady = arena != null || ArenaDirectory.isValidEndpoint(arenaLabel),
            bestScore = maxOf(profile.highestScore, localBestScore()),
            kills = maxOf(profile.kills, localTotalKills()),
            foodLabel = normalFood?.options?.getOrNull(normalFood.index) ?: "Original",
            foodStyle = normalFood?.index ?: 0,
            controlsLabel = playControlsLabel(),
            publicRoomCode = room?.id?.take(8)?.uppercase().orEmpty(),
            pickServerLabel = playPickLabel(arena, ping),
            teamName = teamName,
            teamOnline = teamState.members.count { it.playing },
            teamConfigured = team.configured && teamEnabled,
            arenaEndpoint = arena?.endpoint ?: arenaLabel.takeIf { ArenaDirectory.isValidEndpoint(it) }.orEmpty(),
            arenaLive = arena != null || ArenaDirectory.isValidEndpoint(arenaLabel),
            voiceRoomsLive = voiceState.rooms.count { it.active },
            voiceRoomName = voiceState.rooms.firstOrNull { it.active }?.name.orEmpty(),
            nearOriginal = NearOriginalStore.on,
            onNearOriginal = { if (interactive) NearOriginalStore.applyOn(it) },
            unreadNotifications = visibleNotifications().count { !it.read },
            insetTop = insetTop,
            insetBottom = insetBottom,
            showRootTabs = showRootTabs,
            onAppear = {
                if (interactive) {
                    refreshSettings()
                    if (arenaState.arenas.isEmpty()) refreshArenas()
                }
            },
            onNicknameChange = { if (interactive) chooseNickname(it) },
            onNicknameDone = {
                if (interactive) {
                    // Sent as typed, blank included: the arena shows it as is (OM).
                    host?.onSetNickname(nickname)
                    syncIngameName()
                }
            },
            onEnterArena = {
                if (interactive) {
                    val address = arena?.endpoint ?: arenaLabel
                    if (!ArenaDirectory.isValidEndpoint(address)) {
                        openPanel(Rect.Zero) { openArenaPicker() }
                    } else {
                        if (address != arenaLabel) selectArena(address)
                        recentArenas = com.wyrm.omrajput.data.RecentArenas.push(activity, address)
                        openLobby()
                    }
                }
            },
            onPickServer = { origin ->
                if (interactive) openPanel(origin) { openArenaPicker() }
            },
            onOpenProfile = { origin ->
                if (interactive) openPanel(origin) { openProfile() }
            },
            onOpenFood = { origin ->
                if (interactive) openPanel(origin) {
                    refreshSettings()
                    route = Route.SETTINGS_FOOD
                }
            },
            onOpenControls = { origin ->
                if (interactive) openPanel(origin) { openControls() }
            },
            onOpenMode = { origin ->
                if (interactive) openPanel(origin) {
                    refreshSettings()
                    route = Route.SETTINGS_ASSIST
                }
            },
            onOpenTeam = { origin ->
                if (interactive) {
                    teamTab = TeamTab.TEAM
                    teamAdding = false
                    openPanel(origin) { route = Route.TEAM }
                }
            },
            onOpenVoice = { origin ->
                if (interactive) openPanel(origin) { openVoiceChat() }
            },
            onTabNotifications = { origin ->
                if (interactive) openNotifications(origin)
            },
            onTabSocial = {
                if (interactive) {
                    tabRoot = Route.SOCIAL
                    route = Route.SOCIAL
                }
            },
            onTabSkin = {
                if (interactive) openSkinTab()
            },
            onTabSettings = {
                if (interactive) openSettingsTab()
            },
        )
    }

    @Composable
    private fun SkinHome(interactive: Boolean = true, showRootTabs: Boolean = true) {
        // Wyrm iOS's Skin studio: Compose draws the preview itself, so the
        // engine's own postcard stays off and the engine is only asked what is
        // worn. Every change goes to the engine at once and is saved there.
        LaunchedEffect(interactive) {
            if (interactive) {
                host?.onSkinPostcard(false)
                host?.onSkinSync(false)
                refreshSettings()
            }
        }
        fun saved(change: () -> Unit) {
            if (!interactive) return
            change()
            host?.onSkinSync(true)
        }
        // "Try this skin": every pick changes the draft only; Wear sends it.
        val trial = skinTrial.takeIf { interactive }
        IosSkinScreen(
            state = trial?.skin ?: skinState,
            background = arenaBackground,
            settings = settings,
            insetTop = insetTop,
            onPickPreset = { index ->
                if (trial != null) editTrial { it.copy(skin = it.skin.copy(preset = index, custom = false)) }
                else saved { host?.onSkinPreset(index) }
            },
            onCode = { code, colours ->
                if (trial != null) editTrial { it.copy(skin = it.skin.copy(code = code, colours = colours, custom = code.isNotEmpty())) }
                else saved { host?.onSkinCode(code, colours) }
            },
            onPickAccessory = { id ->
                if (trial != null) editTrial { it.copy(skin = it.skin.copy(accessory = id)) }
                else saved { host?.onSkinAccessory(id) }
            },
            onPickBackground = { index ->
                saved {
                    arenaBackground = index
                    host?.onSkinBackground(index)
                }
            },
            onSettingChange = { setting, values -> if (interactive) writeSetting(setting, values) },
            look = trial?.look,
            onLook = if (trial != null) ({ next -> editTrial { it.copy(look = next) } }) else null,
            banner = if (trial != null) ({
                SkinTrialBanner(trial.author, onWear = ::wearSkinTrial, onBack = { skinTrial = null })
            }) else null,
            // Only the worn skin is shared, never one being tried.
            onShareSkin = if (interactive && trial == null && TRAILS_ENABLED && repository.hasSession) ::openShareSkin else null,
            onAdjustBackgroundSize = if (interactive) ({ openBackgroundSizeEditor(Route.SKIN) }) else null,
        )
    }

    @Composable
    private fun SocialHome(interactive: Boolean = true, showRootTabs: Boolean = true) {
        val liveRooms = voiceState.rooms.count { it.active }
        val mineOpen = voiceState.rooms.any { it.mine && it.active }
        val killRank = killBoard.indexOfFirst { it.id == profile.id }
            .takeIf { it >= 0 }?.plus(1)
        val threads = conversations.size
        val recents = when {
            conversations.isNotEmpty() -> conversations.take(6).map { row ->
                SocialRecent(
                    id = row.player.id,
                    name = row.player.displayName,
                    handle = row.player.handle,
                    avatarUrl = row.player.avatarUrl,
                    avatarKey = row.player.avatarKey,
                    whenLabel = "",
                )
            }
            else -> following.take(6).map { row ->
                SocialRecent(
                    id = row.id,
                    name = row.displayName,
                    handle = row.handle,
                    avatarUrl = row.avatarUrl,
                    avatarKey = row.avatarKey,
                )
            }
        }
        val initials = profile.displayName.filter { it.isLetter() }.take(2).uppercase()
            .ifEmpty { "W" }
        SocialScreen(
            killRank = killRank,
            messageDetail = if (threads > 0) {
                "Global room and $threads direct threads"
            } else {
                "Global and direct"
            },
            unreadMessages = unreadDmCount,
            unreadGlobal = globalUnread,
            voiceBadge = unreadOf(NotificationKind.VOICE_INVITE),
            followBadge = unreadOf(NotificationKind.FOLLOW),
            voiceDetail = when {
                liveRooms == 0 -> "Rooms and calls"
                mineOpen -> "$liveRooms live · yours is open"
                else -> "$liveRooms live"
            },
            followerCount = profile.followerCount,
            followingCount = profile.followingCount,
            profileHandle = if (profile.username.isBlank()) "" else "@${profile.username}",
            profileInitials = initials,
            recents = recents,
            unreadNotifications = visibleNotifications().count { !it.read },
            insetTop = insetTop,
            insetBottom = insetBottom,
            showRootTabs = showRootTabs,
            onAppear = {},
            onRefresh = { done ->
                if (interactive) {
                    if (TRAILS_ENABLED) TrailsStore.refresh()
                    scope.launch {
                        bootstrapSession()
                        refreshProfile(force = true)
                        done()
                    }
                } else {
                    done()
                }
            },
            // Trails is a tab of its own now (OM, 2026-10-04): no card on Social.
            trailsTeaser = null,
            onOpenLeaderboard = { origin ->
                if (interactive) {
                    tabRoot = Route.SOCIAL
                    openPanel(origin) { openLeaderboard() }
                }
            },
            onOpenMessages = { origin ->
                if (interactive) {
                    tabRoot = Route.SOCIAL
                    openPanel(origin) { openChat() }
                }
            },
            liveRooms = liveRooms,
            onOpenGlobalChat = { origin ->
                if (interactive) {
                    tabRoot = Route.SOCIAL
                    openPanel(origin) {
                        openChat()
                        switchChatTab(ChatTab.GLOBAL)
                    }
                }
            },
            onOpenVoice = { origin ->
                if (interactive) {
                    tabRoot = Route.SOCIAL
                    openPanel(origin) { openVoiceChat() }
                }
            },
            onOpenFollowers = { origin ->
                if (interactive) {
                    tabRoot = Route.SOCIAL
                    openPanel(origin) { openConnections(profile.id, "followers") }
                }
            },
            onOpenProfile = { origin ->
                if (interactive) {
                    tabRoot = Route.SOCIAL
                    openPanel(origin) { openProfile() }
                }
            },
            onOpenPlayer = { id, origin ->
                if (interactive) {
                    tabRoot = Route.SOCIAL
                    openPanel(origin) { openPlayer(id) }
                }
            },
            onTabNotifications = {
                if (interactive) openNotifications()
            },
            onTabPlay = {
                if (interactive) {
                    tabRoot = Route.HOME
                    route = Route.HOME
                }
            },
            onTabSkin = {
                if (interactive) openSkinTab()
            },
            onTabSettings = {
                if (interactive) openSettingsTab()
            },
        )
    }

    @Composable
    private fun SettingsHome(interactive: Boolean = true, showRootTabs: Boolean = true) {
        val handle = if (profile.username.isBlank()) "" else "@${profile.username}"
        val notificationsOn = enabledNotificationKinds.size
        val backupDetail = backupState.title.trim()
        val updateDetail = when {
            updateState.available -> "Update available"
            updateState.busy -> "Checking"
            else -> "Up to date"
        }
        val normalFood = settings.named("normal.food_type")
        val foodValue = normalFood?.options?.getOrNull(normalFood.index).orEmpty()
        SettingsScreen(
            settings = settings,
            hotkeys = hotkeys,
            profileHandle = handle,
            notificationsOn = notificationsOn,
            backupDetail = backupDetail,
            updateDetail = updateDetail,
            settingsVersion = settingsVersion,
            appVersion = BuildConfig.VERSION_NAME,
            themeName = appTheme.displayName,
            foodValue = foodValue,
            performanceValue = com.wyrm.omrajput.data.WyrmPerformance.summary(),
            unreadNotifications = visibleNotifications().count { !it.read },
            insetTop = insetTop,
            insetBottom = insetBottom,
            showRootTabs = showRootTabs,
            search = if (interactive) settingsIndex() else emptyList(),
            onOpenDisplay = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    refreshSettings()
                    openPanel(origin) { route = Route.SETTINGS_GENERAL }
                }
            },
            onOpenControls = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    openPanel(origin) { openControls() }
                }
            },
            onOpenButtons = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    refreshSettings()
                    openPanel(origin) { route = Route.SETTINGS_BUTTONS }
                }
            },
            onOpenAssist = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    refreshSettings()
                    openPanel(origin) { route = Route.SETTINGS_ASSIST }
                }
            },
            onOpenBot = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    refreshSettings()
                    openPanel(origin) { route = Route.SETTINGS_BOT }
                }
            },
            onOpenProfile = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    openPanel(origin) { openProfile() }
                }
            },
            onOpenNotifications = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    openPanel(origin) { route = Route.SETTINGS_NOTIFICATIONS }
                }
            },
            onOpenPrivacy = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    privacyReturn = Route.SETTINGS
                    openPanel(origin) { route = Route.PRIVACY }
                }
            },
            onOpenBackup = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    openPanel(origin) { openSettingsBackup() }
                }
            },
            onOpenAbout = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    openPanel(origin) { route = Route.ABOUT }
                }
            },
            helpValue = "",
            helpBadge = com.wyrm.omrajput.data.SupportStore.unseenReplies,
            onOpenHelp = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    openPanel(origin) { route = Route.HELP }
                }
            },
            onResetAll = {
                if (interactive) {
                    host?.onSettingsAction(1)
                    refreshSettingsSoon()
                }
            },
            onLogOut = { if (interactive && repository.hasSession) askLogOut() },
            onOpenAccessibility = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    openPanel(origin) { route = Route.SETTINGS_ACCESSIBILITY }
                }
            },
            onOpenFood = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    refreshSettings()
                    openPanel(origin) { route = Route.SETTINGS_FOOD }
                }
            },
            onOpenPerformance = { origin ->
                if (interactive) {
                    tabRoot = Route.SETTINGS
                    openPanel(origin) { route = Route.SETTINGS_PERFORMANCE }
                }
            },
            onTabNotifications = {
                if (interactive) openNotifications()
            },
            onTabPlay = {
                if (interactive) {
                    tabRoot = Route.HOME
                    route = Route.HOME
                }
            },
            onTabSocial = {
                if (interactive) {
                    tabRoot = Route.SOCIAL
                    route = Route.SOCIAL
                }
            },
            onTabSkin = {
                if (interactive) openSkinTab()
            },
        )
    }

    private fun featuredArena(): Arena? {
        /* The label changes synchronously when a picker row is tapped, while
           the native settings acknowledgement arrives on the next engine
           frame. Prefer the record matching that label so the old lobby record
           cannot visually overwrite the newly selected server meanwhile. */
        val selected = arenaState.arenas.firstOrNull {
            it.endpoint.equals(arenaLabel, ignoreCase = true)
        } ?: lobbyArena?.takeIf {
            it.endpoint.equals(arenaLabel, ignoreCase = true)
        }
        if (selected != null) return selected
        /* A valid custom address has no directory record by definition. Do not
           replace it with the nearest public arena just because lookup returns
           null; the address itself is the selected arena. */
        if (ArenaDirectory.isValidEndpoint(arenaLabel)) return null
        return arenaState.arenas
            .filter { (arenaState.pings[it.endpoint] ?: Int.MAX_VALUE) < Int.MAX_VALUE }
            .minByOrNull { arenaState.pings[it.endpoint] ?: Int.MAX_VALUE }
    }

    /** Wyrm iOS names a directory arena by its four-digit code. */
    private fun iosArenaTitle(arena: Arena?): String = when {
        arena != null && arena.id >= 0 -> "Arena %04d".format(arena.id % 10_000)
        ArenaDirectory.isValidEndpoint(arenaLabel) -> "Custom arena"
        else -> "Pick a server"
    }

    private fun playArenaTitle(arena: Arena?): String = when {
        arena != null && arena.id >= 0 -> "Server ${arena.id} · cluster ${arena.cluster}"
        ArenaDirectory.isValidEndpoint(arenaLabel) -> arenaLabel
        else -> "Select arena"
    }

    private fun playPickLabel(arena: Arena?, ping: Int): String {
        if (arena != null && ping > 0) return "cluster ${arena.cluster} · $ping ms"
        if (ArenaDirectory.isValidEndpoint(arenaLabel)) return arenaLabel
        return "Choose"
    }

    private fun playControlsLabel(): String {
        val mode = settings.firstOrNull { it.id == "controls.joystick_mode" }
        val steering = when {
            com.wyrm.omrajput.data.PlayOrientation.portrait || mode?.index == 2 -> "Arrow"
            else -> "Joystick"
        }
        val hand = settings.firstOrNull { it.id == "controls.handedness" }
        val side = hand?.options?.getOrNull(hand.index)?.replaceFirstChar { it.uppercase() }
            ?: "Right"
        return "$steering · $side"
    }

    /**
     * Wraps a nested screen in its enter/leave, or renders a root plainly.
     *
     * Drill-ins (leaderboard, profile, chat, settings, …) share one panel so
     * the push plays once from Play/Social and once on the way back — not on
     * every sideways step. The motion is a light right-edge push, not a
     * row morph. New spec pages go through here; do not revive ExpandingPanel's
     * old clip-from-rect spring.
     */
    @Composable
    private fun Grown(current: Route, content: @Composable (Route) -> Unit) {
        // One page layer for the whole overlay. The floating tab bar bends
        // whatever this holds; made per branch, it was thrown away on every
        // drill-in and the bar came back flat for a frame before the glass.
        val pageBackdrop = rememberLayerBackdrop()
        if (!current.growsFromHome) {
            val selectedTab = rootTabForRoute(current)
            if (selectedTab != null) {
                RootStage(selectedTab, pageBackdrop) {
                    AnimatedContent(
                        targetState = current,
                        modifier = Modifier.fillMaxSize().background(Wyrm.Paper).layerBackdrop(pageBackdrop),
                        transitionSpec = {
                            // Wyrm iOS: .opacity + .scale(0.985) on the tab bar's spring.
                            (fadeIn(iosSpring(0.34f, 0.72f)) + scaleIn(iosSpring(0.34f, 0.72f), initialScale = 0.985f)) togetherWith
                                (fadeOut(iosSpring(0.34f, 0.72f)) + scaleOut(iosSpring(0.34f, 0.72f), targetScale = 0.985f))
                        },
                        label = "root-page-transition",
                    ) { visibleRoute ->
                        content(visibleRoute)
                    }
                }
            } else {
                AnimatedContent(
                    targetState = current,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { fadeIn(tween(190)) togetherWith fadeOut(tween(140)) },
                    label = "page-transition",
                ) { visibleRoute ->
                    content(visibleRoute)
                }
            }
            return
        }
        // Under a drill-in the tab page and its floating bar stay drawn, as on
        // iOS where the bar sits beneath every pushed page.
        val underneath: @Composable () -> Unit = {
            RootStage(rootTabForRoute(tabRoot) ?: RootTab.PLAY, pageBackdrop) {
                Box(Modifier.fillMaxSize().background(Wyrm.Paper).layerBackdrop(pageBackdrop)) { TabUnderneath() }
            }
        }
        if (current == Route.ARENA) {
            // Round trips live and die with the open picker. Any way out of it —
            // a pick, the back gesture, the scrim — closes every probe in flight.
            LaunchedEffect(panelOpen) { if (!panelOpen) stopArenaProbes() }
            DisposableEffect(Unit) { onDispose { stopArenaProbes() } }
            ArenaExpandingPanel(
                expanded = panelOpen,
                behind = underneath,
                onDismiss = { panelOpen = false },
                onCollapsed = { route = tabRoot },
                onExpanded = ::scanArenasAfterExpansion,
                content = { content(current) },
            )
            return
        }
        ExpandingPanel(
            origin = panelOrigin,
            expanded = panelOpen,
            behind = underneath,
            onCollapsed = { route = tabRoot },
            onExpanded = {
                // The engine is told to open the skin editor only once the
                // panel has finished arriving. It draws the live preview
                // beneath Compose, and asking for it on the way in put a
                // full-screen preview behind a panel that was still the size
                // of a row — which is what the flicker was.
                if (current == Route.SKIN_EDIT) host?.onOpenSkinEditor()
            },
            content = { RouteStack(current, content) },
        )
    }

    /**
     * Pages inside one drill-in, pushed and popped the Wyrm iOS way. A route
     * already on the stack is a pop: the page on top slides 38 dp right into
     * a blur and fades, the one beneath stays still. Anything else is a push:
     * the new page arrives 52 dp from the right out of a blur, over the old.
     */
    @Composable
    private fun RouteStack(current: Route, content: @Composable (Route) -> Unit) {
        val stack = remember { mutableListOf<Route>() }
        val forward = remember(current) {
            val at = stack.indexOf(current)
            if (at >= 0) {
                while (stack.size > at + 1) stack.removeAt(stack.lastIndex)
                false
            } else {
                stack.add(current)
                true
            }
        }
        val push52 = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.roundToPx() }
        val pop38 = with(androidx.compose.ui.platform.LocalDensity.current) { 38.dp.roundToPx() }
        AnimatedContent(
            targetState = current,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (forward) {
                    androidx.compose.animation.ContentTransform(
                        targetContentEnter = fadeIn(iosSpring(0.44f, 0.84f)) +
                            slideInHorizontally(iosSpring(0.44f, 0.84f)) { push52 },
                        initialContentExit = androidx.compose.animation.ExitTransition.KeepUntilTransitionsFinished,
                        targetContentZIndex = 1f,
                    )
                } else {
                    androidx.compose.animation.ContentTransform(
                        targetContentEnter = androidx.compose.animation.EnterTransition.None,
                        initialContentExit = fadeOut(iosSpring(0.38f, 0.88f)) +
                            slideOutHorizontally(iosSpring(0.38f, 0.88f)) { pop38 },
                        targetContentZIndex = -1f,
                    )
                }
            },
            label = "route-stack",
        ) { shown ->
            val moving = if (forward) shown == current else shown != current
            val blur by transition.animateFloat(
                transitionSpec = { if (forward) iosSpring(0.44f, 0.84f) else iosSpring(0.38f, 0.88f) },
                label = "route-blur",
            ) { state -> if (state == androidx.compose.animation.EnterExitState.Visible || !moving) 0f else if (forward) 16f else 12f }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val radius = blur * density
                        renderEffect = if (radius > 0.5f && Build.VERSION.SDK_INT >= 31) {
                            androidx.compose.ui.graphics.BlurEffect(radius, radius, androidx.compose.ui.graphics.TileMode.Decal)
                        } else {
                            null
                        }
                    },
            ) { content(shown) }
        }
    }

    /**
     * A root page with Wyrm iOS's floating glass tab bar over it. [page]
     * must record itself into [pageBackdrop] so the bar has it to refract.
     */
    @Composable
    private fun RootStage(
        selectedTab: RootTab,
        pageBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
        page: @Composable () -> Unit,
    ) {
        val imeUp = ComposeInsets.ime.getBottom(LocalDensity.current) > 0
        val barAlpha by animateFloatAsState(if (imeUp) 0f else 1f, tween(180), label = "tab-bar-keyboard")
        // Scrolling a root page down folds the bar; scrolling back up opens it.
        val foldOnScroll = remember {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPostScroll(
                    consumed: androidx.compose.ui.geometry.Offset,
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                ): androidx.compose.ui.geometry.Offset {
                    if (consumed.y < -6f) rootBarCollapsed = true
                    else if (consumed.y > 6f) rootBarCollapsed = false
                    return androidx.compose.ui.geometry.Offset.Zero
                }
            }
        }
        LaunchedEffect(selectedTab) { rootBarCollapsed = false }
        Box(Modifier.fillMaxSize().nestedScroll(foldOnScroll)) {
            CompositionLocalProvider(LocalRootTabClearance provides 114.dp) { page() }
            if (barAlpha > 0.01f) {
                CompositionLocalProvider(LocalPageBackdrop provides pageBackdrop) {
                    FixedRootTabs(
                        selectedTab,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = insetBottom.times(0.48f).coerceIn(14.dp, 18.dp))
                            .graphicsLayer { alpha = barAlpha },
                    )
                }
            }
        }
    }

    private fun rootTabPosition(value: Route): Int? = when (value) {
        Route.TRAILS -> 0
        Route.SOCIAL -> 1
        Route.HOME -> 2
        Route.SKIN -> 3
        Route.SETTINGS -> 4
        else -> null
    }

    private fun rootTabForRoute(value: Route): RootTab? = when (value) {
        Route.TRAILS -> RootTab.TRAILS
        Route.SOCIAL -> RootTab.SOCIAL
        Route.HOME -> RootTab.PLAY
        Route.SKIN -> RootTab.SKIN
        Route.SETTINGS -> RootTab.SETTINGS
        else -> null
    }

    @Composable
    private fun FixedRootTabs(selected: RootTab, modifier: Modifier = Modifier) {
        FloatingRootTabs(
            selected = selected,
            trailsBadge = trailsBadgeCount(),
            settingsBadge = com.wyrm.omrajput.data.SupportStore.unseenReplies,
            socialBadge = socialBadgeCount(),
            modifier = modifier,
            collapsed = rootBarCollapsed,
            onExpand = { rootBarCollapsed = false },
            onSelect = { tab ->
                when (tab) {
                    RootTab.TRAILS -> openTrailsTab()
                    RootTab.SOCIAL -> {
                        tabRoot = Route.SOCIAL
                        route = Route.SOCIAL
                    }
                    RootTab.PLAY -> {
                        tabRoot = Route.HOME
                        route = Route.HOME
                    }
                    RootTab.SKIN -> openSkinTab()
                    RootTab.SETTINGS -> openSettingsTab()
                }
            },
        )
    }

    /**
     * Reads the system bars off the decor view.
     *
     * SDL runs its window immersive and edge to edge, so the bars are hidden
     * and the insets Compose would normally see arrive empty — Home would then
     * draw its one loud action underneath a navigation bar the moment a swipe
     * brings that bar back. What matters here is where the bars *would* be, not
     * whether they happen to be up, so this asks for exactly that. Listening at
     * the decor view sees the insets before SDL's own view tree consumes them,
     * and the default handling still runs afterwards, so SDL keeps behaving
     * exactly as it did.
     */
    private fun observeSystemBars() {
        val decor = activity.window.decorView
        decor.setOnApplyWindowInsetsListener { view, insets ->
            val density = view.resources.displayMetrics.density
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars()
                )
                val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
                top = maxOf(bars.top, cutout.top)
                bottom = maxOf(bars.bottom, cutout.bottom)
            } else {
                @Suppress("DEPRECATION")
                top = insets.stableInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.stableInsetBottom
            }
            insetTop = (top / density).dp
            insetBottom = (bottom / density).dp
            // The layout editors work in pixels against the same safe area the
            // engine draws the controls into, so they keep the raw values too.
            val sides = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
                intArrayOf(maxOf(bars.left, cutout.left), maxOf(bars.right, cutout.right))
            } else {
                @Suppress("DEPRECATION")
                intArrayOf(insets.stableInsetLeft, insets.stableInsetRight)
            }
            safeInsets = SafeInsets(
                top = top.toFloat(),
                bottom = bottom.toFloat(),
                left = sides[0].toFloat(),
                right = sides[1].toFloat(),
            )
            view.onApplyWindowInsets(insets)
        }
        decor.requestApplyInsets()
    }

    /**
     * Home's index.
     *
     * Profile, Chat and Leaderboard arrive with the Vlither Arena v1 merge and
     * are listed but not openable until then — a section that is coming is worth
     * showing, a section that silently does nothing is not.
     */
    private fun sections(): List<HomeSection> = listOf(
        HomeSection("Profile", "Identity and record", R.drawable.ic_page_profile) {
            openPanel(it) { openProfile() }
        },
        HomeSection(
            title = "Chat",
            detail = if (unreadDmCount > 0) "${unreadBadge(unreadDmCount)} new" else "Global and direct",
            icon = R.drawable.ic_page_chat,
            onOpen = { openPanel(it) { openChat() } },
        ),
        HomeSection("Leaderboard", "Score and kills", R.drawable.ic_page_leaderboard) {
            openPanel(it) { openLeaderboard() }
        },
        HomeSection("Skin Editor", "Look and tags", R.drawable.ic_page_skin) {
            openPanel(it) { openSkinEditor() }
        },
        HomeSection(
            "Team Mode",
            if (team.configured) "Roster and NTL" else "Connect a team",
            R.drawable.ic_page_team,
        ) { openPanel(it) { route = Route.TEAM } },
        HomeSection("Controls", "Touch and keys", R.drawable.ic_page_controls) {
            openPanel(it) { openControls() }
        },
        HomeSection(
            title = "Voice Chat",
            detail = if (VoiceCallController.state.value.active) "In a room" else "Rooms and calls",
            icon = R.drawable.ic_page_voice,
            accent = if (VoiceCallController.state.value.active) Wyrm.Green else null,
            onOpen = { openPanel(it) { openVoiceChat() } },
        ),
        HomeSection("Settings", "Audio, graphics, account", R.drawable.ic_page_settings) {
            openPanel(it) { openSettings() }
        },
    )

    /** Opens a screen out of the control that was pressed. */
    private fun openPanel(origin: Rect, open: () -> Unit) {
        panelOrigin = origin
        panelOpen = true
        open()
    }

    /* ------------------------------------------------------------ team mode */

    /**
     * The team poll, running for as long as the app does.
     *
     * Not tied to the team screen: the whole point is that teammates appear on
     * the minimap during a match, when no Compose screen is visible at all.
     * One request every four seconds carries this player's position up and
     * brings the team's back down, because that is all the service offers.
     */
    private fun startTeamPolling() {
        // No NTL traffic at all while NTL services are switched off.
        if (TeamService.NTL_SERVICES_DISABLED) return
        if (teamJob != null) return
        // The arena's chat window shows the same list as the Team page (OM,
        // 2026-10-04): every change is handed to the engine.
        if (teamChatJob == null) {
            teamChatJob = scope.launch {
                androidx.compose.runtime.snapshotFlow { teamMessages }.collect { list ->
                    host?.onWriteTeamChat(packTeamChat(list))
                }
            }
        }
        teamJob = scope.launch {
            while (true) {
                if (teamEnabled && team.configured) {
                    val presence = TeamPresence.parse(host?.onReadPresence().orEmpty())
                    val outgoing = teamOutbox
                    teamOutbox = null
                    val next = team.poll(presence, outgoing)

                    // A poll that fails leaves the last known team alone.
                    //
                    // Replacing the state wholesale meant one bad answer wiped
                    // everybody off the screen and off the map, and four
                    // seconds later they came back — the team appeared to
                    // flicker in and out rather than simply going a few
                    // seconds stale. Only a successful poll replaces anyone.
                    val updated = if (next.connected) {
                        next
                    } else {
                        teamState.copy(configured = true, status = next.status)
                    }
                    if (updated != teamState) teamState = updated
                    if (next.messages.isNotEmpty()) {
                        teamMessageTotal += next.messages.size
                        teamMessages = (teamMessages + next.messages).takeLast(200)
                    }
                    if (next.connected) {
                        val packed = pack(next, presence.arena)
                        if (packed != packedTeamSnapshot) {
                            packedTeamSnapshot = packed
                            host?.onWriteTeamMembers(packed)
                        }
                    }
                } else if (teamState.configured) {
                    teamState = TeamState()
                    if (packedTeamSnapshot.isNotEmpty()) {
                        packedTeamSnapshot = ""
                        host?.onWriteTeamMembers("")
                    }
                }
                delay(TeamService.POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * The team, flattened for the engine.
     *
     * Only what a marker and a roster row need — the engine has no business
     * holding chat or credentials. `present` is decided here, by comparing
     * arenas: a teammate's coordinates mean nothing unless they are in the same
     * arena as this player.
     */
    private var teamChatJob: Job? = null
    private var teamMessageTotal = 0L

    /** First line: messages ever received (the folded window counts unread from it). */
    private fun packTeamChat(list: List<TeamMessage>): String = buildString {
        append(teamMessageTotal.coerceAtLeast(list.size.toLong()))
        list.forEach { message ->
            append('\n')
            append(message.from.replace('\t', ' ').replace('\n', ' '))
            append('\t')
            append(message.text.replace('\t', ' ').replace('\n', ' '))
        }
    }

    private fun pack(state: TeamState, myArena: String): String =
        state.members
            // The service reports the whole team, you included, and a green dot
            // sitting on top of your own snake is noise — you already know
            // where you are. Matched by name, which is all the service knows
            // you by.
            .filterNot { it.name.equals(nickname.trim(), ignoreCase = true) }
            .joinToString("\n") { member ->
            val present = member.playing && member.arena == myArena
            listOf(
                member.name.replace('\t', ' ').replace('\n', ' '),
                member.x, member.y, member.score, member.rank,
                if (member.bot) 1 else 0,
                if (present) 1 else 0,
                0, -1,
                member.owner.replace('\t', ' ').replace('\n', ' '),
                if (member.playing) member.arena.replace('\t', ' ').replace('\n', ' ') else "",
            ).joinToString("\t")
        }

    private fun addTeam(name: String, teamId: String, authKey: String) {
        val problem = team.addTeam(name, teamId, authKey)
        teamState = if (problem == null) {
            teamAdding = false
            teamMessages = emptyList()
            clearPackedTeam()
            TeamState(configured = true, status = "Connecting…")
        } else {
            TeamState(status = problem)
        }
        refreshTeams()
        startTeamPolling()
    }

    /** Switching teams clears what the last one was saying. */
    private fun selectTeam(index: Int) {
        team.selectTeam(index)
        teamMessages = emptyList()
        teamState = TeamState(configured = true, status = "Connecting…")
        clearPackedTeam()
        refreshTeams()
    }

    private fun clearPackedTeam() {
        packedTeamSnapshot = ""
        host?.onWriteTeamMembers("")
    }

    /** Opened by the engine's in-game button; the bot is already steering. */
    fun setArenaChat(shown: Boolean) {
        activity.runOnUiThread {
            if (shown) {
                // Only from outside chat. Asked to open while already open —
                // which the engine and this screen can disagree about for a
                // frame — the old line recorded the chat route as the place to
                // go back to, and closing chat then returned to chat. There is
                // no way out of that from the inside.
                if (route != Route.ARENA_CHAT) chatReturn = route
                teamDraft = ""
                route = Route.ARENA_CHAT
            } else if (route == Route.ARENA_CHAT) {
                route = chatReturn
            }
        }
    }

    /**
     * Closes chat and gets out of the way.
     *
     * The route is not reset here: the activity answers this by hiding the
     * interface layer through the same call the engine uses, and that is what
     * puts the player back in the match rather than in the menu.
     */
    private fun closeArenaChat() {
        host?.onCloseTeamChat(handoverSeconds.toFloat())
    }

    private fun chooseHandover(seconds: Int) {
        handoverSeconds = seconds
        activity.getSharedPreferences("wyrm_team_mode", android.content.Context.MODE_PRIVATE)
            .edit().putInt("handover_seconds", seconds).apply()
    }

    private fun refreshTeams() {
        teamProfiles = team.teams
        teamActive = team.activeTeam
    }

    /**
     * Queues a line for the next poll.
     *
     * There is no send call in this protocol — a message rides along with the
     * next position report, so it goes out within four seconds rather than
     * immediately. Saying "sending" until then is the honest thing to show.
     */
    private fun sendTeamMessage() {
        val body = teamDraft.trim()
        if (body.isEmpty() || teamSending) return
        teamOutbox = body
        teamDraft = ""
        teamSending = true
        scope.launch {
            delay(TeamService.POLL_INTERVAL_MS)
            teamSending = false
        }
    }

    /* ----------------------------------------------------------------- chat */

    /**
     * Chat is polled while its screen is open, and only while it is open.
     *
     * The server has a socket and it is the better answer, but a socket that
     * has to survive a phone sleeping, a network changing and a proxy in the
     * middle is a project of its own. Asking every couple of seconds is honest,
     * costs nothing while the app is elsewhere, and can be swapped underneath
     * these screens without them noticing.
     */
    private fun openChat() {
        chatError = ""
        draft = ""
        chatTab = ChatTab.DIRECT
        route = Route.CHAT
        scope.launch { refreshFollowing() }
        pollChat()
    }

    private fun leaveChat() {
        chatJob?.cancel()
        draft = ""
        panelOpen = false
        refreshUnreadDmCount()
    }

    private fun switchChatTab(tab: ChatTab) {
        chatTab = tab
        chatError = ""
        pollChat()
    }

    private fun pollChat() {
        chatJob?.cancel()
        chatJob = scope.launch {
            while (route == Route.CHAT || route == Route.THREAD) {
                // The Direct badge is visible even while Global is selected, so
                // its source has to move on every chat tick rather than only on
                // the Direct tab.
                runCatching { repository.conversations() }
                    .onSuccess {
                        conversations = it
                        unreadDmCount = it.sumOf { row -> row.unread }
                    }
                when {
                    route == Route.THREAD -> threadPlayer?.let { other ->
                        runCatching { repository.directMessages(other.id) }
                            .onSuccess { threadMessages = it }
                    }

                    chatTab == ChatTab.GLOBAL ->
                        runCatching { repository.globalMessages() }
                            .onSuccess {
                                globalMessages = it
                                chatError = ""
                                noteGlobalSeen(it)
                            }
                            .onFailure { chatError = "Can't reach chat right now." }

                    else -> Unit
                }
                delay(if (route == Route.THREAD) 4000 else 3000)
            }
        }
    }

    private suspend fun refreshFollowing() {
        if (profile.id.isEmpty()) return
        runCatching { repository.connections(profile.id, "following") }
            .onSuccess { following = it }
        runCatching { repository.connections(profile.id, "followers") }
            .onSuccess { followers = it }
    }

    private fun sendGlobal() {
        val body = draft.trim().take(280)
        if (body.isEmpty() || sending) return
        draft = ""
        scope.launch {
            sending = true
            runCatching { repository.sendGlobal(body) }
                .onSuccess {
                    chatError = ""
                    runCatching { repository.globalMessages() }.onSuccess {
                        globalMessages = it
                        noteGlobalSeen(it)
                    }
                }
                .onFailure {
                    chatError = iosChatError(repository.errorCode(it))
                    if (draft.isEmpty()) draft = body
                }
            sending = false
        }
    }

    private fun sendDirect() {
        val other = threadPlayer ?: return
        val body = draft.trim().take(1000)
        if (body.isEmpty() || sending) return
        draft = ""
        scope.launch {
            sending = true
            runCatching { repository.sendDirect(other.id, body) }
                .onSuccess {
                    chatError = ""
                    runCatching { repository.directMessages(other.id) }
                        .onSuccess { threadMessages = it }
                }
                .onFailure {
                    chatError = readableChatError(repository.errorCode(it))
                    if (draft.isEmpty()) draft = body
                }
            sending = false
        }
    }

    private fun openInvite() {
        inviteQuery = ""
        inviteNote = ""
        inviteSelectedServer = arenaState.selected.ifEmpty { arenaLabel }
        inviteSheetOpen = true
        if (arenaState.arenas.isEmpty()) refreshArenas()
    }

    private fun closeInvite() {
        inviteSheetOpen = false
    }

    /** A private backend invite; its room credential never becomes a chat body. */
    private fun sendVoiceInvite() {
        val other = threadPlayer ?: return
        if (inviteSending) return
        scope.launch {
            inviteSending = true
            chatError = ""
            runCatching {
                if (!voiceRepository.verificationStatus().verified) {
                    throw VoiceApiException(403, "VOICE_VERIFICATION_REQUIRED")
                }
                val activeRoom = VoiceCallController.state.value.roomId
                val room = voiceRepository.rooms().firstOrNull {
                    it.id == activeRoom || (activeRoom.isBlank() && it.mine)
                } ?: throw VoiceApiException(409, "VOICE_ROOM_REQUIRED")
                voiceRepository.invite(room.id, other.id)
            }.onFailure { chatError = voiceMessage(it) }
            inviteSending = false
        }
    }

    /** Sends the invite as an ordinary direct message — see [encodeInvite]. */
    private fun sendInvite() {
        val other = threadPlayer ?: return
        val address = inviteSelectedServer
        if (address.isEmpty() || inviteSending) return
        val note = inviteNote.trim().ifEmpty { "$nickname has invited you to join them on this server" }
        scope.launch {
            inviteSending = true
            runCatching { repository.sendDirect(other.id, encodeInvite(address, note)) }
                .onSuccess {
                    inviteSheetOpen = false
                    runCatching { repository.directMessages(other.id) }
                        .onSuccess { threadMessages = it }
                }
                .onFailure { chatError = readableChatError(repository.errorCode(it)) }
            inviteSending = false
        }
    }

    private fun openThread(other: ApiPlayer) {
        threadReturn = Route.CHAT
        chatJob?.cancel()
        threadPlayer = other
        threadMessages = emptyList()
        draft = ""
        chatError = ""
        route = Route.THREAD
        scope.launch {
            // The follow state on a leaderboard row can be minutes old, and it
            // decides whether this thread is open at all.
            runCatching { repository.player(other.id) }.onSuccess { threadPlayer = it }
            pollChat()
        }
    }

    private fun reportMessage(message: ChatMessage, reason: String = "Reported from chat") {
        scope.launch {
            runCatching { repository.reportMessage(message.id, reason) }
                .onSuccess { chatError = "Reported. An operator will look at it." }
                .onFailure { chatError = "Couldn't send that report." }
        }
    }

    /* ---------------------------------------------------------------- social */

    private fun openPlayer(id: String) {
        if (id == profile.id) {
            openProfile()
            return
        }
        // Remembered before the route changes, so Back returns to the screen
        // the player was actually opened from rather than always the board.
        playerReturn = when (route) {
            Route.CHAT, Route.THREAD -> Route.CHAT
            Route.CONNECTIONS -> Route.CONNECTIONS
            Route.NOTIFICATIONS -> Route.NOTIFICATIONS
            Route.SOCIAL -> Route.SOCIAL
            Route.TRAILS -> Route.TRAILS
            Route.TRAIL -> Route.TRAIL
            // Opened from a root page (an alert tapped on Play, say): back there.
            else -> if (!route.growsFromHome) route else Route.LEADERBOARD
        }
        formError = ""
        // The last visit paints at once (no "Player" flash); the server answer replaces it.
        viewedPlayer = socialCache.player(id)?.value
        route = Route.PLAYER
        scope.launch {
            runCatching { repository.player(id) }
                .onSuccess {
                    viewedPlayer = it
                    socialCache.savePlayer(it)
                }
                .onFailure {
                    if (viewedPlayer == null) {
                        formError = "That player couldn't be loaded."
                        route = playerReturn
                    }
                }
        }
    }

    /** Pull to refresh on another player's profile: the record again, in place. */
    private fun refreshPlayer(id: String) {
        scope.launch {
            runCatching { repository.player(id) }.onSuccess { fresh ->
                if (viewedPlayer?.id == id) viewedPlayer = fresh
                socialCache.savePlayer(fresh)
            }
        }
    }

    /** A trail from the feed, a profile's grid or an alert; Back returns there. */
    private fun openTrail(id: String) {
        // The end of the trail-reply trail: this trail's likes and replies, seen.
        notifications.filter { !it.read && it.trailId == id }.forEach { setNotificationRead(it.id, true) }
        if (!TRAILS_ENABLED || id.isBlank()) return
        trailOpenId = id
        trailReturn = if (route == Route.TRAIL) trailReturn else route
        if (!route.growsFromHome) {
            // From a root page (Trails, Play…): the trail opens in the panel.
            rootTabForRoute(route)?.let { tabRoot = route }
            panelOrigin = Rect.Zero
            panelOpen = true
        }
        route = Route.TRAIL
    }

    /** A Trails route while Trails are paused (TRAILS_ENABLED): close the panel, or go back to the tab. */
    @Composable
    private fun TrailsPausedExit() {
        LaunchedEffect(Unit) {
            if (panelOpen) panelOpen = false else route = tabRoot
        }
    }

    private fun openSupportCompose(kind: com.wyrm.omrajput.data.SupportKind) {
        supportKind = kind
        supportReturn = if (route == Route.SUPPORT_REPORTS) Route.SUPPORT_REPORTS else Route.HELP
        route = Route.SUPPORT_COMPOSE
    }

    private fun openEditProfile() {
        formError = ""
        renameAllowance = null
        route = Route.EDIT_PROFILE
        scope.launch {
            runCatching { repository.renameAllowance() }
                .onSuccess { renameAllowance = it }
        }
    }

    /** Share profile: Android's share sheet with the player's handle. */
    private fun shareProfile() {
        val name = if (profile.username.isBlank()) profile.displayName.ifBlank { "Wyrm" } else "@${profile.username}"
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Find me on Wyrm: $name")
        runCatching { activity.startActivity(Intent.createChooser(send, "Share profile")) }
    }

    private fun toggleFollow() {
        val other = viewedPlayer ?: return
        if (formBusy) return
        scope.launch {
            formBusy = true
            formError = ""
            runCatching { repository.setFollow(other.id, !other.isFollowing) }
                .onSuccess {
                    viewedPlayer = it
                    socialCache.savePlayer(it)
                    refreshFollowing()
                    refreshProfile()
                }
                .onFailure { formError = "That didn't go through. Try again." }
            formBusy = false
        }
    }

    private fun openConnections(playerId: String, kind: String) {
        // The end of the new-follower trail: your own followers, seen.
        if (playerId == profile.id && kind == "followers") markKindsRead(NotificationKind.FOLLOW)
        connectionsPlayerId = playerId
        connectionsKind = kind
        connectionsReturn = when (route) {
            Route.PLAYER -> Route.PLAYER
            Route.SOCIAL -> Route.SOCIAL
            Route.CONNECTIONS -> connectionsReturn
            else -> Route.PROFILE
        }
        connections = emptyList()
        connectionsLoading = true
        route = Route.CONNECTIONS
        scope.launch {
            runCatching { repository.connections(playerId, kind) }
                .onSuccess { connections = it }
            runCatching { repository.connections(playerId, "followers") }
                .onSuccess { connectionsFollowers = it }
            runCatching { repository.connections(playerId, "following") }
                .onSuccess { connectionsFollowing = it }
            connectionsLoading = false
        }
    }

    private fun toggleFollowInList(player: ApiPlayer) {
        if (formBusy) return
        scope.launch {
            formBusy = true
            runCatching { repository.setFollow(player.id, !player.isFollowing) }
                .onSuccess { updated ->
                    connections = connections.map { row ->
                        if (row.id == updated.id) row.copy(isFollowing = updated.isFollowing) else row
                    }
                    if (viewedPlayer?.id == updated.id) viewedPlayer = updated
                    refreshFollowing()
                    refreshProfile()
                    runCatching { repository.connections(connectionsPlayerId, connectionsKind) }
                        .onSuccess { connections = it }
                }
            formBusy = false
        }
    }

    /* ------------------------------------------------------------- settings */

    /**
     * Reads the engine's description of every setting it holds.
     *
     * Done on the way in rather than kept in step continuously: nothing else
     * writes these while a settings screen is open, and a snapshot taken at
     * the door cannot drift from what is on screen.
     */
    private fun openVoiceChat() {
        // The end of the voice-invite trail: seen once the rooms are open.
        markKindsRead(NotificationKind.VOICE_INVITE)
        route = Route.VOICE
        voiceState = voiceState.copy(
            page = if (VoiceCallController.state.value.active) VoicePage.CALL else VoicePage.DIRECTORY,
            error = "",
        )
        refreshVoice()
    }

    private fun refreshVoice() {
        if (!repository.hasSession) return
        val cached = socialCache.voiceRooms()
        if (cached != null && cached.fresh) {
            voiceState = voiceState.copy(rooms = cached.value, updatedAt = cached.savedAt, offline = false)
            return
        }
        scope.launch {
            voiceState = voiceState.copy(loading = true, error = "")
            val verification = runCatching { voiceRepository.verificationStatus() }
            val rooms = runCatching { voiceRepository.rooms() }
            voiceState = voiceState.copy(
                verified = verification.getOrNull()?.verified ?: voiceState.verified,
                rooms = rooms.getOrNull() ?: voiceState.rooms,
                loading = false,
                offline = rooms.isFailure,
                updatedAt = rooms.getOrNull()?.let { System.currentTimeMillis() } ?: voiceState.updatedAt,
                error = verification.exceptionOrNull()?.let(::voiceMessage)
                    ?: rooms.exceptionOrNull()?.let(::voiceMessage).orEmpty(),
            )
            rooms.getOrNull()?.let(socialCache::saveVoiceRooms)
        }
    }

    private fun refreshVoiceRooms() {
        scope.launch {
            voiceState = voiceState.copy(loading = true, error = "")
            runCatching { voiceRepository.rooms() }
                .onSuccess {
                    socialCache.saveVoiceRooms(it)
                    voiceState = voiceState.copy(rooms = it, loading = false, offline = false, updatedAt = System.currentTimeMillis())
                }
                .onFailure { voiceState = voiceState.copy(loading = false, offline = true, error = voiceMessage(it)) }
        }
    }

    private fun voiceBack() {
        voiceState = when (voiceState.page) {
            VoicePage.DIRECTORY -> {
                panelOpen = false
                voiceState
            }
            VoicePage.CALL, VoicePage.ROOM, VoicePage.CREATE_ROOM,
            VoicePage.VERIFY_EMAIL, VoicePage.VERIFY_CODE, VoicePage.VERIFIED ->
                voiceState.copy(page = VoicePage.DIRECTORY, error = "")
        }
        if (voiceState.page == VoicePage.DIRECTORY && panelOpen) refreshVoiceRooms()
    }

    private fun openVoiceCreate() {
        voiceState = voiceState.copy(
            page = if (voiceState.verified) VoicePage.CREATE_ROOM else VoicePage.VERIFY_EMAIL,
            error = "",
        )
    }

    private fun openVoiceRoom(room: VoiceRoom) {
        voiceState = voiceState.copy(page = VoicePage.ROOM, selectedRoom = room, error = "", loading = true)
        scope.launch {
            runCatching { voiceRepository.room(room.id) }
                .onSuccess { voiceState = voiceState.copy(selectedRoom = it, loading = false) }
                .onFailure { voiceState = voiceState.copy(loading = false, offline = true, error = voiceMessage(it)) }
        }
    }

    private fun startVoiceVerification(email: String) {
        if (email.isBlank() || voiceState.loading) return
        scope.launch {
            voiceState = voiceState.copy(loading = true, error = "", email = email.trim())
            runCatching { voiceRepository.startVerification(email.trim()) }
                .onSuccess { challenge ->
                    voiceState = voiceState.copy(
                        page = VoicePage.VERIFY_CODE,
                        challengeId = challenge.id,
                        resendAt = challenge.resendAt,
                        loading = false,
                    )
                }
                .onFailure { voiceState = voiceState.copy(loading = false, error = voiceMessage(it)) }
        }
    }

    private fun resendVoiceVerification() {
        if (voiceState.challengeId.isBlank() || voiceState.loading) return
        scope.launch {
            voiceState = voiceState.copy(loading = true, error = "")
            runCatching {
                voiceRepository.resendVerification(voiceState.challengeId, voiceState.email)
            }.onSuccess { challenge ->
                voiceState = voiceState.copy(
                    challengeId = challenge.id,
                    resendAt = challenge.resendAt,
                    loading = false,
                )
            }.onFailure { voiceState = voiceState.copy(loading = false, error = voiceMessage(it)) }
        }
    }

    private fun confirmVoiceVerification(code: String) {
        if (code.length != 6 || voiceState.loading) return
        scope.launch {
            voiceState = voiceState.copy(loading = true, error = "")
            runCatching { voiceRepository.confirmVerification(voiceState.challengeId, code) }
                .onSuccess { result ->
                    val pendingInvite = voiceState.pendingInviteId
                    voiceState = voiceState.copy(
                        verified = result.verified,
                        loading = false,
                        page = VoicePage.VERIFIED,
                        challengeId = "",
                        email = "",
                        pendingInviteId = "",
                    )
                    delay(850)
                    voiceState = voiceState.copy(page = VoicePage.DIRECTORY)
                    refreshVoiceRooms()
                    if (pendingInvite.isNotBlank()) acceptVoiceInvite(pendingInvite)
                }
                .onFailure { voiceState = voiceState.copy(loading = false, error = voiceMessage(it)) }
        }
    }

    private fun createVoiceRoom(name: String) {
        if (name.isBlank()) return
        val retry = { createVoiceRoom(name) }
        startVoiceOperation(VoiceOperationKind.CREATE_ROOM, retry) { generation ->
            // The server checks verification itself; no separate round trip first.
            voiceStage(generation, 0)
            val (room, password) = voiceRepository.createRoom(name.trim(), voiceState.operation.id)
            voiceStage(generation, 2)
            voiceState = voiceState.copy(
                page = VoicePage.ROOM,
                selectedRoom = room,
                roomPassword = password,
                rooms = voiceRepository.rooms(),
            )
            finishVoiceOperation(generation)
        }
    }

    private fun joinVoiceRoom(password: String, roomOverride: VoiceRoom? = null) {
        val room = roomOverride ?: voiceState.selectedRoom ?: return
        if (voiceState.offline) {
            voiceState = voiceState.copy(error = "You're offline. Reconnect to enter a voice room.")
            return
        }
        if (!room.managedPublic && !voiceState.verified) {
            voiceState = voiceState.copy(page = VoicePage.VERIFY_EMAIL, error = "Verify your profile before entering a room.")
            return
        }
        val granted = activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            pendingVoicePermission = { joinVoiceRoom(password, roomOverride) }
            (activity as? WyrmActivity)?.requestVoiceMicrophonePermission()
            return
        }
        voiceState = voiceState.copy(selectedRoom = room)
        val retry = { joinVoiceRoom(password, room) }
        startVoiceOperation(VoiceOperationKind.JOIN_ROOM, retry) { generation ->
            // The server checks verification itself; no separate round trip first.
            voiceStage(generation, 0)
            val preferences = voicePreferences.read(profile.id)
            val ticket = voiceRepository.joinRoom(
                room.id, password, voiceState.operation.id,
                preferences.muted, preferences.deafened,
            )
            voiceStage(generation, 1)
            VoiceCallService.connect(activity, profile.id, ticket)
            val terminal = withTimeout(25_000) {
                VoiceCallController.state.first { call ->
                    // Three plain stages: joining, connecting audio, in.
                    val stage = when (call.stage) {
                        VoiceConnectionStage.ENTERING, VoiceConnectionStage.CONNECTED -> 2
                        else -> 1
                    }
                    if (generation == voiceOperationGeneration && stage != voiceState.operation.stage) {
                        voiceState = voiceState.copy(operation = voiceState.operation.copy(stage = stage))
                    }
                    call.stage == VoiceConnectionStage.CONNECTED || call.stage == VoiceConnectionStage.FAILED
                }
            }
            if (terminal.stage == VoiceConnectionStage.FAILED) error(terminal.error.ifBlank { "VOICE_CONNECTION_FAILED" })
            voiceState = voiceState.copy(page = VoicePage.CALL, selectedRoom = ticket.room)
            finishVoiceOperation(generation)
        }
    }

    private fun revealVoicePassword() {
        val room = voiceState.selectedRoom ?: return
        scope.launch {
            runCatching { voiceRepository.revealPassword(room.id) }
                .onSuccess { voiceState = voiceState.copy(roomPassword = it, error = "") }
                .onFailure { voiceState = voiceState.copy(error = voiceMessage(it)) }
        }
    }

    private fun acceptVoiceInvite(inviteId: String) {
        if (!voiceState.verified) {
            voiceState = voiceState.copy(
                page = VoicePage.VERIFY_EMAIL,
                pendingInviteId = inviteId,
                error = "Verify your profile to accept this private voice invitation.",
            )
            return
        }
        val granted = activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            pendingVoicePermission = { acceptVoiceInvite(inviteId) }
            (activity as? WyrmActivity)?.requestVoiceMicrophonePermission()
            return
        }
        val retry = { acceptVoiceInvite(inviteId) }
        startVoiceOperation(VoiceOperationKind.ACCEPT_INVITE, retry) { generation ->
            voiceStage(generation, 0)
            val preferences = voicePreferences.read(profile.id)
            val ticket = voiceRepository.acceptInvite(
                inviteId, voiceState.operation.id, preferences.muted, preferences.deafened,
            )
            voiceStage(generation, 1)
            VoiceCallService.connect(activity, profile.id, ticket)
            val terminal = withTimeout(25_000) {
                VoiceCallController.state.first { call ->
                    // Three plain stages: joining, connecting audio, in.
                    val stage = when (call.stage) {
                        VoiceConnectionStage.ENTERING, VoiceConnectionStage.CONNECTED -> 2
                        else -> 1
                    }
                    if (generation == voiceOperationGeneration && stage != voiceState.operation.stage) {
                        voiceState = voiceState.copy(operation = voiceState.operation.copy(stage = stage))
                    }
                    call.stage == VoiceConnectionStage.CONNECTED || call.stage == VoiceConnectionStage.FAILED
                }
            }
            if (terminal.stage == VoiceConnectionStage.FAILED) error(terminal.error.ifBlank { "VOICE_CONNECTION_FAILED" })
            voiceState = voiceState.copy(
                page = VoicePage.CALL,
                selectedRoom = ticket.room,
                pendingInviteId = "",
            )
            finishVoiceOperation(generation)
        }
    }

    private fun regenerateVoicePassword() {
        val room = voiceState.selectedRoom ?: return
        scope.launch {
            runCatching { voiceRepository.regeneratePassword(room.id, room.revision) }
                .onSuccess { (updated, password) ->
                    voiceState = voiceState.copy(selectedRoom = updated, roomPassword = password, error = "")
                    refreshVoiceRooms()
                }
                .onFailure { voiceState = voiceState.copy(error = voiceMessage(it)) }
        }
    }

    private fun toggleVoiceGate(from: VoiceRoom? = null) {
        val room = from ?: voiceState.selectedRoom ?: return
        val next = if (room.gate == "open") "closed" else "open"
        scope.launch {
            runCatching { voiceRepository.updateRoom(room.id, room.revision, gate = next) }
                .onSuccess { updated ->
                    voiceState = voiceState.copy(
                        selectedRoom = if (voiceState.selectedRoom?.id == updated.id) updated else voiceState.selectedRoom,
                        rooms = voiceState.rooms.map { if (it.id == updated.id) updated else it },
                        error = "",
                    )
                }
                .onFailure { voiceState = voiceState.copy(error = voiceMessage(it)) }
        }
    }

    private fun deleteVoiceRoom() {
        val room = voiceState.selectedRoom ?: return
        scope.launch {
            runCatching { voiceRepository.deleteRoom(room.id) }
                .onSuccess {
                    if (VoiceCallController.state.value.roomId == room.id) VoiceCallService.leave(activity)
                    voiceState = voiceState.copy(page = VoicePage.DIRECTORY, selectedRoom = null, roomPassword = "")
                    refreshVoiceRooms()
                }
                .onFailure { voiceState = voiceState.copy(error = voiceMessage(it)) }
        }
    }

    private fun moderateVoiceParticipant(playerId: String, ban: Boolean) {
        val room = voiceState.selectedRoom ?: return
        scope.launch {
            runCatching { voiceRepository.kick(room.id, playerId, ban) }
                .onFailure { voiceState = voiceState.copy(error = voiceMessage(it)) }
        }
    }

    private fun unbanVoiceParticipant(playerId: String) {
        val room = voiceState.selectedRoom ?: return
        scope.launch {
            runCatching { voiceRepository.unban(room.id, playerId) }
                .onSuccess {
                    runCatching { voiceRepository.room(room.id) }
                        .onSuccess { voiceState = voiceState.copy(selectedRoom = it, error = "") }
                }
                .onFailure { voiceState = voiceState.copy(error = voiceMessage(it)) }
        }
    }

    private fun leaveVoiceCall() {
        VoiceCallService.leave(activity)
        voiceState = voiceState.copy(page = VoicePage.DIRECTORY, operation = VoiceOperationState())
        refreshVoiceRooms()
    }

    private fun startVoiceOperation(
        kind: VoiceOperationKind,
        retry: () -> Unit,
        operation: suspend (Long) -> Unit,
    ) {
        voiceOperationJob?.cancel()
        val generation = ++voiceOperationGeneration
        voiceRetryAction = retry
        voiceState = voiceState.copy(
            error = "",
            operation = VoiceOperationState(
                id = UUID.randomUUID().toString(),
                kind = kind,
                stage = 0,
                running = true,
            ),
        )
        voiceOperationJob = scope.launch {
            runCatching { operation(generation) }
                .onFailure { failure ->
                    if (generation == voiceOperationGeneration &&
                        (failure as? VoiceApiException)?.code == "VOICE_VERIFICATION_REQUIRED") {
                        voiceRetryAction = null
                        voiceState = voiceState.copy(
                            verified = false,
                            page = VoicePage.VERIFY_EMAIL,
                            error = "Verify your profile once to use player rooms.",
                            operation = VoiceOperationState(),
                        )
                    } else if (generation == voiceOperationGeneration) {
                        voiceState = voiceState.copy(
                            operation = voiceState.operation.copy(
                                running = false,
                                failure = voiceMessage(failure),
                            )
                        )
                    }
                }
        }
    }

    /** Shows a stage the moment it is reached (the old 240 ms pauses made joining feel slow). */
    private fun voiceStage(generation: Long, stage: Int) {
        if (generation != voiceOperationGeneration) return
        voiceState = voiceState.copy(operation = voiceState.operation.copy(stage = stage))
    }

    private fun finishVoiceOperation(generation: Long) {
        if (generation != voiceOperationGeneration) return
        voiceState = voiceState.copy(operation = VoiceOperationState())
        voiceRetryAction = null
    }

    private fun cancelVoiceOperation() {
        voiceOperationGeneration++
        voiceOperationJob?.cancel()
        voiceOperationJob = null
        pendingVoicePermission = null
        if (VoiceCallController.state.value.active &&
            voiceState.operation.kind != VoiceOperationKind.CREATE_ROOM) {
            VoiceCallService.leave(activity)
        }
        voiceState = voiceState.copy(operation = VoiceOperationState())
    }

    private fun retryVoiceOperation() {
        val retry = voiceRetryAction ?: return
        voiceState = voiceState.copy(operation = VoiceOperationState())
        retry()
    }

    fun onVoiceMicrophonePermission(granted: Boolean) {
        activity.runOnUiThread {
            val continuation = pendingVoicePermission
            pendingVoicePermission = null
            if (granted) continuation?.invoke()
            else voiceState = voiceState.copy(error = "Microphone permission is required to enter voice chat.")
        }
    }

    private fun voiceMessage(failure: Throwable): String {
        val code = (failure as? VoiceApiException)?.code ?: failure.message.orEmpty()
        return when (code) {
            "VOICE_VERIFICATION_REQUIRED" -> "Verify your profile before using voice chat."
            "VOICE_RESEND_TOO_EARLY" -> "Please wait for the 30-second resend timer."
            "VOICE_OTP_INVALID" -> "That code is incorrect."
            "VOICE_OTP_EXPIRED" -> "That code expired. Request a new one."
            "VOICE_OTP_LOCKED" -> "Too many incorrect attempts. Request a new code."
            "VOICE_EMAIL_QUOTA_EXHAUSTED" -> "Email delivery is temporarily full. Try again later."
            "ROOM_CLOSED" -> "This room is closed right now. Try again when its creator opens it."
            "ROOM_FULL" -> "This room already has 10 participants."
            "ROOM_PASSWORD_INCORRECT" -> "That code didn't work. Check it with the room's creator."
            "ROOM_PASSWORD_REQUIRED" -> "Enter the room code to join."
            "ROOM_CODE_CHANGED" -> "The creator changed the code. Ask them for the new one."
            "ROOM_BANNED" -> "You cannot enter this room."
            "VOICE_DISABLED" -> "Voice Chat is not available yet."
            "VOICE_JOIN_PAUSED" -> "New voice joins are temporarily paused."
            "VOICE_ROOM_REQUIRED" -> "Create a room or enter one before sending a voice invitation."
            "VOICE_CONNECTION_FAILED", "VOICE_ICE_FAILED", "VOICE_PEER_CONNECTION_FAILED" ->
                "The secure voice connection could not be completed."
            else -> code.takeIf { it.isNotBlank() }?.replace('_', ' ')?.lowercase()
                ?.replaceFirstChar { it.uppercase() } ?: "Voice chat could not complete that action."
        }
    }

    private fun openSettings() {
        openSettingsTab()
    }

    private fun openSettingsTab() {
        refreshSettings()
        tabRoot = Route.SETTINGS
        route = Route.SETTINGS
        panelOpen = false
    }

    /** Controls is a Home destination now, not a category buried in Settings. */
    /* ------------------------------------------------------- settings search */

    /** Opens a settings page from a search result; the page reveals the row itself. */
    private fun openSettingsPage(page: Route) {
        tabRoot = Route.SETTINGS
        refreshSettings()
        panelOpen = true
        when (page) {
            Route.SETTINGS_CONTROLS -> openControls()
            Route.SETTINGS_BACKUP -> {
                backupReturn = Route.SETTINGS
                pollTransferState()
                route = page
            }
            else -> route = page
        }
    }

    private fun applyBetaUpdates(enabled: Boolean) {
        betaUpdates = enabled
        com.wyrm.omrajput.UpdateChannel.setBetaEnabled(activity, enabled)
        host?.onUpdateAction(0)
    }


    /**
     * Where a page draws an engine setting, as `WyrmSettingsIndex.place` on iOS.
     * Settings no page shows are left out, so every arrow lands on something.
     */
    private fun placeSetting(setting: Setting): Pair<Route, String>? {
        val id = setting.id
        val group = setting.group
        if (setting.label.isBlank() || id.startsWith("tags.")) return null
        // Settings › Performance owns the present mode now.
        if (id == "general.vsync") return null
        val arrowSteering = settings.named("controls.joystick_mode")?.index == 2
        return when {
            id == "general.bot_circle" || id == "general.bot_radius" -> Route.SETTINGS_BOT to "Bot"
            group == "general.bot" -> null
            group == "general" || group.startsWith("general.") -> Route.SETTINGS_GENERAL to "Display"
            group == "controls.zoom" -> Route.SETTINGS_CONTROLS to "Controls · Zoom bar"
            group == "controls.arrow" -> when {
                !arrowSteering || id == "arrow.style" -> null
                id == "arrow.color" && ArrowSkinStore.skin >= 0 -> null
                else -> Route.SETTINGS_CONTROLS to "Controls · Arrow"
            }
            id == "controls.joystick_size" -> if (arrowSteering) null else Route.SETTINGS_CONTROLS to "Controls"
            id == "controls.boost_size" ->
                if (settings.named("controls.boost_mode")?.index == 1) Route.SETTINGS_CONTROLS to "Controls" else null
            id in setOf("controls.joystick_mode", "controls.handedness", "controls.boost_mode", "controls.opacity") ->
                Route.SETTINGS_CONTROLS to "Controls"
            id == "keys.key_scale" || id == "keys.opacity" -> Route.SETTINGS_BUTTONS to "On-screen buttons"
            group == "normal" || group == "assist" -> {
                val mode = if (group == "assist") "Assist" else "Normal"
                if (setting.isFoodSetting()) Route.SETTINGS_FOOD to "Food · $mode"
                else Route.SETTINGS_ASSIST to "Modes · $mode"
            }
            else -> null
        }
    }

    /** Every setting the hub's search can find, each with its live control. */
    private fun settingsIndex(): List<SettingsSearchEntry> {
        val out = mutableListOf<SettingsSearchEntry>()
        settings.forEach { setting ->
            if (setting.id.endsWith(".bg_scale")) return@forEach
            val (page, label) = placeSetting(setting) ?: return@forEach
            out += SettingsSearchEntry(
                id = setting.id,
                title = setting.label,
                detail = setting.hint,
                page = label,
                keywords = setting.id.replace('_', ' ').replace('.', ' '),
                open = { openSettingsPage(page) },
            ) { SettingTypedRow(setting, first = true, onChange = ::writeSetting) }
        }
        out += SettingsSearchEntry(
            id = "app.bg-size",
            title = "Adjust arena background size",
            detail = "See the arena and choose how big its floor looks",
            page = "Modes",
            keywords = "background scale size floor zoom arena bg",
            open = { openSettingsPage(Route.SETTINGS_ASSIST) },
        ) {
            SettingsValueRow(
                title = "Adjust arena background size",
                value = bgScaleLabel(value("normal.bg_scale", DEFAULT_BG_SCALE)),
                first = true,
                onOpen = { openBackgroundSizeEditor(Route.SETTINGS_ASSIST) },
            )
        }
        val byAction = hotkeys.associateBy { it.action }
        listOf(1, 2, 3, 4, 6, 7, 8, 9).mapNotNull(byAction::get).forEach { key ->
            out += SettingsSearchEntry(
                id = "hotkey.${key.action}",
                title = "${key.name} button",
                detail = "Show it in matches, and whether a press toggles or holds",
                page = "On-screen buttons",
                keywords = "hotkey key toggle hold",
                open = { openSettingsPage(Route.SETTINGS_BUTTONS) },
            ) { ButtonLine(button = key, onChange = ::writeHotkey) }
        }
        if (settings.named("controls.joystick_mode")?.index == 2) {
            val style = settings.named("arrow.style")
            out += SettingsSearchEntry(
                id = "arrow.style",
                title = "Arrow style",
                detail = "Drawn and image arrows",
                page = "Controls · Arrow",
                keywords = "arrow skin steering cursor picker",
                open = { openSettingsPage(Route.SETTINGS_CONTROLS) },
            ) {
                SettingsValueRow(
                    title = "Arrow style",
                    value = if (ArrowSkinStore.skin >= 0) "Image" else style?.options?.getOrNull(style.index).orEmpty(),
                    first = true,
                    onOpen = {
                        SettingsFocus.reveal("arrow.style")
                        openSettingsPage(Route.SETTINGS_CONTROLS)
                    },
                )
            }
            out += SettingsSearchEntry(
                id = "app.arrow-brightness",
                title = "Arrow brightness",
                detail = "How bright image arrows draw",
                page = "Controls · Arrow",
                keywords = "arrow skin brightness dim",
                open = { openSettingsPage(Route.SETTINGS_CONTROLS) },
            ) {
                SettingsSliderRow(
                    title = "Brightness",
                    valueText = "${(ArrowSkinStore.brightness * 100).roundToInt()}%",
                    detail = "",
                    value = ArrowSkinStore.brightness,
                    range = 0.2f..1f,
                    steps = 0,
                    first = true,
                    onChange = { ArrowSkinStore.updateBrightness(it) },
                )
            }
        }
        out += SettingsSearchEntry(
            id = "app.play-orientation",
            title = "Play orientation",
            detail = "Landscape or portrait: the lobby, the match and the layout editor",
            page = "Controls",
            keywords = "portrait landscape upright vertical orientation rotate hold phone",
            open = { openSettingsPage(Route.SETTINGS_CONTROLS) },
        ) {
            SettingsEnumBlock(
                title = "Play orientation",
                detail = "Each way keeps its own layout",
                options = listOf("Landscape", "Portrait"),
                selected = if (com.wyrm.omrajput.data.PlayOrientation.portrait) 1 else 0,
                first = true,
                onSelect = { switchPlayOrientation(it == 1) },
            )
        }
        out += SettingsSearchEntry(
            id = "app.joystick-laser",
            title = "Assist laser in joystick",
            detail = "With assist on, a line from your head shows where your snake is heading",
            page = "Modes · Assist",
            keywords = "laser line aim joystick assist helper length guide",
            open = { openSettingsPage(Route.SETTINGS_ASSIST) },
        ) {
            SettingsBoolRow(
                title = "Assist laser in joystick",
                detail = "With assist on, a line from your head shows where your snake is heading.",
                on = JoystickLaserStore.on,
                first = true,
                onToggle = { JoystickLaserStore.applyOn(it) },
            )
        }
        out += SettingsSearchEntry(
            id = "app.performance",
            title = "Performance mode",
            detail = "Auto, Balanced or Performance: frame rate, heat and battery",
            page = "Performance",
            keywords = "fps frame rate vsync heat hot battery smooth lag 120 hz refresh balanced auto",
            open = { openSettingsPage(Route.SETTINGS_PERFORMANCE) },
        ) {
            SettingsValueRow(
                title = "Performance mode",
                value = com.wyrm.omrajput.data.WyrmPerformance.summary(),
                first = true,
                onOpen = { openSettingsPage(Route.SETTINGS_PERFORMANCE) },
            )
        }
        out += SettingsSearchEntry(
            id = "app.fps-limit",
            title = "Frame limit",
            detail = "Auto, 30, 60, 90, 120 or Max, as this display allows",
            page = "Performance",
            keywords = "fps cap limit frame rate 30 60 90 120 max",
            open = { openSettingsPage(Route.SETTINGS_PERFORMANCE) },
        ) {
            val choices = com.wyrm.omrajput.data.WyrmPerformance.limitChoices()
            SettingsEnumBlock(
                title = "Frame limit",
                detail = "Auto lets the mode decide",
                options = choices.map(com.wyrm.omrajput.data.WyrmPerformance::limitLabel),
                selected = choices.indexOf(com.wyrm.omrajput.data.WyrmPerformance.limit).coerceAtLeast(0),
                first = true,
                onSelect = { com.wyrm.omrajput.data.WyrmPerformance.applyLimit(choices[it]) },
            )
        }
        out += SettingsSearchEntry(
            id = "app.theme",
            title = "Theme",
            detail = "Paper, dark and colour appearances",
            page = "Accessibility",
            keywords = "appearance dark mode colour color " + WyrmThemeId.entries.joinToString(" ") { it.displayName },
            open = { openSettingsPage(Route.SETTINGS_ACCESSIBILITY) },
        ) {
            SettingsEnumBlock(
                title = "Theme",
                detail = appTheme.description,
                options = WyrmThemeId.entries.map { it.displayName },
                selected = appTheme.ordinal,
                first = true,
                onSelect = { selectAppTheme(WyrmThemeId.entries[it]) },
            )
        }
        out += SettingsSearchEntry(
            id = "app.theme-intensity",
            title = "Theme intensity",
            detail = "50% is the original look",
            page = "Accessibility",
            keywords = "strength richer appearance",
            open = { openSettingsPage(Route.SETTINGS_ACCESSIBILITY) },
        ) {
            SettingsSliderRow(
                title = "Theme intensity",
                valueText = "${(themeIntensity * 100f).roundToInt()}%",
                detail = "",
                value = themeIntensity,
                range = 0f..1f,
                steps = 0,
                first = true,
                onChange = ::applyThemeIntensity,
            )
        }
        out += SettingsSearchEntry(
            id = "app.beta-updates",
            title = "Beta updates",
            detail = "Early builds before everyone else; they can have rough edges",
            page = "Updates & version",
            keywords = "update beta test early stable channel version",
            open = { openSettingsPage(Route.SETTINGS_BACKUP) },
        ) {
            SettingsBoolRow(
                title = "Beta updates",
                detail = "Get early builds before everyone else. Turn this off to get stable updates only.",
                on = betaUpdates,
                first = true,
                onToggle = ::applyBetaUpdates,
            )
        }
        out += SettingsSearchEntry(
            id = "app.crash.auto",
            title = "Always send crash reports",
            detail = "If Wyrm closes unexpectedly, the report goes without asking",
            page = "Help & feedback",
            keywords = "crash report bug problem feedback support",
            open = { openSettingsPage(Route.HELP) },
        ) {
            SettingsBoolRow(
                title = "Always send crash reports",
                detail = "If Wyrm closes unexpectedly, the report goes without asking.",
                on = com.wyrm.omrajput.data.CrashWatch.autoSend,
                first = true,
                onToggle = { com.wyrm.omrajput.data.CrashWatch.applyAutoSend(it) },
            )
        }
        out += SettingsSearchEntry(
            id = "app.drop.auto",
            title = "Always send drop reports",
            detail = "If an arena drops you mid-match, the report goes without asking",
            page = "Help & feedback",
            keywords = "drop disconnect arena kicked network report feedback support",
            open = { openSettingsPage(Route.HELP) },
        ) {
            SettingsBoolRow(
                title = "Always send drop reports",
                detail = "If an arena drops you mid-match, the report goes without asking.",
                on = com.wyrm.omrajput.data.DropWatch.autoSend,
                first = true,
                onToggle = { com.wyrm.omrajput.data.DropWatch.applyAutoSend(it) },
            )
        }
        out += SettingsSearchEntry(
            id = "app.help",
            title = "Help & feedback",
            detail = "Report a problem, suggest an idea, ask for help",
            page = "Settings",
            keywords = "support bug crash problem idea suggestion feedback contact question faq",
            open = { openSettingsPage(Route.HELP) },
        ) {
            SettingsValueRow(title = "Help & feedback", value = "", first = true, onOpen = { openSettingsPage(Route.HELP) })
        }
        out += SettingsSearchEntry(
            id = "app.notify.all",
            title = "All notifications",
            detail = "The Android permission for Wyrm",
            page = "Notifications",
            keywords = "alerts push permission",
            open = { openSettingsPage(Route.SETTINGS_NOTIFICATIONS) },
        ) {
            SettingsBoolRow(
                title = "All notifications",
                detail = "Tap to manage the Android permission.",
                on = notificationsAllowed,
                first = true,
                onToggle = { NotificationPreferences.openSystemSettings(activity) },
            )
        }
        notificationGroups.forEach { group ->
            group.rows.forEach { choice ->
                out += SettingsSearchEntry(
                    id = "app.notify.${choice.kind}",
                    title = choice.title,
                    detail = choice.detail,
                    page = "Notifications · ${group.title}",
                    keywords = "notification alert push",
                    open = { openSettingsPage(Route.SETTINGS_NOTIFICATIONS) },
                ) {
                    val on = notificationsAllowed && choice.kind in enabledNotificationKinds
                    Box(Modifier.alpha(if (notificationsAllowed) 1f else 0.46f)) {
                        SettingsBoolRow(
                            title = choice.title,
                            detail = choice.detail,
                            on = on,
                            first = true,
                            onToggle = {
                                if (notificationsAllowed) {
                                    setNotificationKindEnabled(choice.kind, choice.kind !in enabledNotificationKinds)
                                }
                            },
                        )
                    }
                }
            }
        }
        return out
    }

    private fun openControls() {
        refreshSettings()
        controlsWorkspaceTab = ControlsWorkspaceTab.CONTROLS
        route = Route.SETTINGS_CONTROLS
    }

    private fun settingsBackLabel(): String = when (tabRoot) {
        Route.HOME -> "Play"
        Route.TRAILS -> "Trails"
        Route.SOCIAL -> "Social"
        Route.SKIN -> "Skin"
        else -> "Settings"
    }

    private fun selectAppTheme(theme: WyrmThemeId) {
        if (theme == appTheme) return
        appTheme = theme
        Wyrm.applyTheme(theme, themeIntensity)
        uiPreferences.edit().putString("theme", theme.storedName).apply()
        publishArenaTheme()
    }

    private fun applyThemeIntensity(value: Float) {
        val next = value.coerceIn(0f, 1f)
        if (kotlin.math.abs(next - themeIntensity) < 0.0001f) return
        themeIntensity = next
        Wyrm.applyTheme(appTheme, next)
        uiPreferences.edit().putFloat("theme_intensity", next).apply()
        publishArenaTheme()
    }

    private fun publishArenaTheme(target: Host? = host) {
        val palette = Wyrm.currentPalette
        target?.onArenaTheme(
            intArrayOf(
                palette.paper.toArgb(), palette.card.toArgb(), palette.ink.toArgb(),
                palette.onInk.toArgb(), palette.quiet.toArgb(), palette.mute.toArgb(),
                palette.rule.toArgb(), palette.live.toArgb(), palette.link.toArgb(),
                palette.well.toArgb(), palette.track.toArgb(), palette.badge.toArgb(),
            ),
            palette.dark,
        )
    }

    /**
     * The skin editor needs the settings too, and this is why the tag tab was
     * dead.
     *
     * `settings` is a snapshot and nothing but this filled it, so a player who
     * went Home -> Skin arrived with an empty list. The tags tab is built out
     * of that list: no `tags.index` in it means no setting to write, so tapping
     * a tag called nothing, and no tag settings in it means the gear opened an
     * empty panel — which reads as a button that does nothing. Visiting
     * Settings first brought both back to life, which is what named the cause.
     */
    private fun openSkinTab() {
        refreshSettings()
        tabRoot = Route.SKIN
        route = Route.SKIN
        panelOpen = false
    }

    private fun openSkinEditor(tab: Int = 0) {
        refreshSettings()
        skinEditTab = tab
        skinReady = false
        route = Route.SKIN_EDIT
    }

    private fun refreshSettings() {
        settings = SettingsCodec.settings(host?.onReadSettings().orEmpty())
        hotkeys = SettingsCodec.hotkeys(host?.onReadHotkeys().orEmpty())
        if (settingsVersion.isEmpty()) {
            settingsVersion = host?.onReadSettingsVersion().orEmpty()
        }
    }

    /**
     * Re-reads after asking the engine to do something to itself.
     *
     * Resets and handedness swaps are applied on the engine's own thread on its
     * next frame, so reading immediately would read the old values back. One
     * frame is all it takes; this waits for several.
     */
    private fun refreshSettingsSoon() {
        scope.launch {
            delay(150)
            refreshSettings()
        }
    }

    /** From the beta prompt: Settings › Backup, with the Beta updates switch lit. */
    private fun openBetaUpdateSetting() {
        dismissUpdatePrompt()
        tabRoot = Route.SETTINGS
        backupReturn = Route.SETTINGS
        pollTransferState()
        panelOpen = true
        route = Route.SETTINGS_BACKUP
        SettingsFocus.reveal("app.beta-updates")
    }

    private fun openSettingsBackup() {
        backupReturn = Route.SETTINGS
        pollTransferState()
        route = Route.SETTINGS_BACKUP
    }

    /**
     * "What's new" shows by itself once, on the first Home after an UPDATE
     * (OM, 2026-10-02: it was showing on every launch). A fresh install never
     * shows it. The notes are fetched first: only notes that loaded are shown,
     * and a build whose notes cannot be read (a test build has none) is marked
     * seen silently, so it can never come back on each start. Closing the
     * sheet marks it seen; Settings › Updates still opens it any time.
     */
    private fun maybeShowAutomaticWhatsNew() {
        if (automaticWhatsNewRequested || whatsNewState != null) return
        if (!releaseNotes.needsAcknowledgement(BuildConfig.VERSION_CODE)) return
        automaticWhatsNewRequested = true
        val freshInstall = runCatching {
            val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
            info.firstInstallTime == info.lastUpdateTime
        }.getOrDefault(false)
        if (freshInstall) {
            releaseNotes.acknowledge(BuildConfig.VERSION_CODE)
            return
        }
        val request = WhatsNewState(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            automatic = true,
            newVersion = true,
            title = "Wyrm ${BuildConfig.VERSION_NAME}",
        )
        scope.launch {
            runCatching { releaseNotes.fetch(request.versionName) }
                .onSuccess { notes ->
                    if (whatsNewState != null) return@onSuccess
                    whatsNewBackdropVisible = true
                    whatsNewState = request.copy(
                        title = notes.title,
                        markdown = notes.markdown,
                        loading = false,
                        error = "",
                    )
                }
                .onFailure { releaseNotes.acknowledge(request.versionCode) }
        }
    }

    /** Manual history and automatic first-view both use the same exact tag. */
    private fun openWhatsNew(
        versionName: String,
        versionCode: Int = 0,
        automatic: Boolean,
        newVersion: Boolean,
    ) {
        val initial = WhatsNewState(
            versionName = versionName,
            versionCode = versionCode,
            automatic = automatic,
            newVersion = newVersion,
            title = "Wyrm $versionName",
        )
        whatsNewBackdropVisible = true
        whatsNewState = initial
        loadWhatsNew(initial)
    }

    private fun loadWhatsNew(request: WhatsNewState) {
        whatsNewState = request.copy(loading = true, error = "", markdown = "")
        scope.launch {
            runCatching { releaseNotes.fetch(request.versionName) }
                .onSuccess { notes ->
                    whatsNewState = request.copy(
                        title = notes.title,
                        markdown = notes.markdown,
                        loading = false,
                        error = "",
                    )
                }
                .onFailure { error ->
                    whatsNewState = request.copy(
                        loading = false,
                        error = error.message ?: "Release notes could not be reached.",
                    )
                }
        }
    }

    /** Persists one device-local category without rewriting the native settings file. */
    private fun setNotificationKindEnabled(kind: String, enabled: Boolean) {
        NotificationPreferences.setKindEnabled(activity, kind, enabled)
        enabledNotificationKinds = NotificationPreferences.enabledKinds(activity)
    }

    /** Reconciles the app screen after Android's own notification settings close. */
    private fun refreshNotificationPreferences() {
        notificationsAllowed = NotificationPreferences.systemEnabled(activity)
        enabledNotificationKinds = NotificationPreferences.enabledKinds(activity)
    }

    /**
     * Writes one setting through to the engine, and locally at once.
     *
     * The local copy is updated without waiting for a round trip because a
     * slider that lags a thumb by a frame feels broken; the engine is still the
     * one that persists it.
     */
    private fun writeSetting(setting: Setting, values: List<Float>) {
        host?.onWriteSetting(setting.id, values.toFloatArray())
        val handednessChanged = setting.id == "controls.handedness" &&
            setting.index != values.first().toInt()
        val raw = when (setting.type) {
            SettingType.COLOR3, SettingType.COLOR4 ->
                values.joinToString(",") { formatSettingNumber(it) }
            SettingType.BOOL -> if (values.first() != 0f) "1" else "0"
            else -> formatSettingNumber(values.first())
        }
        settings = settings.map { current ->
            when {
                current.id == setting.id -> current.copy(raw = raw)
                handednessChanged && current.id in setOf("layout.joystick_x", "layout.boost_x") -> {
                    val mirrored = 1f - sanitizeLayoutPosition(
                        androidx.compose.ui.geometry.Offset(current.number, 0.5f)
                    ).x
                    current.copy(raw = formatSettingNumber(mirrored))
                }
                else -> current
            }
        }
        // Swapping hands mirrors the layout as well, so the positions this
        // screen holds are no longer the engine's.
        if (setting.id == "controls.handedness") refreshSettingsSoon()
    }

    private fun writeHotkey(hotkey: Hotkey) {
        host?.onWriteHotkey(
            hotkey.action, hotkey.key, hotkey.mode, hotkey.visible, hotkey.x, hotkey.y,
        )
        hotkeys = hotkeys.map { if (it.action == hotkey.action) hotkey else it }
        // A key's printed name belongs to the engine, so a rebind is read back
        // rather than guessed at.
        if (hotkeys.any { it.action == hotkey.action && it.key != hotkey.key }) refreshSettingsSoon()
    }

    private fun value(id: String, fallback: Float = 0f): Float =
        settings.firstOrNull { it.id == id }?.number ?: fallback

    private fun flag(id: String): Boolean = settings.firstOrNull { it.id == id }?.enabled ?: false

    private fun layoutPosition(xId: String, yId: String) =
        androidx.compose.ui.geometry.Offset(value(xId, 0.5f), value(yId, 0.7f))

    private fun moveControl(target: LayoutTarget, position: androidx.compose.ui.geometry.Offset) {
        val prefix = when (target) {
            LayoutTarget.JOYSTICK -> "layout.joystick"
            LayoutTarget.BOOST -> "layout.boost"
            LayoutTarget.ZOOM -> "layout.zoom"
        }
        val safe = sanitizeLayoutPosition(position)
        host?.onWriteSetting(prefix, floatArrayOf(safe.x, safe.y))
        settings = settings.map { setting ->
            when (setting.id) {
                "${prefix}_x" -> setting.copy(raw = formatSettingNumber(safe.x))
                "${prefix}_y" -> setting.copy(raw = formatSettingNumber(safe.y))
                else -> setting
            }
        }
    }

    private fun moveArenaHud(
        target: ArenaHudTarget,
        position: androidx.compose.ui.geometry.Offset,
    ) {
        val safe = sanitizeLayoutPosition(position, target.fallback)
        host?.onWriteSetting(target.prefix, floatArrayOf(safe.x, safe.y))
        settings = settings.map { setting ->
            when (setting.id) {
                "${target.prefix}_x" -> setting.copy(raw = formatSettingNumber(safe.x))
                "${target.prefix}_y" -> setting.copy(raw = formatSettingNumber(safe.y))
                else -> setting
            }
        }
    }

    private fun moveKey(action: Int, position: androidx.compose.ui.geometry.Offset) {
        val hotkey = hotkeys.firstOrNull { it.action == action } ?: return
        val moved = hotkey.copy(x = position.x, y = position.y)
        host?.onWriteHotkey(moved.action, moved.key, moved.mode, moved.visible, moved.x, moved.y)
        hotkeys = hotkeys.map { if (it.action == action) moved else it }
    }

    /** Where the background-size editor goes back to: Modes, or the Skin tab. */
    private var backgroundEditorReturn = Route.SETTINGS_ASSIST

    /**
     * Adjust arena background size (OM, 2026-10-01): the AI arena sideways with
     * one slider. One size for both modes, written live; Cancel puts it back.
     */
    private fun openBackgroundSizeEditor(from: Route) {
        backgroundEditorReturn = from
        host?.onSetEditorBare(true)
        openEditor(Route.BACKGROUND_SIZE_EDITOR)
    }

    private fun setBackgroundScale(scale: Float) {
        listOf("normal.bg_scale", "assist.bg_scale").forEach { id ->
            settings.named(id)?.let { writeSetting(it, listOf(scale)) }
        }
    }

    /** The editors are the only Compose screens that want the phone sideways. */
    private fun openEditor(editor: Route) {
        editorSettingsSnapshot = settings
        editorHotkeysSnapshot = hotkeys
        host?.onRequestLandscape(true)
        route = editor
        host?.onEnterAiLayoutEditor(nickname.ifBlank { "Wyrm Player" })
    }

    private fun closeEditor(back: Route, save: Boolean) {
        if (!save) restoreEditorSnapshot(route)
        layoutEditorExitPending = true
        host?.onExitAiLayoutEditor()
        host?.onRequestLandscape(false)
        route = back
        refreshSettingsSoon()
    }

    private fun restoreEditorSnapshot(editor: Route) {
        fun restorePair(prefix: String) {
            val x = editorSettingsSnapshot.firstOrNull { it.id == "${prefix}_x" }
                ?.raw?.toFloatOrNull() ?: return
            val y = editorSettingsSnapshot.firstOrNull { it.id == "${prefix}_y" }
                ?.raw?.toFloatOrNull() ?: return
            host?.onWriteSetting(prefix, floatArrayOf(x, y))
        }
        /* One canvas edits every group, so Cancel restores every group too. */
        listOf("layout.joystick", "layout.boost", "layout.zoom").forEach(::restorePair)
        ArenaHudTarget.entries.forEach { restorePair(it.prefix) }
        editorHotkeysSnapshot.forEach {
            host?.onWriteHotkey(it.action, it.key, it.mode, it.visible, it.x, it.y)
        }
        editorSettingsSnapshot.filter { it.id in setOf(
            "controls.joystick_size", "controls.boost_size", "controls.zoom_length", "controls.zoom_orientation", "controls.opacity",
            "keys.key_scale", "keys.opacity", "general.minimap_size", "general.lb_font", "general.stats_font",
            "layout.joystick_opacity", "layout.boost_opacity", "layout.zoom_opacity",
            "layout.stats_scale", "layout.stats_opacity", "layout.chat_scale", "layout.chat_opacity",
            "normal.bg_scale", "assist.bg_scale",
        ) }.forEach { setting ->
            setting.raw.toFloatOrNull()?.let { host?.onWriteSetting(setting.id, floatArrayOf(it)) }
        }
        editorSettingsSnapshot.filter { it.id.startsWith("layout.key_") }.forEach { setting ->
            setting.raw.toFloatOrNull()?.let { host?.onWriteSetting(setting.id, floatArrayOf(it)) }
        }
        settings = editorSettingsSnapshot
        hotkeys = editorHotkeysSnapshot
    }

    /* ---------------------------------------------------------- the update */

    /** Whether the prompt should be on screen this frame. */
    private val updatePromptVisible: Boolean
        get() = updatePromptShown &&
            (route != Route.SETTINGS_UPDATES || updateStarted)

    private val backupPromptVisible: Boolean
        get() = backupPromptShown && route != Route.SETTINGS_BACKUP

    /** A completed manual restore is modal even over Settings > Backup itself. */
    private val backupResultPromptVisible: Boolean
        get() = backupPromptShown && (backupState.restoring || backupState.restored ||
            backupState.partial || (backupState.failed && backupRestoreInFlight))

    /**
     * Watches for a new version, for the life of the app.
     *
     * The updater checks once per launch on its own; this reads the answer and
     * decides whether the player needs to know. Once the prompt is up it keeps
     * reading, because the same card then has to show a download arriving and
     * an install starting. It asks slowly while nothing is happening and
     * quickly while something is.
     */
    private fun watchForUpdates() {
        scope.launch {
            while (true) {
                val snapshot = host?.onReadUpdate().orEmpty()
                val next = SettingsCodec.update(snapshot)
                val nextBackup = SettingsCodec.backup(snapshot)
                updateState = next
                applyBackupState(nextBackup)
                when {
                    // Anything in flight keeps the card up and honest, and
                    // cannot be dismissed away.
                    next.busy && updatePromptShown -> Unit
                    next.available && !promptDismissed -> updatePromptShown = true
                    next.failed && updatePromptShown -> Unit
                    !next.available && !next.busy && !next.failed -> updatePromptShown = false
                    else -> Unit
                }
                if (next.status == 2 || next.status == 8) updateStarted = false
                delay(if (updatePromptShown || backupPromptShown) 500L else 4_000L)
            }
        }
    }

    /** Files one account-bound receipt when a backup operation reaches a terminal state. */
    private fun onBackupState(state: BackupState) {
        val finished = state.saved || state.partial || state.failed || state.restored
        if (!finished || state.status == lastBackupReceiptStatus || profile.id.isBlank()) {
            lastBackupReceiptStatus = state.status
            return
        }

        val title = when {
            state.saved -> "Backup saved"
            state.partial -> "Backup saved, partly"
            state.restored -> "Backup restored"
            else -> "Backup failed"
        }
        val included = """
- [x] In-game name and arena
- [x] Saved arena IPs
- [x] Skin, pattern and accessory
- [x] Tags
- [x] Every game setting
- [x] Both visual modes
- [x] Controls and on-screen buttons
- [x] On-screen layouts
- [x] Local career stats
- [ ] Account session — *never written to a portable file*
- [ ] Team Auth Key — *never written to a portable file*
        """.trimIndent()
        val body = when {
            state.failed -> "> ${state.detail.ifBlank { "The backup could not be written." }}\n\n" +
                "Nothing was written. Your existing backups are untouched."
            state.partial -> "> ${state.detail.ifBlank { "Some items could not be restored." }}\n\n$included"
            else -> listOf(state.detail.takeIf { it.isNotBlank() }, included).filterNotNull()
                .joinToString("\n\n")
        }
        localNotifications.add(profile.id, title, body)
        mergeNotifications(notifications.filter { row -> row.kind != NotificationKind.BACKUP })
        if (!activityResumed) {
            WyrmMessagingService.showBackupReceipt(activity, title, state.detail)
        }
        lastBackupReceiptStatus = state.status
    }

    /**
     * Claims a private tag.
     *
     * The id and the password belong to whoever was given them, and they are
     * checked by the tag service rather than here — Wyrm has no way of knowing
     * which tags exist, let alone who is entitled to one, and should not
     * pretend to. Whatever comes back is added after the tags this build
     * already carries.
     */
    private fun fetchTag(code: String) {
        // A player who was given a tag was given the mod's command with it, so
        // that is what they type; this field asks for "id password". Both are
        // taken. Sending "!tag" itself as the id is what the service was being
        // asked about the first time this was tried.
        val parts = code.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .dropWhile { it.equals("!tag", ignoreCase = true) }
        if (parts.size < 2) {
            tagFetchState = "Needs an id and a password"
            return
        }
        val id = parts[0]
        tagFetchState = "Checking…"
        scope.launch {
            val answer = runCatching { team.claimTag(id, parts[1]) }
                .getOrNull() ?: "That id and password were not accepted"
            // The service authenticates and stops there — it sends back one
            // word and no artwork. A private tag's picture lives on NTL's side
            // and Wyrm ships only its own set, so reporting this as "claimed"
            // and then showing nothing would be the misleading half of it.
            tagFetchState = if (answer == TeamService.TAG_CLAIMED) {
                "Tag $id is yours — Wyrm has no artwork for it yet"
            } else {
                answer
            }
        }
    }

    /** Not now. The next launch will ask again. */
    private fun dismissUpdatePrompt() {
        promptDismissed = true
        updatePromptShown = false
        updateStarted = false
    }

    /**
     * Starts the download, and keeps the card.
     *
     * From here the player watches rather than decides: the engine backs up
     * their settings, fetches the build, checks it, and hands it to Android to
     * install. The card follows all of it.
     */
    private fun startUpdate() {
        updateStarted = true
        host?.onUpdateAction(1)
    }

    private fun dismissBackupPrompt() {
        backupPromptDismissed = true
        backupPromptShown = false
        backupRestoreInFlight = false
    }

    private fun restoreBackup() {
        backupPromptDismissed = false
        backupRestoreInFlight = true
        backupPromptShown = true
        host?.onBackupAction(2)
    }

    /** Shows feedback in the pressed button before the native snapshot catches up. */
    private fun requestBackupAction(action: BackupAction, nativeAction: Int) {
        val continuingFolderConsent = action == BackupAction.CHOOSE_FOLDER &&
            backupState.selectingFolder
        if ((!continuingFolderConsent && backupAction != null) || backupState.busy) return
        val target = host ?: return
        if (action == BackupAction.RESTORE) {
            backupPromptDismissed = false
            backupRestoreInFlight = true
        }
        backupAction = action
        backupActionStartedAt = System.currentTimeMillis()
        target.onBackupAction(nativeAction)
    }

    private fun closeBackupPrompt() {
        backupPromptShown = false
        backupPromptDismissed = true
        backupRestoreInFlight = false
        // Replace the terminal report under the modal with a fresh view of the
        // persisted folder. A valid selection is reused without another picker.
        if (route == Route.SETTINGS_BACKUP) {
            requestBackupAction(BackupAction.CHECK, 1)
        }
        refreshSettingsSoon()
    }

    /**
     * One reducer owns backup actions, folder consent and the restore modal.
     * Both polling loops pass through here, so a result cannot wait for a route
     * change before Compose notices it.
     */
    private fun applyBackupState(next: BackupState) {
        val previous = backupState
        onBackupState(next)
        backupState = next

        val action = backupAction
        if (action != null) {
            val folderConsentReady = next.selectingFolder &&
                (action == BackupAction.CREATE || action == BackupAction.CHECK)
            val acknowledged = next.status != previous.status || next.busy ||
                next.saved || next.found || next.restored || next.partial || next.failed ||
                next.status == 6 || next.status == 11
            val timedOut = !next.selectingFolder &&
                System.currentTimeMillis() - backupActionStartedAt > 8_000L
            if (folderConsentReady || acknowledged || timedOut) {
                backupAction = null
            }
        }

        when {
            next.restoring -> {
                backupRestoreInFlight = true
                backupPromptDismissed = false
                backupPromptShown = true
            }
            (next.restored || next.partial) &&
                (backupRestoreInFlight || previous.restoring) -> backupPromptShown = true
            next.failed && (backupRestoreInFlight || previous.restoring) ->
                backupPromptShown = true
            next.restoreOffer && !backupPromptDismissed -> backupPromptShown = true
            !next.restoreOffer && !next.restoring && !next.restored && !next.partial &&
                !(next.failed && backupRestoreInFlight) -> backupPromptShown = false
        }
    }

    /**
     * Follows the updater while its screen is open.
     *
     * The updater reports to the engine, not here, so this reads its state on a
     * timer rather than being told — cheap, and it stops the moment the screen
     * is left.
     */
    private fun pollTransferState() {
        scope.launch {
            while (route == Route.SETTINGS_UPDATES || route == Route.SETTINGS_BACKUP) {
                val snapshot = host?.onReadUpdate().orEmpty()
                updateState = SettingsCodec.update(snapshot)
                val nextBackup = SettingsCodec.backup(snapshot)
                applyBackupState(nextBackup)
                delay(500)
            }
        }
    }

    // Where the engine should draw, in framebuffer pixels. Both halves are sent
    // together because the engine holds one layout, not two.
    private var previewCentreY = -1f
    private var previewScale = 48f
    private var accessoryX = 0f
    private var accessoryY = 0f
    private var accessoryCell = 0f
    private var accessoryGap = 0f

    private fun pushSkinLayout() {
        host?.onSkinLayout(
            previewCentreY, previewScale,
            accessoryX, accessoryY, accessoryCell, accessoryGap,
        )
    }

    /** Chooses the Compose screen from the engine's current screen. */
    fun showRoute(next: Route) {
        activity.runOnUiThread {
            if (next == Route.HOME) enteringArena = false
            // Leaving the skin editor tells the engine first and closes the
            // panel afterwards. The engine answers within a frame, and taking
            // that answer literally would drop Home in place and cut the
            // closing animation off at its first frame.
            if (next == Route.HOME && route == Route.SKIN_EDIT && panelOpen) return@runOnUiThread
            // Share run leaves the lobby first; the engine then reports its
            // title screen, which means Home everywhere else but not here.
            if (next == Route.HOME && route == Route.TRAIL_SHARE) return@runOnUiThread
            route = if (next == Route.HOME && !repository.hasSession) Route.AUTH else next
            if (route == Route.LOBBY) watchLobbyArena()
            else lobbyJob?.cancel()
            if (route == Route.PROFILE) refreshProfile()
        }
    }

    fun updateSkinTables(palette: IntArray, codeChars: ByteArray, presets: ByteArray) {
        activity.runOnUiThread {
            skinTables = SkinTables(palette, codeChars, presets)
        }
    }

    /*
     * Arena skins.
     *
     * The whole point of this is that it costs the backend almost nothing: one
     * write when the arena admits us, one delete on the way out, and one batched
     * read per group of snakes we have never met. Everything answered is cached
     * for the rest of the arena session, including the answer "not a Wyrm
     * player", which is why a busy arena settles into silence.
     */
    /**
     * The arena-skin side channel is switched off (OM, 2026-09-28): nothing
     * sends this player's skin code to the backend in the background, and no
     * other snakes' skins are asked for. As `arenaSkinSyncDisabled` on iOS.
     * Set to false to bring it back.
     */
    private val ARENA_SKIN_SYNC_DISABLED = true
    private var arenaSkinArena = ""
    private var arenaSkinGeneration: String? = null
    private val arenaSkinLifecycle = Mutex()
    private val arenaSkinPending = linkedSetOf<Int>()
    private val arenaSkinInFlight = mutableSetOf<Int>()
    private val arenaSkinRetryAt = mutableMapOf<Int, Long>()
    private val arenaSkinAttempts = mutableMapOf<Int, Int>()
    private val arenaSkinsResolved = object : LinkedHashMap<Int, Long>(160, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Long>) = size > 128
    }

    private fun resetArenaSkinCache() {
        arenaSkinPending.clear()
        arenaSkinInFlight.clear()
        arenaSkinRetryAt.clear()
        arenaSkinAttempts.clear()
        arenaSkinsResolved.clear()
        WyrmActivity.clearArenaSkins()
    }

    fun publishArenaSkin(arena: String, snakeId: Int, nickname: String) {
        if (ARENA_SKIN_SYNC_DISABLED) return
        if (snakeId < 0 || arena.isBlank()) {
            val leaving = arenaSkinGeneration
            if (leaving != null) {
                arenaSkinArena = ""
                arenaSkinGeneration = null
                resetArenaSkinCache()
                scope.launch {
                    arenaSkinLifecycle.withLock {
                        runCatching { repository.clearArenaSkin(leaving) }
                    }
                }
            }
            return
        }
        // A new arena, or a new life: nothing learned in the last one applies.
        val previous = arenaSkinGeneration
        val generation = UUID.randomUUID().toString()
        arenaSkinArena = arena
        arenaSkinGeneration = generation
        resetArenaSkinCache()
        val state = skinState
        if (!state.custom || state.code.isEmpty()) {
            if (previous != null) scope.launch {
                arenaSkinLifecycle.withLock {
                    runCatching { repository.clearArenaSkin(previous) }
                }
            }
            return
        }
        val colours = state.coloursFor(state.code.length)
        if (colours.none { it != 0 }) {
            if (previous != null) scope.launch {
                arenaSkinLifecycle.withLock {
                    runCatching { repository.clearArenaSkin(previous) }
                }
            }
            return
        }
        scope.launch {
            var attempt = 0
            while (arenaSkinGeneration == generation) {
                val published = arenaSkinLifecycle.withLock {
                    if (previous != null && attempt == 0)
                        runCatching { repository.clearArenaSkin(previous) }
                    if (arenaSkinGeneration != generation) return@withLock true
                    runCatching {
                        repository.publishArenaSkin(
                            arena, snakeId, nickname, state.code, colours, generation,
                        )
                    }.isSuccess
                }
                if (arenaSkinGeneration != generation) break
                if (published) {
                    // The backend keeps a row for two minutes; a longer life
                    // republishes the same generation so late arrivals still
                    // see this skin (Wyrm iOS's 60 s heartbeat).
                    attempt = 1
                    delay(60_000L)
                    continue
                }
                attempt++
                delay((250L shl attempt.coerceAtMost(5)).coerceAtMost(10_000L))
            }
        }
    }

    fun requestArenaSkins(snakeIds: IntArray) {
        if (ARENA_SKIN_SYNC_DISABLED) return
        val arena = arenaSkinArena
        val generation = arenaSkinGeneration ?: return
        if (arena.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        snakeIds.asSequence().filter { it >= 0 }.distinct().forEach { id ->
            val resolvedAt = arenaSkinsResolved[id]
            if (resolvedAt != null && now - resolvedAt < 30_000L) return@forEach
            if (resolvedAt != null) arenaSkinsResolved.remove(id)
            if (id !in arenaSkinInFlight && (arenaSkinRetryAt[id] ?: 0L) <= now)
                arenaSkinPending.add(id)
        }
        val wanted = arenaSkinPending.take(64)
        if (wanted.isEmpty()) return
        arenaSkinPending.removeAll(wanted.toSet())
        arenaSkinInFlight.addAll(wanted)
        scope.launch {
            val result = runCatching { repository.arenaSkins(arena, wanted) }
            if (arenaSkinGeneration != generation) return@launch
            arenaSkinInFlight.removeAll(wanted.toSet())
            result.onSuccess { skins ->
                val resolvedAt = SystemClock.elapsedRealtime()
                wanted.forEach { id ->
                    arenaSkinsResolved[id] = resolvedAt
                    arenaSkinRetryAt.remove(id)
                    arenaSkinAttempts.remove(id)
                }
                skins.forEach { skin ->
                    WyrmActivity.applyArenaSkin(skin.snakeId, skin.nickname, skin.colours)
                }
            }.onFailure {
                val failedAt = SystemClock.elapsedRealtime()
                wanted.forEach { id ->
                    val attempts = (arenaSkinAttempts[id] ?: 0) + 1
                    arenaSkinAttempts[id] = attempts
                    arenaSkinRetryAt[id] = failedAt +
                        (250L shl attempts.coerceAtMost(5)).coerceAtMost(10_000L)
                }
            }
        }
    }

    fun updateSkinState(custom: Boolean, preset: Int, code: String, accessory: Int,
                        colours: IntArray, background: Int) {
        activity.runOnUiThread {
            skinState = SkinState(custom, preset, code, accessory, colours)
            arenaBackground = background
            // The engine only publishes this on its way into the editor, so it
            // is the moment the preview exists to be shown.
            skinReady = true
        }
    }

    /**
     * Raises or drops the death card.
     *
     * Deliberately separate from [showRoute]: the engine is still on PLAYING
     * throughout, the phone stays landscape, and the arena keeps rendering
     * behind the card. Nothing about the death card is a change of screen.
     */
    fun setDeath(shown: Boolean, score: Int, kills: Int, playTimeSeconds: Double,
                 autoRespawnOn: Boolean) {
        activity.runOnUiThread {
            autoRespawn = autoRespawnOn
            enteringArena = false
            if (shown) {
                deathStats = DeathStats(score, kills, playTimeSeconds)
                route = Route.DEATH
            } else {
                if (route == Route.DEATH) route = Route.HOME
            }
        }
    }

    /* ---------------------------------------------------- play orientation */

    /**
     * Play orientation (OM, 2026-10-01; data/PlayOrientation.kt). The lobby,
     * the match and the layout editors turn upright when this is on.
     */
    fun playPortrait(): Boolean = com.wyrm.omrajput.data.PlayOrientation.portrait

    /** The positions that belong to one orientation: controls, then the HUD pieces. */
    private val orientationPairs: List<String>
        get() = listOf("layout.joystick", "layout.boost", "layout.zoom") + ArenaHudTarget.entries.map { it.prefix }

    /** This orientation's layout as the engine holds it now, or null before the engine has described it. */
    private fun captureLayout(): org.json.JSONObject? {
        val pairs = org.json.JSONObject()
        for (prefix in orientationPairs) {
            val x = settings.firstOrNull { it.id == "${prefix}_x" }?.raw?.toFloatOrNull() ?: return null
            val y = settings.firstOrNull { it.id == "${prefix}_y" }?.raw?.toFloatOrNull() ?: return null
            pairs.put(prefix, org.json.JSONArray().put(x.toDouble()).put(y.toDouble()))
        }
        val keys = org.json.JSONObject()
        hotkeys.forEach { keys.put(it.action.toString(), org.json.JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }
        return org.json.JSONObject().put("pairs", pairs).put("keys", keys)
    }

    /** Writes a stored layout's positions to the engine (sizes and opacities are shared). */
    private fun applyLayout(layout: org.json.JSONObject) {
        val pairs = layout.optJSONObject("pairs")
        for (prefix in orientationPairs) {
            val xy = pairs?.optJSONArray(prefix) ?: continue
            val fallback = ArenaHudTarget.entries.firstOrNull { it.prefix == prefix }?.fallback
                ?: androidx.compose.ui.geometry.Offset(0.5f, 0.7f)
            val safe = sanitizeLayoutPosition(
                androidx.compose.ui.geometry.Offset(xy.optDouble(0, Double.NaN).toFloat(), xy.optDouble(1, Double.NaN).toFloat()),
                fallback,
            )
            host?.onWriteSetting(prefix, floatArrayOf(safe.x, safe.y))
            settings = settings.map { setting ->
                when (setting.id) {
                    "${prefix}_x" -> setting.copy(raw = formatSettingNumber(safe.x))
                    "${prefix}_y" -> setting.copy(raw = formatSettingNumber(safe.y))
                    else -> setting
                }
            }
        }
        val keys = layout.optJSONObject("keys") ?: return
        hotkeys = hotkeys.map { hotkey ->
            val xy = keys.optJSONArray(hotkey.action.toString()) ?: return@map hotkey
            val x = xy.optDouble(0, Double.NaN).toFloat()
            val y = xy.optDouble(1, Double.NaN).toFloat()
            if (!x.isFinite() || !y.isFinite()) return@map hotkey
            val moved = hotkey.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f))
            host?.onWriteHotkey(moved.action, moved.key, moved.mode, moved.visible, moved.x, moved.y)
            moved
        }
    }

    /**
     * A first layout for an orientation that has none yet. Upright: the stick
     * low on the thumb's side, boost low on the other, zoom along the bottom,
     * map and board across the top, stats and team under them, chat at the top
     * centre. Sideways: the engine's own defaults. On-screen buttons keep their
     * place (two banks along the sides suit both shapes).
     */
    private fun defaultLayout(portrait: Boolean, keysFrom: org.json.JSONObject?): org.json.JSONObject {
        val right = value("layout.joystick_x", 0.8f) >= 0.5f
        val pairs = org.json.JSONObject()
        fun put(prefix: String, x: Float, y: Float) {
            pairs.put(prefix, org.json.JSONArray().put(x.toDouble()).put(y.toDouble()))
        }
        if (portrait) {
            put("layout.joystick", if (right) 0.74f else 0.26f, 0.80f)
            put("layout.boost", if (right) 0.24f else 0.76f, 0.80f)
            put("layout.zoom", 0.50f, 0.93f)
            put("hud.minimap", 0.17f, 0.115f)
            put("hud.leaderboard", 0.80f, 0.11f)
            put("hud.stats", 0.86f, 0.32f)
            put("hud.team", 0.17f, 0.32f)
            put("hud.chat", 0.50f, 0.045f)
        } else {
            put("layout.joystick", if (right) 0.80f else 0.20f, 0.72f)
            put("layout.boost", if (right) 0.18f else 0.82f, 0.72f)
            put("layout.zoom", 0.50f, 0.88f)
            ArenaHudTarget.entries.forEach { put(it.prefix, it.fallback.x, it.fallback.y) }
        }
        return org.json.JSONObject().put("pairs", pairs).put("keys", keysFrom?.optJSONObject("keys") ?: org.json.JSONObject())
    }

    /**
     * Turns play upright or back. The layout in use is kept for the orientation
     * it was made in, and the other orientation's layout (or a first one) comes
     * in. In a layout editor the edits so far stay with the orientation they
     * were made in, Cancel now goes back to the new one, and the phone turns
     * at once.
     */
    fun switchPlayOrientation(portrait: Boolean) {
        val orientation = com.wyrm.omrajput.data.PlayOrientation
        if (portrait == orientation.portrait) return
        if (!isLayoutEditorActive()) refreshSettings()
        val current = captureLayout()
        if (current == null) {
            // The engine has not described its layout yet: switch without losing it.
            orientation.applyPortrait(portrait)
            return
        }
        orientation.saveLayout(orientation.portrait, current)
        applyLayout(orientation.savedLayout(portrait) ?: defaultLayout(portrait, current))
        orientation.applyPortrait(portrait)
        if (isLayoutEditorActive()) {
            editorSettingsSnapshot = settings
            editorHotkeysSnapshot = hotkeys
            host?.onRequestLandscape(true)
        }
    }

    /** Reset layout: the engine's sideways defaults, or the upright first layout when playing upright. */
    private fun resetOrientationLayout(actions: List<Int>) {
        actions.forEach { host?.onSettingsAction(it) }
        // Action 2 resets the controls, 8 the HUD (4, the buttons, suits both shapes).
        val upright = buildSet {
            if (2 in actions) addAll(listOf("layout.joystick", "layout.boost", "layout.zoom"))
            if (8 in actions) addAll(ArenaHudTarget.entries.map { it.prefix })
        }
        if (!playPortrait() || upright.isEmpty()) {
            refreshSettingsSoon()
            return
        }
        scope.launch {
            // The engine resets on its next frame; the upright positions go on top.
            delay(250)
            refreshSettings()
            val all = defaultLayout(true, null).getJSONObject("pairs")
            val pairs = org.json.JSONObject()
            upright.forEach { prefix -> all.optJSONArray(prefix)?.let { pairs.put(prefix, it) } }
            applyLayout(org.json.JSONObject().put("pairs", pairs))
            delay(150)
            refreshSettings()
        }
    }

    /** Shows or hides the whole interface layer. */
    /**
     * Whether the product is somewhere that wants the phone sideways.
     *
     * Orientation used to be derived from the engine's screen, and the engine's
     * screen flaps: pressing Play publishes PLAYING, a join that falls over
     * publishes TITLE again, and the retry publishes PLAYING. Each of those was
     * a real rotation — which is the portrait flash between the lobby and the
     * match, and it landed on top of admission, which is the one thing the
     * lobby exists to prevent.
     *
     * So Compose answers instead. It is the half that knows whether the player
     * is on their way into an arena or standing on Home.
     */
    fun wantsLandscape(): Boolean =
        route == Route.LOBBY || route == Route.DEATH || isLayoutEditorActive() || enteringArena

    fun isLayoutEditorActive(): Boolean = route in setOf(
        Route.CONTROL_LAYOUT, Route.ON_SCREEN_BUTTON_LAYOUT, Route.ARENA_HUD_LAYOUT, Route.BACKGROUND_SIZE_EDITOR,
    )

    fun consumeLayoutEditorExit(): Boolean {
        if (!layoutEditorExitPending) return false
        layoutEditorExitPending = false
        return true
    }

    /** Native publishes this after each arena-state change. Death and team
     * overlays can show Compose while PLAYING, so their visibility is never
     * evidence that the old WebSocket has closed. */
    fun onArenaPortAvailable(available: Boolean) {
        arenaPortFree = available
        if (!available) {
            if (arenaProbeGatePending) {
                arenaNativePortBusySeen = true
                arenaGateWatchdog?.cancel()
                arenaGateWatchdog = null
            }
        } else if (arenaProbeGatePending && arenaNativePortBusySeen) {
            releaseArenaGate()
        }
    }

    /**
     * The engine turned this Play down without dialling: a join was still
     * active, the request was stale, or the address was invalid.
     *
     * Before this existed nothing told Compose, the port never changed, and the
     * gate waited for a busy-then-free pair that could never come — Play sat on
     * its loader until the app was restarted. Nothing was dialled, so the gate
     * simply opens again.
     */
    fun onArenaEnterIgnored() {
        activity.runOnUiThread {
            if (arenaProbeGatePending && !arenaNativePortBusySeen) releaseArenaGate()
        }
    }

    /** One Play owns the arena port until the engine says it is free again. */
    private fun armArenaGate() {
        arenaNativePortBusySeen = false
        arenaProbeGatePending = true
        ArenaDirectory.beginArenaPlay()
        arenaGateWatchdog?.cancel()
        /* The engine takes a request on its next frame and reports the port
           busy at once, even while it holds the dial for pacing. Silence for
           this long means the request never reached a live engine. */
        arenaGateWatchdog = scope.launch {
            delay(ARENA_GATE_ACK_MS)
            if (arenaProbeGatePending && !arenaNativePortBusySeen) releaseArenaGate()
        }
    }

    private fun releaseArenaGate() {
        arenaGateWatchdog?.cancel()
        arenaGateWatchdog = null
        arenaNativePortBusySeen = false
        arenaProbeGatePending = false
        enteringArena = false
        ArenaDirectory.endArenaPlay()
        if (route == Route.LOBBY) watchLobbyArena()
    }

    fun setInterfaceVisible(show: Boolean) {
        activity.runOnUiThread {
            if (!show) {
                enteringArena = false
                lobbyJob?.cancel()
            }
            shown = show
            root?.visibility = if (show) View.VISIBLE else View.GONE
            voiceHud?.visibility = if (!show && VoiceCallController.state.value.active) {
                View.VISIBLE
            } else View.GONE
            lifecycleRegistry.currentState =
                if (show) Lifecycle.State.RESUMED else Lifecycle.State.STARTED
        }
    }

    fun isInterfaceVisible(): Boolean = shown

    /** Positions the two-button surface immediately to the minimap's right edge. */
    fun setVoiceHudAnchor(left: Float, top: Float, diameter: Float) {
        activity.runOnUiThread {
            val hud = voiceHud ?: return@runOnUiThread
            val params = hud.layoutParams as? FrameLayout.LayoutParams ?: return@runOnUiThread
            params.leftMargin = (left + diameter + 12f).toInt()
            params.topMargin = top.toInt()
            hud.layoutParams = params
        }
    }

    /**
     * SDLActivity does not provide Compose's back dispatcher owner. The modal
     * therefore guards Android Back here, at the host boundary, and keeps its
     * promised single exit: the acknowledgement button at the bottom.
     */
    fun consumeSystemBack(): Boolean = whatsNewState != null

    fun updateProfile(name: String, score: Int, kills: Int) {
        activity.runOnUiThread {
            nickname = name
            // The engine publishes the *last* run, not a career. Career numbers
            // are accumulated by [recordRunFromNative] and read back from here,
            // so a bad game can never overwrite a personal best.
            if (!repository.hasSession || profile.isEmpty) {
                profile = profile.copy(
                    id = if (name.isEmpty()) "" else "local",
                    displayName = name,
                    ingameName = name.lowercase().replace(" ", "_"),
                    highestScore = localBestScore(),
                    kills = localTotalKills(),
                )
            }
        }
    }

    /** Re-reads the Java-owned categories after native state was applied live. */
    fun onBackupRestored() {
        activity.runOnUiThread {
            savedArenas = SavedArenas.load(activity)
            team.reloadVault()
            refreshTeams()
            handoverSeconds = activity
                .getSharedPreferences("wyrm_team_mode", android.content.Context.MODE_PRIVATE)
                .getInt("handover_seconds", 5)
            if (!repository.hasSession || profile.isEmpty) {
                profile = profile.copy(
                    highestScore = localBestScore(),
                    kills = localTotalKills(),
                )
            }
            refreshSettingsSoon()
        }
    }

    /*
     * Career totals for a player without an account.
     *
     * Kept to the same two rules the server applies in db.mjs: the score is the
     * best ever reached, the kills are every kill ever made added together. They
     * are folded in once per death, which is the only moment the engine reports
     * a finished run, so nothing is counted twice.
     */
    private val stats by lazy {
        activity.getSharedPreferences("wyrm_local_stats", android.content.Context.MODE_PRIVATE)
    }

    private fun localBestScore(): Long = stats.getLong("best_score", 0L)

    private fun localTotalKills(): Long = stats.getLong("total_kills", 0L)

    /**
     * Records every death, whether or not Auto Respawn shows a card, and keeps
     * it as the last run for Share run. Called on the main thread; returns the
     * run's number for its screenshot ([setRunScreenshot]).
     */
    fun recordRunFromNative(score: Int, kills: Int, seconds: Double): Int {
        val run = ++lastRunSeq
        activity.runOnUiThread {
            lastRun = LastRun(score, kills, if (seconds.isFinite()) seconds.coerceAtLeast(0.0) else 0.0, System.currentTimeMillis())
            recordFinishedRun(score, kills)
            val playerId = profile.id
            if (repository.hasSession && playerId.isNotBlank()) {
                pendingRuns.enqueue(playerId, score, kills)
                flushPendingRuns()
            }
        }
        return run
    }

    /** The arena at death (WyrmActivity's PixelCopy). Dropped when a newer run has begun since. */
    fun setRunScreenshot(run: Int, shot: Bitmap) {
        activity.runOnUiThread {
            val current = lastRun ?: return@runOnUiThread
            if (run != lastRunSeq) return@runOnUiThread
            val updated = current.copy(screenshot = shot)
            lastRun = updated
            if (shareRun?.endedAt == current.endedAt) shareRun = updated
        }
    }

    /** A run began (the engine went to PLAYING, or a new life started): the last one is no longer shareable. */
    fun clearLastRun() {
        activity.runOnUiThread {
            lastRunSeq++
            lastRun = null
            if (route != Route.TRAIL_SHARE) shareRun = null
        }
    }

    /**
     * Drains the durable run outbox in order.
     *
     * Three short retries cover a tunnel or radio wobble while the app is still
     * open. Anything left stays on disk and is tried after the same account is
     * accepted again; another account can never inherit it.
     */
    private fun flushPendingRuns() {
        val playerId = profile.id
        if (!repository.hasSession || playerId.isBlank() || runUploadJob?.isActive == true) return
        runUploadJob = scope.launch {
            var stoppedForFailure = false
            var failures = 0
            try {
                while (repository.hasSession && profile.id == playerId) {
                    val run = pendingRuns.pendingFor(playerId).firstOrNull() ?: break
                    val result = runCatching {
                        repository.reportRun(run.eventId, run.score, run.kills)
                    }
                    if (result.isFailure) {
                        val error = result.exceptionOrNull()!!
                        if (repository.isUnauthorized(error) || ++failures >= 3) {
                            stoppedForFailure = true
                            break
                        }
                        delay(5_000L * failures)
                        continue
                    }
                    val earned = result.getOrThrow()
                    failures = 0
                    pendingRuns.remove(run.eventId)
                    val additions = earned.map { it.toWyrmNotification() }
                    val known = pendingAchievements.mapTo(mutableSetOf()) { it.id }
                    pendingAchievements = pendingAchievements + additions.filter { known.add(it.id) }
                    refreshProfile()
                    refreshNotifications()
                    refreshLeaderboards()
                }
            } finally {
                runUploadJob = null
                if (!stoppedForFailure && pendingRuns.pendingFor(playerId).isNotEmpty()) {
                    flushPendingRuns()
                }
            }
        }
    }

    /**
     * Five-hour repair pass, with the last success kept across app updates and
     * restarts. Per-run receipts remain primary; this only closes a missed gap.
     */
    private fun startStatsReconciliation() {
        val playerId = profile.id
        if (!repository.hasSession || playerId.isBlank() || statsSyncJob?.isActive == true) return
        statsSyncJob = scope.launch {
            while (repository.hasSession && profile.id == playerId) {
                val lastSuccess = statsSyncPreferences.getLong("last_success_$playerId", 0L)
                val remaining = STATS_RECONCILE_MS - (System.currentTimeMillis() - lastSuccess)
                if (remaining > 0L) delay(remaining)
                if (!repository.hasSession || profile.id != playerId) break

                val result = runCatching {
                    repository.reconcileStats(localBestScore(), localTotalKills())
                }
                if (result.isSuccess) {
                    val player = result.getOrThrow()
                    statsSyncPreferences.edit()
                        .putLong("last_success_$playerId", System.currentTimeMillis())
                        .apply()
                    profile = player.toWyrmProfile()
                    loadLeaderboard(boardSort)
                } else {
                    // A failed attempt earns no timestamp, so it is retried
                    // without turning a brief outage into five hours of drift.
                    delay(STATS_RETRY_MS)
                }
            }
        }
    }

    private fun recordFinishedRun(score: Int, kills: Int) {
        val best = maxOf(localBestScore(), score.toLong())
        val total = localTotalKills() + kills.toLong().coerceAtLeast(0L)
        stats.edit().putLong("best_score", best).putLong("total_kills", total).apply()
        profile = profile.copy(highestScore = best, kills = total)
    }

    private fun validateStoredSession() {
        if (!repository.hasSession) return
        // Wyrm iOS keeps its launch W up until the restored account's whole
        // snapshot has arrived, so no screen opens on stale or empty data.
        launchSyncing = true
        scope.launch {
            val cached = socialCache.profile()?.takeIf { it.fresh }?.value
            if (cached != null) {
                acceptPlayer(cached)
            } else {
                val result = runCatching { repository.me() }
                result.onSuccess(::acceptPlayer)
                val error = result.exceptionOrNull()
                if (error != null && repository.isUnauthorized(error)) {
                    repository.clearSession()
                    resetToSignedOut()
                    launchSyncing = false
                    return@launch
                }
            }
            bootstrapSession()
            launchSyncing = false
            resumeAccountSync()
        }
    }

    private fun signInWithGoogle() {
        if (authState.busy) return
        scope.launch {
            authState = authState.copy(busy = true, error = "")
            runCatching {
                val idToken = googleIdToken(activity, BuildConfig.GOOGLE_WEB_CLIENT_ID)
                repository.googleSignIn(idToken)
            }.onSuccess { player ->
                acceptPlayer(player)
                googleName = player.displayName
                // A player with no username has never finished onboarding, whichever
                // device they signed in from, so the server's record decides
                // this rather than anything stored locally.
                route = if (player.username.isNullOrBlank()) {
                    formError = ""
                    Route.ONBOARDING
                } else {
                    Route.HOME
                }
                authState = AuthState(signingUp = false)
            }.onFailure { error ->
                authState = authState.copy(
                    busy = false,
                    error = error.message ?: "Google sign-in failed.",
                )
            }
        }
    }

    /**
     * Creates an account with no Google behind it.
     *
     * The name and username are chosen on that screen, so there is nothing left
     * to ask afterwards — a guest goes straight to Home rather than through
     * onboarding, which exists only to collect what Google could not supply.
     */
    private fun createGuestAccount(displayName: String, username: String, password: String) {
        if (authState.busy) return
        scope.launch {
            authState = authState.copy(busy = true, error = "")
            runCatching { repository.guestSignUp(displayName, username, password) }
                .onSuccess { player ->
                    acceptPlayer(player)
                    route = Route.HOME
                    authState = AuthState(signingUp = false)
                }
                .onFailure { error ->
                    authState = authState.copy(
                        busy = false,
                        error = when {
                            error.message?.contains("USERNAME_TAKEN") == true ->
                                "That username is already taken."
                            else -> error.message ?: "The account could not be created."
                        },
                    )
                }
        }
    }

    /** Signs back into an account made without Google. */
    private fun logInWithPassword(username: String, password: String) {
        if (authState.busy) return
        scope.launch {
            authState = authState.copy(busy = true, error = "")
            runCatching { repository.logIn(username, password) }
                .onSuccess { player ->
                    acceptPlayer(player)
                    route = if (player.username.isNullOrBlank()) Route.ONBOARDING else Route.HOME
                    authState = AuthState(signingUp = false)
                }
                .onFailure { error ->
                    authState = authState.copy(
                        busy = false,
                        // The server deliberately does not say which half was
                        // wrong, and neither does this.
                        error = if (error.message?.contains("INVALID_LOGIN") == true) {
                            "That username and password do not match."
                        } else {
                            error.message ?: "Could not sign in."
                        },
                    )
                }
        }
    }

    /**
     * Opens the arena picker, loading the directory the first time.
     *
     * The list is kept between visits — it costs a network round trip and a
     * sweep of a few hundred pings to rebuild — and Refresh is there for when
     * the player wants it done again.
     */
    private fun openArenaPicker() {
        arenaJob?.cancel()
        ArenaDirectory.openPicker()
        arenaState = arenaState.copy(
            selected = arenaLabel,
            loading = true,
            error = "",
            pings = emptyMap(),
        )
        route = Route.ARENA
    }

    /** The pill settles first; directory traffic begins after the content reveal. */
    private fun scanArenasAfterExpansion() = beginArenaScan(
        reloadDirectory = false,
        delayMs = 180L,
    )

    /**
     * Reloads the directory and re-measures every arena.
     *
     * Results are applied one at a time as they land rather than in a batch at
     * the end, so the list sorts itself into shape while the player is already
     * reading it. Cancelling the previous sweep matters: two of them writing
     * the same map would otherwise leave stale numbers behind.
     */
    private fun refreshArenas() = beginArenaScan(reloadDirectory = true)

    /** The picker is gone: no sweep keeps running behind it. */
    private fun stopArenaProbes() {
        arenaJob?.cancel()
        arenaJob = null
        ArenaDirectory.closePicker()
    }

    private fun beginArenaScan(reloadDirectory: Boolean, delayMs: Long = 0L) {
        if (route != Route.ARENA || !panelOpen) return
        arenaJob?.cancel()
        arenaJob = scope.launch {
            arenaState = arenaState.copy(
                loading = true,
                error = "",
                pings = emptyMap(),
                selected = arenaLabel,
            )
            if (delayMs > 0L) delay(delayMs)
            runCatching {
                if (reloadDirectory || arenaState.arenas.isEmpty()) {
                    ArenaDirectory.load()
                } else {
                    arenaState.arenas
                }
            }
                .onSuccess { arenas ->
                    arenaState = arenaState.copy(arenas = arenas, loading = false)
                    ArenaDirectory.pingAll(arenas) { endpoint, milliseconds ->
                        arenaState = arenaState.copy(
                            pings = arenaState.pings + (endpoint to milliseconds),
                        )
                    }
                }
                .onFailure { error ->
                    if (!isActive) return@onFailure
                    arenaState = arenaState.copy(
                        loading = false,
                        error = error.message ?: "Couldn't reach the arena directory.",
                    )
                }
        }
    }

    private fun saveArena(address: String) {
        savedArenas = SavedArenas.save(activity, address)
    }

    private fun removeSavedArena(address: String) {
        savedArenas = SavedArenas.remove(activity, address)
    }

    /** Hands the chosen address to the engine, which owns and persists it. */
    private fun selectArena(address: String, close: Boolean = true) {
        val selected = arenaState.arenas.firstOrNull {
            it.endpoint.equals(address, ignoreCase = true)
        }
        host?.onSelectArena(address)
        arenaState = arenaState.copy(selected = address)
        arenaLabel = address
        lobbyArena = selected
        lobbyPing = arenaState.pings.entries.firstOrNull {
            it.key.equals(address, ignoreCase = true)
        }?.value ?: 0
        arenaOnline = true
        // Closes the way it opened, back into the button — which is now
        // showing the arena that was just chosen.
        if (close) panelOpen = false
    }

    /**
     * A photograph chosen on the device, handed over by the activity.
     *
     * Nothing is sent yet — the next screen decides which square of it becomes
     * the avatar.
     */
    fun onPhotoPicked(photo: Bitmap?) {
        activity.runOnUiThread {
            if (photo == null) {
                formError = "That image couldn't be opened. Try another."
                if (photoReturn == Route.PROFILE) photoError = formError
                route = photoReturn
                return@runOnUiThread
            }
            pendingPhoto = photo
            formError = ""
            route = Route.CROP_PHOTO
        }
    }

    private fun uploadPhoto(cropped: Bitmap) {
        if (formBusy) return
        scope.launch {
            formBusy = true
            formError = ""
            val bytes = withContext(Dispatchers.Default) { cropped.toUploadBytes() }
            runCatching { repository.uploadAvatar(bytes) }
                .onSuccess { player ->
                    acceptPlayer(player)
                    pendingPhoto = null
                    formBusy = false
                    route = photoReturn
                }
                .onFailure { error ->
                    formBusy = false
                    formError = readablePhotoError(repository.errorCode(error))
                }
        }
    }

    private fun removePhoto() {
        if (formBusy) return
        scope.launch {
            formBusy = true
            formError = ""
            runCatching { repository.removeAvatar() }
                .onSuccess { player ->
                    // The old address is dead now; drop the copy held in memory
                    // so nothing redraws a face that no longer exists.
                    AvatarImages.forget(profile.avatarUrl)
                    acceptPlayer(player)
                    formBusy = false
                }
                .onFailure { error ->
                    formBusy = false
                    formError = readablePhotoError(repository.errorCode(error))
                    photoError = formError
                }
        }
    }

    private fun openProfile() {
        photoError = ""
        route = Route.PROFILE
        refreshProfile()
        refreshProfileRanks()
    }

    private fun refreshProfileRanks() {
        val me = profile.id
        if (me.isBlank()) return
        scope.launch {
            val scores = scoreBoard
            val kills = killBoard
            profileScoreRank = scores.indexOfFirst { it.id == me }.takeIf { it >= 0 }?.plus(1)
            profileKillRank = kills.indexOfFirst { it.id == me }.takeIf { it >= 0 }?.plus(1)
        }
    }

    private fun profileTeamLabel(): String {
        if (!team.configured || !teamEnabled) return "None"
        val name = teamProfiles.getOrNull(teamActive)?.name.orEmpty().ifBlank { "Team" }
        val online = teamState.members.count { it.playing }
        return if (online > 0) "$name · $online online" else name
    }

    private fun refreshProfile(force: Boolean = false) {
        if (!repository.hasSession) {
            route = Route.AUTH
            return
        }
        val cached = socialCache.profile()
        if (!force && cached != null) {
            // Only fills an empty screen: the profile in memory can be newer
            // than the cache (a run's best score lands locally first).
            if (profile.id.isBlank()) {
                profile = cached.value.toWyrmProfile()
                profileUpdatedAt = cached.savedAt
            }
            if (cached.fresh) return
        }
        scope.launch {
            profileLoading = true
            runCatching { voiceRepository.verificationStatus() }
                .onSuccess { voiceState = voiceState.copy(verified = it.verified) }
            runCatching { repository.me() }
                .onSuccess {
                    acceptPlayer(it)
                    profileOffline = false
                    profileLoading = false
                }
                .onFailure { error ->
                    profileLoading = false
                    profileOffline = true
                    if (repository.isUnauthorized(error)) {
                        repository.clearSession()
                        resetToSignedOut()
                    }
                    // Network errors deliberately leave the engine-backed local
                    // profile in place as the offline fallback.
                }
        }
    }

    /**
     * Takes the account, and leaves the in-game name alone.
     *
     * These are two different names and used to be treated as one. The in-game
     * name is what is printed above the snake; the engine owns it, saves it on
     * the device and hands it back on every launch. The display name is what
     * the account is called, and it is capped at two changes a month.
     *
     * This line used to read `ingameName ?: displayName` — and since onboarding
     * never sets an in-game name, the account name replaced whatever the player
     * had typed on Home every single time the app was reopened. The account
     * name is now only ever adopted as a starting point, never as a
     * replacement.
     */
    private fun acceptPlayer(player: ApiPlayer) {
        socialCache.switchAccount(player.id)
        socialCache.saveProfile(player)
        profileUpdatedAt = System.currentTimeMillis()
        profile = player.toWyrmProfile()
        sessionReady = true
        val stored = player.ingameName.orEmpty()
        // The account's name only seeds a name never chosen: a player who
        // cleared it on purpose keeps it blank.
        if (nickname.isBlank() && stored.isNotBlank() && !uiPreferences.getBoolean("nickname_chosen", false)) {
            nickname = stored
        }
        registerPushToken()
        if (activityResumed) startLiveInbox()
        refreshNotifications()
        refreshUnreadDmCount()
        com.wyrm.omrajput.data.SupportStore.refresh()
        flushPendingRuns()
        startStatsReconciliation()
        scope.launch {
            runCatching { voiceRepository.verificationStatus() }
                .onSuccess { voiceState = voiceState.copy(verified = it.verified) }
        }
        pendingNotificationOpen?.let {
            pendingNotificationOpen = null
            routeNotificationOpen(it)
        }
    }

    /**
     * Hands Firebase's current token to the backend.
     *
     * Runs on every sign-in and every relaunch of an existing session, because
     * there is no event for "this device should now receive pushes" other than
     * having a session at all — and a token Firebase already has cached costs
     * nothing to ask for again.
     */
    private fun registerPushToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            scope.launch { runCatching { repository.registerDeviceToken(token) } }
        }
    }

    /**
     * Sends the in-game name up, quietly.
     *
     * So a new device shows the right name and so chat and the board can print
     * it. Best effort on purpose: the arena accepts names the account system
     * will not — spaces, punctuation, more than twenty characters — and a name
     * the server refuses is still a perfectly good name to play under. The
     * device's copy is the one that matters.
     */
    /** The player typed a name (or cleared it): it is theirs from now on. */
    private fun chooseNickname(name: String) {
        nickname = name
        if (!uiPreferences.getBoolean("nickname_chosen", false)) {
            uiPreferences.edit().putBoolean("nickname_chosen", true).apply()
        }
    }

    private fun syncIngameName() {
        val name = nickname.trim()
        if (!repository.hasSession || name == profile.ingameName) return
        if (!name.matches(Regex("^[A-Za-z0-9_]{3,20}$"))) return
        scope.launch {
            runCatching { repository.updateProfile(ingameName = name) }
                .onSuccess { profile = it.toWyrmProfile() }
        }
    }

    /** Finishes a new account: handle, then the real settings those steps picked. */
    private fun completeOnboarding(choices: OnboardingChoices) {
        if (formBusy) return
        scope.launch {
            formBusy = true
            formError = ""
            runCatching {
                repository.updateProfile(
                    displayName = choices.displayName,
                    username = choices.username,
                    phoneNumber = "",
                )
            }.onSuccess { player ->
                acceptPlayer(player)
                settings.firstOrNull { it.id == "controls.joystick_mode" }?.let { setting ->
                    writeSetting(setting, listOf(if (choices.joystick) 0f else 2f))
                }
                settings.firstOrNull { it.id == "controls.handedness" }?.let { setting ->
                    writeSetting(setting, listOf(if (choices.rightHand) 1f else 0f))
                }
                host?.onSkinPreset(choices.preset)
                hotkeys.firstOrNull { it.name.contains("Assist", ignoreCase = true) }?.let { key ->
                    writeHotkey(key.copy(visible = choices.assistOn))
                }
                formBusy = false
                route = Route.HOME
            }.onFailure { error ->
                formBusy = false
                formError = readableProfileError(repository.errorCode(error))
            }
        }
    }

    private fun saveProfile(
        displayName: String,
        username: String,
        bio: String,
        avatarKey: String,
        ingameName: String? = null,
    ) {
        if (formBusy) return
        scope.launch {
            formBusy = true
            formError = ""
            runCatching {
                repository.updateProfile(
                    displayName = displayName,
                    // An empty arena name would fail the 3–20 rule; leave it untouched.
                    ingameName = ingameName?.trim()?.takeIf { it.isNotEmpty() },
                    username = username,
                    bio = bio,
                    avatarKey = avatarKey,
                )
            }.onSuccess { player ->
                acceptPlayer(player)
                formBusy = false
                route = Route.PROFILE
            }.onFailure { error ->
                formBusy = false
                formError = readableProfileError(repository.errorCode(error))
            }
        }
    }

    /* ------------------------------------------------ account-linked settings */
    /*
     * OM, 2026-10-01: settings live in the account (data/AccountSync.kt,
     * backend account-settings.mjs). Saved when the app goes to the background
     * and on log out; restored behind the W on log in; log out wipes the phone.
     */

    /** The Log out sheet (Settings, very bottom). */
    private var logOutAsking by mutableStateOf(false)
    /** Why the settings could not be saved during log out; the three choices show. */
    private var logOutFailure by mutableStateOf<String?>(null)
    /**
     * True once this phone holds the account's own settings: after a restore,
     * or on a relaunch with nothing left to restore. Every save waits for it,
     * so a fresh phone can never upload its defaults over the account's copy.
     */
    private var accountSyncReady = false
    private var accountSaveJob: Job? = null
    private var lastAccountSave = 0L
    private var accountRestoreJob: Job? = null
    private val accountSyncPrefs = activity.getSharedPreferences("wyrm_account_sync", android.content.Context.MODE_PRIVATE)

    private val accountEngine = object : com.wyrm.omrajput.data.AccountSync.Engine {
        override fun readSettings(): String = host?.onReadSettings().orEmpty()
        override fun readHotkeys(): String = host?.onReadHotkeys().orEmpty()
        override fun writeSetting(id: String, values: FloatArray) { host?.onWriteSetting(id, values) }
        override fun writeHotkey(action: Int, key: Int, mode: Int, visible: Boolean, x: Float, y: Float) {
            host?.onWriteHotkey(action, key, mode, visible, x, y)
        }
        override fun action(mask: Int) { host?.onSettingsAction(mask) }
    }

    /** What is the same on every platform: skin, look, arena background, in-game name. */
    private fun sharedSettingsDocument(): org.json.JSONObject = org.json.JSONObject()
        .put("schema", 1)
        .put("skin", skinState.toTrailSkin(WyrmLookStore.spec()).toJson())
        .put("arenaBackground", arenaBackground)
        .put("nickname", nickname)
        .put("nicknameChosen", uiPreferences.getBoolean("nickname_chosen", false))
        // Common to every platform (OM, 2026-10-01): the same on Android and iOS.
        .put("joystickLaser", org.json.JSONObject()
            .put("on", JoystickLaserStore.on)
            .put("length", JoystickLaserStore.length.toDouble()))
        .put("playPortrait", com.wyrm.omrajput.data.PlayOrientation.portrait)
        .put("performanceMode", com.wyrm.omrajput.data.WyrmPerformance.mode.key)
        // Home › Near Original (OM, 2026-10-02): the same switch on every platform.
        .put("nearOriginal", NearOriginalStore.on)

    private fun applySharedSettings(doc: org.json.JSONObject) {
        com.wyrm.omrajput.data.TrailSkin.from(doc.optJSONObject("skin"))?.let { skin ->
            val state = skin.toSkinState()
            // A preset keeps the player's own pattern underneath, as picking one does.
            if (state.code.isNotEmpty()) host?.onSkinCode(state.code, state.coloursFor(state.code.length))
            if (!state.custom) host?.onSkinPreset(state.preset)
            host?.onSkinAccessory(state.accessory)
            WyrmLookStore.wear(skin.lookSpec())
            skinState = state
        }
        val background = doc.optInt("arenaBackground", -1)
        if (background >= 0) {
            host?.onSkinBackground(background)
            arenaBackground = background
        }
        host?.onSkinSync(true)
        if (doc.has("nickname")) {
            val name = doc.optString("nickname").take(24)
            nickname = name
            host?.onSetNickname(name)
            uiPreferences.edit().putBoolean("nickname_chosen", doc.optBoolean("nicknameChosen", name.isNotEmpty())).apply()
        }
        // Common settings (OM, 2026-10-01). Written into this platform's own
        // files before the stores reload, so they win over the platform copy.
        doc.optJSONObject("joystickLaser")?.let { laser ->
            val length = laser.optDouble("length", Double.NaN)
            activity.getSharedPreferences("wyrm_joystick_laser", android.content.Context.MODE_PRIVATE).edit().apply {
                if (laser.has("on")) putBoolean("on", laser.optBoolean("on", true))
                if (length.isFinite()) putFloat("length", length.toFloat().coerceIn(JoystickLaserStore.RANGE))
            }.commit()
        }
        if (doc.has("nearOriginal")) {
            activity.getSharedPreferences("wyrm_near_original", android.content.Context.MODE_PRIVATE).edit()
                .putBoolean("on", doc.optBoolean("nearOriginal", false)).commit()
        }
        doc.optString("performanceMode").takeIf { key ->
            com.wyrm.omrajput.data.WyrmPerformance.Mode.entries.any { it.key == key }
        }?.let { key ->
            activity.getSharedPreferences("wyrm_performance", android.content.Context.MODE_PRIVATE).edit()
                .putString("mode", key).commit()
        }
        // The orientation swaps layouts, so it waits until the restored layout
        // is in the engine (see tryRestoreAccountSettings).
        sharedPortraitPending = if (doc.has("playPortrait")) doc.optBoolean("playPortrait") else null
    }

    /** The account's common play orientation, applied once the restored layout is in. */
    private var sharedPortraitPending: Boolean? = null

    /** Stores that read their files once: read them again after a restore or a wipe. */
    private fun reloadAccountStores() {
        ArrowSkinStore.reload(activity)
        WyrmLookStore.reload(activity)
        com.wyrm.omrajput.data.DropWatch.reloadPrefs()
        com.wyrm.omrajput.data.CrashWatch.reloadPrefs()
        com.wyrm.omrajput.data.WyrmPerformance.reload(activity)
        com.wyrm.omrajput.data.PlayOrientation.reload(activity)
        JoystickLaserStore.reload(activity)
        TeamHudStore.reload(activity)
        NearOriginalStore.reload(activity)
        appTheme = WyrmThemeId.fromStored(uiPreferences.getString("theme", null))
        themeIntensity = uiPreferences.getFloat("theme_intensity", 0.5f).coerceIn(0f, 1f)
        Wyrm.applyTheme(appTheme, themeIntensity)
        publishArenaTheme()
        betaUpdates = com.wyrm.omrajput.UpdateChannel.isBetaEnabled(activity)
        enabledNotificationKinds = NotificationPreferences.enabledKinds(activity)
        refreshSettingsSoon()
    }

    /**
     * Saves this phone's settings to the account. Refuses (and says so) when
     * the phone does not hold the account's settings yet, or when the engine
     * has not described its settings yet: a partial copy would replace a whole one.
     */
    private suspend fun saveAccountSettings(force: Boolean = false): Result<Unit> {
        if (!repository.hasSession || !accountSyncReady) return Result.failure(IllegalStateException("NOT_READY"))
        if (!force && SystemClock.elapsedRealtime() - lastAccountSave < 20_000L) return Result.success(Unit)
        // The engine publishes the worn skin when asked; give it a moment.
        host?.onSkinSync(false)
        delay(250)
        val platform = com.wyrm.omrajput.data.AccountSync.platformDocument(activity, accountEngine)
        if (!platform.has("engine")) return Result.failure(IllegalStateException("ENGINE_NOT_READY"))
        // Before the engine has published the skin, the shared copy on the account stays as it is.
        val shared = if (skinReady) sharedSettingsDocument() else null
        return runCatching {
            repository.saveAccountSettings(com.wyrm.omrajput.data.AccountSync.PLATFORM, platform, shared)
        }.onSuccess { lastAccountSave = SystemClock.elapsedRealtime() }
    }

    /** The app went to the background (Activity.onPause): keep the account's copy current. */
    fun onAppBackground() {
        if (!repository.hasSession || !accountSyncReady) return
        accountSaveJob?.cancel()
        accountSaveJob = scope.launch { saveAccountSettings() }
    }

    /**
     * Log in: the account's Android settings and the shared ones go in behind
     * the W, key by key. Offline: the phone keeps what it has, marks the
     * restore as owed (it survives a relaunch) and tries again every 30 s;
     * until then nothing is saved, so the account's copy is never overwritten.
     */
    private suspend fun restoreAccountSettings() {
        accountSyncReady = false
        accountRestoreJob?.cancel()
        val owner = profile.id
        accountSyncPrefs.edit().putString("restore_owed", owner).commit()
        if (tryRestoreAccountSettings()) return
        accountRestoreJob = scope.launch {
            while (repository.hasSession && profile.id == owner && !accountSyncReady) {
                delay(30_000L)
                if (tryRestoreAccountSettings()) break
            }
        }
    }

    /** One attempt: fetch, apply key by key, reload the stores. False when offline. */
    private suspend fun tryRestoreAccountSettings(): Boolean {
        val (platform, shared) = runCatching {
            repository.accountSettings(com.wyrm.omrajput.data.AccountSync.PLATFORM)
        }.getOrElse { return false }
        if (platform != null) {
            runCatching { com.wyrm.omrajput.data.AccountSync.applyPlatform(activity, platform, accountEngine) }
        }
        if (shared != null) runCatching { applySharedSettings(shared) }
        reloadAccountStores()
        // The account's common orientation: turn (and swap layouts) once the
        // engine has the restored layout.
        sharedPortraitPending?.let { portrait ->
            sharedPortraitPending = null
            scope.launch {
                delay(700)
                refreshSettings()
                if (portrait != com.wyrm.omrajput.data.PlayOrientation.portrait) switchPlayOrientation(portrait)
            }
        }
        accountSyncPrefs.edit().remove("restore_owed").commit()
        accountSyncReady = true
        return true
    }

    /** Relaunch with a session: finish an owed restore, or start keeping the copy current. */
    private fun resumeAccountSync() {
        val owed = accountSyncPrefs.getString("restore_owed", null)
        scope.launch {
            if (owed != null && owed == profile.id) {
                restoreAccountSettings()
            } else {
                accountSyncReady = true
                // The first save after this build arrives uploads what the phone
                // already had, so nobody who upgrades loses a setting.
                delay(6_000L)
                saveAccountSettings()
            }
        }
    }

    /** Settings › Log out: the question first. */
    private fun askLogOut() {
        if (sessionTransitionTitle != null) return
        logOutAsking = true
    }

    /**
     * Log out: save the settings to the account (unless [skipSave]), stop this
     * phone's pushes for the account, then wipe the phone and go to the start.
     */
    private fun logOut(skipSave: Boolean = false) {
        logOutAsking = false
        logOutFailure = null
        if (sessionTransitionTitle != null) return
        sessionTransitionTitle = "Saving your settings…"
        scope.launch {
            if (!skipSave && accountSyncReady) {
                accountSaveJob?.cancel()
                val saved = saveAccountSettings(force = true)
                if (saved.isFailure && saved.exceptionOrNull()?.message != "ENGINE_NOT_READY") {
                    sessionTransitionTitle = null
                    logOutFailure = "Wyrm could not reach your account. If you log out anyway, " +
                        "the changes you made since the last save are cleared with this phone."
                    return@launch
                }
            }
            sessionTransitionTitle = "Logging you out…"
            val token = kotlinx.coroutines.withTimeoutOrNull(3_000L) {
                kotlinx.coroutines.suspendCancellableCoroutine<String?> { done ->
                    FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { if (done.isActive) done.resumeWith(Result.success(it)) }
                        .addOnFailureListener { if (done.isActive) done.resumeWith(Result.success(null)) }
                }
            }
            if (!token.isNullOrBlank()) runCatching { repository.forgetDeviceToken(token) }
            accountRestoreJob?.cancel()
            accountSyncReady = false
            stopLiveInbox()
            repository.signOut()
            socialCache.clear()
            com.wyrm.omrajput.data.AccountSync.wipeDevice(activity, accountEngine)
            reloadAccountStores()
            skinState = SkinState()
            arenaBackground = 0
            // As on iOS: the account's surfaces clear behind the W, then auth.
            delay(920)
            resetToSignedOut()
            sessionTransitionTitle = null
        }
    }

    /** Server codes as Wyrm iOS words them. */
    private fun friendlyAuthError(error: Throwable): String {
        val code = error.message.orEmpty()
        return when {
            error is java.io.IOException -> "Could not reach Wyrm. Check your connection and try again."
            code == "USERNAME_TAKEN" -> "That username is already taken."
            code == "INVALID_LOGIN" -> "Username or password is incorrect."
            code == "NAME_CHANGE_LIMIT" || code == "USERNAME_CHANGE_LIMIT" -> "The monthly name-change limit has been reached."
            code == "IGN_TAKEN" -> "That arena name is already in use."
            code == "INVALID_PROFILE" || code == "INVALID_BODY" -> "Please check the fields and try again."
            code.isBlank() -> "Could not reach Wyrm. Check your connection and try again."
            else -> "Wyrm could not complete that request ($code)."
        }
    }

    /**
     * Wyrm iOS's service bootstrap: every account-scoped surface is fetched
     * together while the W is on screen, each isolated so one failure cannot
     * throw away the rest, and cached so Home, Social and the leaderboard
     * open with this account's data rather than an empty or previous one.
     */
    private suspend fun bootstrapSession() = coroutineScope {
        val playerId = profile.id
        val score = async { runCatching { repository.leaderboard("score") }.getOrNull() }
        val kills = async { runCatching { repository.leaderboard("kills") }.getOrNull() }
        val threads = async { runCatching { repository.conversations() }.getOrNull() }
        val alerts = async { runCatching { repository.notifications() }.getOrNull() }
        val rooms = async { runCatching { voiceRepository.rooms() }.getOrNull() }
        val follows = async {
            if (playerId.isBlank()) null else runCatching { repository.connections(playerId, "following") }.getOrNull()
        }
        score.await()?.let {
            scoreBoard = it
            socialCache.saveLeaderboard("score", it)
        }
        kills.await()?.let {
            killBoard = it
            socialCache.saveLeaderboard("kills", it)
        }
        boardUpdatedAt = System.currentTimeMillis()
        threads.await()?.let {
            conversations = it
            unreadDmCount = it.sumOf { row -> row.unread }
        }
        alerts.await()?.let { rows -> mergeNotifications(rows.map { it.toWyrmNotification() }) }
        rooms.await()?.let {
            socialCache.saveVoiceRooms(it)
            voiceState = voiceState.copy(rooms = it, loading = false, offline = false, updatedAt = System.currentTimeMillis())
        }
        follows.await()?.let { following = it }
        if (playerId.isNotBlank()) {
            runCatching { repository.connections(playerId, "followers") }.getOrNull()?.let { followers = it }
        }
        AvatarImages.prefetch(
            (scoreBoard + killBoard + following + followers + conversations.map { it.player }).map { it.avatarUrl },
        )
        boardUpdatedAt = System.currentTimeMillis()
        startBoardRefresh()
        refreshArenas()
    }

    /**
     * Closes the account, and only then signs out.
     *
     * Signing out regardless of the result was a real bug: a rejected request
     * left the account alive on the server while the app behaved as though it
     * were gone, so the next sign-in walked straight back into it. If the
     * server refuses, the player stays signed in and is told why.
     */
    private fun deleteAccount() {
        if (formBusy) return
        scope.launch {
            formBusy = true
            formError = ""
            runCatching { repository.deleteAccount() }
                .onSuccess {
                    formBusy = false
                    accountSyncReady = false
                    repository.signOut()
                    socialCache.clear()
                    com.wyrm.omrajput.data.AccountSync.wipeDevice(activity, accountEngine)
                    reloadAccountStores()
                    skinState = SkinState()
                    resetToSignedOut()
                }
                .onFailure { error ->
                    formBusy = false
                    formError = "Couldn't delete the account: " +
                        readableProfileError(repository.errorCode(error))
                    route = Route.PROFILE
                }
        }
    }

    private fun resetToSignedOut() {
        if (VoiceCallController.state.value.active) VoiceCallService.leave(activity)
        cancelVoiceOperation()
        runUploadJob?.cancel()
        runUploadJob = null
        statsSyncJob?.cancel()
        statsSyncJob = null
        sessionReady = false
        profile = WyrmProfile()
        nickname = ""
        googleName = ""
        scoreBoard = emptyList()
        killBoard = emptyList()
        formError = ""
        authState = AuthState()
        notifications = emptyList()
        unreadDmCount = 0
        globalUnread = 0
        highlightedNotification = null
        pendingNotificationOpen = null
        pendingAchievements = emptyList()
        voiceState = VoiceScreenState()
        whatsNewBackdropVisible = false
        whatsNewState = null
        // Nothing of one account is shown to the next.
        TrailsStore.reset()
        com.wyrm.omrajput.data.BadgeStore.reset()
        com.wyrm.omrajput.data.SupportStore.reset()
        com.wyrm.omrajput.data.DropWatch.dismissPrompt()
        viewedPlayer = null
        photoError = ""
        route = Route.AUTH
    }

    private fun openLeaderboard() {
        boardSort = LeaderboardSort.SCORE
        route = Route.LEADERBOARD
    }

    /**
     * Alerts, as a page (OM, 2026-10-04): from a tab it opens over that tab
     * (Home's bell, a notification tap); from inside the panel it is pushed
     * there. Back returns to wherever it was opened from.
     */
    private fun openNotifications(origin: Rect = Rect.Zero) {
        if (route == Route.NOTIFICATIONS) {
            refreshNotifications()
            return
        }
        if (panelOpen && route.growsFromHome) {
            notificationsReturn = route
            route = Route.NOTIFICATIONS
        } else {
            rootTabForRoute(route)?.let { tabRoot = route }
            notificationsReturn = tabRoot
            openPanel(origin) { route = Route.NOTIFICATIONS }
        }
        refreshNotifications()
    }

    /** The Trails tab (OM, 2026-10-04). */
    private fun openTrailsTab() {
        panelOpen = false
        tabRoot = Route.TRAILS
        route = Route.TRAILS
        if (TRAILS_ENABLED) TrailsStore.refresh()
    }

    /** What the player has allowed: Android's master gate, then Wyrm's kinds. */
    private fun visibleNotifications(): List<WyrmNotification> {
        if (!notificationsAllowed) return emptyList()
        return notifications.filter {
            it.kind.preferenceKey() in enabledNotificationKinds &&
                // Trails paused: its alerts neither show nor count as unread.
                (TRAILS_ENABLED || (it.kind != NotificationKind.TRAIL_LIKE && it.kind != NotificationKind.TRAIL_REPLY))
        }
    }

    /**
     * Pulls the alerts feed and the unread-DM count together.
     *
     * The two live on the same phone call as far as an operator's broadcast
     * is concerned — both are "something arrived while you weren't looking" —
     * but they render in different places on purpose: see [refreshUnreadDmCount].
     */
    private fun refreshNotifications() {
        if (!repository.hasSession || profile.id.isBlank()) return
        scope.launch {
            runCatching { repository.notifications() }
                .onSuccess { mergeNotifications(it.map { row -> row.toWyrmNotification() }) }
                .onFailure { mergeNotifications(notifications.filter { it.kind != NotificationKind.BACKUP }) }
        }
    }

    private fun mergeNotifications(server: List<WyrmNotification>) {
        notifications = (server + localNotifications.all(profile.id))
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
    }

    private fun openNotificationCard(id: String) {
        val notification = notifications.firstOrNull { it.id == id } ?: return
        setNotificationRead(id, true)
        when (notification.kind) {
            // Pages that live in the panel open inside it; opened with the
            // panel shut, they slid in and straight back out again.
            NotificationKind.FOLLOW -> notification.actorId?.takeIf { it.isNotBlank() }?.let { actor ->
                if (!route.growsFromHome) openPanel(Rect.Zero) { openPlayer(actor) } else openPlayer(actor)
            }
            NotificationKind.TRAIL_LIKE, NotificationKind.TRAIL_REPLY ->
                if (TRAILS_ENABLED) {
                    notification.trailId?.let { trail ->
                        openTrail(trail)
                        TrailsStore.reload(trail)
                    }
                }
            NotificationKind.SUPPORT -> openSupportReportsFromAlert()
            NotificationKind.UPDATE -> {
                tabRoot = Route.SETTINGS
                backupReturn = Route.SETTINGS
                pollTransferState()
                panelOpen = true
                route = Route.SETTINGS_UPDATES
            }
            NotificationKind.RANK -> openLeaderboard()
            NotificationKind.BACKUP -> {
                tabRoot = Route.SETTINGS
                backupReturn = Route.SETTINGS
                pollTransferState()
                panelOpen = true
                route = Route.SETTINGS_BACKUP
            }
            NotificationKind.VOICE_INVITE -> {
                route = Route.VOICE
                notification.voiceRoomId?.let { roomId ->
                    scope.launch {
                        runCatching { voiceRepository.room(roomId) }
                            .onSuccess { voiceState = voiceState.copy(selectedRoom = it) }
                    }
                }
                notification.voiceInviteId?.let(::acceptVoiceInvite)
            }
            else -> Unit
        }
    }

    /* ------------------------------------------------------ the badge trail */
    /*
     * OM, 2026-10-01: a mark never sits on a tab alone. Every count on a tab is
     * also on the row that leads to the thing, and on the thing itself; opening
     * the thing clears the whole trail. Alerts keeps every notification too.
     *   dm            Social tab › Messages › the thread (and Chat › Direct)
     *   voice_invite  Social tab › Voice rooms (read when the rooms open)
     *   follow        Social tab › Connections (read when your followers open)
     *   trail_*       Social tab › Trails (read when the trail opens)
     *   support       Settings tab › Help & feedback › Your reports › the report
     */
    private fun unreadOf(vararg kinds: NotificationKind): Int =
        visibleNotifications().count { !it.read && it.kind in kinds }

    private fun socialBadgeCount(): Int =
        unreadDmCount.toInt() + unreadOf(NotificationKind.VOICE_INVITE, NotificationKind.FOLLOW) + globalUnread

    /**
     * Global chat's count (OM, 2026-10-04): messages from others newer than the
     * newest one this phone has shown in the open room. The first look only
     * sets that mark, so nobody starts with a day's worth of unread.
     */
    private fun refreshGlobalUnread() {
        if (!repository.hasSession || profile.id.isBlank()) return
        if (route == Route.CHAT && chatTab == ChatTab.GLOBAL) return
        scope.launch {
            runCatching { repository.globalMessages() }.onSuccess { messages ->
                val seen = globalChatPrefs.getString("seen_at", null)
                if (seen == null) {
                    noteGlobalSeen(messages)
                    return@onSuccess
                }
                globalUnread = messages.count { it.createdAt > seen && it.authorId != profile.id }
            }
        }
    }

    /** The room is on screen: everything in it is read. */
    private fun noteGlobalSeen(messages: List<ChatMessage>) {
        val newest = messages.maxOfOrNull { it.createdAt } ?: return
        val seen = globalChatPrefs.getString("seen_at", null)
        if (seen == null || newest > seen) globalChatPrefs.edit().putString("seen_at", newest).apply()
        globalUnread = 0
    }

    /** Unread likes and replies on your trails: the Trails tab's count. */
    private fun trailsBadgeCount(): Int =
        if (TRAILS_ENABLED) unreadOf(NotificationKind.TRAIL_LIKE, NotificationKind.TRAIL_REPLY) else 0

    private fun markKindsRead(vararg kinds: NotificationKind) {
        notifications.filter { !it.read && it.kind in kinds }.forEach { setNotificationRead(it.id, true) }
    }

    private fun setNotificationRead(id: String, read: Boolean) {
        if (notifications.none { it.id == id }) return
        notifications = notifications.map { if (it.id == id) it.copy(read = read) else it }
        if (id.startsWith("local-backup-")) {
            localNotifications.setRead(profile.id, id, read)
            return
        }
        scope.launch {
            runCatching { repository.setNotificationRead(id, read) }
                .onFailure { refreshNotifications() }
        }
    }

    private fun deleteNotification(id: String) {
        if (notifications.none { it.id == id }) return
        notifications = notifications.filterNot { it.id == id }
        if (id.startsWith("local-backup-")) {
            localNotifications.delete(profile.id, id)
            return
        }
        scope.launch {
            runCatching { repository.deleteNotification(id) }
                .onFailure { refreshNotifications() }
        }
    }

    private fun markAllNotificationsRead() {
        notifications = notifications.map { it.copy(read = true) }
        localNotifications.markAllRead(profile.id)
        scope.launch { runCatching { repository.markNotificationsRead() } }
    }

    /**
     * The count behind the dot on Home's Chat row.
     *
     * Direct messages do not appear in the alerts panel at all — a message is
     * something to answer, not something to have merely seen, so it gets its
     * own count on the way in rather than sharing the operator's feed.
     */
    private fun refreshUnreadDmCount() {
        if (!repository.hasSession) return
        scope.launch {
            runCatching { repository.conversations() }
                .onSuccess {
                    conversations = it
                    unreadDmCount = it.sumOf { conversation -> conversation.unread }
                }
        }
    }

    /** The soonest advertised event on this address that has not begun yet. */
    private fun pendingEventFor(address: String): WyrmNotification? = visibleNotifications()
        .filter {
            it.kind == NotificationKind.EVENT &&
                it.serverAddress == address &&
                !it.hasStarted()
        }
        .minByOrNull { it.startsAt.orEmpty() }

    private fun requestArenaEntry(address: String) {
        pendingEventFor(address)?.let {
            eventGate = it
            return
        }
        beginArenaEntry(address)
    }

    /**
     * Rotate, and stop.
     *
     * Connecting and rotating used to happen together, and the window skips
     * every frame it spends rebuilding the swapchain — which is where
     * `server_poll` lives. A snake was being admitted while nothing could answer
     * for it. Landing here first means Play connects into a loop that is already
     * settled and already sideways.
     */
    private fun openLobby() {
        panelOpen = false
        host?.onRequestLandscape(true)
        route = Route.LOBBY
        watchLobbyArena()
        host?.onOpenLobby()
    }

    private fun leaveLobby() {
        lobbyJob?.cancel()
        lobbyQuickSettings = false
        host?.onLeaveLobby()
        host?.onRequestLandscape(false)
        route = Route.HOME
    }

    /**
     * Lobby › Share run: out of the lobby (portrait again, the engine back on
     * its title screen) and into the Share editor on the last run. showRoute
     * ignores the engine's title echo while it is open.
     */
    /** The tag worn when Share opened (an index in [SkinCatalog.tags]), for its sticker and JSON. */
    private var shareTagId = -1

    /** The worn tag, read from the engine (the cached [settings] can be empty). */
    private fun wornTagId(): Int =
        SettingsCodec.settings(host?.onReadSettings().orEmpty())
            .firstOrNull { it.id == "tags.index" }?.number?.roundToInt()
            ?.takeIf { it in SkinCatalog.tags.indices } ?: -1

    private fun openShareRun() {
        val run = lastRun ?: return
        if (!TRAILS_ENABLED || !repository.hasSession) return
        shareTagId = wornTagId()
        shareRun = run
        shareSkinOnly = false
        lobbyJob?.cancel()
        lobbyQuickSettings = false
        host?.onLeaveLobby()
        host?.onRequestLandscape(false)
        // The sticker wears what the engine says is worn.
        host?.onSkinSync(false)
        panelOpen = false
        tabRoot = Route.HOME
        route = Route.TRAIL_SHARE
    }

    private fun closeShareRun() {
        val fromSkin = shareSkinOnly
        shareSkinOnly = false
        tabRoot = if (fromSkin) Route.SKIN else Route.HOME
        route = tabRoot
    }

    /** Skin › Share this skin: the Share editor with the worn skin as the preview draws it; closing goes back to Skin. */
    private fun openShareSkin() {
        if (!TRAILS_ENABLED || !repository.hasSession) return
        shareTagId = wornTagId()
        shareSkinOnly = true
        panelOpen = false
        tabRoot = Route.SKIN
        route = Route.TRAIL_SHARE
    }

    /** Posted: the Trails feed, where the upload shows its progress. */
    private fun openTrailsAfterShare() {
        shareSkinOnly = false
        panelOpen = false
        tabRoot = Route.TRAILS
        route = Route.TRAILS
    }

    /** Trail › Try this skin: the Skin tab with the poster's look as a draft. */
    private fun tryTrailSkin(trail: com.wyrm.omrajput.data.Trail) {
        val skin = trail.skin ?: return
        skinTrial = SkinTrial(trail.author.name, skin.toSkinState(), skin.lookSpec(),
            SkinCatalog.tags.firstOrNull { it.ntlId == skin.tag }?.id ?: -1)
        openSkinTab()
    }

    private fun editTrial(change: (SkinTrial) -> SkinTrial) {
        skinTrial = skinTrial?.let(change)
    }

    /**
     * "Try this skin" › Wear: the calls the Skin tab makes, then saved (a
     * preset skin keeps the player's own code underneath, as picking a preset
     * does), and the Wyrm look.
     */
    private fun wearSkinTrial() {
        val trial = skinTrial ?: return
        val skin = trial.skin
        val custom = skin.custom && skin.code.isNotEmpty()
        host?.onSkinPreset(skin.preset)
        if (custom) host?.onSkinCode(skin.code, skin.coloursFor(skin.code.length))
        host?.onSkinAccessory(skin.accessory)
        host?.onSkinSync(true)
        WyrmLookStore.wear(trial.look)
        // The poster's tag too (OM, 2026-10-04). A trail without one leaves yours on.
        if (trial.tagId >= 0) {
            refreshSettings()
            settings.firstOrNull { it.id == "tags.index" }?.let { writeSetting(it, listOf(trial.tagId.toFloat())) }
        }
        // Shown at once; the engine's answer confirms it.
        skinState = if (custom) skin else skinState.copy(custom = false, preset = skin.preset, accessory = skin.accessory)
        skinTrial = null
    }

    /**
     * Fills the lobby's readings from what the picker already knows.
     *
     * The lobby used to dial the selected arena's game port every two seconds
     * for a round trip, right up to the moment Play dialled the same port.
     * Nothing touches the network from here now: the record and the round
     * trip are whatever the picker last measured, and an arena it never
     * measured simply shows no ping. Only an open picker probes.
     */
    private fun watchLobbyArena() {
        lobbyJob?.cancel()
        lobbyJob = null
        val endpoint = arenaLabel
        if (!ArenaDirectory.isValidEndpoint(endpoint)) {
            lobbyArena = null
            lobbyPing = 0
            return
        }
        lobbyArena = arenaState.arenas.firstOrNull { it.endpoint == endpoint }
        lobbyPing = arenaState.pings[endpoint]?.takeIf { it > 0 } ?: 0
    }

    /** Holds one Wyrm frame before native rotation and connection take over. */
    private fun beginArenaEntry(address: String) {
        if (enteringArena || arenaProbeGatePending) return
        val target = address.takeIf { ArenaDirectory.isValidEndpoint(it) }
            ?: return
        val arenaHost = host ?: return
        enteringArena = true
        panelOpen = false
        lobbyJob?.cancel()
        stopArenaProbes()
        armArenaGate()
        val attemptId = SystemClock.elapsedRealtimeNanos()
        if (target != arenaLabel) selectArena(target)
        /*
         * Asked for immediately.
         *
         * There used to be a 700ms wait here so the entering card had time to
         * be looked at. It bought nothing and cost the thing it was decorating:
         * the connection started three quarters of a second after the card
         * appeared, so the card was mostly padding, and on a fast arena the
         * match was live and running before the card had finished being shown —
         * which is the snake arriving mid-flight, already moving, with the first
         * ping measuring the padding rather than the arena.
         *
         * The card now lasts exactly as long as connecting takes, because that
         * is the only honest thing for it to measure.
         */
        arenaHost.onEnterArena(nickname, target, attemptId)
        syncIngameName()
    }

    private fun joinBattledomeEvent(address: String) = requestArenaEntry(address)

    /** Routes a system-notification tap only after the account is validated. */
    fun openFromNotification(kind: String, id: String, actorId: String, action: String, address: String) {
        val target = NotificationOpen(kind, id, actorId, action, address)
        if (!sessionReady) {
            pendingNotificationOpen = target
            return
        }
        routeNotificationOpen(target)
    }

    private fun routeNotificationOpen(target: NotificationOpen) {
        // Play on a Battledome notification: that event's arena, not the lobby's
        // selection. The alerts load first, so an event that has not begun gets
        // its "starts in" pause instead of an early entry.
        if (target.action == "play" && target.address.isNotBlank()) {
            scope.launch {
                runCatching { repository.notifications() }
                    .onSuccess { rows -> mergeNotifications(rows.map { it.toWyrmNotification() }) }
                target.id.takeIf { it.isNotBlank() }?.let { setNotificationRead(it, true) }
                joinBattledomeEvent(target.address)
            }
            return
        }
        when (target.kind) {
            "voice_call" -> openVoiceChat()
            "voice_invite" -> if (target.id.isBlank()) openNotifications() else openVoiceInviteDeepLink(target.id)
            "dm" -> if (target.actorId.isNotBlank()) openThreadById(target.actorId) else {
                openChat()
                chatTab = ChatTab.DIRECT
            }
            "follow" -> if (target.actorId.isNotBlank()) {
                if (!route.growsFromHome) openPanel(Rect.Zero) { openPlayer(target.actorId) } else openPlayer(target.actorId)
            } else {
                route = Route.HOME
            }
            // The push carries only the alert's id: the trail is in its row.
            "trail_like", "trail_reply" -> if (TRAILS_ENABLED) openAlertById(target.id) else openNotifications()
            "support" -> openAlertById(target.id)
            "invite", "notice", "broadcast", "feature", "update", "event",
            "achievement", "rank", "backup" -> {
                highlightedNotification = target.id.takeIf { it.isNotBlank() }
                openNotifications()
            }
            else -> route = Route.HOME
        }
    }

    /** A tapped push for a trail or a reply from Wyrm: find its alert, then open it as a card tap would. */
    private fun openAlertById(notificationId: String) {
        scope.launch {
            runCatching { repository.notifications() }
                .onSuccess { rows ->
                    mergeNotifications(rows.map { it.toWyrmNotification() })
                    if (notifications.any { it.id == notificationId }) {
                        openNotificationCard(notificationId)
                    } else {
                        highlightedNotification = notificationId.takeIf { it.isNotBlank() }
                        openNotifications()
                    }
                }
                .onFailure {
                    highlightedNotification = notificationId.takeIf { it.isNotBlank() }
                    openNotifications()
                }
        }
    }

    /** Your reports, from an alert: its own page in the panel; Back closes it. */
    private fun openSupportReportsFromAlert() {
        supportReportsReturn = null
        if (!route.growsFromHome) {
            rootTabForRoute(route)?.let { tabRoot = route }
            panelOrigin = Rect.Zero
            panelOpen = true
        } else {
            supportReportsReturn = route.takeIf { it == Route.HELP }
        }
        route = Route.SUPPORT_REPORTS
    }

    private fun openVoiceInviteDeepLink(notificationId: String) {
        route = Route.VOICE
        scope.launch {
            val verification = runCatching { voiceRepository.verificationStatus() }
            val feed = runCatching { repository.notifications() }
            verification.getOrNull()?.let { voiceState = voiceState.copy(verified = it.verified) }
            feed.onSuccess { rows ->
                mergeNotifications(rows.map { it.toWyrmNotification() })
                val invite = notifications.firstOrNull {
                    it.id == notificationId && it.kind == NotificationKind.VOICE_INVITE
                }
                if (invite == null) {
                    voiceState = voiceState.copy(error = "That voice invitation is no longer available.")
                } else {
                    openNotificationCard(invite.id)
                }
            }.onFailure {
                highlightedNotification = notificationId
                openNotifications()
            }
        }
    }

    private fun openThreadById(playerId: String) {
        scope.launch {
            runCatching { repository.player(playerId) }
                .onSuccess { player ->
                    if (player.canMessage) openThread(player) else {
                        openChat()
                        chatTab = ChatTab.DIRECT
                        chatError = "This conversation is no longer available."
                    }
                }
                .onFailure {
                    openChat()
                    chatTab = ChatTab.DIRECT
                    chatError = "That conversation couldn't be opened."
                }
        }
    }

    private fun loadLeaderboard(sort: LeaderboardSort, force: Boolean = false) {
        boardSort = sort
        // Switching Score/Kills or opening the board only reads the cache.
        // The network is touched by the sync, the hourly refresh, a counted
        // run, or the player pulling the list down.
        if (force) refreshLeaderboards()
    }

    /** Refreshes the cached boards once an hour for as long as the app runs. */
    private fun startBoardRefresh() {
        if (boardRefreshJob?.isActive == true) return
        boardRefreshJob = scope.launch {
            while (isActive) {
                val savedAt = socialCache.leaderboard("score")?.savedAt ?: 0L
                val wait = (savedAt + 60 * 60_000L - System.currentTimeMillis()).coerceAtLeast(0L)
                delay(wait)
                if (repository.hasSession && profile.id.isNotBlank()) refreshLeaderboards(silent = true)
                delay(60_000L)
            }
        }
    }

    /** Wyrm iOS's `refreshLeaderboards`: score and kills, together. */
    private fun refreshLeaderboards(silent: Boolean = false) {
        val gen = ++boardLoadGen
        scope.launch {
            if (!silent) boardLoading = true
            boardError = ""
            val score = async { runCatching { repository.leaderboard("score") } }
            val kills = async { runCatching { repository.leaderboard("kills") } }
            val scoreResult = score.await()
            val killResult = kills.await()
            if (gen != boardLoadGen) return@launch
            scoreResult.onSuccess {
                scoreBoard = it
                socialCache.saveLeaderboard("score", it)
            }
            killResult.onSuccess {
                killBoard = it
                socialCache.saveLeaderboard("kills", it)
            }
            launch { AvatarImages.prefetch((scoreBoard + killBoard).map { it.avatarUrl }) }
            val failure = scoreResult.exceptionOrNull() ?: killResult.exceptionOrNull()
            boardOffline = failure != null
            boardError = when {
                failure == null -> ""
                repository.isUnauthorized(failure) -> "Your session expired. Sign in again."
                else -> "Couldn't reach the leaderboard."
            }
            if (failure == null) boardUpdatedAt = System.currentTimeMillis()
            boardLoading = false
        }
    }

    @Suppress("unused")
    private fun loadLeaderboardOld(sort: LeaderboardSort, force: Boolean = false) {
        boardSort = sort
        val cached = socialCache.leaderboard(sort.api)
        if (cached != null) {
            if (sort == LeaderboardSort.SCORE) scoreBoard = cached.value else killBoard = cached.value
            boardUpdatedAt = cached.savedAt
            if (!force && cached.fresh) return
        }
        val gen = ++boardLoadGen
        scope.launch {
            boardLoading = true
            boardError = ""
            runCatching { repository.leaderboard(sort.api) }
                .onSuccess {
                    if (gen != boardLoadGen) return@onSuccess
                    if (sort == LeaderboardSort.SCORE) scoreBoard = it else killBoard = it
                    socialCache.saveLeaderboard(sort.api, it)
                    boardUpdatedAt = System.currentTimeMillis()
                    boardOffline = false
                    boardLoading = false
                }
                .onFailure { error ->
                    if (gen != boardLoadGen) return@onFailure
                    boardLoading = false
                    boardOffline = true
                    boardError = if (repository.isUnauthorized(error)) {
                        "Your session expired. Sign in again."
                    } else {
                        "Couldn't reach the leaderboard."
                    }
                }
        }
    }

    fun updateArena(label: String, online: Boolean) {
        activity.runOnUiThread {
            arenaLabel = label
            lobbyArena = arenaState.arenas.firstOrNull {
                it.endpoint.equals(label, ignoreCase = true)
            }
            arenaOnline = online
        }
    }

    /**
     * Notes that an arena refused a join, until [seconds] from now.
     *
     * Kept as the instant the mark lapses rather than the instant it was made,
     * so the picker only ever has to compare it against the clock. Marks that
     * have already lapsed are dropped as they are written, which is often
     * enough for a map that never holds more than a handful of rows.
     */
    fun arenaRefused(endpoint: String, seconds: Int) {
        activity.runOnUiThread {
            val now = SystemClock.elapsedRealtime()
            val lapses = now + seconds * 1000L
            arenaState = arenaState.copy(
                refused = arenaState.refused
                    .filterValues { it > now } + (endpoint to lapses),
            )
        }
    }

    /**
     * The arena dropped a live snake (engine thread, via WyrmActivity). The
     * directory record, when the picker knows the endpoint, adds the arena's
     * id, cluster and player count; DropWatch does the rest.
     */
    fun onArenaDrop(packed: String) {
        activity.runOnUiThread {
            val endpoint = packed.lineSequence()
                .firstOrNull { it.startsWith("arena=") }
                ?.removePrefix("arena=")?.trim().orEmpty()
            val arena = arenaState.arenas.firstOrNull { it.endpoint.equals(endpoint, ignoreCase = true) }
                ?: lobbyArena?.takeIf { it.endpoint.equals(endpoint, ignoreCase = true) }
            com.wyrm.omrajput.data.DropWatch.onNativeDrop(packed, arena, repository)
        }
    }

    fun onActivityResumed() {
        activityResumed = true
        if (shown) lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        refreshNotificationPreferences()
        refreshUnreadDmCount()
        refreshNotifications()
        // The Settings badge counts replies from Wyrm; keep it fresh.
        com.wyrm.omrajput.data.SupportStore.refresh()
        startLiveInbox()
    }

    /* --------------------------------------------------------- live inbox */

    /** `/v1/me/live` while Wyrm is on screen: new notifications land without a refresh. */
    private val liveInbox = com.wyrm.omrajput.data.LiveInbox(activity, BuildConfig.WYRM_API_URL, scope) { kind, _ ->
        onLiveInbox(kind)
    }
    private var inboxPollJob: Job? = null

    private fun startLiveInbox() {
        if (!repository.hasSession) return
        liveInbox.start()
        refreshGlobalUnread()
        // Belt and braces: while the Notifications page is open it also asks every 15 s.
        // Global chat's count is looked at every 45 s, never mid-match.
        if (inboxPollJob?.isActive != true) {
            inboxPollJob = scope.launch {
                var tick = 0
                while (activityResumed && repository.hasSession) {
                    delay(15_000L)
                    if (route == Route.NOTIFICATIONS) refreshNotifications()
                    if (++tick % 3 == 0 && arenaPortFree) refreshGlobalUnread()
                }
            }
        }
    }

    private fun stopLiveInbox() {
        liveInbox.stop()
        inboxPollJob?.cancel()
        inboxPollJob = null
    }

    /** Something new for this player: refetch what it touches. Empty kind: catch up on everything. */
    private fun onLiveInbox(kind: String) {
        when (kind) {
            "dm" -> refreshUnreadDmCount()
            "support" -> { refreshNotifications(); com.wyrm.omrajput.data.SupportStore.refresh() }
            "" -> {
                refreshNotifications()
                refreshUnreadDmCount()
                com.wyrm.omrajput.data.SupportStore.refresh()
            }
            else -> refreshNotifications()
        }
    }

    /** Runtime permission dialogs do not always pause the Activity. */
    fun onNotificationPermissionChanged() {
        activity.runOnUiThread { refreshNotificationPreferences() }
    }

    fun onActivityPaused() {
        activityResumed = false
        stopLiveInbox()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onActivityDestroyed() {
        if (pushReceiverRegistered) {
            runCatching { activity.unregisterReceiver(pushReceiver) }
            pushReceiverRegistered = false
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        scope.cancel()
        repository.close()
        store.clear()
        root = null
        voiceHud = null
    }
}

private fun ApiPlayer.toWyrmProfile(): WyrmProfile = WyrmProfile(
    id = id,
    displayName = displayName,
    ingameName = ingameName.orEmpty(),
    username = username.orEmpty(),
    bio = bio,
    avatarKey = avatarKey,
    avatarUrl = avatarUrl,
    highestScore = highestScore,
    kills = kills,
    followerCount = followerCount,
    followingCount = followingCount,
    memberSince = createdAt,
)
