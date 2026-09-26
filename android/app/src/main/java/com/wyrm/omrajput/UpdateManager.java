package com.wyrm.omrajput;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wyrm's updater.
 *
 * Where builds are published is an implementation detail and stays one: every
 * host, path and vendor name in this file is internal, and nothing the player
 * reads mentions any of it. They are told a new version exists, how big it is,
 * and how far along the download has got.
 */
final class UpdateManager {
    private static final String TAG = "WyrmUpdater";
    private static final String RELEASE_PATH =
            UpdateManifest.RELEASE_OWNER + "/" + UpdateManifest.RELEASE_REPO;
    private static final String UPDATE_BASE_URL =
            "https://raw.githubusercontent.com/" + RELEASE_PATH + "/main/update/";
    private static final String RAW_HOST = "raw.githubusercontent.com";
    private static final String GITHUB_HOST = "github.com";
    private static final String RAW_PATH_PREFIX = "/" + RELEASE_PATH + "/main/update/";

    static final String PREFS_NAME = "vlither_update_state";
    static final String PREF_LAST_SEEN_VERSION = "last_seen_version_code";
    static final String PREF_INSTALL_RESULT = "install_result";
    static final String PREF_INSTALL_MESSAGE = "install_message";
    private static final String PREF_LAST_CHECK = "last_check_ms";
    private static final String PREF_PENDING_APK = "pending_apk";
    private static final String PREF_PENDING_VERSION = "pending_version";
    private static final String PREF_SHARED_APK_URI = "shared_apk_uri";

    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_MANIFEST_BYTES = 128 * 1024;
    private static final int MAX_SIGNATURE_BYTES = 8 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final int TRANSFER_ATTEMPTS = 4;

    private final WyrmActivity activity;
    private final BackupManager backupManager;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final SharedPreferences preferences;
    private final AtomicBoolean checking = new AtomicBoolean(false);
    private final AtomicBoolean downloading = new AtomicBoolean(false);
    private static final AtomicBoolean AUTOMATIC_CHECK_STARTED =
            new AtomicBoolean(false);
    private volatile UpdateManifest availableManifest;

    UpdateManager(WyrmActivity activity, BackupManager backupManager) {
        this.activity = activity;
        this.backupManager = backupManager;
        this.preferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        removeStaleDownloads();
    }

    void onTitleScreenReady(byte[] currentSettings) {
        reportPendingInstallResult();
        recordVersionAndCheckPostUpdate(currentSettings);
        checkForUpdates(false);
    }

    void checkForUpdates(boolean userInitiated) {
        // Every fresh app process performs exactly one Home-screen check.
        // Activity/surface recreation in the same process must not duplicate it.
        if (!userInitiated && !AUTOMATIC_CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }
        if (!checking.compareAndSet(false, true)) {
            emit(WyrmActivity.UPDATE_CHECKING, 0,
                    "Checking for updates", "Already looking.", 0L, "");
            return;
        }

        emit(WyrmActivity.UPDATE_CHECKING, 0,
                "Checking for updates", "Looking for a newer version of Wyrm.", 0L, "");
        worker.execute(() -> {
            try {
                UpdateManifest manifest = fetchAndVerifyManifest();
                long installedVersion = installedVersionCode();
                preferences.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();
                if (manifest.versionCode <= installedVersion) {
                    availableManifest = null;
                    emit(WyrmActivity.UPDATE_UP_TO_DATE, 100,
                            "Wyrm is up to date",
                            "You are on " + versionName() + ".",
                            manifest.versionCode, manifest.versionName);
                    return;
                }
                availableManifest = manifest;
                emit(WyrmActivity.UPDATE_AVAILABLE, 0,
                        "Wyrm " + manifest.versionName + " is available",
                        manifest.headline + " • " + humanBytes(manifest.apkSize),
                        manifest.versionCode, manifest.versionName);
            } catch (HttpStatusException error) {
                if (error.status == HttpURLConnection.HTTP_NOT_FOUND) {
                    preferences.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();
                    availableManifest = null;
                    emit(WyrmActivity.UPDATE_UP_TO_DATE, 100,
                            "Wyrm is up to date",
                            "No newer version has been published yet.", 0L, "");
                } else {
                    handleCheckFailure(error, userInitiated);
                }
            } catch (Exception error) {
                handleCheckFailure(error, userInitiated);
            } finally {
                checking.set(false);
            }
        });
    }

