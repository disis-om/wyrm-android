package com.wyrm.omrajput;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import com.wyrm.omrajput.data.ArenaDirectory;
import com.wyrm.omrajput.data.SavedArenas;
import com.wyrm.omrajput.data.TeamService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Durable, user-owned Wyrm backups stored through Android's document provider. */
final class BackupManager {
    static final int REQUEST_BACKUP_TREE = 6417;

    private static final String TAG = "WyrmBackup";
    private static final String PREFS_NAME = "wyrm_backup_state";
    private static final String LEGACY_PREFS_NAME = "vlither_backup_state";
    private static final String PREF_TREE_URI = "backup_tree_uri";
    private static final String FILE_PREFIX = "wyrm";
    private static final String FILE_SUFFIX = ".wyrm";
    private static final String LEGACY_FILE_PREFIX = "Vlither-Enhanced-Backup-";
    private static final String LEGACY_FILE_SUFFIX = ".vebackup";
    private static final String BACKUP_MIME = "application/vnd.wyrm.backup";
    private static final int MAX_BACKUP_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SETTINGS_BYTES = 1024 * 1024;
    private static final int MAX_PREFERENCES_BYTES = 128 * 1024;
    private static final int MAX_VOICE_BYTES = 128 * 1024;
    private static final int MAX_TEAM_VAULT_CHARS = 96 * 1024;

    private static final String PREF_LOCAL_STATS = "wyrm_local_stats";
    private static final String PREF_TEAM = "wyrm_team_mode";
    private static final String PREF_VOICE = "wyrm_voice_preferences";
    private static final String PREF_VOICE_UI = "wyrm_voice_ui";

    private final WyrmActivity activity;
    private final SharedPreferences preferences;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private byte[] pendingPayload;
    private Runnable pendingSuccess;
    private boolean scanAfterSelection;
    private volatile Uri latestBackupUri;
    private volatile String latestBackupName = "";

    BackupManager(WyrmActivity activity) {
        this.activity = activity;
        preferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        /* The folder grant belongs to Android and survives the rename. Carry
         * its URI forward so somebody who already chose a backup folder is not
         * asked to find it again just because the archive finally says Wyrm. */
        if (!preferences.contains(PREF_TREE_URI)) {
            String legacy = activity
                    .getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(PREF_TREE_URI, "");
            if (legacy != null && !legacy.isEmpty()) {
                preferences.edit().putString(PREF_TREE_URI, legacy).apply();
            }
        }
    }

    void createBackup(byte[] settingsPayload, Runnable onSuccess) {
        if (!validSettingsPayload(settingsPayload)) {
            emit(WyrmActivity.BACKUP_ERROR, 0, "Backup unavailable",
                    "The current settings snapshot could not be prepared.");
            return;
        }
        Uri tree = savedTreeUri();
        if (tree == null || !canReadTree(tree)) {
            pendingPayload = settingsPayload.clone();
            pendingSuccess = onSuccess;
            scanAfterSelection = false;
            requestBackupFolder();
            return;
        }
        saveAsync(tree, settingsPayload.clone(), onSuccess);
    }

    /** A player asking from Settings may choose a folder if there is not one. */
    void checkBackups() {
        Uri tree = savedTreeUri();
        if (tree == null || !canReadTree(tree)) {
            scanAfterSelection = true;
            pendingPayload = null;
            pendingSuccess = null;
            requestBackupFolder();
            return;
        }
        scanAsync(tree, false, null);
    }

    /**
     * A new build checks quietly. No folder picker appears during launch: if
     * Wyrm has never been given a folder there is simply nothing it may read.
     */
    void checkAfterUpdate(byte[] currentSettings) {
        if (!validSettingsPayload(currentSettings)) {
            return;
        }
        Uri tree = savedTreeUri();
        if (tree == null || !canReadTree(tree)) {
            return;
        }
        scanAsync(tree, true, currentSettings.clone());
    }

