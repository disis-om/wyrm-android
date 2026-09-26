package com.wyrm.omrajput;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.wyrm.omrajput.data.NotificationPreferences;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Receives Wyrm's push notifications, whether or not the app is open.
 *
 * The backend sends data-only messages — never a "notification" payload — so
 * this is always the thing that runs, in the foreground and the background
 * alike, instead of the OS silently auto-displaying one shape in the
 * background and never calling here at all.
 */
public class WyrmMessagingService extends FirebaseMessagingService {
    private static final String TAG = "WyrmMessaging";
    public static final String ACTION_PUSH_RECEIVED = "com.wyrm.omrajput.PUSH_RECEIVED";
    public static final String CHANNEL_MESSAGES = "wyrm_messages";
    public static final String CHANNEL_ALERTS = "wyrm_alerts";
    public static final String CHANNEL_SOCIAL = "wyrm_social";
    public static final String CHANNEL_RECEIPTS = "wyrm_receipts";
    private static final String CHANNEL_GROUP = "wyrm_notifications";
    private static final String SHADE_GROUP = "wyrm";

    /**
     * Told the phone's new token directly, in plain Java.
     *
     * The rest of the app talks to the backend through {@code WyrmRepository},
     * a Kotlin suspend API — reasonable everywhere Compose is already running a
     * coroutine, but this service is not, and bridging one call across that
     * boundary is more machinery than the call itself. The session token lives
     * in the same SharedPreferences file WyrmRepository writes it to, so this
     * reads it the same way and sends the same request by hand.
     */
    @Override
    public void onNewToken(String token) {
        Executors.newSingleThreadExecutor().execute(() -> {
            SharedPreferences prefs = getApplicationContext()
                    .getSharedPreferences("wyrm_session", Context.MODE_PRIVATE);
            String session = prefs.getString("token", null);
            if (session == null) return;
            // Diagnostic kill switch; see WYRM_BACKEND_DISCONNECTED.
            if (com.wyrm.omrajput.data.WyrmRepositoryKt.WYRM_BACKEND_DISCONNECTED) return;

            try {
                URL url = new URL(BuildConfig.WYRM_API_URL + "/v1/me/device-token");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                try {
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(12_000);
                    connection.setReadTimeout(15_000);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("skip_zrok_interstitial", "1");
                    connection.setRequestProperty("Authorization", "Bearer " + session);
                    connection.setDoOutput(true);
                    byte[] body = new JSONObject().put("token", token).toString()
                            .getBytes(StandardCharsets.UTF_8);
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(body);
                    }
                    connection.getResponseCode();
                } finally {
                    connection.disconnect();
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not register device token", e);
            }
        });
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();
        String title = data.getOrDefault("title", "Wyrm");
        String body = data.getOrDefault("body", "");
        String kind = data.getOrDefault("kind", "");
        if (NotificationPreferences.isKindEnabled(this, kind)) {
            showNotification(data, title, body);
        }
        Intent signal = new Intent(ACTION_PUSH_RECEIVED);
        signal.setPackage(getPackageName());
        signal.putExtra("kind", data.getOrDefault("kind", ""));
        signal.putExtra("id", data.getOrDefault("id", ""));
        signal.putExtra("actorId", data.getOrDefault("actorId", ""));
        sendBroadcast(signal);
    }

    private void showNotification(Map<String, String> data, String title, String body) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        ensureChannels(this);

        String kind = data.getOrDefault("kind", "");
        int notificationId = notificationIdFor(data);
        Intent intent = new Intent(this, WyrmActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("wyrm.kind", kind);
        intent.putExtra("wyrm.id", data.getOrDefault("id", ""));
        intent.putExtra("wyrm.actorId", data.getOrDefault("actorId", ""));
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, notificationId, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, channelFor(kind))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setGroup(SHADE_GROUP)
                .setContentIntent(pendingIntent);

        manager.notify(notificationId, notification.build());
    }

    /** A low-priority device receipt, used only when backup work finishes off screen. */
    public static void showBackupReceipt(Context context, String title, String body) {
        if (!NotificationPreferences.isKindEnabled(context, "backup")) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        ensureChannels(context);
        Intent intent = new Intent(context, WyrmActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("wyrm.kind", "backup");
        int id = 4999;
        PendingIntent pending = PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_RECEIPTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setGroup(SHADE_GROUP)
                .setContentIntent(pending);
        manager.notify(id, notification.build());
    }

    /** Creates the four user-controllable categories once during app startup. */
    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannelGroup(new NotificationChannelGroup(
                CHANNEL_GROUP, "Wyrm"));
        createChannel(manager, CHANNEL_MESSAGES, "Messages and invites",
                NotificationManager.IMPORTANCE_HIGH);
        createChannel(manager, CHANNEL_ALERTS, "Alerts",
                NotificationManager.IMPORTANCE_HIGH);
        createChannel(manager, CHANNEL_SOCIAL, "Social and milestones",
                NotificationManager.IMPORTANCE_DEFAULT);
        createChannel(manager, CHANNEL_RECEIPTS, "Backup receipts",
                NotificationManager.IMPORTANCE_LOW);
    }

    private static void createChannel(NotificationManager manager, String id, String name,
                                      int importance) {
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setGroup(CHANNEL_GROUP);
        manager.createNotificationChannel(channel);
    }

    private static String channelFor(String kind) {
        if ("dm".equals(kind) || "invite".equals(kind) || "voice_invite".equals(kind)) {
            return CHANNEL_MESSAGES;
        }
        if ("follow".equals(kind) || "achievement".equals(kind) || "rank".equals(kind)) {
            return CHANNEL_SOCIAL;
        }
        if ("backup".equals(kind)) return CHANNEL_RECEIPTS;
        return CHANNEL_ALERTS;
    }

    /** Stable per conversation and per kind, so unrelated pushes stack. */
    private static int notificationIdFor(Map<String, String> data) {
        String kind = data.getOrDefault("kind", "alert");
        String key = "dm".equals(kind) || "invite".equals(kind) || "voice_invite".equals(kind)
                ? "dm:" + data.getOrDefault("actorId", data.getOrDefault("title", ""))
                : kind;
        return 4200 + Math.abs(key.hashCode() % 800);
    }
}