    void downloadAvailableUpdate(byte[] settingsPayload) {
        UpdateManifest manifest = availableManifest;
        if (manifest == null) {
            checkForUpdates(true);
            return;
        }
        if (settingsPayload == null || settingsPayload.length == 0) {
            emitError("Backup preparation failed",
                    "Current settings could not be captured, so the update was not started.");
            return;
        }
        backupManager.createBackup(settingsPayload,
                () -> beginDownload(manifest));
    }

    void resumePendingInstall() {
        String pendingPath = preferences.getString(PREF_PENDING_APK, null);
        if (pendingPath == null || pendingPath.isEmpty()) {
            return;
        }
        File apk = new File(pendingPath);
        if (!apk.isFile()) {
            clearPendingInstall();
            return;
        }
        if (!canInstallPackages()) {
            return;
        }
        long versionCode = preferences.getLong(PREF_PENDING_VERSION, -1L);
        clearPendingInstall();
        worker.execute(() -> {
            try {
                verifyApkIdentity(apk, versionCode);
                commitInstall(apk, versionCode, "");
            } catch (Exception error) {
                Log.e(TAG, "Pending install failed", error);
                emitError("Cannot install update", safeMessage(error));
            }
        });
    }

    void shutdown() {
        worker.shutdownNow();
    }

    private void beginDownload(UpdateManifest manifest) {
        if (!downloading.compareAndSet(false, true)) {
            emit(WyrmActivity.UPDATE_DOWNLOADING, 0,
                    "Download already running", manifest.versionName,
                    manifest.versionCode, manifest.versionName);
            return;
        }
        emit(WyrmActivity.UPDATE_DOWNLOADING, 0,
                "Downloading Wyrm " + manifest.versionName,
                humanBytes(manifest.apkSize), manifest.versionCode, manifest.versionName);
        worker.execute(() -> {
            try {
                File apk = downloadApk(manifest, percent -> emit(
                        WyrmActivity.UPDATE_DOWNLOADING, percent,
                        "Downloading Wyrm " + manifest.versionName,
                        humanBytes(manifest.apkSize * percent / 100)
                                + " of " + humanBytes(manifest.apkSize),
                        manifest.versionCode, manifest.versionName));
                emit(WyrmActivity.UPDATE_VERIFYING, 100,
                        "Checking the download", "Making sure it arrived intact.",
                        manifest.versionCode, manifest.versionName);
                verifyDownloadedFile(apk, manifest);
                verifyApkIdentity(apk, manifest.versionCode);
                Uri sharedApk = publishToSharedDownloads(apk, manifest);
                preferences.edit().putString(PREF_SHARED_APK_URI, sharedApk.toString()).apply();
                requestInstallPermissionOrInstall(apk, manifest);
            } catch (UserActionPendingException ignored) {
                // Android permission UI is already visible; the user can resume afterward.
            } catch (Exception error) {
                Log.e(TAG, "Update download failed", error);
                emitError("Update failed", safeMessage(error));
            } finally {
                downloading.set(false);
            }
        });
    }

    /**
     * Asks for the manifest as it is now, not as it was five minutes ago.
     *
     * The host serves this file with `Cache-Control: max-age=300`, so a copy of
     * it sits in a cache — the network's and possibly the phone's — for five
     * minutes after every publish. That is how a player on 1.0.1 was offered
     * 1.0.2 while 1.0.3 was already out, and how the same player, restarting on
     * 1.0.2, was told they were up to date. Both were the truth as of a few
     * minutes earlier.
     *
     * A changing parameter makes it a different address, which no cache can
     * answer from memory. It weakens nothing: the manifest is signed, so a
     * cache could never have forged one — it could only ever hand back an old
     * one, which is exactly the problem being solved.
     */
    private UpdateManifest fetchAndVerifyManifest() throws Exception {
        // Stable always; beta too when the player opted in, and whichever is
        // newer wins. A missing beta file only means there is no beta yet.
        UpdateManifest stable = null;
        Exception stableError = null;
        try {
            stable = fetchAndVerifyManifest(UpdateChannel.STABLE_MANIFEST);
        } catch (Exception error) {
            stableError = error;
        }
        if (UpdateChannel.isBetaEnabled(activity)) {
            try {
                UpdateManifest beta = fetchAndVerifyManifest(UpdateChannel.BETA_MANIFEST);
                if (stable == null || beta.versionCode > stable.versionCode) return beta;
            } catch (Exception betaError) {
                if (stable == null) throw stableError;
            }
        }
        if (stable == null) throw stableError;
        return stable;
    }

