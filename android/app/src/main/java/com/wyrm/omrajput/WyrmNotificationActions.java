package com.wyrm.omrajput;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Buttons on a Battledome notification in the shade (OM, 2026-10-01).
 * "Copy IP" copies the arena address without opening Wyrm; "Play" is an
 * activity intent handled by WyrmOverlay (it enters that event's arena, with
 * the usual "starts in" pause when the event has not begun).
 */
public final class WyrmNotificationActions extends BroadcastReceiver {
    static final String ACTION_COPY_IP = "com.wyrm.omrajput.action.COPY_IP";
    static final String EXTRA_ADDRESS = "wyrm.address";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_COPY_IP.equals(intent.getAction())) return;
        String address = intent.getStringExtra(EXTRA_ADDRESS);
        if (address == null || address.trim().isEmpty()) return;
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Battledome arena", address.trim()));
        Toast.makeText(context, "Arena IP copied", Toast.LENGTH_SHORT).show();
    }
}
