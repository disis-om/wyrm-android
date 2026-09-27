package com.wyrm.omrajput;

import android.content.Context;

/**
 * Which releases this phone is offered.
 *
 * Stable is `update/latest.json`, the file every Wyrm build has always read.
 * Beta is `update/beta.json` beside it, published only for test builds (marked
 * as pre-releases, versions 6.2.x and up). A player who opts in reads both and
 * is offered whichever is newer, so turning beta on never hides a stable
 * release that has overtaken the last beta. On by default since 2026-09-27
 * (OM); a player who switched it off stays off.
 */
public final class UpdateChannel {
    private static final String PREFS = UpdateManager.PREFS_NAME;
    private static final String PREF_BETA = "beta_updates";
    private static final String PREF_BACKUP_FIRST = "backup_before_update";
    private static final String PREF_OFFERED_BETA = "offered_update_is_beta";

    public static final String STABLE_MANIFEST = "latest.json";
    public static final String BETA_MANIFEST = "beta.json";

    private UpdateChannel() {}

    public static boolean isBetaEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_BETA, true);
    }

    public static void setBetaEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_BETA, enabled).apply();
    }

    /** Whether the update being offered right now came from the beta channel. */
    public static boolean isOfferedBeta(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_OFFERED_BETA, false);
    }

    static void setOfferedBeta(Context context, boolean beta) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_OFFERED_BETA, beta).apply();
    }

    /** Whether an update first writes a dated backup. On by default. */
    public static boolean isBackupBeforeUpdate(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_BACKUP_FIRST, true);
    }

    public static void setBackupBeforeUpdate(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_BACKUP_FIRST, enabled).apply();
    }
}