    private UpdateManifest fetchAndVerifyManifest(String file) throws Exception {
        String moment = "?t=" + (System.currentTimeMillis() / 1000L);
        byte[] manifestBytes = fetchSmallFile(UPDATE_BASE_URL + file + moment, MAX_MANIFEST_BYTES);
        byte[] encodedSignature = fetchSmallFile(UPDATE_BASE_URL + file + ".sig" + moment, MAX_SIGNATURE_BYTES);
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.decode(
                    new String(encodedSignature, StandardCharsets.US_ASCII).trim(),
                    Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            throw new SecurityException("Update signature encoding is invalid", error);
        }

        java.security.Signature verifier = java.security.Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(loadManifestPublicKey());
        verifier.update(manifestBytes);
        if (!verifier.verify(signatureBytes)) {
            throw new SecurityException("The update manifest is not signed by Wyrm");
        }
        UpdateManifest manifest = UpdateManifest.parse(manifestBytes);
        manifest.validate();
        return manifest;
    }

    private PublicKey loadManifestPublicKey() throws Exception {
        byte[] pemBytes;
        try (InputStream input = activity.getResources().openRawResource(
                R.raw.wyrm_update_manifest_public)) {
            pemBytes = readFully(input, 16 * 1024);
        }
        String pem = new String(pemBytes, StandardCharsets.US_ASCII)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.decode(pem, Base64.DEFAULT);
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
    }

