package com.wyrm.omrajput;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.wyrm.omrajput.ui.WyrmOverlay;

import org.libsdl.app.SDLActivity;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android entry point and the lifecycle-safe bridge for the native Update Center. */
public final class WyrmActivity extends SDLActivity {
    private static final String TAG = "Wyrm";

    static final int UPDATE_IDLE = 0;
    static final int UPDATE_CHECKING = 1;
    static final int UPDATE_UP_TO_DATE = 2;
    static final int UPDATE_AVAILABLE = 3;
    static final int UPDATE_DOWNLOADING = 4;
    static final int UPDATE_VERIFYING = 5;
    static final int UPDATE_READY_TO_INSTALL = 6;
    static final int UPDATE_INSTALLING = 7;
    static final int UPDATE_ERROR = 8;

    static final int BACKUP_IDLE = 0;
    static final int BACKUP_SELECTING_FOLDER = 1;
    static final int BACKUP_SAVING = 2;
    static final int BACKUP_SAVED = 3;
    static final int BACKUP_SCANNING = 4;
    static final int BACKUP_FOUND = 5;
    static final int BACKUP_NONE = 6;
    static final int BACKUP_RESTORING = 7;
    static final int BACKUP_RESTORED = 8;
    static final int BACKUP_ERROR = 9;
    static final int BACKUP_RESTORE_AVAILABLE = 10;
    static final int BACKUP_CURRENT = 11;
    static final int BACKUP_RESTARTING = 12;
    static final int BACKUP_PARTIAL = 13;

    private static final int REQUEST_LEGACY_STORAGE = 6418;
    private static final int REQUEST_PROFILE_PHOTO = 7311;
    private static final int REQUEST_POST_NOTIFICATIONS = 8123;
    private static final int REQUEST_RECORD_AUDIO = 8124;

    /** Longest edge kept when reading a chosen photo, before cropping. */
    private static final int MAX_PHOTO_PIXELS = 1600;

    /** Mirrors the screen enum in app/src/constants.h. */
    static final int SCREEN_TITLE = 0;
    static final int SCREEN_SKIN_EDITOR = 1;
    static final int SCREEN_PLAYING = 2;
    static final int SCREEN_LOBBY = 3;

    private final ExecutorService photoExecutor = Executors.newSingleThreadExecutor();
    private BackupManager backupManager;
    private UpdateManager updateManager;
    private WyrmGameMode gameMode;
    private WyrmOverlay overlay;
    private boolean titleScreenReady;

    private static native void nativeOnUpdateState(
            int status, int progress, String title, String detail,
            long versionCode, String versionName);
    private static native void nativeOnBackupState(
            int status, int count, String title, String detail);
    private static native String nativeApplyBackup(byte[] payload, byte[] teamConfig);
    /**
     * Compose Home -> engine. Both are parked in a native mailbox and applied on
     * the engine thread, so calling them from the main thread is safe.
     */
    private static native void nativeEnterArena(
            String nickname, String address, long attemptId);
    private static native void nativeEnterAiMode(String nickname);
    private static native void nativeEnterAiLayoutEditor(String nickname);
    private static native void nativeExitAiLayoutEditor();
    private static native void nativeToggleEditorLeaderboard();
    private static native void nativeSetNickname(String nickname);
    private static native void nativeSelectArena(String address);
    private static native void nativeSetArenaTheme(int[] colours, boolean dark);
    private static native void nativeOpenScreen(int screen);
    private static native void nativeSkinSetPreset(int preset);
    private static native void nativeSkinSetMode(boolean custom);
    private static native void nativeSkinSetCode(String code);
    private static native void nativeSkinSetColors(int[] colors);
    private static native void nativeSkinSync(boolean save);
    private static native void nativeSkinSetBackground(int index);
    private static native void nativeArenaSkinSet(int snakeId, String nickname, int[] colors);
    private static native void nativeArenaSkinsClear();

    /** Hands one fetched arena skin to the engine. */
    public static void applyArenaSkin(int snakeId, String nickname, int[] colors) {
        nativeArenaSkinSet(snakeId, nickname == null ? "" : nickname,
                           colors == null ? new int[0] : colors);
    }

