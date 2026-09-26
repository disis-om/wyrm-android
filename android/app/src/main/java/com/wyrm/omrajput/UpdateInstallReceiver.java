package com.wyrm.omrajput;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.os.Build;

/** Receives PackageInstaller results without placing a touch-blocking Java dialog over SDL. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= 33) {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                //noinspection deprecation
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmation);
            } else {
                saveResult(context, PackageInstaller.STATUS_FAILURE,
                        "Android installer confirmation was unavailable");
            }
            return;
        }

        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (message == null || message.trim().isEmpty()) {
            message = status == PackageInstaller.STATUS_SUCCESS
                    ? "Installed successfully" : "Android installer status " + status;
        }
        saveResult(context, status, message);
    }

    private static void saveResult(Context context, int status, String message) {
        SharedPreferences preferences = context.getSharedPreferences(
                UpdateManager.PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit()
                .putInt(UpdateManager.PREF_INSTALL_RESULT, status)
                .putString(UpdateManager.PREF_INSTALL_MESSAGE, message)
                .apply();
    }
}