    private byte[] fetchSmallFile(String address, int limit) throws Exception {
        URL url = new URL(address);
        validateMetadataUrl(url);
        HttpURLConnection connection = basicConnection(url);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new HttpStatusException(status, "The update service returned HTTP " + status);
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                return readFully(input, limit);
            }
        } finally {
            connection.disconnect();
        }
    }

    private File downloadApk(UpdateManifest manifest, ProgressCallback callback)
            throws Exception {
        File directory = new File(activity.getCacheDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create the secure update staging area");
        }
        File partial = new File(directory, "vlither-" + manifest.versionCode + ".apk.part");
        File complete = new File(directory, "vlither-" + manifest.versionCode + ".apk");
        if (complete.isFile() && complete.length() == manifest.apkSize) {
            return complete;
        }

        Exception lastError = null;
        for (int attempt = 1; attempt <= TRANSFER_ATTEMPTS; attempt++) {
            try {
                transferApk(manifest, partial, callback);
                lastError = null;
                break;
            } catch (Exception error) {
                lastError = error;
                if (!isRetryableTransferError(error) || attempt == TRANSFER_ATTEMPTS) {
                    throw error;
                }
                Thread.sleep(Math.min(2_000L, 400L * attempt));
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        if (partial.length() != manifest.apkSize) {
            throw new IllegalStateException(
                    "Download incomplete: " + partial.length() + " of " + manifest.apkSize);
        }
        deleteQuietly(complete);
        if (!partial.renameTo(complete)) {
            throw new IllegalStateException("Could not finalize the downloaded APK");
        }
        return complete;
    }

    private void transferApk(UpdateManifest manifest, File partial,
                             ProgressCallback callback) throws Exception {
        long existing = partial.isFile() ? partial.length() : 0L;
        if (existing < 0L || existing > manifest.apkSize) {
            deleteQuietly(partial);
            existing = 0L;
        }

        URL current = new URL(manifest.apkUrl);
        validateInitialApkUrl(current, manifest);
        HttpURLConnection connection = null;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            connection = basicConnection(current);
            if (existing > 0L) {
                connection.setRequestProperty("Range", "bytes=" + existing + "-");
            }
            int status = connection.getResponseCode();
            if (!isRedirect(status)) {
                break;
            }
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || redirects == MAX_REDIRECTS) {
                throw new SecurityException("The download redirect chain is invalid");
            }
            current = new URL(current, location);
            validateRedirectUrl(current);
            connection = null;
        }
        if (connection == null) {
            throw new IOException("Could not open the download stream");
        }

        try {
            int status = connection.getResponseCode();
            boolean append = existing > 0L && status == HttpURLConnection.HTTP_PARTIAL;
            if (status != HttpURLConnection.HTTP_OK
                    && status != HttpURLConnection.HTTP_PARTIAL) {
                throw new HttpStatusException(status, "APK download returned HTTP " + status);
            }
            if (!append) {
                existing = 0L;
            }

            long total = existing;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new FileOutputStream(partial, append)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > manifest.apkSize || total > UpdateManifest.MAX_APK_BYTES) {
                        throw new SecurityException("Downloaded APK exceeded the signed size");
                    }
                    output.write(buffer, 0, count);
                    callback.onProgress((int) Math.min(100L,
                            total * 100L / manifest.apkSize));
                }
                output.flush();
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection basicConnection(URL url) throws Exception {
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new SecurityException("Update transport must use HTTPS");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("User-Agent", "Wyrm-Android-Updater/2.0");
        // Belt to the braces above: nothing on the way back should be answered
        // from a copy somebody kept. An update check is only worth making if it
        // reports the present.
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Pragma", "no-cache");
        return connection;
    }

    private void validateMetadataUrl(URL url) {
        // The host and the path are what make this address trustworthy, and
        // both are still pinned. The only query permitted is the timestamp
        // this class adds itself to defeat caching — nothing arriving from
        // outside can put anything in it.
        String query = url.getQuery();
        boolean queryAllowed = query == null || query.matches("t=[0-9]+");
        if (!"https".equalsIgnoreCase(url.getProtocol())
                || !RAW_HOST.equalsIgnoreCase(url.getHost())
                || !url.getPath().startsWith(RAW_PATH_PREFIX)
                || !queryAllowed || url.getRef() != null) {
            throw new SecurityException("Update metadata URL is not trusted");
        }
    }

    private void validateInitialApkUrl(URL url, UpdateManifest manifest) {
        if (!manifest.apkUrl.equals(url.toString())
                || !"https".equalsIgnoreCase(url.getProtocol())
                || !GITHUB_HOST.equalsIgnoreCase(url.getHost())) {
            throw new SecurityException("APK download URL is not trusted");
        }
    }

    private void validateRedirectUrl(URL url) {
        String host = url.getHost().toLowerCase(Locale.US);
        boolean trustedHost = host.equals("release-assets.githubusercontent.com")
                || host.equals("objects.githubusercontent.com")
                || host.equals("github-releases.githubusercontent.com")
                || host.endsWith(".githubusercontent.com");
        if (!"https".equalsIgnoreCase(url.getProtocol()) || !trustedHost) {
            throw new SecurityException("The download was redirected to an untrusted host");
        }
    }

    private void verifyDownloadedFile(File apk, UpdateManifest manifest) throws Exception {
        if (apk.length() != manifest.apkSize) {
            throw new SecurityException("The APK size does not match the signed manifest");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(apk))) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        if (!manifest.sha256.equals(toHex(digest.digest()))) {
            deleteQuietly(apk);
            throw new SecurityException("The downloaded APK checksum is invalid");
        }
    }

    private void verifyApkIdentity(File apk, long expectedVersionCode) throws Exception {
        PackageManager packageManager = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo archive = packageManager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed = packageManager.getPackageInfo(UpdateManifest.EXPECTED_PACKAGE, flags);
        if (Build.VERSION.SDK_INT >= 28 && archive != null && archive.signingInfo == null) {
            //noinspection deprecation
            archive = packageManager.getPackageArchiveInfo(
                    apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        }
        if (Build.VERSION.SDK_INT >= 28 && installed.signingInfo == null) {
            //noinspection deprecation
            installed = packageManager.getPackageInfo(
                    UpdateManifest.EXPECTED_PACKAGE, PackageManager.GET_SIGNATURES);
        }
        if (archive == null || !UpdateManifest.EXPECTED_PACKAGE.equals(archive.packageName)) {
            throw new SecurityException("The APK package identity is invalid");
        }
        long archiveVersion = Build.VERSION.SDK_INT >= 28
                ? archive.getLongVersionCode() : archive.versionCode;
        if (archiveVersion != expectedVersionCode
                || archiveVersion <= installedVersionCode()) {
            throw new SecurityException("The APK version is not a valid upgrade");
        }
        if (!signaturesOf(archive).equals(signaturesOf(installed))) {
            throw new SecurityException("The APK signing certificate does not match this app");
        }
    }

    private Set<String> signaturesOf(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            SigningInfo signingInfo = info.signingInfo;
            signatures = signingInfo.hasMultipleSigners()
                    ? signingInfo.getApkContentsSigners()
                    : signingInfo.getSigningCertificateHistory();
        } else {
            //noinspection deprecation
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) {
            throw new SecurityException("APK has no signing certificate");
        }
        Set<String> result = new HashSet<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures) {
            result.add(toHex(digest.digest(signature.toByteArray())));
            digest.reset();
        }
        return result;
    }

    private Uri publishToSharedDownloads(File apk, UpdateManifest manifest) throws Exception {
        String displayName = "Wyrm-v" + manifest.versionName + ".apk";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver resolver = activity.getContentResolver();
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String relativePath = Environment.DIRECTORY_DOWNLOADS + "/Wyrm";
            deleteOwnedDownload(resolver, collection, displayName, relativePath + "/");
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            values.put(MediaStore.MediaColumns.MIME_TYPE,
                    "application/vnd.android.package-archive");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri item = resolver.insert(collection, values);
            if (item == null) {
                throw new IOException("Could not create Download/Wyrm");
            }
            try {
                try (InputStream input = new BufferedInputStream(new FileInputStream(apk));
                     OutputStream output = resolver.openOutputStream(item, "w")) {
                    if (output == null) {
                        throw new IOException("The shared Downloads file is not writable");
                    }
                    copy(input, output);
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(item, ready, null, null);
                return item;
            } catch (Exception error) {
                resolver.delete(item, null, null);
                throw error;
            }
        }

        if (!activity.hasLegacyStoragePermission()) {
            emit(WyrmActivity.UPDATE_READY_TO_INSTALL, 100,
                    "Storage permission required",
                    "Allow storage access, then tap Download & Install again.",
                    manifest.versionCode, manifest.versionName);
            activity.requestLegacyStoragePermission();
            throw new UserActionPendingException();
        }
        File directory = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Wyrm");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not recreate Download/Wyrm");
        }
        File destination = new File(directory, displayName);
        try (InputStream input = new BufferedInputStream(new FileInputStream(apk));
             OutputStream output = new FileOutputStream(destination, false)) {
            copy(input, output);
        }
        return Uri.fromFile(destination);
    }

    private void deleteOwnedDownload(ContentResolver resolver, Uri collection,
                                     String displayName, String relativePath) {
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        try (Cursor cursor = resolver.query(collection,
                new String[]{MediaStore.MediaColumns._ID}, selection,
                new String[]{displayName, relativePath}, null)) {
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext()) {
                resolver.delete(ContentUris.withAppendedId(collection, cursor.getLong(0)),
                        null, null);
            }
        } catch (Exception error) {
            Log.i(TAG, "Existing shared APK could not be replaced directly", error);
        }
    }

    private void requestInstallPermissionOrInstall(File apk, UpdateManifest manifest) {
        preferences.edit()
                .putString(PREF_PENDING_APK, apk.getAbsolutePath())
                .putLong(PREF_PENDING_VERSION, manifest.versionCode)
                .apply();
        if (!canInstallPackages()) {
            emit(WyrmActivity.UPDATE_READY_TO_INSTALL, 100,
                    "One permission needed",
                    "Android asks once before Wyrm may update itself.",
                    manifest.versionCode, manifest.versionName);
            activity.runOnUiThread(() -> activity.startActivity(new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()))));
            return;
        }
        clearPendingInstall();
        worker.execute(() -> {
            try {
                commitInstall(apk, manifest.versionCode, manifest.versionName);
            } catch (Exception error) {
                Log.e(TAG, "PackageInstaller commit failed", error);
                emitError("Cannot install update", safeMessage(error));
            }
        });
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < 26
                || activity.getPackageManager().canRequestPackageInstalls();
    }

    private void commitInstall(File apk, long versionCode, String versionName)
            throws Exception {
        emit(WyrmActivity.UPDATE_INSTALLING, 100,
                "Restarting", "Wyrm will come back on " + versionName + ".",
                versionCode, versionName);
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(UpdateManifest.EXPECTED_PACKAGE);
        params.setSize(apk.length());
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(
                    PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream input = new BufferedInputStream(new FileInputStream(apk));
                 OutputStream output = session.openWrite(
                         "wyrm-update.apk", 0L, apk.length())) {
                copy(input, output);
                session.fsync(output);
            }
            Intent resultIntent = new Intent(activity, UpdateInstallReceiver.class)
                    .setAction("com.wyrm.omrajput.UPDATE_INSTALL_RESULT")
                    .putExtra("session_id", sessionId);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent result = PendingIntent.getBroadcast(
                    activity, sessionId, resultIntent, flags);
            session.commit(result.getIntentSender());
        } catch (Exception error) {
            installer.abandonSession(sessionId);
            throw error;
        }
    }

    private void reportPendingInstallResult() {
        int result = preferences.getInt(PREF_INSTALL_RESULT, Integer.MIN_VALUE);
        if (result == Integer.MIN_VALUE) {
            return;
        }
        String message = preferences.getString(PREF_INSTALL_MESSAGE, "");
        preferences.edit().remove(PREF_INSTALL_RESULT).remove(PREF_INSTALL_MESSAGE).apply();
        if (result == PackageInstaller.STATUS_SUCCESS) {
            emit(WyrmActivity.UPDATE_UP_TO_DATE, 100,
                    "Updated", "Wyrm is on " + versionName() + ".", 0L, "");
        } else {
            emitError("Installation did not complete",
                    message.isEmpty() ? "Android installer status " + result : message);
        }
    }

    private void recordVersionAndCheckPostUpdate(byte[] currentSettings) {
        try {
            long current = installedVersionCode();
            long previous = preferences.getLong(PREF_LAST_SEEN_VERSION, 0L);
            preferences.edit().putLong(PREF_LAST_SEEN_VERSION, current).apply();
            if (previous > 0L && current > previous) {
                backupManager.checkAfterUpdate(currentSettings);
            }
        } catch (Exception error) {
            Log.w(TAG, "Could not record installed version", error);
        }
    }

    private void handleCheckFailure(Exception error, boolean userInitiated) {
        Log.e(TAG, "Update check failed", error);
        String detail = safeMessage(error);
        emit(WyrmActivity.UPDATE_ERROR, 0,
                userInitiated ? "Update check failed" : "Update check unavailable",
                detail, 0L, "");
    }

    private void emitError(String title, String detail) {
        emit(WyrmActivity.UPDATE_ERROR, 0, title, detail, 0L, "");
    }

    private void emit(int status, int progress, String title, String detail,
                      long versionCode, String versionName) {
        activity.emitUpdateState(status, progress, title, detail,
                versionCode, versionName);
    }

    private long installedVersionCode() throws Exception {
        PackageInfo info = activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private String versionName() {
        try {
            PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "unknown" : info.versionName;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private void removeStaleDownloads() {
        File directory = new File(activity.getCacheDir(), "updates");
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        String pending = preferences.getString(PREF_PENDING_APK, "");
        long cutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L;
        for (File file : files) {
            if (!file.getAbsolutePath().equals(pending) && file.lastModified() < cutoff) {
                deleteQuietly(file);
            }
        }
    }

    private void clearPendingInstall() {
        preferences.edit().remove(PREF_PENDING_APK).remove(PREF_PENDING_VERSION).apply();
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307 || status == 308;
    }

    private static boolean isRetryableTransferError(Exception error) {
        return error instanceof IOException
                || (error instanceof HttpStatusException
                && ((HttpStatusException) error).status >= 500);
    }

    private static byte[] readFully(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) {
                throw new SecurityException("The response exceeded the allowed size");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        output.flush();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "Could not delete " + file);
        }
    }

    private interface ProgressCallback {
        void onProgress(int percent);
    }

    private static final class UserActionPendingException extends Exception {
    }

    private static final class HttpStatusException extends Exception {
        final int status;

        HttpStatusException(int status, String message) {
            super(message + " (" + status + ")");
            this.status = status;
        }
    }
}