    public static void clearArenaSkins() { nativeArenaSkinsClear(); }
    private static native void nativeSkinSetAccessory(int accessory);
    private static native void nativeSkinCommit();
    private static native void nativeSkinSetLayout(
            float previewCentreY, float previewScale,
            float accessoryX, float accessoryY,
            float accessoryCell, float accessoryGap);
    private static native void nativeSkinSetPostcard(boolean on);
    private static native void nativeDeathPlay();
    private static native void nativeDeathHome();

    /**
     * The settings bridge.
     *
     * The engine describes every setting it holds — id, type, range, options —
     * and Compose renders itself from that description, so a new setting is a
     * line of C and nothing here.
     */
    private static native String nativeSettingsSnapshot();
    private static native String nativeHotkeysSnapshot();
    private static native String nativeKeyOptions();
    private static native String nativeSettingsVersion();
    private static native void nativeSetAutoRespawn(boolean on);
    private static native void nativeSetSetting(
            String id, float a, float b, float c, float d, int count);
    private static native void nativeSetHotkey(
            int action, int key, int mode, boolean visible, float x, float y);
    private static native void nativeSettingsAction(int action);
    private static native String nativeTeamPresence();
    private static native void nativeSetTeamMembers(String packed);
    private static native void nativeCloseTeamChat(float seconds);
    private static native String nativeUpdateSnapshot();
    private static native void nativeUpdateAction(int action);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Nobody moves the screen for the keyboard except us.
         *
         * A fullscreen window cannot be resized for the keyboard, so Android
         * falls back to sliding the whole window up until the focused field
         * clears the keys. Compose was then padding for the keyboard as well,
         * on top of a window that had already been pushed — so a composer ended
         * up floating high above the keys, and in the arena the chat panel was
         * shoved off the top of the screen entirely. Two hands on the same
         * wheel. This takes one of them off; the layout keeps its own padding,
         * which is the one that knows where the composer actually is.
         */
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);

        backupManager = new BackupManager(this);
        updateManager = new UpdateManager(this, backupManager);
        gameMode = new WyrmGameMode(this);
        ensureNotificationsReady();

        // Wyrm's interface layer sits above SDL's surface and owns Home
        // outright. It stays hidden until the engine reports which screen it is
        // on, so it can never cover the launch or steal its touches.
        overlay = new WyrmOverlay(this);
        overlay.attach();
        overlay.setHost(new WyrmOverlay.Host() {
            @Override
            public void onEnterArena(String nickname, String address, long attemptId) {
                Log.i(TAG, "Home: enter arena " + address + " as '" + nickname
                        + "' (attempt " + attemptId + ")");
                nativeEnterArena(nickname, address, attemptId);
            }

            @Override
            public void onEnterAiMode(String nickname) {
                Log.i(TAG, "Lobby: enter offline AI mode as '" + nickname + "'");
                nativeEnterAiMode(nickname);
            }

            @Override
            public void onEnterAiLayoutEditor(String nickname) {
                nativeEnterAiLayoutEditor(nickname);
            }

            @Override
            public void onExitAiLayoutEditor() {
                nativeExitAiLayoutEditor();
            }

            @Override
            public void onToggleEditorLeaderboard() {
                nativeToggleEditorLeaderboard();
            }

            @Override
            public void onSetNickname(String nickname) {
                nativeSetNickname(nickname == null ? "" : nickname);
            }

            @Override
            public void onSelectArena(String address) {
                Log.i(TAG, "Home: arena selected " + address);
                nativeSelectArena(address);
            }

            @Override
            public void onArenaTheme(int[] colours, boolean dark) {
                nativeSetArenaTheme(colours, dark);
            }

            @Override
            public void onPickPhoto() {
                pickProfilePhoto();
            }

            @Override
            public String onReadSettings() {
                return nativeSettingsSnapshot();
            }

            @Override
            public String onReadHotkeys() {
                return nativeHotkeysSnapshot();
            }

            @Override
            public String onReadKeyOptions() {
                return nativeKeyOptions();
            }

            @Override
            public String onReadSettingsVersion() {
                return nativeSettingsVersion();
            }

            @Override
            public void onSetAutoRespawn(boolean on) {
                nativeSetAutoRespawn(on);
            }

            @Override
            public void onWriteSetting(String id, float[] values) {
                nativeSetSetting(id,
                        values.length > 0 ? values[0] : 0f,
                        values.length > 1 ? values[1] : 0f,
                        values.length > 2 ? values[2] : 0f,
                        values.length > 3 ? values[3] : 0f,
                        values.length);
            }

            @Override
            public void onWriteHotkey(int action, int key, int mode, boolean visible,
                                      float x, float y) {
                nativeSetHotkey(action, key, mode, visible, x, y);
            }

            @Override
            public void onSettingsAction(int action) {
                nativeSettingsAction(action);
            }

            @Override
            public String onReadPresence() {
                return nativeTeamPresence();
            }

            @Override
            public void onWriteTeamMembers(String packed) {
                nativeSetTeamMembers(packed);
            }

            /**
             * The chat panel closing, and the snake coming back.
             *
             * The engine keeps the bot for a few more seconds and counts them
             * down on screen, so the player is never handed a snake they were
             * not looking at.
             */
            @Override
            public void onCloseTeamChat(float seconds) {
                nativeCloseTeamChat(seconds);
                // The panel put the interface layer up; nothing else will take
                // it down, and leaving it up is what dropped the player back
                // into the menu instead of the match they never left.
                setTeamChatFromNative(false);
            }

            @Override
            public String onReadUpdate() {
                return nativeUpdateSnapshot();
            }

            @Override
            public void onUpdateAction(int action) {
                nativeUpdateAction(action);
            }

            @Override
            public void onBackupAction(int action) {
                // Native owns the current user_settings bytes. Keeping these
                // actions beside update actions means a backup always captures
                // the same in-memory state the next update would protect.
                nativeUpdateAction(action + 2);
            }

            /**
             * Compose asking for the phone to turn.
             *
             * The layout editors are the only Compose screens that want
             * landscape, and they want it while the engine is still sitting on
             * the title screen — so orientation cannot be decided by the
             * engine's screen alone any more.
             */
            @Override
            public void onRequestLandscape(boolean landscape) {
                runOnUiThread(() -> setRequestedOrientation(landscape
                        ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            }

            @Override
            public void onOpenLobby() {
                Log.i(TAG, "Home: enter lobby");
                nativeOpenScreen(SCREEN_LOBBY);
            }

            @Override
            public void onLeaveLobby() {
                nativeOpenScreen(SCREEN_TITLE);
            }

            @Override
            public void onOpenSkinEditor() {
                Log.i(TAG, "Home: open skin editor");
                nativeOpenScreen(SCREEN_SKIN_EDITOR);
            }

            @Override
            public void onSkinPreset(int index) {
                nativeSkinSetPreset(index);
            }

            @Override
            public void onSkinCode(String code, int[] colors) {
                // Colours are positional, so they go first: the engine clears
                // any previous mixture when a code arrives without them.
                nativeSkinSetColors(colors);
                // An empty pattern is not a skin; the engine falls back to the
                // chosen preset, and saying so here keeps the two in step.
                nativeSkinSetCode(code);
                nativeSkinSetMode(!code.isEmpty());
            }

            @Override
            public void onSkinSync(boolean save) {
                nativeSkinSync(save);
            }

            @Override
            public void onSkinBackground(int index) {
                nativeSkinSetBackground(index);
            }

            @Override
            public void onSkinAccessory(int id) {
                nativeSkinSetAccessory(id);
            }

            @Override
            public void onSkinCommit() {
                nativeSkinCommit();
            }

            @Override
            public void onDeathPlay() {
                Log.i(TAG, "Death: play again");
                nativeDeathPlay();
            }

            @Override
            public void onDeathHome() {
                Log.i(TAG, "Death: back to home");
                nativeDeathHome();
            }

            @Override
            public void onSkinLayout(float previewCentreY, float previewScale,
                                     float accessoryX, float accessoryY,
                                     float accessoryCell, float accessoryGap) {
                nativeSkinSetLayout(previewCentreY, previewScale,
                        accessoryX, accessoryY, accessoryCell, accessoryGap);
            }

            @Override
            public void onSkinPostcard(boolean on) {
                nativeSkinSetPostcard(on);
            }

            @Override
            public void onGameModeEligible(boolean eligible) {
                if (gameMode != null) gameMode.setEligible(eligible);
            }
        });
        dispatchNotificationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        dispatchNotificationIntent(intent);
    }

    private void dispatchNotificationIntent(Intent intent) {
        if (overlay == null || intent == null) return;
        String kind = intent.getStringExtra("wyrm.kind");
        if (kind == null || kind.isEmpty()) return;
        overlay.openFromNotification(
                kind,
                safe(intent.getStringExtra("wyrm.id")),
                safe(intent.getStringExtra("wyrm.actorId")));
        intent.removeExtra("wyrm.kind");
        intent.removeExtra("wyrm.id");
        intent.removeExtra("wyrm.actorId");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameMode != null) gameMode.onResume();
        if (overlay != null) overlay.onActivityResumed();
        if (updateManager != null) {
            updateManager.resumePendingInstall();
        }
    }

    @Override
    protected void onPause() {
        if (gameMode != null) gameMode.onPause();
        if (overlay != null) overlay.onActivityPaused();
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PROFILE_PHOTO) {
            Uri picked = resultCode == Activity.RESULT_OK && data != null ? data.getData() : null;
            if (picked != null) {
                decodeProfilePhoto(picked);
            }
            return;
        }
        if (backupManager != null
                && backupManager.onActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Asks the system for one image.
     *
     * OPEN_DOCUMENT rather than a storage permission: the picker hands back a
     * single readable image and nothing else, so Wyrm never asks to read the
     * gallery and never can.
     */
    private void pickProfilePhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/png", "image/jpeg"});
        try {
            startActivityForResult(intent, REQUEST_PROFILE_PHOTO);
        } catch (Exception unavailable) {
            Log.w(TAG, "No image picker on this device", unavailable);
            if (overlay != null) overlay.onPhotoPicked(null);
        }
    }

    /**
     * Reads the chosen image at a workable size.
     *
     * A modern phone camera produces something far larger than any screen can
     * show, and decoding it whole would be several hundred megabytes of bitmap
     * for a picture that ends up 512 pixels square. This samples it down on the
     * way in, off the main thread, and hands the interface something it can
     * hold.
     */
    private void decodeProfilePhoto(Uri source) {
        photoExecutor.execute(() -> {
            Bitmap decoded = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream probe = getContentResolver().openInputStream(source)) {
                    BitmapFactory.decodeStream(probe, null, bounds);
                }
                int longest = Math.max(bounds.outWidth, bounds.outHeight);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (longest / options.inSampleSize > MAX_PHOTO_PIXELS) options.inSampleSize *= 2;
                try (InputStream stream = getContentResolver().openInputStream(source)) {
                    decoded = BitmapFactory.decodeStream(stream, null, options);
                }
            } catch (Throwable failure) {
                Log.w(TAG, "Could not read the chosen image", failure);
            }
            final Bitmap photo = decoded;
            runOnUiThread(() -> {
                if (overlay != null) overlay.onPhotoPicked(photo);
            });
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            if (overlay != null) overlay.onNotificationPermissionChanged();
            return;
        }
        if (requestCode == REQUEST_RECORD_AUDIO) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (overlay != null) overlay.onVoiceMicrophonePermission(granted);
            return;
        }
        if (requestCode != REQUEST_LEGACY_STORAGE) {
            return;
        }
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        emitUpdateState(granted ? UPDATE_READY_TO_INSTALL : UPDATE_ERROR, 100,
                granted ? "Storage access granted" : "Storage access denied",
                granted
                        ? "Tap Download & Install again to finish the verified update."
                        : "Shared Downloads access is required on this Android version.",
                0L, "");
    }

    @Override
    protected void onDestroy() {
        if (overlay != null) {
            overlay.onActivityDestroyed();
            overlay = null;
        }
        if (updateManager != null) {
            updateManager.shutdown();
        }
        if (backupManager != null) {
            backupManager.shutdown();
        }
        photoExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (overlay != null && overlay.consumeSystemBack()) {
            return;
        }
        super.onBackPressed();
    }

    /**
     * The channel Wyrm's pushes arrive on, and the permission Android 13+
     * gates it behind.
     *
     * Created unconditionally rather than lazily on first push: a channel
     * created after the user has already found and muted "Wyrm alerts" would
     * silently start showing again, which is worse than asking once, early,
     * with nothing to show for it yet.
     */
    private void ensureNotificationsReady() {
        WyrmMessagingService.ensureChannels(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_POST_NOTIFICATIONS);
        }
    }

    boolean hasLegacyStoragePermission() {
        return Build.VERSION.SDK_INT >= 29
                || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    void requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT < 29) {
            runOnUiThread(() -> requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_LEGACY_STORAGE));
        }
    }

    String applyBackupPayload(byte[] payload, byte[] teamConfig) {
        try {
            return payload == null ? "FAILED\nReason: Backup payload is empty."
                    : nativeApplyBackup(payload, teamConfig);
        } catch (Throwable error) {
            String message = error.getMessage();
            return "FAILED\nReason: " + (message == null || message.trim().isEmpty()
                    ? error.getClass().getSimpleName() : message);
        }
    }

    void emitUpdateState(int status, int progress, String title, String detail,
                         long versionCode, String versionName) {
        nativeOnUpdateState(status, Math.max(0, Math.min(100, progress)),
                safe(title), safe(detail), versionCode, safe(versionName));
    }

    void emitBackupState(int status, int count, String title, String detail) {
        nativeOnBackupState(status, Math.max(0, count), safe(title), safe(detail));
    }

    void notifyBackupAppliedToOverlay() {
        runOnUiThread(() -> {
            if (overlay != null) overlay.onBackupRestored();
        });
    }

    /** Called once the engine has finished starting; avoids startup-touch races. */
    public static void notifyTitleScreenReadyFromNative(byte[] settingsPayload) {
        final byte[] currentSettings = settingsPayload == null
                ? null : settingsPayload.clone();
        withActivity(activity -> activity.runOnUiThread(() -> {
            if (!activity.titleScreenReady) {
                activity.titleScreenReady = true;
                if (activity.updateManager != null) {
                    activity.updateManager.onTitleScreenReady(currentSettings);
                }
            }
        }));
    }

    /**
     * The engine reporting which layer owns the screen.
     *
     * Compose is visible exactly when the engine has nothing of its own to draw,
     * so the two can never both be live and a tap on Home has nothing
     * underneath it to fall through to. Orientation rides along: Home is
     * portrait, and everything the engine draws — the arena, the arena picker,
     * the skin editor — is landscape.
     */
    public static void setScreenFromNative(int screen) {
        final boolean isLobby = screen == SCREEN_LOBBY;
        final boolean composeOwns =
                screen == SCREEN_TITLE || screen == SCREEN_SKIN_EDITOR || isLobby;
        /*
         * The lobby is a Compose screen that wants the phone sideways while the
         * engine is still sitting on the title screen, waiting to be told to
         * connect. Deciding orientation from the engine's screen alone turned
         * pressing Play into portrait, then landscape again a moment later — so
         * the rotation landed on top of admission after all, which is the one
         * thing the lobby exists to prevent. It also yanked the route back to
         * Home mid-entry.
         */
        withActivity(activity -> activity.runOnUiThread(() -> {
            /* Asked here rather than tracked in a flag, because a flag has a
             * lifetime and this question does not: Compose always knows where
             * the player currently is, and the engine's screen alone does not.
             * Read on the UI thread, which is where that state lives. */
            final boolean keepLandscape = activity.overlay != null
                    && activity.overlay.wantsLandscape();
            final boolean layoutEditor = activity.overlay != null
                    && activity.overlay.isLayoutEditorActive();
            final boolean editorReturned = screen == SCREEN_TITLE
                    && activity.overlay != null
                    && activity.overlay.consumeLayoutEditorExit();
            activity.setRequestedOrientation(composeOwns && !keepLandscape
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            if (activity.overlay != null) {
                if (isLobby) {
                    activity.overlay.showRoute(WyrmOverlay.Route.LOBBY);
                } else if (composeOwns && !keepLandscape && !editorReturned) {
                    activity.overlay.showRoute(screen == SCREEN_SKIN_EDITOR
                            ? WyrmOverlay.Route.SKIN_EDIT
                            : WyrmOverlay.Route.HOME);
                }
                activity.overlay.setInterfaceVisible(composeOwns || layoutEditor);
            }
            if (activity.gameMode != null) {
                activity.gameMode.setGameplay(!composeOwns);
            }
            if (!composeOwns) {
                activity.restoreNativeSurfaceFocus();
            }
        }));
    }

    public static void setArenaPortAvailableFromNative(boolean available) {
        withActivity(activity -> activity.runOnUiThread(() -> {
            if (activity.overlay != null) activity.overlay.onArenaPortAvailable(available);
        }));
    }

    /** The engine declined a Play without dialling; see WyrmOverlay.onArenaEnterIgnored. */
    public static void setArenaEnterIgnoredFromNative() {
        withActivity(activity -> {
            if (activity.overlay != null) activity.overlay.onArenaEnterIgnored();
        });
    }

    /**
     * The engine reporting that the in-game chat button was pressed.
     *
     * Not a change of screen: the arena carries on, the bot is already
     * steering, and only the interface layer changes.
     */
    public static void setTeamChatFromNative(boolean shown) {
        withActivity(activity -> activity.runOnUiThread(() -> {
            if (activity.overlay == null) return;
            activity.overlay.setArenaChat(shown);
            activity.overlay.setInterfaceVisible(shown);
            if (!shown) activity.restoreNativeSurfaceFocus();
        }));
    }

    /**
     * The death card, raised and dropped by the engine.
     *
     * Not routed through {@link #setScreenFromNative}: the engine is still on
     * PLAYING, the phone stays landscape, and the arena carries on rendering
     * behind the card with the bot at the controls. Only the interface changes.
     */
    public static void setDeathFromNative(boolean shown, int score, int kills,
                                          double playTimeSeconds,
                                          boolean autoRespawn) {
        withActivity(activity -> activity.runOnUiThread(() -> {
            if (activity.overlay == null) return;
            activity.overlay.setDeath(shown, score, kills, playTimeSeconds,
                                      autoRespawn);
            activity.overlay.setInterfaceVisible(shown);
            if (!shown) {
                activity.restoreNativeSurfaceFocus();
            }
        }));
    }

    /** Native arena geometry keeps the small voice surface beside the minimap. */
    public static void setVoiceHudAnchorFromNative(float left, float top, float diameter) {
        withActivity(activity -> {
            if (activity.overlay != null) {
                activity.overlay.setVoiceHudAnchor(left, top, diameter);
            }
        });
    }

    /** Starts the microphone consent only from the visible Voice Chat screen. */
    public void requestVoiceMicrophonePermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            if (overlay != null) overlay.onVoiceMicrophonePermission(true);
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    /** A finished run exists independently of whether Auto Respawn shows a card. */
    public static void recordRunFromNative(int score, int kills) {
        withActivity(activity -> activity.runOnUiThread(() -> {
            if (activity.overlay != null) {
                activity.overlay.recordRunFromNative(score, kills);
            }
        }));
    }

    /** The engine's palette and preset table, sent once. */
    public static void setSkinTablesFromNative(int[] palette, byte[] codeChars,
                                               byte[] presets) {
        withActivity(activity -> {
            if (activity.overlay != null) {
                activity.overlay.updateSkinTables(palette, codeChars, presets);
            }
        });
    }

    /**
     * The engine saying which snake is ours in which arena, or that we are no
     * longer in one. A snake id of -1 is the signal to stop publishing.
     */
    public static void publishArenaSkinFromNative(String arena, int snakeId, String nickname) {
        final String safeArena = safe(arena);
        final String safeNickname = safe(nickname);
        withActivity(activity -> {
            if (activity.overlay != null) {
                activity.overlay.publishArenaSkin(safeArena, snakeId, safeNickname);
            }
        });
    }

    /** Snakes now in view that Wyrm has not yet been asked about. */
    public static void requestArenaSkinsFromNative(int[] snakeIds) {
        final int[] safeIds = snakeIds != null ? snakeIds : new int[0];
        withActivity(activity -> {
            if (activity.overlay != null) activity.overlay.requestArenaSkins(safeIds);
        });
    }

    /** What is currently worn. */
    public static void setSkinStateFromNative(boolean custom, int preset,
                                              String code, int accessory,
                                              int[] colors, int background) {
        final String safeCode = safe(code);
        final int[] safeColors = colors != null ? colors : new int[0];
        withActivity(activity -> {
            if (activity.overlay != null) {
                activity.overlay.updateSkinState(custom, preset, safeCode, accessory,
                                                 safeColors, background);
            }
        });
    }

    /** The engine handing Compose the values it persists. */
    public static void setHomeStateFromNative(String nickname, String arena,
                                              int score, int kills) {
        final String safeNickname = safe(nickname);
        final String safeArena = safe(arena);
        withActivity(activity -> {
            if (activity.overlay == null) return;
            activity.overlay.updateProfile(safeNickname, score, kills);
            activity.overlay.updateArena(
                    safeArena.isEmpty() ? "Choose arena" : safeArena,
                    !safeArena.isEmpty());
        });
    }

    /**
     * An arena that would not take us, and how many seconds to remember it for.
     *
     * Called by app/src/platform/android_home.c when a connect attempt runs past
     * its time. The picker cannot work this out for itself: a refusing arena
     * answers the TCP ping it is measured by perfectly well.
     */
    public static void setArenaRefusedFromNative(String arena, int seconds) {
        final String safeArena = safe(arena);
        if (safeArena.isEmpty() || seconds <= 0) return;
        withActivity(activity -> {
            if (activity.overlay == null) return;
            activity.overlay.arenaRefused(safeArena, seconds);
        });
    }

    /** Called by app/src/platform/android_update.c. */
    public static void checkForUpdatesFromNative() {
        withActivity(activity -> activity.runOnUiThread(() -> {
            if (activity.updateManager != null) {
                activity.updateManager.checkForUpdates(true);
            }
        }));
    }

    public static void downloadAvailableUpdateFromNative(byte[] payload,
                                                         byte[] unused) {
        if (payload == null) {
            return;
        }
        byte[] copy = payload.clone();
        withActivity(activity -> activity.runOnUiThread(() -> {
            if (activity.updateManager != null) {
                activity.updateManager.downloadAvailableUpdate(copy);
            }
        }));
    }

    public static void createBackupFromNative(byte[] payload, byte[] unused) {
        if (payload == null) {
            return;
        }
        byte[] copy = payload.clone();
        withActivity(activity -> activity.runOnUiThread(() -> {
            if (activity.backupManager != null) {
                activity.backupManager.createBackup(copy, null);
            }
        }));
    }

    public static void checkBackupsFromNative() {
        withActivity(activity -> activity.runOnUiThread(() -> {
            if (activity.backupManager != null) {
                activity.backupManager.checkBackups();
            }
        }));
    }

    public static void restoreLatestBackupFromNative() {
        withActivity(activity -> activity.runOnUiThread(() -> {
            if (activity.backupManager != null) {
                activity.backupManager.restoreLatest();
            }
        }));
    }

    /** The instruction card's OK button; selection itself stays with Android. */
    public static void chooseBackupFolderFromNative() {
        withActivity(activity -> activity.runOnUiThread(() -> {
            if (activity.backupManager != null) {
                activity.backupManager.openFolderPicker();
            }
        }));
    }

    private static void withActivity(ActivityAction action) {
        Activity context = SDLActivity.getContext();
        if (context instanceof WyrmActivity) {
            action.run((WyrmActivity) context);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void restoreNativeSurfaceFocus() {
        if (mSurface == null) return;
        final View surface = mSurface;
        surface.setFocusable(true);
        surface.setFocusableInTouchMode(true);
        surface.requestFocus();
        surface.post(surface::requestFocus);
    }

    private interface ActivityAction {
        void run(WyrmActivity activity);
    }
}
