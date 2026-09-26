package com.wyrm.omrajput;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;

/**
 * Immutable, signed description of the newest published build.
 *
 * Where it is hosted is this file's business and nobody else's: none of these
 * strings reach the player, who is told only that a new version exists.
 */
final class UpdateManifest {
    static final String EXPECTED_PACKAGE = "com.wyrm.omrajput";
    static final String EXPECTED_PRODUCT = "Wyrm";
    static final String EXPECTED_AUTHOR = "OM Rajput";
    static final String EXPECTED_SOURCE = "github";
    static final String RELEASE_OWNER = "disis-om";
    static final String RELEASE_REPO = "wyrm-android";
    static final long MAX_APK_BYTES = 250L * 1024L * 1024L;

    final int schemaVersion;
    final String source;
    final String productName;
    final String packageName;
    final long versionCode;
    final String versionName;
    final long minimumVersionCode;
    final boolean mandatory;
    final String apkUrl;
    final long apkSize;
    final String sha256;
    final String publishedAt;
    final String headline;
    final String author;
    final String releaseNotes;

    private UpdateManifest(JSONObject json) throws Exception {
        schemaVersion = json.getInt("schemaVersion");
        source = json.optString("source", EXPECTED_SOURCE);
        productName = json.optString("productName", EXPECTED_PRODUCT);
        packageName = json.getString("packageName");
        versionCode = json.getLong("versionCode");
        versionName = json.getString("versionName");
        minimumVersionCode = json.optLong("minimumVersionCode", 0L);
        mandatory = json.optBoolean("mandatory", false);
        apkUrl = json.getString("apkUrl");
        apkSize = json.getLong("apkSize");
        sha256 = json.getString("sha256");
        publishedAt = json.optString("publishedAt", "");
        headline = json.optString("headline", "A new version of Wyrm");
        author = json.optString("author", EXPECTED_AUTHOR);

        JSONArray notes = json.optJSONArray("releaseNotes");
        StringBuilder text = new StringBuilder();
        if (notes != null) {
            for (int index = 0; index < notes.length(); index++) {
                String note = notes.getString(index).trim();
                if (note.isEmpty()) {
                    continue;
                }
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append("- ").append(note);
            }
        }
        releaseNotes = text.length() == 0
                ? "Performance and reliability improvements."
                : text.toString();
    }

    static UpdateManifest parse(byte[] bytes) throws Exception {
        return new UpdateManifest(new JSONObject(
                new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
    }

    void validate() throws Exception {
        if (schemaVersion != 1 || !EXPECTED_SOURCE.equals(source)) {
            throw new SecurityException("Unsupported update manifest");
        }
        if (!EXPECTED_PRODUCT.equals(productName)
                || !EXPECTED_PACKAGE.equals(packageName)
                || !EXPECTED_AUTHOR.equals(author)) {
            throw new SecurityException("Update identity does not match Wyrm");
        }
        if (versionCode <= 0L || minimumVersionCode < 0L
                || versionName.length() > 64
                || !versionName.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new SecurityException("Invalid update version");
        }
        if (apkSize <= 0L || apkSize > MAX_APK_BYTES) {
            throw new SecurityException("Invalid APK size");
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new SecurityException("Invalid APK checksum");
        }
        if (headline.trim().isEmpty() || headline.length() > 160
                || releaseNotes.length() > 16_000 || publishedAt.length() > 64) {
            throw new SecurityException("Invalid release metadata");
        }

        /*
         * The manifest may only ever point at one exact address.
         *
         * A signed manifest already proves who wrote it, but this pins where
         * the build itself comes from as well: one host, one path, built from
         * the version being offered. Nothing signed can redirect the download
         * somewhere else, even by mistake.
         */
        URL url = new URL(apkUrl);
        String expectedPath = "/" + RELEASE_OWNER + "/" + RELEASE_REPO
                + "/releases/download/v" + versionName + "/Wyrm-v" + versionName + ".apk";
        if (!"https".equalsIgnoreCase(url.getProtocol())
                || !"github.com".equalsIgnoreCase(url.getHost())
                || !expectedPath.equals(url.getPath())
                || url.getQuery() != null || url.getRef() != null) {
            throw new SecurityException("The update points somewhere unexpected");
        }
    }
}
