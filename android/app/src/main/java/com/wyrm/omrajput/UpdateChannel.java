package com.wyrm.omrajput;

import android.content.Context;

/**
 * Which releases this phone is offered.
 *
 * Stable is `update/latest.json`, the file every Wyrm build has always read.
 * Beta is `update/beta.json` beside it, published only for test builds (marked
 * as pre-releases, versions 6.2.x and up). A player who opts in reads both and
 * is offered whichever is newer, so turning beta on never hides a stable
 * release that has overtaken the last beta. Off by default.
 */
public final class UpdateChannel {
    private static final String PREFS = UpdateManager.PREFS_NAME;
    private static final String PREF_BETA = "beta_updates";

    public static final String STABLE_MANIFEST = "latest.json";
    public static final String BETA_MANIFEST = "beta.json";

    private UpdateChannel() {}

    public static boolean isBetaEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_BETA, false);
    }

    public static void setBetaEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_BETA, enabled).apply();
    }
}