    void restoreLatest() {
        Uri backup = latestBackupUri;
        if (backup == null) {
            checkBackups();
            return;
        }
        emit(WyrmActivity.BACKUP_RESTORING, 4, "Opening backup",
                latestBackupName);
        worker.execute(() -> {
            try {
                pauseForVisibleProgress();
                emit(WyrmActivity.BACKUP_RESTORING, 14, "Checking backup identity",
                        "Verifying the Wyrm archive before anything is applied.");
                BackupPayload restored = readAndVerifyBackup(backup);
                pauseForVisibleProgress();
                emit(WyrmActivity.BACKUP_RESTORING, 28, "Recovering safe data",
                        "Each category is validated independently.");
                String report = restored.settings == null
                        ? "FAILED\nNative settings payload is missing or unreadable."
                        : activity.applyBackupPayload(restored.settings, null);
                if (report == null || report.trim().isEmpty()) {
                    report = "FAILED\nThe native restore engine returned no result.";
                }

                RestoreReport result = parseNativeReport(report);
                if (restored.preferences != null) {
                    result.add(restorePreferences(restored.preferences));
                } else if (restored.schemaVersion >= 3) {
                    result.skip("Local stats and app preferences",
                            "missing or unreadable in this archive");
                }
                if (restored.voice != null) {
                    result.add(restoreVoice(restored.voice));
                } else if (restored.schemaVersion >= 5) {
                    result.skip("Voice preferences", "missing or unreadable in this archive");
                }
                for (String warning : restored.warnings) {
                    result.skip("Archive check", warning);
                }
                if (result.restored.isEmpty()) {
                    emit(WyrmActivity.BACKUP_ERROR, 0, "Restore failed",
                            result.failureText());
                    return;
                }

                showRestoreTimeline(result);
                activity.notifyBackupAppliedToOverlay();
                emit(result.skipped.isEmpty()
                                ? WyrmActivity.BACKUP_RESTORED
                                : WyrmActivity.BACKUP_PARTIAL,
                        100,
                        result.skipped.isEmpty()
                                ? "Restore complete"
                                : "Restore complete with skipped items",
                        result.finalReport());
            } catch (Exception error) {
                Log.e(TAG, "Backup restore failed", error);
                emit(WyrmActivity.BACKUP_ERROR, 0, "Restore failed",
                        safeMessage(error));
            }
        });
    }

    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_BACKUP_TREE) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            pendingPayload = null;
            pendingSuccess = null;
            scanAfterSelection = false;
            emit(WyrmActivity.BACKUP_ERROR, 0, "Backup folder not selected",
                    "Choose a shared folder before creating or restoring backups.");
            return true;
        }

        Uri tree = data.getData();
        int flags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            activity.getContentResolver().takePersistableUriPermission(tree, flags);
            preferences.edit().putString(PREF_TREE_URI, tree.toString()).apply();
        } catch (Exception error) {
            Log.e(TAG, "Could not persist backup folder access", error);
            emit(WyrmActivity.BACKUP_ERROR, 0, "Folder access failed",
                    safeMessage(error));
            return true;
        }

        byte[] payload = pendingPayload;
        Runnable success = pendingSuccess;
        boolean shouldScan = scanAfterSelection;
        pendingPayload = null;
        pendingSuccess = null;
        scanAfterSelection = false;
        if (payload != null) {
            saveAsync(tree, payload, success);
        } else if (shouldScan) {
            scanAsync(tree, false, null);
        }
        return true;
    }

    void shutdown() {
        worker.shutdownNow();
    }

    private void requestBackupFolder() {
        emit(WyrmActivity.BACKUP_SELECTING_FOLDER, 0, "Choose a Wyrm folder",
                "Create a folder named Wyrm, select it, then approve access. "
                        + "Wyrm will continue the pending backup automatically.");
    }

    /** Opens Android's picker only after the player accepts the explanation. */
    void openFolderPicker() {
        activity.runOnUiThread(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_BACKUP_TREE);
        });
    }

    private void saveAsync(Uri tree, byte[] settingsPayload, Runnable onSuccess) {
        emit(WyrmActivity.BACKUP_SAVING, 8, "Preparing backup",
                "Collecting IGN, skin, tags and settings.");
        worker.execute(() -> {
            Uri document = null;
            try {
                String filename = backupFilename();
                ContentResolver resolver = activity.getContentResolver();
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(
                        tree, DocumentsContract.getTreeDocumentId(tree));
                document = DocumentsContract.createDocument(
                        resolver, parent, BACKUP_MIME, filename);
                if (document == null) {
                    throw new IllegalStateException("The backup file could not be created");
                }
                emit(WyrmActivity.BACKUP_SAVING, 34, "Packing Wyrm data",
                        "Adding controls, keys, layouts and local stats.");
                try (OutputStream raw = resolver.openOutputStream(document, "w")) {
                    if (raw == null) {
                        throw new IllegalStateException("The selected folder is not writable");
                    }
                    writeBackup(raw, settingsPayload);
                }
                emit(WyrmActivity.BACKUP_SAVING, 78, "Verifying backup",
                        "Reading the new file back before the update may continue.");
                BackupPayload verification = readAndVerifyBackup(document);
                if (verification.settings == null || verification.preferences == null
                        || verification.voice == null
                        || !verification.warnings.isEmpty()) {
                    throw new SecurityException("The new backup did not pass verification");
                }
                String storedName = documentName(document, filename);
                latestBackupUri = document;
                latestBackupName = storedName;
                int count = listBackups(tree).size();
                emit(WyrmActivity.BACKUP_SAVED, count, "Backup saved",
                        displayLocation(tree, storedName));
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception error) {
                Log.e(TAG, "Backup creation failed", error);
                if (document != null) {
                    try {
                        DocumentsContract.deleteDocument(
                                activity.getContentResolver(), document);
                    } catch (Exception ignored) {
                        // Best-effort cleanup of an incomplete document.
                    }
                }
                emit(WyrmActivity.BACKUP_ERROR, 0, "Backup failed",
                        safeMessage(error));
            }
        });
    }

    private void scanAsync(Uri tree, boolean afterUpdate, byte[] currentSettings) {
        emit(WyrmActivity.BACKUP_SCANNING, 0, "Checking for backups",
                "Reading the selected Wyrm backup folder.");
        worker.execute(() -> {
            try {
                List<BackupDocument> backups = listBackups(tree);
                if (backups.isEmpty()) {
                    latestBackupUri = null;
                    latestBackupName = "";
                    emit(WyrmActivity.BACKUP_NONE, 0, "No backups found",
                            "Create one before installing a future update.");
                    return;
                }
                backups.sort(Comparator.comparingLong(
                        (BackupDocument value) -> value.modified).reversed());
                BackupDocument latest = backups.get(0);
                latestBackupUri = latest.uri;
                latestBackupName = latest.name;

                if (afterUpdate) {
                    BackupPayload payload = readAndVerifyBackup(latest.uri);
                    if (sameState(payload, currentSettings)) {
                        emit(WyrmActivity.BACKUP_CURRENT, backups.size(),
                                "Backup already applied", "Latest: " + latest.name);
                    } else {
                        emit(WyrmActivity.BACKUP_RESTORE_AVAILABLE, backups.size(),
                                "Backup found", "Latest: " + latest.name);
                    }
                    return;
                }

                emit(WyrmActivity.BACKUP_FOUND, backups.size(),
                        backups.size() + (backups.size() == 1
                                ? " backup found" : " backups found"),
                        "Latest: " + latest.name);
            } catch (Exception error) {
                Log.e(TAG, "Backup scan failed", error);
                emit(WyrmActivity.BACKUP_ERROR, 0, "Backup check failed",
                        safeMessage(error));
            }
        });
    }

    private List<BackupDocument> listBackups(Uri tree) throws Exception {
        List<BackupDocument> result = new ArrayList<>();
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor cursor = activity.getContentResolver().query(
                children, projection, null, null, null)) {
            if (cursor == null) {
                throw new IllegalStateException("The selected backup folder is unavailable");
            }
            while (cursor.moveToNext()) {
                String documentId = cursor.getString(0);
                String name = cursor.getString(1);
                long modified = cursor.isNull(2) ? 0L : cursor.getLong(2);
                if (isBackupName(name)) {
                    result.add(new BackupDocument(
                            DocumentsContract.buildDocumentUriUsingTree(tree, documentId),
                            name, modified));
                }
            }
        }
        return result;
    }

    private void writeBackup(OutputStream raw, byte[] settingsPayload) throws Exception {
        byte[] preferencesPayload = capturePreferences().toString(2)
                .getBytes(StandardCharsets.UTF_8);
        byte[] voicePayload = captureVoice().toString(2)
                .getBytes(StandardCharsets.UTF_8);
        String settingsHash = sha256(settingsPayload);
        String preferencesHash = sha256(preferencesPayload);
        String voiceHash = sha256(voicePayload);
        PackageInfo packageInfo = activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0);
        long versionCode = android.os.Build.VERSION.SDK_INT >= 28
                ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
        String versionName = packageInfo.versionName == null
                ? "unknown" : packageInfo.versionName;
        JSONObject manifest = new JSONObject()
                .put("schemaVersion", 5)
                .put("productName", "Wyrm")
                .put("packageName", activity.getPackageName())
                .put("appVersionName", versionName)
                .put("appVersionCode", versionCode)
                .put("settingsVersion", settingsVersion(settingsPayload))
                .put("settingsSize", settingsPayload.length)
                .put("settingsSha256", settingsHash)
                .put("preferencesSize", preferencesPayload.length)
                .put("preferencesSha256", preferencesHash)
                .put("voiceSize", voicePayload.length)
                .put("voiceSha256", voiceHash)
                .put("createdAt", isoTimestamp())
                .put("author", "OM Rajput");
        JSONObject checksums = new JSONObject()
                .put("user.dat", settingsHash)
                .put("preferences.json", preferencesHash)
                .put("voice.json", voiceHash);

        try (ZipOutputStream zip = new ZipOutputStream(raw)) {
            putEntry(zip, "manifest.json",
                    manifest.toString(2).getBytes(StandardCharsets.UTF_8));
            putEntry(zip, "preferences.json", preferencesPayload);
            putEntry(zip, "voice.json", voicePayload);
            /* Preferences come first so a damaged native payload cannot hide
             * the independent state that was already safely read. */
            putEntry(zip, "user.dat", settingsPayload);
            putEntry(zip, "checksums.json",
                    checksums.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.finish();
        }
    }

    private BackupPayload readAndVerifyBackup(Uri document) throws Exception {
        byte[] archive;
        try (InputStream input = activity.getContentResolver().openInputStream(document)) {
            if (input == null) {
                throw new IllegalStateException("The selected backup cannot be opened");
            }
            archive = readFully(input, MAX_BACKUP_BYTES);
        }

        List<String> warnings = new ArrayList<>();
        byte[] manifestBytes = null;
        byte[] settings = null;
        byte[] preferencesBytes = null;
        byte[] voiceBytes = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            while (true) {
                ZipEntry entry;
                try {
                    entry = zip.getNextEntry();
                } catch (Exception damagedTail) {
                    /* A broken later entry must not erase earlier independent
                     * categories that were already recovered from the stream. */
                    warnings.add("The archive ends with damaged data; readable categories were kept.");
                    break;
                }
                if (entry == null) break;
                if (entry.isDirectory()) {
                    continue;
                }
                if ("manifest.json".equals(entry.getName())) {
                    if (manifestBytes != null) throw new SecurityException("Duplicate manifest");
                    try {
                        manifestBytes = readFully(zip, 128 * 1024);
                    } catch (Exception error) {
                        throw new SecurityException("Backup identity is damaged", error);
                    }
                } else if ("user.dat".equals(entry.getName())) {
                    if (settings != null) throw new SecurityException("Duplicate settings payload");
                    try {
                        settings = readFully(zip, MAX_SETTINGS_BYTES);
                    } catch (Exception error) {
                        warnings.add("Native settings payload is damaged and was skipped.");
                    }
                } else if ("preferences.json".equals(entry.getName())) {
                    if (preferencesBytes != null) throw new SecurityException("Duplicate preferences payload");
                    try {
                        preferencesBytes = readFully(zip, MAX_PREFERENCES_BYTES);
                    } catch (Exception error) {
                        warnings.add("Local stats and app preferences are damaged and were skipped.");
                    }
                } else if ("voice.json".equals(entry.getName())) {
                    if (voiceBytes != null) throw new SecurityException("Duplicate voice preferences");
                    try {
                        voiceBytes = readFully(zip, MAX_VOICE_BYTES);
                    } catch (Exception error) {
                        warnings.add("Voice preferences are damaged and were skipped.");
                    }
                }
            }
        }
        if (manifestBytes == null) {
            throw new SecurityException("Backup identity is missing");
        }
        JSONObject manifest = new JSONObject(
                new String(manifestBytes, StandardCharsets.UTF_8));
        int schemaVersion = manifest.getInt("schemaVersion");
        String productName = manifest.getString("productName");
        boolean knownProduct = "Wyrm".equals(productName)
                || "Vlither Enhanced".equals(productName);
        if (schemaVersion < 1 || schemaVersion > 5
                || !knownProduct
                || !activity.getPackageName().equals(manifest.getString("packageName"))) {
            throw new SecurityException("Backup verification failed");
        }
        if (settings == null) {
            warnings.add("Native settings are missing and could not be restored.");
        } else {
            if (manifest.optInt("settingsSize", -1) != settings.length) {
                warnings.add("Native settings size check failed; safe categories will be recovered individually.");
            }
            if (!manifest.optString("settingsSha256", "").equals(sha256(settings))) {
                warnings.add("Native settings checksum failed; invalid categories will be skipped.");
            }
        }

        JSONObject restoredPreferences = null;
        if (schemaVersion >= 3) {
            if (preferencesBytes == null) {
                warnings.add("Local stats and app preferences are missing.");
            } else {
                if (manifest.optInt("preferencesSize", -1) != preferencesBytes.length
                        || !manifest.optString("preferencesSha256", "")
                        .equals(sha256(preferencesBytes))) {
                    warnings.add("Preferences checksum failed; only validated values were recovered.");
                }
                try {
                    restoredPreferences = new JSONObject(
                            new String(preferencesBytes, StandardCharsets.UTF_8));
                    try {
                        validatePreferences(restoredPreferences);
                    } catch (Exception invalidCategory) {
                        warnings.add("Some app preferences are invalid; valid categories will still be recovered.");
                    }
                } catch (Exception error) {
                    restoredPreferences = null;
                    warnings.add("Local stats and app preferences contain invalid values and were skipped.");
                }
            }
        }
        JSONObject restoredVoice = null;
        if (schemaVersion >= 5) {
            if (voiceBytes == null) {
                warnings.add("Voice preferences are missing.");
            } else {
                if (manifest.optInt("voiceSize", -1) != voiceBytes.length
                        || !manifest.optString("voiceSha256", "").equals(sha256(voiceBytes))) {
                    warnings.add("Voice preferences checksum failed; only validated values were recovered.");
                }
                try {
                    restoredVoice = new JSONObject(new String(voiceBytes, StandardCharsets.UTF_8));
                    validateVoice(restoredVoice);
                } catch (Exception error) {
                    restoredVoice = null;
                    warnings.add("Voice preferences contain invalid values and were skipped.");
                }
            }
        }
        return new BackupPayload(schemaVersion, settings, restoredPreferences, restoredVoice, warnings);
    }

    /** Secrets enter the archive only as Android-Keystore ciphertext. */
    private JSONObject capturePreferences() throws Exception {
        SharedPreferences stats = activity.getSharedPreferences(
                PREF_LOCAL_STATS, Context.MODE_PRIVATE);
        SharedPreferences team = activity.getSharedPreferences(
                PREF_TEAM, Context.MODE_PRIVATE);
        JSONArray savedArenas = new JSONArray();
        for (String endpoint : SavedArenas.INSTANCE.load(activity)) {
            savedArenas.put(endpoint);
        }
        String encryptedTeams = new TeamService(activity).encryptedVaultForBackup();
        return new JSONObject()
                .put("schemaVersion", 4)
                .put("localStats", new JSONObject()
                        .put("bestScore", Math.max(0L, stats.getLong("best_score", 0L)))
                        .put("totalKills", Math.max(0L, stats.getLong("total_kills", 0L))))
                .put("teamUi", new JSONObject()
                        .put("handoverSeconds", Math.max(0,
                                Math.min(60, team.getInt("handover_seconds", 5)))))
                .put("savedArenas", savedArenas)
                .put("savedTeams", new JSONObject()
                        .put("format", "android-keystore-aes-gcm-v1")
                        .put("vault", encryptedTeams));
    }

    /**
     * Voice settings are deliberately allow-listed. Verification email, OTPs,
     * room credentials and active SFU state never share these preferences and
     * therefore cannot accidentally enter a portable archive.
     */
    private JSONObject captureVoice() throws Exception {
        SharedPreferences source = activity.getSharedPreferences(PREF_VOICE, Context.MODE_PRIVATE);
        Map<String, ?> stored = source.getAll();
        Set<String> accountIds = new TreeSet<>();
        String[] fields = {"muted", "deafened", "volume", "audioRoute", "notifications"};
        for (String key : stored.keySet()) {
            for (String field : fields) {
                String suffix = "." + field;
                if (key.endsWith(suffix) && key.length() > suffix.length()) {
                    accountIds.add(key.substring(0, key.length() - suffix.length()));
                }
            }
        }

        JSONObject accounts = new JSONObject();
        for (String accountId : accountIds) {
            if (accountId.length() > 128) continue;
            String prefix = accountId + ".";
            String route = source.getString(prefix + "audioRoute", "system");
            if (!isVoiceRoute(route)) route = "system";
            accounts.put(accountId, new JSONObject()
                    .put("muted", source.getBoolean(prefix + "muted", true))
                    .put("deafened", source.getBoolean(prefix + "deafened", false))
                    .put("volume", Math.max(0f, Math.min(1f,
                            source.getFloat(prefix + "volume", 1f))))
                    .put("audioRoute", route)
                    .put("notifications", source.getBoolean(prefix + "notifications", true)));
        }

        SharedPreferences uiSource = activity.getSharedPreferences(PREF_VOICE_UI, Context.MODE_PRIVATE);
        JSONObject ui = new JSONObject()
                .put("compactDirectory", uiSource.getBoolean("compactDirectory", false))
                .put("showConnectionQuality", uiSource.getBoolean("showConnectionQuality", true))
                .put("hudX", Math.max(0f, Math.min(1f, uiSource.getFloat("hudX", 0f))))
                .put("hudY", Math.max(0f, Math.min(1f, uiSource.getFloat("hudY", 0f))));
        return new JSONObject()
                .put("schemaVersion", 1)
                .put("accounts", accounts)
                .put("ui", ui);
    }

    private RestoreReport restoreVoice(JSONObject value) {
        RestoreReport result = new RestoreReport();
        try {
            validateVoice(value);
            JSONObject accounts = value.getJSONObject("accounts");
            SharedPreferences.Editor accountEditor = activity
                    .getSharedPreferences(PREF_VOICE, Context.MODE_PRIVATE).edit();
            java.util.Iterator<String> ids = accounts.keys();
            int restored = 0;
            while (ids.hasNext()) {
                String accountId = ids.next();
                JSONObject state = accounts.getJSONObject(accountId);
                String prefix = accountId + ".";
                accountEditor
                        .putBoolean(prefix + "muted", state.optBoolean("muted", true))
                        .putBoolean(prefix + "deafened", state.optBoolean("deafened", false))
                        .putFloat(prefix + "volume", (float) state.optDouble("volume", 1.0))
                        .putString(prefix + "audioRoute", state.optString("audioRoute", "system"))
                        .putBoolean(prefix + "notifications", state.optBoolean("notifications", true));
                restored++;
            }
            accountEditor.apply();

            JSONObject ui = value.optJSONObject("ui");
            if (ui != null) {
                activity.getSharedPreferences(PREF_VOICE_UI, Context.MODE_PRIVATE).edit()
                        .putBoolean("compactDirectory", ui.optBoolean("compactDirectory", false))
                        .putBoolean("showConnectionQuality", ui.optBoolean("showConnectionQuality", true))
                        .putFloat("hudX", (float) ui.optDouble("hudX", 0.0))
                        .putFloat("hudY", (float) ui.optDouble("hudY", 0.0))
                        .apply();
            }
            result.restore(restored == 1 ? "Voice preference" : "Voice preferences");
        } catch (Exception invalid) {
            result.skip("Voice preferences", "voice data is invalid");
        }
        return result;
    }

    private static void validateVoice(JSONObject value) throws Exception {
        if (value.getInt("schemaVersion") != 1) {
            throw new SecurityException("Voice preferences version is unsupported");
        }
        JSONObject accounts = value.getJSONObject("accounts");
        if (accounts.length() > 128) throw new SecurityException("Too many voice accounts");
        java.util.Iterator<String> ids = accounts.keys();
        while (ids.hasNext()) {
            String accountId = ids.next();
            if (accountId.isEmpty() || accountId.length() > 128) {
                throw new SecurityException("Invalid voice account");
            }
            JSONObject state = accounts.getJSONObject(accountId);
            double volume = state.optDouble("volume", 1.0);
            String route = state.optString("audioRoute", "system");
            if (Double.isNaN(volume) || volume < 0.0 || volume > 1.0 || !isVoiceRoute(route)) {
                throw new SecurityException("Invalid voice preference");
            }
        }
        JSONObject ui = value.optJSONObject("ui");
        if (ui != null) {
            double x = ui.optDouble("hudX", 0.0);
            double y = ui.optDouble("hudY", 0.0);
            if (Double.isNaN(x) || Double.isNaN(y) || x < 0.0 || x > 1.0 || y < 0.0 || y > 1.0) {
                throw new SecurityException("Invalid voice HUD layout");
            }
        }
    }

    private static boolean isVoiceRoute(String value) {
        return "system".equals(value) || "speaker".equals(value)
                || "earpiece".equals(value) || "bluetooth".equals(value);
    }

    private RestoreReport restorePreferences(JSONObject value) {
        RestoreReport result = new RestoreReport();
        int schemaVersion = value.optInt("schemaVersion", -1);
        if (schemaVersion < 1 || schemaVersion > 4) {
            result.skip("Local stats and app preferences",
                    "preferences version is unsupported");
            return result;
        }

        JSONObject stats = value.optJSONObject("localStats");
        long bestScore = stats == null ? -1L : stats.optLong("bestScore", -1L);
        long totalKills = stats == null ? -1L : stats.optLong("totalKills", -1L);
        if (bestScore >= 0L && totalKills >= 0L) {
            activity.getSharedPreferences(PREF_LOCAL_STATS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong("best_score", bestScore)
                    .putLong("total_kills", totalKills)
                    .apply();
            result.restore("Local career stats");
        } else {
            result.skip("Local career stats", "missing or invalid values");
        }

        JSONObject team = value.optJSONObject("teamUi");
        int handover = team == null ? -1 : team.optInt("handoverSeconds", -1);
        if (handover >= 0 && handover <= 60) {
            activity.getSharedPreferences(PREF_TEAM, Context.MODE_PRIVATE)
                    .edit()
                    .putInt("handover_seconds", handover)
                    .apply();
            result.restore("Team handover preference");
        } else {
            result.skip("Team handover preference", "missing or invalid value");
        }

        if (schemaVersion >= 2) {
            JSONArray saved = value.optJSONArray("savedArenas");
            List<String> endpoints = new ArrayList<>();
            boolean valid = saved != null && saved.length() <= SavedArenas.MAX_SAVED;
            if (valid) {
                for (int index = 0; index < saved.length(); index++) {
                    String endpoint = saved.optString(index, "");
                    if (!ArenaDirectory.INSTANCE.isValidEndpoint(endpoint)
                            || endpoints.contains(endpoint)) {
                        valid = false;
                        break;
                    }
                    endpoints.add(endpoint);
                }
            }
            if (valid) {
                SavedArenas.INSTANCE.replace(activity, endpoints);
                result.restore("Saved arena IPs");
            } else {
                result.skip("Saved arena IPs", "missing or invalid values");
            }
        }
        if (schemaVersion >= 3) {
            JSONObject savedTeams = value.optJSONObject("savedTeams");
            String format = savedTeams == null ? "" : savedTeams.optString("format", "");
            String vault = savedTeams == null ? null : savedTeams.optString("vault", null);
            boolean valid = "android-keystore-aes-gcm-v1".equals(format)
                    && vault != null && vault.length() <= MAX_TEAM_VAULT_CHARS;
            if (valid && new TeamService(activity).restoreEncryptedVaultFromBackup(vault)) {
                result.restore("Saved teams and auth keys");
            } else {
                result.skip("Saved teams and auth keys",
                        "encrypted team vault is invalid or belongs to another installation");
            }
        }
        return result;
    }

    private static void validatePreferences(JSONObject value) throws Exception {
        int schemaVersion = value.getInt("schemaVersion");
        if (schemaVersion < 1 || schemaVersion > 4) {
            throw new SecurityException("Backup preferences version is unsupported");
        }
        JSONObject stats = value.getJSONObject("localStats");
        JSONObject team = value.getJSONObject("teamUi");
        long bestScore = stats.getLong("bestScore");
        long totalKills = stats.getLong("totalKills");
        int handover = team.getInt("handoverSeconds");
        if (bestScore < 0L || totalKills < 0L || handover < 0 || handover > 60) {
            throw new SecurityException("Backup preferences contain invalid values");
        }
        if (schemaVersion >= 2) {
            JSONArray saved = value.getJSONArray("savedArenas");
            if (saved.length() > SavedArenas.MAX_SAVED) {
                throw new SecurityException("Backup contains too many saved arenas");
            }
            List<String> endpoints = new ArrayList<>();
            for (int index = 0; index < saved.length(); index++) {
                String endpoint = saved.getString(index);
                if (!ArenaDirectory.INSTANCE.isValidEndpoint(endpoint)
                        || endpoints.contains(endpoint)) {
                    throw new SecurityException("Backup contains an invalid saved arena");
                }
                endpoints.add(endpoint);
            }
        }
        if (schemaVersion >= 3) {
            JSONObject savedTeams = value.getJSONObject("savedTeams");
            if (!"android-keystore-aes-gcm-v1".equals(savedTeams.getString("format"))) {
                throw new SecurityException("Backup team vault format is unsupported");
            }
            String vault = savedTeams.getString("vault");
            if (vault.length() > MAX_TEAM_VAULT_CHARS) {
                throw new SecurityException("Backup team vault is too large");
            }
        }
    }

    private boolean sameState(BackupPayload backup, byte[] currentSettings)
            throws Exception {
        if (!backup.warnings.isEmpty()
                || !sameSettings(backup.settings, currentSettings)) {
            return false;
        }
        if (backup.preferences == null) {
            return true;
        }
        JSONObject current = capturePreferences();
        JSONObject backupStats = backup.preferences.getJSONObject("localStats");
        JSONObject currentStats = current.getJSONObject("localStats");
        JSONObject backupTeam = backup.preferences.getJSONObject("teamUi");
        JSONObject currentTeam = current.getJSONObject("teamUi");
        boolean same = backupStats.getLong("bestScore") == currentStats.getLong("bestScore")
                && backupStats.getLong("totalKills") == currentStats.getLong("totalKills")
                && backupTeam.getInt("handoverSeconds")
                == currentTeam.getInt("handoverSeconds");
        if (!same || backup.preferences.optInt("schemaVersion", 1) < 2) {
            return same;
        }
        JSONArray backupSaved = backup.preferences.getJSONArray("savedArenas");
        JSONArray currentSaved = current.getJSONArray("savedArenas");
        if (backupSaved.length() != currentSaved.length()) return false;
        for (int index = 0; index < backupSaved.length(); index++) {
            if (!backupSaved.getString(index).equals(currentSaved.getString(index))) {
                return false;
            }
        }
        if (backup.preferences.optInt("schemaVersion", 1) >= 3) {
            JSONObject backupTeams = backup.preferences.getJSONObject("savedTeams");
            JSONObject currentTeams = current.getJSONObject("savedTeams");
            if (!backupTeams.getString("format").equals(currentTeams.getString("format"))
                    || !backupTeams.getString("vault").equals(currentTeams.getString("vault"))) {
                return false;
            }
        }
        return backup.voice == null || backup.voice.toString().equals(captureVoice().toString());
    }

    /**
     * Settings are append-only by project rule. Ignore the four-byte format
     * label and compare the whole older payload against the same prefix of the
     * current one, so a migration that only appends defaults does not invent a
     * difference and bother the player with a pointless restore.
     */
    private static boolean sameSettings(byte[] backup, byte[] current) {
        if (!validSettingsPayload(backup) || !validSettingsPayload(current)
                || backup.length > current.length) {
            return false;
        }
        int start = Math.min(4, backup.length);
        for (int index = start; index < backup.length; index++) {
            if (backup[index] != current[index]) {
                return false;
            }
        }
        return true;
    }

    private Uri savedTreeUri() {
        String value = preferences.getString(PREF_TREE_URI, "");
        return value == null || value.isEmpty() ? null : Uri.parse(value);
    }

    private boolean canReadTree(Uri tree) {
        try {
            Uri root = DocumentsContract.buildDocumentUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree));
            try (Cursor cursor = activity.getContentResolver().query(
                    root, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                    null, null, null)) {
                return cursor != null && cursor.moveToFirst();
            }
        } catch (Exception error) {
            preferences.edit().remove(PREF_TREE_URI).apply();
            return false;
        }
    }

    private void emit(int status, int count, String title, String detail) {
        activity.emitBackupState(status, count, title, detail);
    }

    private static boolean validSettingsPayload(byte[] value) {
        return value != null && value.length >= 4 && value.length <= MAX_SETTINGS_BYTES;
    }

    private static boolean isBackupName(String name) {
        return name != null && ((name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX))
                || (name.startsWith(LEGACY_FILE_PREFIX)
                && name.endsWith(LEGACY_FILE_SUFFIX)));
    }

    private static String backupFilename() {
        /* Android treats slash as a path separator, so the requested
         * wyrmDD/MM/YY/TTTT shape is written with safe separators. */
        SimpleDateFormat format = new SimpleDateFormat("dd-MM-yy-HHmm", Locale.US);
        return FILE_PREFIX + format.format(new Date()) + FILE_SUFFIX;
    }

    private static String settingsVersion(byte[] payload) {
        int length = 0;
        while (length < Math.min(4, payload.length) && payload[length] != 0) {
            length++;
        }
        return new String(Arrays.copyOf(payload, length), StandardCharsets.UTF_8);
    }

    private static String isoTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] bytes)
            throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] readFully(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) {
                throw new SecurityException("Backup exceeded the allowed size");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }

    private static String displayLocation(Uri tree, String filename) {
        try {
            String treeId = Uri.decode(DocumentsContract.getTreeDocumentId(tree));
            if (treeId.startsWith("primary:")) {
                String relative = treeId.substring("primary:".length());
                return "Internal storage/" + (relative.isEmpty() ? "" : relative + "/")
                        + filename;
            }
            int separator = treeId.indexOf(':');
            if (separator >= 0) {
                String volume = treeId.substring(0, separator);
                String relative = treeId.substring(separator + 1);
                return volume + "/" + (relative.isEmpty() ? "" : relative + "/")
                        + filename;
            }
        } catch (Exception ignored) {
            // A provider-specific URI still has a useful filename fallback.
        }
        return "Selected Wyrm folder/" + filename;
    }

    private String documentName(Uri document, String fallback) {
        try (Cursor cursor = activity.getContentResolver().query(document,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) return value;
            }
        } catch (Exception ignored) {
            // The requested name is still accurate for providers without metadata.
        }
        return fallback;
    }

    private static void pauseForVisibleProgress() {
        try {
            Thread.sleep(140L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void showRestoreTimeline(RestoreReport report) {
        int total = report.restored.size() + report.skipped.size();
        int shown = 0;
        StringBuilder visible = new StringBuilder();
        for (RestoreItem item : report.restored) {
            shown++;
            visible.append("Restored: ").append(item.name).append('\n');
            emit(WyrmActivity.BACKUP_RESTORING,
                    32 + (shown * 64 / Math.max(1, total)),
                    "Restored " + item.name, trimReport(visible.toString()));
            pauseForVisibleProgress();
        }
        for (RestoreItem item : report.skipped) {
            shown++;
            visible.append("Failed: ").append(item.name)
                    .append(" - ").append(item.reason).append('\n');
            emit(WyrmActivity.BACKUP_RESTORING,
                    32 + (shown * 64 / Math.max(1, total)),
                    "Could not restore " + item.name, trimReport(visible.toString()));
            pauseForVisibleProgress();
        }
    }

    private static RestoreReport parseNativeReport(String raw) {
        RestoreReport result = new RestoreReport();
        String[] lines = raw == null ? new String[0] : raw.split("\\r?\\n");
        if (lines.length == 0 || "FAILED".equals(lines[0])) {
            String reason = lines.length > 1 ? lines[1] : "native restore returned no result";
            result.skip("Native game settings", reason.replaceFirst("^Reason:\\s*", ""));
            return result;
        }
        for (int index = 1; index < lines.length; index++) {
            String[] fields = lines[index].split("\\|", 3);
            if (fields.length >= 2 && "RESTORED".equals(fields[0])) {
                result.restore(fields[1]);
            } else if (fields.length >= 2 && "SKIPPED".equals(fields[0])) {
                result.skip(fields[1], fields.length >= 3 ? fields[2] : "invalid data");
            }
        }
        if (result.restored.isEmpty() && result.skipped.isEmpty()) {
            result.skip("Native game settings", "restore report was unreadable");
        }
        return result;
    }

    private static String trimReport(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 960 ? text : text.substring(0, 957) + "...";
    }

    private static final class BackupDocument {
        final Uri uri;
        final String name;
        final long modified;

        BackupDocument(Uri uri, String name, long modified) {
            this.uri = uri;
            this.name = name;
            this.modified = modified;
        }
    }

    private static final class BackupPayload {
        final int schemaVersion;
        final byte[] settings;
        final JSONObject preferences;
        final JSONObject voice;
        final List<String> warnings;

        BackupPayload(int schemaVersion, byte[] settings, JSONObject preferences, JSONObject voice,
                      List<String> warnings) {
            this.schemaVersion = schemaVersion;
            this.settings = settings;
            this.preferences = preferences;
            this.voice = voice;
            this.warnings = warnings;
        }
    }

    private static final class RestoreItem {
        final String name;
        final String reason;

        RestoreItem(String name, String reason) {
            this.name = name;
            this.reason = reason;
        }
    }

    private static final class RestoreReport {
        final List<RestoreItem> restored = new ArrayList<>();
        final List<RestoreItem> skipped = new ArrayList<>();

        void restore(String name) {
            restored.add(new RestoreItem(name, ""));
        }

        void skip(String name, String reason) {
            skipped.add(new RestoreItem(name, reason == null ? "invalid data" : reason));
        }

        void add(RestoreReport other) {
            restored.addAll(other.restored);
            skipped.addAll(other.skipped);
        }

        String failureText() {
            if (skipped.isEmpty()) return "No safe backup category could be recovered.";
            StringBuilder text = new StringBuilder("Nothing was changed.\n");
            for (RestoreItem item : skipped) {
                text.append("- ").append(item.name).append(": ")
                        .append(item.reason).append('\n');
            }
            return trimReport(text.toString());
        }

        String finalReport() {
            StringBuilder text = new StringBuilder("Restored:\n");
            for (RestoreItem item : restored) {
                text.append("- ").append(item.name).append('\n');
            }
            if (skipped.isEmpty()) {
                text.append("Not restored: none");
            } else {
                text.append("Not restored:\n");
                for (RestoreItem item : skipped) {
                    text.append("- ").append(item.name).append(": ")
                            .append(item.reason).append('\n');
                }
            }
            return trimReport(text.toString());
        }
    }
}
