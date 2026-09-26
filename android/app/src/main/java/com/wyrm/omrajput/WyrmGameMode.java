package com.wyrm.omrajput;

import android.app.Activity;
import android.os.Build;
import android.util.Log;

/**
 * Tells Android what part of Wyrm the player has reached.
 *
 * The package is a game, but a fresh account is not playing while it is still
 * signing in or choosing its identity. Compose owns that eligibility decision;
 * the native screen bridge only distinguishes the Home shell from live arena
 * gameplay. Android still owns the user's Performance/Battery/Standard choice.
 */
final class WyrmGameMode {
    private static final String TAG = "Wyrm";

    private final Object manager;
    private boolean resumed;
    private boolean eligible;
    private boolean gameplay;
    private int publishedState = -1;
    private int reportedMode = Integer.MIN_VALUE;

    WyrmGameMode(Activity activity) {
        manager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? Api31.manager(activity)
                : null;
    }

    void onResume() {
        resumed = true;
        publish();
    }

    void onPause() {
        resumed = false;
        publish();
    }

    void setEligible(boolean value) {
        if (eligible == value) return;
        eligible = value;
        if (!eligible) gameplay = false;
        publish();
    }

    void setGameplay(boolean value) {
        if (gameplay == value) return;
        gameplay = value;
        publish();
    }

    private void publish() {
        if (manager == null) return;

        int mode = Api31.currentMode(manager);
        if (mode != reportedMode) {
            reportedMode = mode;
            Log.i(TAG, "Android game mode: " + Api31.modeName(mode));
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        int state = !resumed || !eligible
                ? Api33.INACTIVE
                : gameplay ? Api33.GAMEPLAY : Api33.CONTENT;
        if (publishedState == state) return;
        Api33.publish(manager, state);
        publishedState = state;
        Log.i(TAG, "Android game state: " + Api33.stateName(state));
    }

    /** Kept behind an API fence so Android 8-11 never resolve GameManager. */
    private static final class Api31 {
        static Object manager(Activity activity) {
            return activity.getSystemService(android.app.GameManager.class);
        }

        static int currentMode(Object value) {
            return ((android.app.GameManager) value).getGameMode();
        }

        static String modeName(int mode) {
            switch (mode) {
                case android.app.GameManager.GAME_MODE_PERFORMANCE:
                    return "performance";
                case android.app.GameManager.GAME_MODE_BATTERY:
                    return "battery";
                case android.app.GameManager.GAME_MODE_CUSTOM:
                    return "custom";
                case android.app.GameManager.GAME_MODE_STANDARD:
                    return "standard";
                default:
                    return "unsupported";
            }
        }
    }

    /** GameState was added two releases after GameManager. */
    private static final class Api33 {
        static final int INACTIVE = 0;
        static final int CONTENT = 1;
        static final int GAMEPLAY = 2;

        static void publish(Object value, int state) {
            int mode = state == GAMEPLAY
                    ? android.app.GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE
                    : state == CONTENT
                            ? android.app.GameState.MODE_CONTENT
                            : android.app.GameState.MODE_NONE;
            ((android.app.GameManager) value).setGameState(
                    new android.app.GameState(false, mode));
        }

        static String stateName(int state) {
            if (state == GAMEPLAY) return "gameplay";
            if (state == CONTENT) return "home";
            return "inactive";
        }
    }
}
